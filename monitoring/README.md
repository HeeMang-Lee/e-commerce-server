# 모니터링 환경 설정

부하 테스트 및 성능 분석을 위한 Prometheus + Grafana 모니터링 환경입니다.

## 구성 요소

| 서비스 | 포트 | 용도 |
|--------|------|------|
| Prometheus | 9090 | 메트릭 수집 및 저장 |
| Grafana | 3000 | 대시보드 시각화 |
| Redis Exporter | 9121 | Redis 메트릭 노출 |
| Kafka Exporter | 9308 | Kafka 메트릭 노출 |

## 실행 방법

### 1. 전체 환경 실행 (개발 + 모니터링)

```bash
# 프로젝트 루트에서 실행
docker-compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d
```

### 2. 모니터링만 실행 (기존 인프라 사용)

```bash
docker-compose -f docker-compose.monitoring.yml up -d
```

### 3. 상태 확인

```bash
docker-compose -f docker-compose.yml -f docker-compose.monitoring.yml ps
```

## 접속 정보

- **Grafana**: http://localhost:3000
  - ID: admin / PW: admin
  - 첫 로그인 시 비밀번호 변경 화면이 나타남 (Skip 가능)

- **Prometheus**: http://localhost:9090
  - 메트릭 검색 및 쿼리 테스트 가능

## Spring Boot 설정

애플리케이션에서 Prometheus 메트릭을 노출하려면 다음 설정이 필요합니다:

### build.gradle

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

### application.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ecommerce
```

## 주요 메트릭

### Application

| 메트릭 | 설명 |
|--------|------|
| `http_server_requests_seconds_count` | HTTP 요청 수 |
| `http_server_requests_seconds_sum` | HTTP 요청 처리 시간 합계 |
| `jvm_memory_used_bytes` | JVM 메모리 사용량 |
| `jvm_threads_live_threads` | 활성 스레드 수 |
| `hikaricp_connections_active` | 활성 DB 커넥션 수 |

### Redis

| 메트릭 | 설명 |
|--------|------|
| `redis_commands_processed_total` | 처리된 명령 수 |
| `redis_memory_used_bytes` | 메모리 사용량 |
| `redis_keyspace_hits_total` | 캐시 히트 수 |
| `redis_keyspace_misses_total` | 캐시 미스 수 |
| `redis_connected_clients` | 연결된 클라이언트 수 |

### Kafka

| 메트릭 | 설명 |
|--------|------|
| `kafka_consumergroup_lag` | Consumer Lag (★ 중요) |
| `kafka_topic_partition_current_offset` | 현재 오프셋 |
| `kafka_brokers` | 활성 브로커 수 |

## K6 연동

K6 테스트 결과를 Prometheus로 전송할 수 있습니다:

```bash
# Prometheus Remote Write로 K6 메트릭 전송
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run --out experimental-prometheus-rw k6/coupon-issue-test.js
```

## Grafana 대시보드

자동으로 프로비저닝되는 대시보드:

1. **E-commerce Load Test Dashboard**
   - Application Metrics: TPS, Response Time, Error Rate
   - JVM Metrics: Heap Usage, Threads, GC Pause
   - Redis Metrics: Commands/sec, Memory, Cache Hit Rate
   - Kafka Metrics: Consumer Lag, Brokers

### 추가 대시보드 임포트 (선택)

Grafana에서 다음 공식 대시보드를 임포트할 수 있습니다:

| Dashboard | ID | 설명 |
|-----------|-----|------|
| JVM Micrometer | 4701 | JVM 상세 메트릭 |
| Spring Boot Statistics | 6756 | Spring Boot 통계 |
| Redis Dashboard | 763 | Redis 상세 메트릭 |
| Kafka Exporter Overview | 7589 | Kafka 상세 메트릭 |

임포트 방법: Grafana → Dashboards → Import → Dashboard ID 입력

## 트러블슈팅

### Prometheus 타겟 연결 실패

```bash
# 타겟 상태 확인
curl http://localhost:9090/api/v1/targets

# Spring Boot 메트릭 엔드포인트 확인
curl http://localhost:8080/actuator/prometheus
```

### Grafana 데이터 안보임

1. Prometheus가 정상 실행 중인지 확인
2. Grafana → Settings → Data Sources → Prometheus 연결 테스트
3. 쿼리 직접 실행해보기

### Docker 네트워크 이슈

```bash
# 네트워크 확인
docker network ls
docker network inspect ecommerce-server_default
```

## 정리

```bash
# 컨테이너 중지 및 볼륨 삭제
docker-compose -f docker-compose.yml -f docker-compose.monitoring.yml down -v
```
