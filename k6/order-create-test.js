/**
 * 시나리오 2: 상품 주문 생성 (Load Test)
 *
 * 목적: 재고 차감 시 동시성 제어 및 TPS 한계 측정
 *
 * 테스트 조건:
 * - 상품 재고: 1,000개
 * - 동시 사용자: 2,000명
 * - 주문 수량: 각 1개씩
 * - 목표: 1,000명 성공, 1,000명 재고 부족 실패
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, defaultHeaders, randomUserId, randomProductId } from './common.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// 커스텀 메트릭
const orderCreated = new Counter('order_created');
const stockInsufficient = new Counter('stock_insufficient');
const orderLatency = new Trend('order_latency');
const successRate = new Rate('success_rate');

// 테스트 설정
const PRODUCT_ID = __ENV.PRODUCT_ID || 1;
const MAX_USERS = __ENV.MAX_USERS || 2000;

export const options = {
    scenarios: {
        order_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 1000 },  // 30초간 1,000명까지 증가
                { duration: '30s', target: 2000 },  // 30초간 2,000명까지 증가
                { duration: '120s', target: 2000 }, // 120초간 2,000명 유지
                { duration: '30s', target: 0 },     // 30초간 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],  // P95 2초 미만
        http_req_failed: ['rate<0.01'],      // 시스템 에러 1% 미만
    },
};

export default function () {
    const userId = randomUserId(MAX_USERS);

    const payload = JSON.stringify({
        userId: userId,
        orderItems: [
            {
                productId: parseInt(PRODUCT_ID),
                quantity: 1,
            }
        ],
    });

    const startTime = Date.now();

    const response = http.post(
        `${BASE_URL}/api/orders`,
        payload,
        { headers: defaultHeaders }
    );

    const latency = Date.now() - startTime;
    orderLatency.add(latency);

    // 응답 검증
    if (response.status === 200 || response.status === 201) {
        orderCreated.add(1);
        successRate.add(1);
        check(response, {
            'order created successfully': (r) => true,
        });
    } else {
        successRate.add(0);

        try {
            const body = JSON.parse(response.body);

            if (body.code === 'STOCK_INSUFFICIENT' ||
                body.code === 'INSUFFICIENT_STOCK' ||
                body.message?.includes('stock') ||
                body.message?.includes('재고')) {
                stockInsufficient.add(1);
                check(response, {
                    'stock insufficient (expected)': () => true,
                });
            } else {
                check(response, {
                    'unexpected error': () => false,
                });
                console.log(`Unexpected error: ${response.status} - ${response.body}`);
            }
        } catch {
            check(response, {
                'parse error response': () => false,
            });
        }
    }

    sleep(0.5);
}

export function handleSummary(data) {
    const created = data.metrics.order_created?.values?.count || 0;
    const insufficient = data.metrics.stock_insufficient?.values?.count || 0;

    console.log('\n========== 주문 생성 테스트 결과 ==========');
    console.log(`주문 성공: ${created}건`);
    console.log(`재고 부족: ${insufficient}건`);
    console.log(`총 요청: ${created + insufficient}건`);
    console.log('==========================================\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/order-create-result.json': JSON.stringify(data, null, 2),
    };
}
