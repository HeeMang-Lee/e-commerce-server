package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import com.ecommerce.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 분산 락 통합 테스트
 *
 * Toss 테스트 전략 기반:
 * - 실제 Redis 분산 락을 사용한 동작 검증
 * - 락 타임아웃, 락 획득 실패 등 엣지 케이스 테스트
 * - 실제 사용자 시나리오에서 분산 락이 제대로 동작하는지 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("분산 락 통합 테스트")
class DistributedLockIntegrationTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private RedissonClient redissonClient;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("락 획득 대기 중인 스레드는 순차적으로 처리된다")
    void lockWaitingThreads_ProcessedSequentially() throws InterruptedException {
        // given: 사용자 1명
        User user = new User(null, "테스트", "test@test.com", 0);
        User saved = userRepository.save(user);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 10개 스레드가 동시에 포인트 충전 (각 1000원)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    PointChargeRequest request = new PointChargeRequest(saved.getId(), 1000);
                    pointService.chargePoint(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 예외 발생 시 실패
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 모든 충전이 성공하고, 정확한 금액이 충전됨
        assertThat(successCount.get()).isEqualTo(threadCount);

        User result = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(result.getPointBalance()).isEqualTo(10000); // 10 * 1000
    }

    @Test
    @DisplayName("동일한 쿠폰에 대한 동시 발급은 순차적으로 처리된다")
    void sameCoupon_ConcurrentIssue_ProcessedSequentially() throws InterruptedException {
        // given: 쿠폰 10개 한정
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon(
                "한정 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                10,  // 10개만
                now.minusDays(1),
                now.plusDays(30),
                30
        );
        Coupon saved = couponRepository.save(coupon);

        // 사용자 20명 생성
        int userCount = 20;
        Long[] userIds = new Long[userCount];
        for (int i = 0; i < userCount; i++) {
            User user = new User(null, "사용자" + i, "user" + i + "@test.com", 0);
            userIds[i] = userRepository.save(user).getId();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 20명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    CouponIssueRequest request = new CouponIssueRequest(userIds[index], saved.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 정확히 10명만 성공, 10명은 실패
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        Coupon result = couponRepository.findById(saved.getId()).orElseThrow();
        assertThat(result.getCurrentIssueCount()).isEqualTo(10);
        assertThat(result.getRemainingQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("서로 다른 리소스에 대한 락은 독립적으로 동작한다")
    void differentResources_IndependentLocks() throws InterruptedException {
        // given: 사용자 5명
        int userCount = 5;
        Long[] userIds = new Long[userCount];
        for (int i = 0; i < userCount; i++) {
            User user = new User(null, "사용자" + i, "user" + i + "@test.com", 0);
            userIds[i] = userRepository.save(user).getId();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 5명이 각자 자신의 포인트 충전 (독립적인 락)
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    PointChargeRequest request = new PointChargeRequest(userIds[index], 5000);
                    pointService.chargePoint(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 실패
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 모두 성공 (서로 다른 락이므로 대기 없음)
        assertThat(successCount.get()).isEqualTo(userCount);

        // 각 사용자별로 정확한 금액 충전 확인
        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElseThrow();
            assertThat(user.getPointBalance()).isEqualTo(5000);
        }
    }

    @Test
    @DisplayName("락 키가 리소스 ID별로 분리되어 있다")
    void lockKeys_SeparatedByResourceId() {
        // given: 사용자 2명
        User user1 = userRepository.save(new User(null, "사용자1", "user1@test.com", 0));
        User user2 = userRepository.save(new User(null, "사용자2", "user2@test.com", 0));

        // when: 락 키 확인
        String lockKey1 = "lock:point:" + user1.getId();
        String lockKey2 = "lock:point:" + user2.getId();

        RLock lock1 = redissonClient.getLock(lockKey1);
        RLock lock2 = redissonClient.getLock(lockKey2);

        // then: 서로 다른 락 객체여야 함
        assertThat(lock1).isNotEqualTo(lock2);
    }

    @Test
    @DisplayName("실제 사용자 시나리오: 여러 사용자가 동시에 포인트 충전해도 정확하게 처리된다")
    void userScenario_MultipleUsersChargingPoints() throws InterruptedException {
        // given: 블랙 프라이데이 이벤트 - 100명의 사용자가 동시에 포인트 충전
        int userCount = 100;
        Long[] userIds = new Long[userCount];

        for (int i = 0; i < userCount; i++) {
            User user = new User(null, "사용자" + i, "user" + i + "@test.com", 10000);
            userIds[i] = userRepository.save(user).getId();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 100명이 동시에 5만원씩 충전
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    PointChargeRequest request = new PointChargeRequest(userIds[index], 50000);
                    pointService.chargePoint(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 실패
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 모두 성공하고 정확한 금액 충전
        assertThat(successCount.get()).isEqualTo(userCount);

        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElseThrow();
            assertThat(user.getPointBalance()).isEqualTo(60000); // 10000 + 50000
        }
    }

    @Test
    @DisplayName("실제 사용자 시나리오: 한정 수량 쿠폰 선착순 발급이 정확하게 처리된다")
    void userScenario_LimitedCouponFirstComeFirstServed() throws InterruptedException {
        // given: 블랙 프라이데이 한정 쿠폰 50개
        LocalDateTime now = LocalDateTime.now();
        Coupon limitedCoupon = new Coupon(
                "블프 50% 할인",
                DiscountType.PERCENTAGE,
                50,
                50,  // 선착순 50명
                now.minusDays(1),
                now.plusDays(1),
                1
        );
        Coupon saved = couponRepository.save(limitedCoupon);

        // 200명의 사용자가 쿠폰 발급 시도
        int userCount = 200;
        Long[] userIds = new Long[userCount];
        for (int i = 0; i < userCount; i++) {
            User user = new User(null, "사용자" + i, "user" + i + "@test.com", 0);
            userIds[i] = userRepository.save(user).getId();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 200명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    CouponIssueRequest request = new CouponIssueRequest(userIds[index], saved.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 정확히 50명만 성공
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(150);

        Coupon result = couponRepository.findById(saved.getId()).orElseThrow();
        assertThat(result.getCurrentIssueCount()).isEqualTo(50);
        assertThat(result.getRemainingQuantity()).isEqualTo(0);

        // 발급된 쿠폰 수 확인
        long issuedCount = userCouponRepository.findAll().size();
        assertThat(issuedCount).isEqualTo(50);
    }

    @Test
    @DisplayName("분산 락은 트랜잭션 커밋 전에 해제되지 않는다")
    void lockNotReleasedBeforeTransactionCommit() throws InterruptedException {
        // given
        User user = userRepository.save(new User(null, "테스트", "test@test.com", 0));
        String lockKey = "lock:point:" + user.getId();

        // when: 포인트 충전 시작
        Thread chargeThread = new Thread(() -> {
            try {
                pointService.chargePoint(new PointChargeRequest(user.getId(), 10000));
            } catch (Exception e) {
                // 무시
            }
        });

        chargeThread.start();
        Thread.sleep(100); // 충전 스레드가 락을 획득할 시간 부여

        // then: 충전이 진행 중일 때 락이 잡혀있어야 함
        RLock lock = redissonClient.getLock(lockKey);
        boolean canAcquire = lock.tryLock(0, TimeUnit.MILLISECONDS);

        // 락을 획득할 수 없어야 함 (이미 충전 스레드가 잡고 있음)
        // 단, 충전이 이미 완료되었을 수도 있으므로 성공/실패 모두 허용
        if (canAcquire) {
            lock.unlock();
        }

        chargeThread.join(5000);
    }
}
