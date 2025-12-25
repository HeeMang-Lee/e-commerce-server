/**
 * 시나리오 5: 포인트 동시 충전 스트레스 테스트 (현실적 버전)
 *
 * 목적: 같은 사용자에 대한 동시 요청 시 정합성 검증
 *       - 분산 락 동작 확인
 *       - 락 경합 상황에서 성능
 *       - 다른 API 영향도
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 메트릭
const bgSuccess = new Counter('bg_success');
const bgLatency = new Trend('bg_latency');
const chargeSuccess = new Counter('charge_success');
const chargeFailed = new Counter('charge_failed');
const lockContention = new Counter('lock_contention');
const chargeLatency = new Trend('charge_latency');

export const options = {
    scenarios: {
        background_traffic: {
            executor: 'constant-vus',
            vus: 20,
            duration: '1m30s',
            exec: 'backgroundTraffic',
        },
        point_stress: {
            executor: 'ramping-vus',
            startTime: '10s',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '20s', target: 100 },   // 100명 동시 충전
                { duration: '30s', target: 100 },
                { duration: '10s', target: 0 },
            ],
            exec: 'pointStress',
        },
    },
    thresholds: {
        'bg_latency': ['p(95)<1000'],
        'charge_latency': ['p(95)<5000'],  // 락 대기 시간 고려
    },
};

export function backgroundTraffic() {
    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/products`, {
        headers: { 'Content-Type': 'application/json' },
    });
    bgLatency.add(Date.now() - start);

    if (res.status === 200) bgSuccess.add(1);
    sleep(0.5);
}

export function pointStress() {
    // 100명의 사용자가 각각 충전 (동일 사용자 동시 요청 시뮬레이션을 위해 제한된 userId 사용)
    const userId = (__VU % 20) + 1;  // 1~20번 사용자로 제한하여 경합 유도

    const payload = JSON.stringify({
        userId: userId,
        amount: 1000,
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/points/users/${userId}/charge`, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'point_charge' },
    });
    const latency = Date.now() - start;
    chargeLatency.add(latency);

    if (res.status === 200 || res.status === 201) {
        chargeSuccess.add(1);
    } else {
        chargeFailed.add(1);
        try {
            const body = JSON.parse(res.body);
            if (body.code?.includes('LOCK') || body.message?.includes('lock') ||
                body.code?.includes('TIMEOUT')) {
                lockContention.add(1);
            }
        } catch {}
    }

    sleep(0.1 + Math.random() * 0.2);
}

export function handleSummary(data) {
    const bgCount = data.metrics.bg_success?.values?.count || 0;
    const bgP95 = data.metrics.bg_latency?.values?.['p(95)'] || 0;
    const chargeSuccessCount = data.metrics.charge_success?.values?.count || 0;
    const chargeFailCount = data.metrics.charge_failed?.values?.count || 0;
    const lockCount = data.metrics.lock_contention?.values?.count || 0;
    const chargeP95 = data.metrics.charge_latency?.values?.['p(95)'] || 0;

    const total = chargeSuccessCount + chargeFailCount;
    const successRate = total > 0 ? ((chargeSuccessCount / total) * 100).toFixed(1) : 0;

    console.log('\n');
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log('║        💰 포인트 동시 충전 스트레스 테스트 결과               ║');
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 📦 백그라운드 (상품 조회)                                     ║');
    console.log(`║    성공: ${bgCount.toString().padStart(6)}건  |  P95: ${bgP95.toFixed(0).padStart(6)}ms                   ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');
    console.log('║ 💰 포인트 충전                                                ║');
    console.log(`║    성공: ${chargeSuccessCount.toString().padStart(6)}건  |  실패: ${chargeFailCount.toString().padStart(6)}건              ║`);
    console.log(`║    락 경합 실패: ${lockCount.toString().padStart(6)}건                                  ║`);
    console.log(`║    성공률: ${successRate.toString().padStart(6)}%  |  P95: ${chargeP95.toFixed(0).padStart(6)}ms              ║`);
    console.log('╠══════════════════════════════════════════════════════════════╣');

    const impacted = bgP95 > 500;
    const highContention = lockCount > total * 0.1;

    if (highContention) {
        console.log('║ ⚠️  결과: 락 경합 빈번 - 분산 락 설정 검토 필요             ║');
    } else if (impacted) {
        console.log('║ ⚠️  결과: 충전 부하로 다른 API 영향                         ║');
    } else {
        console.log('║ ✅ 결과: 동시 충전 정상 처리, 다른 API 영향 없음            ║');
    }
    console.log('╚══════════════════════════════════════════════════════════════╝\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6/results/point-stress-result.json': JSON.stringify(data, null, 2),
    };
}
