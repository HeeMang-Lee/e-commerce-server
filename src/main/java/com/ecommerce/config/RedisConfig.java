package com.ecommerce.config;

import com.ecommerce.infrastructure.redis.RankingCacheInvalidator;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 및 Redisson 설정
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String password = redisProperties.getPassword();
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setPassword(password != null && !password.isEmpty() ? password : null);

        return Redisson.create(config);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RankingCacheInvalidator rankingCacheInvalidator) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                rankingCacheInvalidator,
                new ChannelTopic(RankingCacheInvalidator.RANKING_CACHE_CHANNEL)
        );
        return container;
    }
}
