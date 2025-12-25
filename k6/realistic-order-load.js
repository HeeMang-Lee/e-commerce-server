/**
 * 시나리오 2: 주문 생성 부하 테스트 (현실적 버전)
 *
 * 목적: 평상시 상품 조회가 있는 상황에서 주문이 몰릴 때
 *       - 재고 동시성 제어가 정상 동작하는지
 *       - 상품 조회 API 응답이 느려지는지
 *
 * 시나리오:
 * 1. 백그라운드: 상품 조회 지속
 * 2. 주문 부하: 점진적으로 증가하여 동시 주문 처리 테스트
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 메트릭
const bgSuccess = new Counter('bg_success');
const bgLatency = new Trend('bg_latency');
const orderCreated = new Counter('order_created');
const stockInsufficient = new Counter('stock_insufficient');
const orderLatency = new Trend('order_latency');

export const options = {
    scenarios: {
        background_traffic: {
            executor: 'constant-vus',
            vus: 20,
            duration: '2m30s',
            exec: 'backgroundTraffic',
        },
        order_load: {
            executor: 'ramping-vus',
            startTime: '20s',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 50 },    // 50명까지
                { duration: '30s', target: 150 },   // 150명까지
                { duration: '40s', target: 200 },   // 200명까지 (피크) - 로컬 환경 적정 수준
                { duration: '20s', target: 0 },     // 종료
            ],
            exec: 'orderLoad',
        },
    },
    thresholds: {
        'bg_latency': ['p(95)<1000'],
        'order_latency': ['p(95)<3000'],
    },
};

export function backgroundTraffic() {
    const urls = ['/api/products', '/api/products/1', '/api/products/2'];
    const url = urls[Math.floor(Math.random() * urls.length)];

    const start = Date.now();
    const res = http.get(`${BASE_URL}${url}`, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'background' },
    });
    bgLatency.add(Date.now() - start);

    if (res.status === 200) bgSuccess.add(1);
    sleep(0.3 + Math.random() * 0.4);
}

export function orderLoad() {
    const userId = Math.floor(Math.random() * 5000) + 1;
    const productId = Math.floor(Math.random() * 5) + 1;

    const payload = JSON.stringify({
        userId: userId,
        items: [{ productId: productId, quantity: 1 }],
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/orders`, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'order' },
    });
    orderLatency.add(Date.now() - start);

    if (res.status === 200 || res.status === 201) {
        orderCreated.add(1);
    } else {
        try {
            const body = JSON.parse(res.body);
            if (body.code?.includes('STOCK') || body.message?.includes('재고')) {
                stockInsufficient.add(1);
            }
        } catch {}
    }

    sleep(0.2);
}

export function handleSummary(data) {
    const bgCount = data.metrics.bg_success?.values?.count || 0;
    const bgP95 = data.metrics.bg_latency?.values?.['p(95)'] || 0;
    const orderCount = data.metrics.order_created?.values?.count || 0;
    const stockOut = data.metrics.stock_insufficient?.values?.count || 0;
    const orderP95 = data.metrics.order_latency?.values?.['p(95)'] || 0;

    console.log('\n');
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log('║        🛒 주문 생성 부하 테스트 결과                          ║');
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 📦 백그라운드 (상품 조회)                                     ║');
    console.log(`║    성공: ${bgCount.toString().padStart(6)}건  |  P95: ${bgP95.toFixed(0).padStart(6)}ms                   ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 🛒 주문 트래픽                                                ║');
    console.log(`║    주문 성공: ${orderCount.toString().padStart(6)}건                                    ║`);
    console.log(`║    재고 부족: ${stockOut.toString().padStart(6)}건                                    ║`);
    console.log(`║    P95 응답시간: ${orderP95.toFixed(0).padStart(6)}ms                                  ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');

    const impacted = bgP95 > 500;
    console.log(impacted
        ? '║ ⚠️  결과: 주문 부하로 인해 상품 조회 지연 발생               ║'
        : '║ ✅ 결과: 주문 부하 중에도 상품 조회 정상                     ║');
    console.log('╚══════════════════════════════════════════════════════════════╝\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6/results/order-load-result.json': JSON.stringify(data, null, 2),
    };
}
