package com.ecommerce.infrastructure.redis;

import com.ecommerce.config.IntegrationTestSupport;
import com.ecommerce.domain.service.CouponIssueResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lua Script 기반 쿠폰 발급 테스트
 *
 * Lua Script는 Redis 내부에서 실행되어 디버깅이 어렵기 때문에
 * 모든 케이스를 철저히 테스트해야 한다.
 */
@DisplayName("CouponRedisRepository Lua Script 테스트")
class CouponRedisRepositoryLuaScriptTest extends IntegrationTestSupport {

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long TEST_COUPON_ID = 999L;

    @BeforeEach
    void setUp() {
        couponRedisRepository.initializeCoupon(TEST_COUPON_ID);
        couponRedisRepository.clearQueue();
    }

    @Nested
    @DisplayName("기본 동작 테스트")
    class BasicOperationTest {

        @Test
        @DisplayName("새로운 사용자가 수량 내에서 요청하면 SUCCESS 반환")
        void tryIssue_newUser_withinQuantity_returnsSuccess() {
            // given
            Long userId = 1L;
            int maxQuantity = 10;

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);
            assertThat(couponRedisRepository.isIssued(userId, TEST_COUPON_ID)).isTrue();
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 사용자가 다시 요청하면 ALREADY_ISSUED 반환")
        void tryIssue_sameUser_returnsAlreadyIssued() {
            // given
            Long userId = 1L;
            int maxQuantity = 10;
            couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.ALREADY_ISSUED);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(1);
        }

        @Test
        @DisplayName("수량이 초과되면 SOLD_OUT 반환")
        void tryIssue_exceedQuantity_returnsSoldOut() {
            // given
            int maxQuantity = 2;
            couponRedisRepository.tryIssue(1L, TEST_COUPON_ID, maxQuantity, false);
            couponRedisRepository.tryIssue(2L, TEST_COUPON_ID, maxQuantity, false);

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(3L, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SOLD_OUT);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(2);
            assertThat(couponRedisRepository.isIssued(3L, TEST_COUPON_ID)).isFalse();
        }

        @Test
        @DisplayName("여러 사용자가 순차적으로 요청하면 각각 SUCCESS 반환")
        void tryIssue_multipleUsers_sequential_allSuccess() {
            // given
            int maxQuantity = 5;

            // when
            List<CouponIssueResult> results = new ArrayList<>();
            for (long userId = 1; userId <= 5; userId++) {
                results.add(couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false));
            }

            // then
            assertThat(results).allMatch(r -> r == CouponIssueResult.SUCCESS);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("경계값 테스트")
    class BoundaryTest {

        @Test
        @DisplayName("maxQuantity가 0이면 첫 요청부터 SOLD_OUT")
        void tryIssue_zeroQuantity_returnsSoldOut() {
            // given
            int maxQuantity = 0;

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(1L, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SOLD_OUT);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(0);
        }

