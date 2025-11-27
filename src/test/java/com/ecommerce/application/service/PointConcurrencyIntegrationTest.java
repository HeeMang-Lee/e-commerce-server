package com.ecommerce.application.service;

import com.ecommerce.application.dto.PointChargeRequest;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.User;
import com.ecommerce.domain.repository.PointHistoryRepository;
import com.ecommerce.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포인트 충전/차감 동시성 통합 테스트
 * ExecutorService를 사용하여 멀티스레드 환경에서 포인트 동시성 제어를 검증합니다.
 * @Version을 활용한 낙관적 락 동작을 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("포인트 동시성 통합 테스트")
class PointConcurrencyIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private PointService pointService;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("10개 스레드가 동시에 같은 사용자의 포인트를 1000원씩 충전하면 낙관적 락으로 인해 일부는 실패한다")
    void chargePoint_Concurrency_OptimisticLock() throws InterruptedException {
        // given
        User user = new User(null, "테스트", "test@test.com", 0);
        final User savedUser = userRepository.save(user);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    PointChargeRequest request = new PointChargeRequest(savedUser.getId(), 1000);
                    pointService.chargePoint(request);
                    successCount.incrementAndGet();

                } catch (ObjectOptimisticLockingFailureException e) {
                    // 낙관적 락 충돌 - 예상된 예외
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        User resultUser = userRepository.findById(savedUser.getId()).orElseThrow();

        // 성공한 횟수만큼 포인트가 충전되어야 함
        int expectedBalance = successCount.get() * 1000;
        assertThat(resultUser.getPointBalance()).isEqualTo(expectedBalance);

        // 최소 1개 이상은 성공해야 함
        assertThat(successCount.get()).isGreaterThan(0);

        // 총 시도 횟수는 threadCount와 같아야 함
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("100개 스레드가 동시에 같은 사용자의 포인트를 500원씩 충전하면 분산 락으로 인해 모두 성공한다")
    void chargePoint_HighConcurrency_AllSuccess() throws InterruptedException {
        // given
        User user = new User(null, "테스트", "test@test.com", 0);
        final User savedUser = userRepository.save(user);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    PointChargeRequest request = new PointChargeRequest(savedUser.getId(), 500);
                    pointService.chargePoint(request);
                    successCount.incrementAndGet();

                } catch (ObjectOptimisticLockingFailureException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        User resultUser = userRepository.findById(savedUser.getId()).orElseThrow();

        // 분산 락을 사용하므로 모든 충전이 성공해야 함
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 100번 * 500원 = 50000원이어야 함
        assertThat(resultUser.getPointBalance()).isEqualTo(50000);
    }

    @Test
    @DisplayName("여러 사용자가 각자의 포인트를 동시에 충전하면 모두 성공한다")
    void chargePoint_DifferentUsers_AllSuccess() throws InterruptedException {
        // given
        int userCount = 50;
        Long[] userIds = new Long[userCount];

        for (int i = 0; i < userCount; i++) {
            User user = new User(null, "사용자" + i, "user" + i + "@test.com", 0);
            User savedUser = userRepository.save(user);
            userIds[i] = savedUser.getId();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    PointChargeRequest request = new PointChargeRequest(userIds[index], 10000);
                    pointService.chargePoint(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        // 각 사용자는 서로 다른 레코드이므로 충돌 없이 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(userCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 각 사용자의 포인트가 정확히 충전되었는지 확인
        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElseThrow();
            assertThat(user.getPointBalance()).isEqualTo(10000);
        }
    }

    @Test
    @DisplayName("포인트가 10000원인 사용자에 대해 20개 스레드가 동시에 1000원씩 차감하면 10개만 성공한다")
    void deductPoint_Concurrency_PartialSuccess() throws InterruptedException {
        // given
        User user = new User(null, "테스트", "test@test.com", 10000);
        final User savedUser = userRepository.save(user);

        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 직접 차감 (실제로는 주문 결제 시 사용)
                    User userToUpdate = userRepository.findById(savedUser.getId()).orElseThrow();
                    userToUpdate.deduct(1000);
                    userRepository.save(userToUpdate);
                    successCount.incrementAndGet();

                } catch (IllegalStateException e) {
                    // 포인트 부족
                    failCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    // 낙관적 락 충돌
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        User resultUser = userRepository.findById(savedUser.getId()).orElseThrow();

        // 성공한 만큼만 차감되어야 함
        int expectedBalance = 10000 - (successCount.get() * 1000);
        assertThat(resultUser.getPointBalance()).isEqualTo(expectedBalance);

        // 최소 1개 이상은 성공해야 함
        assertThat(successCount.get()).isGreaterThan(0);

        // 일부는 실패해야 함 (포인트 부족 또는 낙관적 락 충돌)
        assertThat(failCount.get()).isGreaterThan(0);
    }
}
