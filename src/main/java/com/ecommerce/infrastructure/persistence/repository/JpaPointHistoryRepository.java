package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaPointHistoryRepository extends JpaRepository<PointHistory, Long> {
    List<PointHistory> findByUserId(Long userId);
}
