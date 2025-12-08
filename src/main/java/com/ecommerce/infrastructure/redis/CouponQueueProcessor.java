package com.ecommerce.infrastructure.redis;

import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponQueueProcessor {

    private final CouponRedisRepository couponRedisRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processQueue() {
        List<String> queueItems = couponRedisRepository.popFromQueue(BATCH_SIZE);

        if (queueItems == null || queueItems.isEmpty()) {
            return;
        }

        log.info("쿠폰 대기열 처리 시작: {}건", queueItems.size());

        List<CouponIssueData> issueDataList = parseQueueItems(queueItems);

        if (issueDataList.isEmpty()) {
            return;
        }

        Map<Long, List<CouponIssueData>> groupedByCoupon = issueDataList.stream()
                .collect(Collectors.groupingBy(CouponIssueData::couponId));

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<Long, List<CouponIssueData>> entry : groupedByCoupon.entrySet()) {
            Long couponId = entry.getKey();
            List<CouponIssueData> userList = entry.getValue();

            try {
                int processed = processCouponBatch(couponId, userList);
                successCount += processed;
                failCount += (userList.size() - processed);
            } catch (Exception e) {
                log.error("쿠폰 배치 처리 실패: couponId={}, error={}", couponId, e.getMessage());
                failCount += userList.size();
            }
        }

        log.info("쿠폰 대기열 처리 완료: 성공={}, 실패={}", successCount, failCount);
    }

    private int processCouponBatch(Long couponId, List<CouponIssueData> userList) {
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            log.warn("쿠폰을 찾을 수 없음: couponId={}", couponId);
            return 0;
        }

        List<Long> userIds = userList.stream()
                .map(CouponIssueData::userId)
                .toList();

        Set<Long> alreadyIssuedUserIds = userCouponRepository
                .findByCouponIdAndUserIdIn(couponId, userIds)
                .stream()
                .map(UserCoupon::getUserId)
                .collect(Collectors.toSet());

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
        List<UserCoupon> userCoupons = userList.stream()
                .filter(data -> !alreadyIssuedUserIds.contains(data.userId()))
                .map(data -> new UserCoupon(data.userId(), couponId, expiresAt))
                .toList();

        if (!userCoupons.isEmpty()) {
            userCouponRepository.saveAll(userCoupons);

            for (int i = 0; i < userCoupons.size(); i++) {
                coupon.issue();
            }
            couponRepository.save(coupon);

            log.debug("UserCoupon 저장 완료: couponId={}, count={}", couponId, userCoupons.size());
        }

        return userCoupons.size();
    }

    private List<CouponIssueData> parseQueueItems(List<String> items) {
        List<CouponIssueData> result = new ArrayList<>();

        for (String item : items) {
            try {
                String[] parts = item.split(":");
                if (parts.length == 2) {
                    Long couponId = Long.parseLong(parts[0]);
                    Long userId = Long.parseLong(parts[1]);
                    result.add(new CouponIssueData(couponId, userId));
                }
            } catch (NumberFormatException e) {
                log.warn("대기열 아이템 파싱 실패: {}", item);
            }
        }

        return result;
    }

    public long getQueueSize() {
        return couponRedisRepository.getQueueSize();
    }

    private record CouponIssueData(Long couponId, Long userId) {}
}
