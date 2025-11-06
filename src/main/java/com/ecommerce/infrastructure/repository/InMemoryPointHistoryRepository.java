package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.PointHistory;
import com.ecommerce.domain.repository.PointHistoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * InMemory PointHistory Repository 구현
 */
@Repository
public class InMemoryPointHistoryRepository implements PointHistoryRepository {

    private final Map<Long, PointHistory> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public PointHistory save(PointHistory history) {
        if (history.getId() == null) {
            history.setId(idGenerator.getAndIncrement());
        }
        store.put(history.getId(), history);
        return history;
    }

    @Override
    public List<PointHistory> findByUserId(Long userId) {
        return store.values().stream()
                .filter(history -> history.getUserId().equals(userId))
                .collect(Collectors.toList());
    }
}
