package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.OutboxEvent;
import com.ecommerce.domain.entity.OutboxStatus;
import com.ecommerce.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final JpaOutboxEventRepository jpaOutboxEventRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpaOutboxEventRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxStatus status) {
        return jpaOutboxEventRepository.findByStatus(status);
    }

    @Override
    public void deleteAll() {
        jpaOutboxEventRepository.deleteAll();
    }
}
