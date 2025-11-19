package com.ecommerce.domain.entity;

import com.ecommerce.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 Entity
 * 사용자에게 발급된 쿠폰을 관리합니다.
 */
@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_coupon", columnNames = {"user_id", "coupon_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserCouponStatus status;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public UserCoupon(Long userId, Long couponId, LocalDateTime expiresAt) {
        validateConstructorParams(userId, couponId, expiresAt);

        this.userId = userId;
        this.couponId = couponId;
        this.expiresAt = expiresAt;
        this.status = UserCouponStatus.AVAILABLE;
        this.issuedAt = LocalDateTime.now();
    }

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

    public boolean canUse() {
        return this.status == UserCouponStatus.AVAILABLE && !isExpired();
    }

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

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
