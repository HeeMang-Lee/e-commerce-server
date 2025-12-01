package com.ecommerce.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 분산 락 실행기
 *
 * Redisson을 사용한 분산 락 로직을 추상화하여
 * 서비스 레이어에서 저수준 예외 처리를 분리합니다.
 *
 * 사용 예시:
 * - 값 반환: lockExecutor.executeWithLock("lock:key", () -> service.doSomething());
 * - 값 없음: lockExecutor.executeWithLock("lock:key", () -> service.doSomethingVoid());
 */
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private static final long DEFAULT_WAIT_TIME_SECONDS = 30;
    private static final long DEFAULT_LEASE_TIME_SECONDS = 10;

    private final RedissonClient redissonClient;

    /**
     * 분산 락을 획득하고 작업을 실행합니다.
     *
     * @param lockKey 락 키
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> task) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME_SECONDS, DEFAULT_LEASE_TIME_SECONDS, task);
    }

    /**
     * 분산 락을 획득하고 작업을 실행합니다. (타임아웃 지정)
     *
     * @param lockKey 락 키
     * @param waitTimeSeconds 락 획득 대기 시간 (초)
     * @param leaseTimeSeconds 락 유지 시간 (초)
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    public <T> T executeWithLock(String lockKey, long waitTimeSeconds, long leaseTimeSeconds, Supplier<T> task) {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LockAcquisitionException("락 획득 실패: " + lockKey);
            }

            return task.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("락 획득 중 인터럽트 발생: " + lockKey, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 분산 락을 획득하고 작업을 실행합니다. (반환값 없음)
     *
     * @param lockKey 락 키
     * @param task 실행할 작업
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    public void executeWithLock(String lockKey, Runnable task) {
        executeWithLock(lockKey, () -> {
            task.run();
            return null;
        });
    }
}
