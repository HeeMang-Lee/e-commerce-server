/**
 * 시나리오 3: 주문 결제 (Stress Test)
 *
 * 목적: 결제 프로세스 전체 흐름의 안정성 검증
 *
 * 테스트 조건:
 * - 사전 조건: 주문 생성 완료 상태
 * - 동시 결제 요청: 점진적 증가 (100 → 500 → 1,000)
 * - 포인트 사용 + 쿠폰 적용 혼합
 */
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, defaultHeaders, randomUserId } from './common.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// 커스텀 메트릭
const paymentSuccess = new Counter('payment_success');
const paymentFailed = new Counter('payment_failed');
const paymentLatency = new Trend('payment_latency');
const successRate = new Rate('success_rate');

// 테스트 설정
const MAX_USERS = __ENV.MAX_USERS || 1000;

export const options = {
    scenarios: {
        payment_stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '60s', target: 100 },   // 60초간 100명까지 증가
                { duration: '60s', target: 500 },   // 60초간 500명까지 증가
                { duration: '60s', target: 1000 },  // 60초간 1,000명까지 증가 (피크)
                { duration: '120s', target: 1000 }, // 120초간 1,000명 유지
                { duration: '60s', target: 0 },     // 60초간 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],  // P95 3초 미만
        http_req_failed: ['rate<0.05'],      // 시스템 에러 5% 미만 (스트레스 테스트)
    },
};

// 주문 ID 저장용 (테스트 중 생성된 주문)
const createdOrders = [];

export function setup() {
    // 테스트 전 주문 생성 (결제 대상)
    console.log('Setting up: Creating orders for payment test...');

    const orders = [];
    for (let i = 1; i <= 100; i++) {
        const userId = i;
        const payload = JSON.stringify({
            userId: userId,
            orderItems: [{ productId: 1, quantity: 1 }],
        });

        const response = http.post(
            `${BASE_URL}/api/orders`,
            payload,
            { headers: defaultHeaders }
        );

        if (response.status === 200 || response.status === 201) {
            try {
                const body = JSON.parse(response.body);
                if (body.orderId || body.id) {
                    orders.push({
                        orderId: body.orderId || body.id,
                        userId: userId,
                    });
                }
            } catch (e) {
                console.log(`Failed to parse order response: ${e}`);
            }
        }
    }

    console.log(`Setup complete: ${orders.length} orders created`);
    return { orders };
}

export default function (data) {
    const userId = randomUserId(MAX_USERS);

    group('Create and Pay Order', function () {
        // 1. 주문 생성
        const orderPayload = JSON.stringify({
            userId: userId,
            orderItems: [
                { productId: Math.floor(Math.random() * 10) + 1, quantity: 1 }
            ],
        });

        const orderResponse = http.post(
            `${BASE_URL}/api/orders`,
            orderPayload,
            { headers: defaultHeaders }
        );

        if (orderResponse.status !== 200 && orderResponse.status !== 201) {
            paymentFailed.add(1);
            successRate.add(0);
            return;
        }

        let orderId;
        try {
            const body = JSON.parse(orderResponse.body);
            orderId = body.orderId || body.id;
        } catch {
            paymentFailed.add(1);
            successRate.add(0);
            return;
        }

        if (!orderId) {
            paymentFailed.add(1);
            successRate.add(0);
            return;
        }

        // 2. 결제 요청
        const paymentPayload = JSON.stringify({
            orderId: orderId,
            usePoint: Math.random() > 0.5 ? 1000 : 0,  // 50% 확률로 포인트 사용
            couponId: Math.random() > 0.7 ? 1 : null,   // 30% 확률로 쿠폰 사용
        });

        const startTime = Date.now();

        const paymentResponse = http.post(
            `${BASE_URL}/api/orders/${orderId}/payment`,
            paymentPayload,
            { headers: defaultHeaders }
        );

        const latency = Date.now() - startTime;
        paymentLatency.add(latency);

        if (paymentResponse.status === 200 || paymentResponse.status === 201) {
            paymentSuccess.add(1);
            successRate.add(1);
            check(paymentResponse, {
                'payment completed': () => true,
            });
        } else {
            paymentFailed.add(1);
            successRate.add(0);

            try {
                const body = JSON.parse(paymentResponse.body);
                // 포인트 부족, 쿠폰 만료 등은 예상되는 실패
                if (body.code?.includes('POINT') || body.code?.includes('COUPON')) {
                    check(paymentResponse, {
                        'business error (expected)': () => true,
                    });
                } else {
                    check(paymentResponse, {
                        'unexpected payment error': () => false,
                    });
                }
            } catch {
                check(paymentResponse, {
                    'parse payment error': () => false,
                });
            }
        }
    });

    sleep(1);
}

export function handleSummary(data) {
    const success = data.metrics.payment_success?.values?.count || 0;
    const failed = data.metrics.payment_failed?.values?.count || 0;

    console.log('\n========== 결제 테스트 결과 ==========');
    console.log(`결제 성공: ${success}건`);
    console.log(`결제 실패: ${failed}건`);
    console.log(`성공률: ${((success / (success + failed)) * 100).toFixed(2)}%`);
    console.log('======================================\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/payment-result.json': JSON.stringify(data, null, 2),
    };
}
