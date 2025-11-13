package com.ecommerce.domain.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 생성/수정 시간을 관리하는 Entity 기본 클래스
 * 변경 가능한 엔티티에서 사용합니다.
 */
@MappedSuperclass
@Getter
public abstract class BaseTimeEntity extends BaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 테스트용: 생성 시간과 수정 시간을 수동으로 초기화
     */
    protected void initializeTimestamps() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 수정 시간을 갱신합니다.
     */
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