        @Test
        @DisplayName("maxQuantity가 1이면 첫 번째만 SUCCESS, 두 번째부터 SOLD_OUT")
        void tryIssue_singleQuantity_onlyFirstSuccess() {
            // given
            int maxQuantity = 1;

            // when
            CouponIssueResult first = couponRedisRepository.tryIssue(1L, TEST_COUPON_ID, maxQuantity, false);
            CouponIssueResult second = couponRedisRepository.tryIssue(2L, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(first).isEqualTo(CouponIssueResult.SUCCESS);
            assertThat(second).isEqualTo(CouponIssueResult.SOLD_OUT);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(1);
        }

        @Test
        @DisplayName("정확히 maxQuantity 수만큼 발급 후 다음은 SOLD_OUT")
        void tryIssue_exactlyMaxQuantity_nextIsSoldOut() {
            // given
            int maxQuantity = 3;

            // when
            CouponIssueResult r1 = couponRedisRepository.tryIssue(1L, TEST_COUPON_ID, maxQuantity, false);
            CouponIssueResult r2 = couponRedisRepository.tryIssue(2L, TEST_COUPON_ID, maxQuantity, false);
            CouponIssueResult r3 = couponRedisRepository.tryIssue(3L, TEST_COUPON_ID, maxQuantity, false);
            CouponIssueResult r4 = couponRedisRepository.tryIssue(4L, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(r1).isEqualTo(CouponIssueResult.SUCCESS);
            assertThat(r2).isEqualTo(CouponIssueResult.SUCCESS);
            assertThat(r3).isEqualTo(CouponIssueResult.SUCCESS);
            assertThat(r4).isEqualTo(CouponIssueResult.SOLD_OUT);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(3);
        }

        @Test
        @DisplayName("큰 수량(10000)에서도 정상 동작")
        void tryIssue_largeQuantity_worksCorrectly() {
            // given
            int maxQuantity = 10000;

            // when - 100명만 테스트
            for (long userId = 1; userId <= 100; userId++) {
                CouponIssueResult result = couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);
                assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);
            }

            // then
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("동시성 테스트 - Lua Script 원자성 검증")
    class ConcurrencyTest {

        @Test
        @DisplayName("100명 동시 요청, 10개 한정 - 정확히 10명만 SUCCESS")
        void tryIssue_concurrent100Users_10Coupons_exactly10Success() throws InterruptedException {
            // given
            int totalUsers = 100;
            int maxQuantity = 10;

            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(totalUsers);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger alreadyIssuedCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);

            // when
            for (int i = 1; i <= totalUsers; i++) {
                final long userId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        CouponIssueResult result = couponRedisRepository.tryIssue(
                                userId, TEST_COUPON_ID, maxQuantity, false);

                        switch (result) {
                            case SUCCESS -> successCount.incrementAndGet();
                            case ALREADY_ISSUED -> alreadyIssuedCount.incrementAndGet();
                            case SOLD_OUT -> soldOutCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(maxQuantity);
            assertThat(soldOutCount.get()).isEqualTo(totalUsers - maxQuantity);
            assertThat(alreadyIssuedCount.get()).isEqualTo(0); // 모두 다른 userId이므로
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(maxQuantity);
        }

        @Test
        @DisplayName("1000명 동시 요청, 100개 한정 - 정확히 100명만 SUCCESS")
        void tryIssue_concurrent1000Users_100Coupons_exactly100Success() throws InterruptedException {
            // given
            int totalUsers = 1000;
            int maxQuantity = 100;

            ExecutorService executor = Executors.newFixedThreadPool(50);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(totalUsers);

            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 1; i <= totalUsers; i++) {
                final long userId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        CouponIssueResult result = couponRedisRepository.tryIssue(
                                userId, TEST_COUPON_ID, maxQuantity, false);
                        if (result == CouponIssueResult.SUCCESS) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            // then - 핵심 검증: 정확히 maxQuantity만 발급
            assertThat(successCount.get()).isEqualTo(maxQuantity);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(maxQuantity);
        }

        @Test
        @DisplayName("동시 중복 요청 - 같은 userId로 여러 스레드가 동시에 요청해도 1번만 SUCCESS")
        void tryIssue_concurrentDuplicateRequests_onlyOneSuccess() throws InterruptedException {
            // given
            Long sameUserId = 1L;
            int maxQuantity = 100;
            int threadCount = 50;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger alreadyIssuedCount = new AtomicInteger(0);

            // when - 같은 userId로 50개 스레드가 동시에 요청
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        CouponIssueResult result = couponRedisRepository.tryIssue(
                                sameUserId, TEST_COUPON_ID, maxQuantity, false);

                        if (result == CouponIssueResult.SUCCESS) {
                            successCount.incrementAndGet();
                        } else if (result == CouponIssueResult.ALREADY_ISSUED) {
                            alreadyIssuedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // then - 핵심 검증: 딱 1번만 SUCCESS
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(alreadyIssuedCount.get()).isEqualTo(threadCount - 1);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("원자성 검증 테스트")
    class AtomicityTest {

        @Test
        @DisplayName("SUCCESS 시 Set에 userId가 추가되어 있어야 함")
        void tryIssue_success_userIdInSet() {
            // given
            Long userId = 1L;
            int maxQuantity = 10;

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);
            Set<String> issuedUsers = couponRedisRepository.getIssuedUsers(TEST_COUPON_ID);
            assertThat(issuedUsers).contains(userId.toString());
        }

        @Test
        @DisplayName("SOLD_OUT 시 Set에 userId가 추가되지 않아야 함")
        void tryIssue_soldOut_userIdNotInSet() {
            // given
            int maxQuantity = 1;
            couponRedisRepository.tryIssue(1L, TEST_COUPON_ID, maxQuantity, false);

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(2L, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SOLD_OUT);
            Set<String> issuedUsers = couponRedisRepository.getIssuedUsers(TEST_COUPON_ID);
            assertThat(issuedUsers).doesNotContain("2");
            assertThat(issuedUsers).hasSize(1);
        }

        @Test
        @DisplayName("ALREADY_ISSUED 시 Set 크기가 변하지 않아야 함")
        void tryIssue_alreadyIssued_setUnchanged() {
            // given
            Long userId = 1L;
            int maxQuantity = 10;
            couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);
            long countBefore = couponRedisRepository.getIssuedCount(TEST_COUPON_ID);

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.ALREADY_ISSUED);
            assertThat(couponRedisRepository.getIssuedCount(TEST_COUPON_ID)).isEqualTo(countBefore);
        }

        @Test
        @DisplayName("동시 요청에서 Set 크기가 maxQuantity를 초과하지 않아야 함")
        void tryIssue_concurrent_setNeverExceedsMax() throws InterruptedException {
            // given
            int totalUsers = 500;
            int maxQuantity = 50;

            ExecutorService executor = Executors.newFixedThreadPool(100);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(totalUsers);

            // when
            for (int i = 1; i <= totalUsers; i++) {
                final long userId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // then - 핵심: Set 크기가 절대 maxQuantity를 초과하지 않음
            long finalCount = couponRedisRepository.getIssuedCount(TEST_COUPON_ID);
            assertThat(finalCount).isEqualTo(maxQuantity);

            Set<String> issuedUsers = couponRedisRepository.getIssuedUsers(TEST_COUPON_ID);
            assertThat(issuedUsers).hasSize(maxQuantity);
        }
    }

    @Nested
    @DisplayName("Queue Push 옵션 테스트")
    class QueuePushTest {

        @Test
        @DisplayName("pushToQueue=true면 Queue에 추가됨")
        void tryIssue_withPushToQueue_addedToQueue() {
            // given
            Long userId = 1L;
            int maxQuantity = 10;

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, true);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);
            assertThat(couponRedisRepository.getQueueSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("pushToQueue=false면 Queue에 추가되지 않음")
        void tryIssue_withoutPushToQueue_notAddedToQueue() {
            // given
            Long userId = 1L;
            int maxQuantity = 10;

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(userId, TEST_COUPON_ID, maxQuantity, false);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);
            assertThat(couponRedisRepository.getQueueSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("SOLD_OUT이면 Queue에 추가되지 않음")
        void tryIssue_soldOut_notAddedToQueue() {
            // given
            int maxQuantity = 1;
            couponRedisRepository.tryIssue(1L, TEST_COUPON_ID, maxQuantity, true);

            // when
            CouponIssueResult result = couponRedisRepository.tryIssue(2L, TEST_COUPON_ID, maxQuantity, true);

            // then
            assertThat(result).isEqualTo(CouponIssueResult.SOLD_OUT);
            assertThat(couponRedisRepository.getQueueSize()).isEqualTo(1); // 첫 번째 것만
        }
    }
}
