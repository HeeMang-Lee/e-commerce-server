/**
 * 통합 부하 테스트 실행 스크립트
 *
 * 모든 시나리오를 순차적으로 실행하며 시스템 한계를 측정합니다.
 * 각 시나리오는 독립적으로 실행되며 결과는 별도로 저장됩니다.
 *
 * 실행 방법:
 * k6 run run-all-tests.js
 *
 * 환경 변수:
 * - BASE_URL: API 서버 주소 (기본: http://localhost:8080)
 * - SCENARIO: 특정 시나리오만 실행 (coupon|order|payment|popular|point)
 */
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const SCENARIO = __ENV.SCENARIO || 'all';

const defaultHeaders = {
    'Content-Type': 'application/json',
};

// 커스텀 메트릭
const totalRequests = new Counter('total_requests');
const totalSuccess = new Counter('total_success');
const totalFailed = new Counter('total_failed');
const overallSuccessRate = new Rate('overall_success_rate');

export const options = {
    scenarios: {
        // 시나리오 1: 선착순 쿠폰 발급 (만 건 이상 트래픽)
        coupon_rush: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 2000 },
                { duration: '20s', target: 5000 },
                { duration: '30s', target: 5000 },
                { duration: '10s', target: 0 },
            ],
            exec: 'couponTest',
            startTime: '0s',
        },
        // 시나리오 2: 주문 생성
        order_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 1000 },
                { duration: '40s', target: 2000 },
                { duration: '10s', target: 0 },
            ],
            exec: 'orderTest',
            startTime: '80s',
        },
        // 시나리오 3: 인기 상품 조회 (캐시 성능)
        popular_products: {
            executor: 'constant-vus',
            vus: 500,
            duration: '60s',
            exec: 'popularTest',
            startTime: '160s',
        },
        // 시나리오 4: 포인트 충전 (동시성)
        point_charge: {
            executor: 'per-vu-iterations',
            vus: 100,
            iterations: 10,
            maxDuration: '2m',
            exec: 'pointTest',
            startTime: '230s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.05'],
    },
};

function randomUserId(max = 10000) {
    return Math.floor(Math.random() * max) + 1;
}

// 시나리오 1: 쿠폰 발급 테스트
export function couponTest() {
    group('쿠폰 발급', function () {
        const userId = randomUserId();
        const payload = JSON.stringify({
            userId: userId,
            couponId: 1,
        });

        const response = http.post(
            `${BASE_URL}/api/coupons/issue`,
            payload,
            { headers: defaultHeaders }
        );

        totalRequests.add(1);

        if (response.status === 200 || response.status === 201) {
            totalSuccess.add(1);
            overallSuccessRate.add(1);
        } else {
            // 품절/중복은 예상되는 실패
            if (response.status === 400 || response.status === 409) {
                overallSuccessRate.add(1); // 비즈니스 로직상 정상
            } else {
                totalFailed.add(1);
                overallSuccessRate.add(0);
            }
        }

        check(response, {
            'coupon response received': (r) => r.status !== 0,
        });

        sleep(0.1);
    });
}

// 시나리오 2: 주문 생성 테스트
export function orderTest() {
    group('주문 생성', function () {
        const userId = randomUserId(2000);
        const payload = JSON.stringify({
            userId: userId,
            orderItems: [
                { productId: Math.floor(Math.random() * 10) + 1, quantity: 1 }
            ],
        });

        const response = http.post(
            `${BASE_URL}/api/orders`,
            payload,
            { headers: defaultHeaders }
        );

        totalRequests.add(1);

        if (response.status === 200 || response.status === 201) {
            totalSuccess.add(1);
            overallSuccessRate.add(1);
        } else if (response.status === 400 || response.status === 409) {
            // 재고 부족은 예상되는 실패
            overallSuccessRate.add(1);
        } else {
            totalFailed.add(1);
            overallSuccessRate.add(0);
        }

        check(response, {
            'order response received': (r) => r.status !== 0,
        });

        sleep(0.5);
    });
}

// 시나리오 3: 인기 상품 조회 테스트
export function popularTest() {
    group('인기 상품 조회', function () {
        const response = http.get(
            `${BASE_URL}/api/products/popular`,
            { headers: defaultHeaders }
        );

        totalRequests.add(1);

        if (response.status === 200) {
            totalSuccess.add(1);
            overallSuccessRate.add(1);
        } else {
            totalFailed.add(1);
            overallSuccessRate.add(0);
        }

        check(response, {
            'popular products status 200': (r) => r.status === 200,
            'response time < 100ms (cache hit)': (r) => r.timings.duration < 100,
        });

        sleep(0.5 + Math.random() * 0.5);
    });
}

// 시나리오 4: 포인트 충전 테스트
export function pointTest() {
    group('포인트 충전', function () {
        const userId = __VU; // VU ID를 사용자 ID로 사용
        const payload = JSON.stringify({
            userId: userId,
            amount: 1000,
        });

        const response = http.post(
            `${BASE_URL}/api/points/charge`,
            payload,
            { headers: defaultHeaders }
        );

        totalRequests.add(1);

        if (response.status === 200 || response.status === 201) {
            totalSuccess.add(1);
            overallSuccessRate.add(1);
        } else {
            totalFailed.add(1);
            overallSuccessRate.add(0);
        }

        check(response, {
            'point charge response received': (r) => r.status !== 0,
        });

        sleep(0.1 + Math.random() * 0.2);
    });
}

export function handleSummary(data) {
    const total = data.metrics.total_requests?.values?.count || 0;
    const success = data.metrics.total_success?.values?.count || 0;
    const failed = data.metrics.total_failed?.values?.count || 0;
    const rate = data.metrics.overall_success_rate?.values?.rate || 0;

    console.log('\n');
    console.log('╔══════════════════════════════════════════════════════════╗');
    console.log('║           통합 부하 테스트 결과 요약                      ║');
    console.log('╠══════════════════════════════════════════════════════════╣');
    console.log(`║  총 요청 수: ${total.toString().padEnd(44)}║`);
    console.log(`║  성공: ${success.toString().padEnd(50)}║`);
    console.log(`║  실패: ${failed.toString().padEnd(50)}║`);
    console.log(`║  성공률: ${(rate * 100).toFixed(2)}%${' '.repeat(46)}║`);
    console.log('╚══════════════════════════════════════════════════════════╝');
    console.log('\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/all-tests-result.json': JSON.stringify(data, null, 2),
    };
}
