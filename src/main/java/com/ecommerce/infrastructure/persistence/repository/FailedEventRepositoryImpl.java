package com.ecommerce.infrastructure.persistence.repository;

import com.ecommerce.domain.entity.FailedEvent;
import com.ecommerce.domain.entity.FailedEventStatus;
import com.ecommerce.domain.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class FailedEventRepositoryImpl implements FailedEventRepository {

    private final JpaFailedEventRepository jpaFailedEventRepository;

    @Override
    public FailedEvent save(FailedEvent event) {
        return jpaFailedEventRepository.save(event);
    }

    @Override
    public List<FailedEvent> findByStatus(FailedEventStatus status) {
        return jpaFailedEventRepository.findByStatus(status);
    }

    @Override
    public List<FailedEvent> findRetryableEvents() {
        return jpaFailedEventRepository.findRetryableEvents();
    }

    @Override
    public List<FailedEvent> findRetryableEventsNow(LocalDateTime now) {
        return jpaFailedEventRepository.findRetryableEventsNow(now);
    }

    @Override
    public void deleteAll() {
        jpaFailedEventRepository.deleteAll();
    }
}
