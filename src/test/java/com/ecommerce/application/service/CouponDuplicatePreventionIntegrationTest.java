package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.config.TestcontainersConfig;
import com.ecommerce.domain.entity.*;
import com.ecommerce.domain.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 쿠폰 중복 발급 방지 통합 테스트
 * UNIQUE 제약 조건(user_id, coupon_id)이 제대로 동작하는지 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("쿠폰 중복 발급 방지 통합 테스트")
class CouponDuplicatePreventionIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private User testUser;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = new User(null, "테스트사용자", "test@example.com", 50000);
        userRepository.save(testUser);

        // 테스트 쿠폰 생성
        testCoupon = new Coupon(
                "10% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                10,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                30
        );
        couponRepository.save(testCoupon);
    }

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 사용자가 같은 쿠폰을 중복 발급받을 수 없다")
    void cannotIssueDuplicateCoupon() {
        // given
        CouponIssueRequest request = new CouponIssueRequest(testUser.getId(), testCoupon.getId());

        // when: 첫 번째 발급 성공
        var firstIssue = couponService.issueCoupon(request);
        assertThat(firstIssue.id()).isNotNull();

        // then: 두 번째 발급 시도 실패
        assertThatThrownBy(() -> couponService.issueCoupon(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");
    }

    @Test
    @DisplayName("다른 사용자는 같은 쿠폰을 발급받을 수 있다")
    void differentUsersCanIssueSameCoupon() {
        // given
        User anotherUser = new User(null, "다른사용자", "another@example.com", 50000);
        userRepository.save(anotherUser);

        CouponIssueRequest request1 = new CouponIssueRequest(testUser.getId(), testCoupon.getId());
        CouponIssueRequest request2 = new CouponIssueRequest(anotherUser.getId(), testCoupon.getId());

        // when
        var issue1 = couponService.issueCoupon(request1);
        var issue2 = couponService.issueCoupon(request2);

        // then
        assertThat(issue1.id()).isNotNull();
        assertThat(issue2.id()).isNotNull();
        assertThat(issue1.id()).isNotEqualTo(issue2.id());

        // 각 사용자의 쿠폰 확인
        List<UserCoupon> user1Coupons = userCouponRepository.findByUserId(testUser.getId());
        List<UserCoupon> user2Coupons = userCouponRepository.findByUserId(anotherUser.getId());

        assertThat(user1Coupons).hasSize(1);
        assertThat(user2Coupons).hasSize(1);
    }

    @Test
    @DisplayName("같은 사용자가 서로 다른 쿠폰을 발급받을 수 있다")
    void sameUserCanIssueDifferentCoupons() {
        // given
        Coupon anotherCoupon = new Coupon(
                "20% 할인 쿠폰",
                DiscountType.PERCENTAGE,
                20,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                30
        );
        couponRepository.save(anotherCoupon);

        CouponIssueRequest request1 = new CouponIssueRequest(testUser.getId(), testCoupon.getId());
        CouponIssueRequest request2 = new CouponIssueRequest(testUser.getId(), anotherCoupon.getId());

        // when
        var issue1 = couponService.issueCoupon(request1);
        var issue2 = couponService.issueCoupon(request2);

        // then
        assertThat(issue1.id()).isNotNull();
        assertThat(issue2.id()).isNotNull();

        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(testUser.getId());
        assertThat(userCoupons).hasSize(2);
    }

    @Test
    @DisplayName("동시에 같은 쿠폰을 발급받으려 해도 1번만 성공한다")
    void concurrentDuplicateIssue_OnlyOneSucceeds() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 10개 스레드가 동시에 같은 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    CouponIssueRequest request = new CouponIssueRequest(testUser.getId(), testCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();

                } catch (IllegalStateException | DataIntegrityViolationException e) {
                    // 중복 발급 차단 (예상된 예외)
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

        // then: 정확히 1번만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // DB에도 1개만 저장되어 있어야 함
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(testUser.getId());
        assertThat(userCoupons).hasSize(1);
        assertThat(userCoupons.get(0).getCouponId()).isEqualTo(testCoupon.getId());
    }

    @Test
    @DisplayName("여러 사용자가 동시에 같은 쿠폰을 발급받을 수 있다")
    void multipleUsers_ConcurrentIssue_AllSucceed() throws InterruptedException {
        // given
        int userCount = 10;
        User[] users = new User[userCount];
        for (int i = 0; i < userCount; i++) {
            users[i] = new User(null, "사용자" + i, "user" + i + "@test.com", 50000);
            userRepository.save(users[i]);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when: 10명이 동시에 같은 쿠폰 발급
        for (int i = 0; i < userCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    CouponIssueRequest request = new CouponIssueRequest(users[index].getId(), testCoupon.getId());
                    couponService.issueCoupon(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 발급 실패
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 쿠폰 수량이 충분하면 모두 성공
        assertThat(successCount.get()).isEqualTo(userCount);

        // 각 사용자가 1개씩 발급받았는지 확인
        for (User user : users) {
            List<UserCoupon> userCoupons = userCouponRepository.findByUserId(user.getId());
            assertThat(userCoupons).hasSize(1);
        }
    }

    @Test
    @DisplayName("사용한 쿠폰을 다시 발급받을 수 없다")
    void cannotReissuedUsedCoupon() {
        // given: 쿠폰 발급 및 사용
        CouponIssueRequest request = new CouponIssueRequest(testUser.getId(), testCoupon.getId());
        var issue = couponService.issueCoupon(request);

        UserCoupon userCoupon = userCouponRepository.findById(issue.id()).orElseThrow();
        userCoupon.use();
        userCouponRepository.save(userCoupon);

        // when & then: 재발급 시도 실패
        assertThatThrownBy(() -> couponService.issueCoupon(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");

        // 여전히 1개만 존재해야 함
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(testUser.getId());
        assertThat(userCoupons).hasSize(1);
        assertThat(userCoupons.get(0).getStatus()).isEqualTo(UserCouponStatus.USED);
    }
}
