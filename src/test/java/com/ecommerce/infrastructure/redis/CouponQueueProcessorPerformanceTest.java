package com.ecommerce.infrastructure.redis;

import com.ecommerce.config.IntegrationTestSupport;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.DiscountType;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponQueueProcessorPerformanceTest extends IntegrationTestSupport {

    @Autowired
    private CouponQueueProcessor couponQueueProcessor;

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRedisRepository.clearQueue();
        redisTemplate.delete("coupon:1:issued");

        testCoupon = new Coupon(
                "테스트 쿠폰",
                DiscountType.FIXED_AMOUNT,
                1000,
                1000,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                30
        );
        testCoupon = couponRepository.save(testCoupon);
    }

    @Test
    @DisplayName("100건 배치 처리가 5초 이내에 완료되어야 한다")
    void processQueue_100Items_ShouldCompleteWithin5Seconds() {
        // given
        int batchSize = 100;
        Long couponId = testCoupon.getId();

        for (int i = 1; i <= batchSize; i++) {
            String queueData = couponId + ":" + i;
            redisTemplate.opsForList().rightPush("coupon:queue", queueData);
        }

        assertThat(couponRedisRepository.getQueueSize()).isEqualTo(batchSize);

        // when
        long startTime = System.currentTimeMillis();
        couponQueueProcessor.processQueue();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // then
        System.out.println("=== 배치 처리 성능 테스트 결과 ===");
        System.out.println("처리 건수: " + batchSize + "건");
        System.out.println("소요 시간: " + elapsedTime + "ms");
        System.out.println("건당 평균: " + (elapsedTime / (double) batchSize) + "ms");

        assertThat(elapsedTime)
                .as("100건 처리가 5초(5000ms) 이내에 완료되어야 함")
                .isLessThan(5000);

        assertThat(couponRedisRepository.getQueueSize()).isEqualTo(0);
        assertThat(userCouponRepository.findAll()).hasSize(batchSize);
    }

    @Test
    @DisplayName("500건 배치 처리 성능 측정 (스케줄러 간격 검증)")
    void processQueue_500Items_PerformanceMeasurement() {
        // given
        int totalItems = 500;
        Long couponId = testCoupon.getId();

        for (int i = 1; i <= totalItems; i++) {
            String queueData = couponId + ":" + i;
            redisTemplate.opsForList().rightPush("coupon:queue", queueData);
        }

        // when - 5번의 배치 처리 (각 100건씩)
        long startTime = System.currentTimeMillis();
        int processedTotal = 0;
        int iteration = 0;

        while (couponRedisRepository.getQueueSize() > 0) {
            iteration++;
            long batchStart = System.currentTimeMillis();
            couponQueueProcessor.processQueue();
            long batchTime = System.currentTimeMillis() - batchStart;
            System.out.println("배치 " + iteration + " 처리 시간: " + batchTime + "ms");
        }

        long totalTime = System.currentTimeMillis() - startTime;

        // then
        System.out.println("=== 전체 처리 결과 ===");
        System.out.println("총 처리 건수: " + totalItems + "건");
        System.out.println("총 소요 시간: " + totalTime + "ms");
        System.out.println("배치 횟수: " + iteration + "회");
        System.out.println("예상 스케줄러 시간 (5초 간격): " + (iteration * 5000) + "ms");

        assertThat(userCouponRepository.findAll()).hasSize(totalItems);
    }

    @Test
    @DisplayName("여러 쿠폰에 대한 혼합 배치 처리 성능")
    void processQueue_MultipleCoupons_Performance() {
        // given
        Coupon coupon2 = new Coupon(
                "테스트 쿠폰 2",
                DiscountType.PERCENTAGE,
                10,
                500,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                30
        );
        coupon2 = couponRepository.save(coupon2);

        int itemsPerCoupon = 50;
        for (int i = 1; i <= itemsPerCoupon; i++) {
            redisTemplate.opsForList().rightPush("coupon:queue", testCoupon.getId() + ":" + i);
            redisTemplate.opsForList().rightPush("coupon:queue", coupon2.getId() + ":" + (i + 1000));
        }

        // when
        long startTime = System.currentTimeMillis();
        couponQueueProcessor.processQueue();
        long elapsedTime = System.currentTimeMillis() - startTime;

        // then
        System.out.println("=== 복수 쿠폰 배치 처리 결과 ===");
        System.out.println("처리 건수: " + (itemsPerCoupon * 2) + "건 (2개 쿠폰)");
        System.out.println("소요 시간: " + elapsedTime + "ms");

        assertThat(elapsedTime).isLessThan(5000);
        assertThat(userCouponRepository.findAll()).hasSize(itemsPerCoupon * 2);
    }
}
