/**
 * 시나리오 1: 선착순 쿠폰 발급 스파이크 테스트 (현실적 버전)
 *
 * 목적: 평상시 트래픽이 있는 상황에서 쿠폰 이벤트 스파이크가 발생했을 때
 *       - 다른 API들이 영향받는지
 *       - 서버가 다운되는지
 *       - 응답 시간이 급격히 증가하는지
 *
 * 시나리오:
 * 1. 백그라운드 트래픽: 상품 조회 API 지속적으로 호출 (평상시 트래픽)
 * 2. 스파이크 트래픽: 특정 시점에 쿠폰 발급 요청 폭증
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// ============ 커스텀 메트릭 ============
// 백그라운드 트래픽 메트릭
const bgSuccess = new Counter('bg_success');
const bgFailure = new Counter('bg_failure');
const bgLatency = new Trend('bg_latency');

// 쿠폰 스파이크 메트릭
const couponIssued = new Counter('coupon_issued');
const couponSoldOut = new Counter('coupon_sold_out');
const couponLatency = new Trend('coupon_latency');
const couponSuccessRate = new Rate('coupon_success_rate');

// ============ 테스트 옵션 ============
export const options = {
    scenarios: {
        // 백그라운드 트래픽: 상품 조회 (평상시)
        background_traffic: {
            executor: 'constant-vus',
            vus: 30,                    // 30명이 지속적으로 상품 조회
            duration: '2m',             // 전체 2분간 유지
            exec: 'backgroundTraffic',
        },
        // 쿠폰 스파이크: 30초 후 시작, 폭발적 증가
        coupon_spike: {
            executor: 'ramping-vus',
            startTime: '30s',           // 30초 후 시작 (백그라운드가 안정된 후)
            startVUs: 0,
            stages: [
                { duration: '5s', target: 500 },    // 5초만에 500명 폭증
                { duration: '20s', target: 1000 },  // 20초간 1000명까지
                { duration: '30s', target: 1000 },  // 30초간 1000명 유지 (쿠폰 소진)
                { duration: '10s', target: 0 },     // 종료
            ],
            exec: 'couponSpike',
        },
    },
    thresholds: {
        // 백그라운드 API는 스파이크 중에도 P95 1초 이내 유지되어야 함
        'bg_latency': ['p(95)<1000'],
        // 시스템 에러는 1% 미만
        'http_req_failed': ['rate<0.01'],
    },
};

// ============ 백그라운드 트래픽 (상품 조회) ============
export function backgroundTraffic() {
    const endpoints = [
        '/api/products',
        '/api/products/1',
        '/api/products/2',
        '/api/products/3',
    ];

    const url = endpoints[Math.floor(Math.random() * endpoints.length)];
    const startTime = Date.now();

    const response = http.get(`${BASE_URL}${url}`, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'background' },
    });

    const latency = Date.now() - startTime;
    bgLatency.add(latency);

    if (response.status === 200) {
        bgSuccess.add(1);
        check(response, { 'bg: status 200': (r) => r.status === 200 });
    } else {
        bgFailure.add(1);
        check(response, { 'bg: failed': () => false });
    }

    sleep(0.5 + Math.random() * 0.5);  // 0.5~1초 간격
}

// ============ 쿠폰 스파이크 ============
export function couponSpike() {
    const userId = Math.floor(Math.random() * 10000) + 1;
    const couponId = 1;

    const startTime = Date.now();

    // POST /api/coupons/{couponId}/issue?userId={userId}
    const response = http.post(`${BASE_URL}/api/coupons/${couponId}/issue?userId=${userId}`, null, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'coupon_spike' },
    });

    const latency = Date.now() - startTime;
    couponLatency.add(latency);

    if (response.status === 200 || response.status === 201) {
        couponIssued.add(1);
        couponSuccessRate.add(1);
    } else {
        couponSuccessRate.add(0);

        try {
            const body = JSON.parse(response.body);
            if (body.code?.includes('SOLD_OUT') || body.message?.includes('sold out') ||
                body.code?.includes('EXHAUSTED') || body.message?.includes('소진')) {
                couponSoldOut.add(1);
            }
        } catch {}
    }

    sleep(0.1);  // 최소 대기
}

// ============ 결과 요약 ============
export function handleSummary(data) {
    const bgSuccessCount = data.metrics.bg_success?.values?.count || 0;
    const bgFailCount = data.metrics.bg_failure?.values?.count || 0;
    const bgP95 = data.metrics.bg_latency?.values?.['p(95)'] || 0;

    const couponIssuedCount = data.metrics.coupon_issued?.values?.count || 0;
    const couponSoldOutCount = data.metrics.coupon_sold_out?.values?.count || 0;
    const couponP95 = data.metrics.coupon_latency?.values?.['p(95)'] || 0;

    console.log('\n');
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log('║        🎫 선착순 쿠폰 스파이크 테스트 결과                    ║');
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 📦 백그라운드 트래픽 (상품 조회)                              ║');
    console.log(`║    성공: ${bgSuccessCount.toString().padStart(6)}건  |  실패: ${bgFailCount.toString().padStart(6)}건              ║`);
    console.log(`║    P95 응답시간: ${bgP95.toFixed(0).padStart(6)}ms                                  ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 🎫 쿠폰 스파이크 트래픽                                       ║');
    console.log(`║    발급 성공: ${couponIssuedCount.toString().padStart(6)}건                                    ║`);
    console.log(`║    품절 거부: ${couponSoldOutCount.toString().padStart(6)}건                                    ║`);
    console.log(`║    P95 응답시간: ${couponP95.toFixed(0).padStart(6)}ms                                  ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');

    // 영향도 분석
    const impacted = bgP95 > 500;
    const serverDown = bgFailCount > bgSuccessCount * 0.1;

    if (serverDown) {
        console.log('║ ⚠️  결과: 서버 불안정 - 백그라운드 실패율 높음              ║');
    } else if (impacted) {
        console.log('║ ⚠️  결과: 스파이크로 인해 다른 API 응답 지연 발생           ║');
    } else {
        console.log('║ ✅ 결과: 스파이크 중에도 다른 API 정상 응답                 ║');
    }
    console.log('╚══════════════════════════════════════════════════════════════╝');
    console.log('\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6/results/coupon-spike-result.json': JSON.stringify(data, null, 2),
    };
}
