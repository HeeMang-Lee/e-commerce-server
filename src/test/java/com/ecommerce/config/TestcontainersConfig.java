package com.ecommerce.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 설정
 * 통합 테스트 시 MySQL과 Redis 컨테이너를 자동으로 시작하고 관리합니다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final RedisContainer redisContainer;

    static {
        redisContainer = new RedisContainer(DockerImageName.parse("redis:7.0"))
                .withExposedPorts(6379);
        redisContainer.start();
    }

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("ecommerce_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
    }

    @Bean
    public RedisContainer redisContainer() {
        return redisContainer;
    }

    public static String getRedisHost() {
        return redisContainer.getHost();
    }

    public static Integer getRedisPort() {
        return redisContainer.getMappedPort(6379);
    }
}
