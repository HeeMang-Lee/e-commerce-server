package com.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingVersionScheduler {

    private final ProductRankingRedisRepository rankingRedisRepository;

    @Scheduled(cron = "0 */10 * * * *")
    public void refreshRankingVersion() {
        try {
            long newVersion = rankingRedisRepository.incrementVersion();
            log.info("랭킹 버전 갱신 완료: version={}", newVersion);
        } catch (Exception e) {
            log.error("랭킹 버전 갱신 실패: {}", e.getMessage());
        }
    }
}
