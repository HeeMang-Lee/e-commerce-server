/**
 * 시나리오 4: 인기 상품 조회 (Load Test)
 *
 * 목적: 캐시 효과 및 Cache Stampede 방지 검증
 *
 * 테스트 조건:
 * - Case A: 캐시 활성화 (Redis + Caffeine)
 * - Case B: 캐시 비활성화 (DB 직접 조회) - 별도 테스트
 * - 동시 사용자: 1,000명
 * - 지속 시간: 3분
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { BASE_URL, defaultHeaders } from './common.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// 커스텀 메트릭
const requestSuccess = new Counter('request_success');
const requestFailed = new Counter('request_failed');
const responseLatency = new Trend('response_latency');
const cacheHitIndicator = new Rate('cache_hit_indicator');

export const options = {
    scenarios: {
        popular_products_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 500 },   // 30초간 500명까지 증가
                { duration: '30s', target: 1000 },  // 30초간 1,000명까지 증가
                { duration: '180s', target: 1000 }, // 180초간 1,000명 유지 (3분)
                { duration: '30s', target: 0 },     // 30초간 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(50)<50', 'p(95)<200', 'p(99)<500'],  // 캐시 효과 기대
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const startTime = Date.now();

    const response = http.get(
        `${BASE_URL}/api/products/popular`,
        { headers: defaultHeaders }
    );

    const latency = Date.now() - startTime;
    responseLatency.add(latency);

    if (response.status === 200) {
        requestSuccess.add(1);

        // 캐시 히트 추정 (응답 시간 50ms 미만이면 캐시 히트로 추정)
        if (latency < 50) {
            cacheHitIndicator.add(1);
        } else {
            cacheHitIndicator.add(0);
        }

        check(response, {
            'status is 200': (r) => r.status === 200,
            'has products': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return Array.isArray(body) || (body.data && Array.isArray(body.data));
                } catch {
                    return false;
                }
            },
            'response time < 100ms': (r) => r.timings.duration < 100,
        });
    } else {
        requestFailed.add(1);
        cacheHitIndicator.add(0);
        check(response, {
            'request failed': () => false,
        });
    }

    // 실제 사용자 행동 시뮬레이션
    sleep(0.5 + Math.random() * 0.5);
}

export function handleSummary(data) {
    const success = data.metrics.request_success?.values?.count || 0;
    const failed = data.metrics.request_failed?.values?.count || 0;
    const cacheHitRate = data.metrics.cache_hit_indicator?.values?.rate || 0;
    const p50 = data.metrics.response_latency?.values?.['p(50)'] || 0;
    const p95 = data.metrics.response_latency?.values?.['p(95)'] || 0;
    const p99 = data.metrics.response_latency?.values?.['p(99)'] || 0;

    console.log('\n========== 인기 상품 조회 테스트 결과 ==========');
    console.log(`총 요청: ${success + failed}건`);
    console.log(`성공: ${success}건, 실패: ${failed}건`);
    console.log(`캐시 히트 추정률: ${(cacheHitRate * 100).toFixed(2)}%`);
    console.log(`응답 시간 - P50: ${p50.toFixed(2)}ms, P95: ${p95.toFixed(2)}ms, P99: ${p99.toFixed(2)}ms`);
    console.log('================================================\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/popular-products-result.json': JSON.stringify(data, null, 2),
    };
}
