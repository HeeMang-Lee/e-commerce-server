# Pinpoint Agent 설정 가이드

## 1. Agent 다운로드

```bash
# Pinpoint Agent 다운로드 (v2.5.2)
wget https://github.com/pinpoint-apm/pinpoint/releases/download/v2.5.2/pinpoint-agent-2.5.2.tar.gz

# 압축 해제
tar -xzf pinpoint-agent-2.5.2.tar.gz
```

## 2. Agent 설정

`pinpoint-agent-2.5.2/pinpoint-root.config` 파일 수정:

```properties
# Collector 주소 설정 (Docker 환경)
profiler.transport.grpc.collector.ip=localhost
profiler.transport.grpc.agent.collector.port=9991
profiler.transport.grpc.metadata.collector.port=9991
profiler.transport.grpc.stat.collector.port=9992
profiler.transport.grpc.span.collector.port=9993
```

## 3. 애플리케이션 실행

### IDE에서 실행 (IntelliJ)

Run Configuration > VM Options에 추가:

```
-javaagent:/path/to/pinpoint-agent-2.5.2/pinpoint-bootstrap-2.5.2.jar
-Dpinpoint.agentId=ecommerce-local-1
-Dpinpoint.applicationName=ecommerce
-Dpinpoint.config=/path/to/pinpoint-agent-2.5.2/pinpoint-root.config
```

### JAR로 실행

```bash
java -javaagent:./pinpoint-agent-2.5.2/pinpoint-bootstrap-2.5.2.jar \
     -Dpinpoint.agentId=ecommerce-1 \
     -Dpinpoint.applicationName=ecommerce \
     -Dpinpoint.config=./pinpoint-agent-2.5.2/pinpoint-root.config \
     -jar build/libs/ecommerce-1.0.0.jar
```

### Docker에서 실행

Dockerfile에 추가:

```dockerfile
# Pinpoint Agent 복사
COPY pinpoint-agent-2.5.2 /pinpoint-agent

# 실행 명령어
ENTRYPOINT ["java", \
    "-javaagent:/pinpoint-agent/pinpoint-bootstrap-2.5.2.jar", \
    "-Dpinpoint.agentId=${PINPOINT_AGENT_ID:-ecommerce-1}", \
    "-Dpinpoint.applicationName=${PINPOINT_APP_NAME:-ecommerce}", \
    "-Dpinpoint.config=/pinpoint-agent/pinpoint-root.config", \
    "-jar", "/app.jar"]
```

## 4. 확인

1. Pinpoint 실행: `docker-compose -f docker-compose.pinpoint.yml up -d`
2. 애플리케이션 실행 (Agent 적용)
3. Pinpoint Web 접속: http://localhost:8079
4. 애플리케이션 선택하여 트레이스 확인

## 주요 모니터링 항목

| 항목 | 설명 |
|------|------|
| Server Map | 서비스 간 호출 관계 시각화 |
| Request | API 호출 목록 및 응답시간 |
| Transaction | 개별 트랜잭션 상세 트레이스 |
| Inspector | JVM 메트릭, 스레드, 힙 덤프 |

## 트러블슈팅

### Agent 연결 안됨

```bash
# Collector 상태 확인
docker logs ecommerce-pinpoint-collector

# 포트 확인
netstat -an | grep 999
```

### 트레이스 안보임

1. Agent ID가 고유한지 확인 (중복 X)
2. Application Name 확인
3. Collector 로그에서 Agent 연결 확인
