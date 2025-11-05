package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.OutboxStatus;
import com.ecommerce.domain.repository.OutboxEventRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryOutboxEventRepository implements OutboxEventRepository {

    private final Map<Long, OutboxEvent> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OutboxEvent save(OutboxEvent event) {
        if (event.getId() == null) {
            event.setId(idGenerator.getAndIncrement());
        }
        store.put(event.getId(), event);
        return event;
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxStatus status) {
        return store.values().stream()
                .filter(event -> event.getStatus() == status)
                .collect(Collectors.toList());
    }
}
