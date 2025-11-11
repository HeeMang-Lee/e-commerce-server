package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.repository.PointHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PointHistoryRepositoryImpl implements PointHistoryRepository {

    private final JpaPointHistoryRepository jpaPointHistoryRepository;

    @Override
    public PointHistory save(PointHistory history) {
        return jpaPointHistoryRepository.save(history);
    }

    @Override
    public List<PointHistory> findByUserId(Long userId) {
        return jpaPointHistoryRepository.findByUserId(userId);
    }
}
