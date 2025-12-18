package com.ecommerce.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles({"test", "kafka"})
@Testcontainers
public abstract class KafkaIntegrationTestSupport {

    static final MySQLContainer<?> mysqlContainer;
    static final RedisContainer redisContainer;
    static final KafkaContainer kafkaContainer;

    static {
        mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("ecommerce_test")
                .withUsername("test")
                .withPassword("test");
        mysqlContainer.start();

        redisContainer = new RedisContainer(DockerImageName.parse("redis:7.0"))
                .withExposedPorts(6379);
        redisContainer.start();

        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
        kafkaContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }
}
