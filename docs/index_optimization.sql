-- 인덱스 최적화 스크립트
-- 부하 테스트 결과 분석 후 추가된 인덱스

-- 1. order_payments 복합 인덱스: 결제 상태+시간 조회 최적화
-- 사용 쿼리: findByStatusAndPaidAtAfter(PaymentStatus status, LocalDateTime after)
-- 기존: idx_status, idx_paid_at 개별 인덱스 사용
-- 개선: 복합 인덱스로 인덱스 스캔 효율 향상
CREATE INDEX idx_status_paid_at ON order_payments (payment_status, paid_at);

-- 2. failed_events 복합 인덱스: DLT 스케줄러 조회 최적화
-- 사용 쿼리: findEventsToRetry(LocalDateTime now)
-- WHERE status = 'PENDING' AND retry_count < max_retry_count AND next_retry_at <= :now
-- 복합 인덱스로 스케줄러 폴링 성능 향상
CREATE INDEX idx_status_next_retry ON failed_events (status, next_retry_at);

-- 3. failed_events 기본 인덱스 (테이블이 새로 생성된 경우)
CREATE INDEX idx_fe_status ON failed_events (status);
CREATE INDEX idx_fe_topic ON failed_events (topic);
CREATE INDEX idx_fe_created ON failed_events (created_at);

-- 인덱스 확인
SHOW INDEX FROM order_payments;
SHOW INDEX FROM failed_events;
