package com.ecommerce.application.service;

import com.ecommerce.application.dto.CouponIssueRequest;
import com.ecommerce.application.dto.UserCouponResponse;
import com.ecommerce.domain.entity.Coupon;
import com.ecommerce.domain.entity.UserCoupon;
import com.ecommerce.domain.repository.CouponRepository;
import com.ecommerce.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 쿠폰 서비스
 * 동시성 제어를 포함한 쿠폰 발급 로직
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰을 발급합니다.
     * Lock을 사용하여 동시성을 제어합니다.
     *
     * @param request 발급 요청
     * @return 발급된 쿠폰 정보
     */
    public UserCouponResponse issueCoupon(CouponIssueRequest request) {
        // Lock 보호 하에 Read -> Modify -> Save 실행
        UserCoupon userCoupon = couponRepository.executeWithLock(
                request.getCouponId(),
                coupon -> {
                    // 쿠폰 발급 (동시성 제어된 상태에서 실행)
                    coupon.issue();

                    // 사용자 쿠폰 생성
                    LocalDateTime expiresAt = LocalDateTime.now().plusDays(coupon.getValidPeriodDays());
                    UserCoupon newUserCoupon = new UserCoupon(
                            request.getUserId(),
                            request.getCouponId(),
                            expiresAt
                    );
                    userCouponRepository.save(newUserCoupon);

                    return newUserCoupon;
                }
        );

        return UserCouponResponse.from(userCoupon);
    }

    /**
     * 사용자의 쿠폰 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 쿠폰 목록
     */
    public List<UserCouponResponse> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId).stream()
                .map(UserCouponResponse::from)
                .collect(Collectors.toList());
    }
}
