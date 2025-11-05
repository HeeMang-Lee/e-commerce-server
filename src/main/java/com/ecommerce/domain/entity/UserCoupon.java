package com.ecommerce.domain.entity;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 Entity
 * 사용자에게 발급된 쿠폰을 관리합니다.
 */
@Getter
public class UserCoupon {

    private Long id;
    private final Long userId;
    private final Long couponId;
    private UserCouponStatus status;
    private final LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private final LocalDateTime expiresAt;

    /**
     * 사용자에게 쿠폰을 발급합니다.
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param expiresAt 만료일시
     */
    public UserCoupon(Long userId, Long couponId, LocalDateTime expiresAt) {
        validateConstructorParams(userId, couponId, expiresAt);

        this.userId = userId;
        this.couponId = couponId;
        this.expiresAt = expiresAt;
        this.status = UserCouponStatus.AVAILABLE;
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * 생성자 파라미터를 검증합니다.
     */
    private void validateConstructorParams(Long userId, Long couponId, LocalDateTime expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다");
        }
        if (couponId == null) {
            throw new IllegalArgumentException("쿠폰 ID는 필수입니다");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("만료일은 필수입니다");
        }
    }

    /**
     * 쿠폰 사용 가능 여부를 확인합니다.
     *
     * @return 사용 가능하면 true
     */
    public boolean canUse() {
        return this.status == UserCouponStatus.AVAILABLE && !isExpired();
    }

    /**
     * 쿠폰을 사용합니다.
     *
     * @throws IllegalStateException 이미 사용되었거나 만료된 경우
     */
    public void use() {
        if (this.status == UserCouponStatus.USED) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다");
        }
        if (isExpired()) {
            throw new IllegalStateException("만료된 쿠폰은 사용할 수 없습니다");
        }

        this.status = UserCouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰이 만료되었는지 확인합니다.
     *
     * @return 만료되었으면 true
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 쿠폰 ID를 설정합니다. (Repository에서 저장 후 호출)
     *
     * @param id 쿠폰 ID
     */
    public void setId(Long id) {
        this.id = id;
    }
}
