package com.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 설정
 * 통합 테스트 시 MySQL 컨테이너를 자동으로 시작하고 관리합니다.
 * Redis는 별도 Docker 컨테이너(포트 6380)를 사용합니다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("ecommerce_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);  // 컨테이너 재사용으로 테스트 속도 향상
    }
}
