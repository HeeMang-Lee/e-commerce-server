/**
 * 시나리오 4: 인기 상품 조회 부하 테스트 (현실적 버전)
 *
 * 목적: 캐시 효과 검증 및 대량 조회 시 DB 부하 테스트
 *       - 캐시 히트율 측정
 *       - 캐시 미스 시 DB 쿼리 성능
 *       - 다른 API에 미치는 영향
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 메트릭
const bgSuccess = new Counter('bg_success');
const bgLatency = new Trend('bg_latency');
const popularSuccess = new Counter('popular_success');
const popularFailure = new Counter('popular_failure');
const popularLatency = new Trend('popular_latency');
const cacheHit = new Counter('cache_hit');

export const options = {
    scenarios: {
        // 백그라운드: 일반 상품 조회 + 주문
        background_traffic: {
            executor: 'constant-vus',
            vus: 30,
            duration: '1m30s',
            exec: 'backgroundTraffic',
        },
        // 인기 상품 조회: 대량 요청
        popular_products_load: {
            executor: 'ramping-vus',
            startTime: '10s',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 100 },
                { duration: '30s', target: 300 },   // 피크
                { duration: '20s', target: 300 },
                { duration: '10s', target: 0 },
            ],
            exec: 'popularProductsLoad',
        },
    },
    thresholds: {
        'bg_latency': ['p(95)<1000'],
        'popular_latency': ['p(95)<500'],  // 캐시 덕분에 빨라야 함
    },
};

export function backgroundTraffic() {
    const actions = [
        () => http.get(`${BASE_URL}/api/products`),
        () => http.get(`${BASE_URL}/api/products/${Math.floor(Math.random() * 5) + 1}`),
    ];

    const start = Date.now();
    const res = actions[Math.floor(Math.random() * actions.length)]();
    bgLatency.add(Date.now() - start);

    if (res.status === 200) bgSuccess.add(1);
    sleep(0.3 + Math.random() * 0.3);
}

export function popularProductsLoad() {
    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/products/top`, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'popular' },
    });
    const latency = Date.now() - start;
    popularLatency.add(latency);

    if (res.status === 200) {
        popularSuccess.add(1);
        // 캐시 히트 추정 (10ms 이하면 캐시 히트로 간주)
        if (latency < 10) cacheHit.add(1);
    } else {
        popularFailure.add(1);
    }

    sleep(0.1);
}

export function handleSummary(data) {
    const bgCount = data.metrics.bg_success?.values?.count || 0;
    const bgP95 = data.metrics.bg_latency?.values?.['p(95)'] || 0;
    const popSuccess = data.metrics.popular_success?.values?.count || 0;
    const popFail = data.metrics.popular_failure?.values?.count || 0;
    const popP95 = data.metrics.popular_latency?.values?.['p(95)'] || 0;
    const hits = data.metrics.cache_hit?.values?.count || 0;

    const hitRate = popSuccess > 0 ? ((hits / popSuccess) * 100).toFixed(1) : 0;

    console.log('\n');
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log('║        🔥 인기 상품 조회 부하 테스트 결과                     ║');
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 📦 백그라운드 (일반 조회)                                     ║');
    console.log(`║    성공: ${bgCount.toString().padStart(6)}건  |  P95: ${bgP95.toFixed(0).padStart(6)}ms                   ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 🔥 인기 상품 조회                                             ║');
    console.log(`║    성공: ${popSuccess.toString().padStart(6)}건  |  실패: ${popFail.toString().padStart(6)}건              ║`);
    console.log(`║    P95 응답시간: ${popP95.toFixed(0).padStart(6)}ms                                  ║`);
    console.log(`║    캐시 히트율 (추정): ${hitRate.toString().padStart(5)}%                             ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');

    const impacted = bgP95 > 500;
    const cacheWorking = popP95 < 100;

    if (popFail > 0) {
        console.log('║ ⚠️  결과: 인기 상품 API 오류 발생                            ║');
    } else if (!cacheWorking) {
        console.log('║ ⚠️  결과: 캐시 효과 미흡 - 응답 시간 느림                    ║');
    } else if (impacted) {
        console.log('║ ⚠️  결과: 인기 상품 부하로 다른 API 영향                     ║');
    } else {
        console.log('║ ✅ 결과: 캐시 정상 동작, 다른 API 영향 없음                  ║');
    }
    console.log('╚══════════════════════════════════════════════════════════════╝\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6/results/popular-products-result.json': JSON.stringify(data, null, 2),
    };
}
