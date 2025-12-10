package com.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리를 위한 ThreadPoolTaskExecutor 설정
 *
 * @Async 사용 시 ThreadPoolExecutor를 설정하지 않으면 매번 새 스레드를 생성하므로
 * 반드시 풀을 설정해야 한다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 이벤트 핸들러용 Executor
     *
     * 결제 완료 후 부가 로직(데이터 플랫폼 전송, 랭킹 기록)을 비동기로 처리한다.
     * CallerRunsPolicy: 큐가 가득 차면 호출 스레드에서 실행 → 데이터 유실 방지
     */
    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("Event executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}
