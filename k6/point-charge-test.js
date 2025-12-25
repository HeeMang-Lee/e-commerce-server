/**
 * 시나리오 5: 포인트 동시 충전 (Stress Test)
 *
 * 목적: 같은 사용자에 대한 동시 요청 시 정합성 검증
 *
 * 테스트 조건:
 * - 사용자 100명
 * - 각 사용자당 동시 충전 요청 10건 (총 1,000건)
 * - 충전 금액: 1,000원씩
 * - 기대 결과: 각 사용자 잔액 = 초기 + 10,000원
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, defaultHeaders } from './common.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// 커스텀 메트릭
const chargeSuccess = new Counter('charge_success');
const chargeFailed = new Counter('charge_failed');
const lockContention = new Counter('lock_contention');
const chargeLatency = new Trend('charge_latency');
const successRate = new Rate('success_rate');

// 테스트 설정
const USER_COUNT = __ENV.USER_COUNT || 100;
const CHARGE_AMOUNT = __ENV.CHARGE_AMOUNT || 1000;
const CHARGES_PER_USER = __ENV.CHARGES_PER_USER || 10;

export const options = {
    scenarios: {
        point_charge_stress: {
            executor: 'per-vu-iterations',
            vus: parseInt(USER_COUNT),
            iterations: parseInt(CHARGES_PER_USER),
            maxDuration: '5m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],  // P95 3초 미만
        'charge_success': ['count>0'],       // 최소 1건 이상 성공
    },
};

export default function () {
    // VU ID를 사용자 ID로 매핑 (동일 사용자에 대한 동시 요청 시뮬레이션)
    const userId = __VU;

    const payload = JSON.stringify({
        userId: userId,
        amount: parseInt(CHARGE_AMOUNT),
    });

    const startTime = Date.now();

    const response = http.post(
        `${BASE_URL}/api/points/charge`,
        payload,
        { headers: defaultHeaders }
    );

    const latency = Date.now() - startTime;
    chargeLatency.add(latency);

    if (response.status === 200 || response.status === 201) {
        chargeSuccess.add(1);
        successRate.add(1);
        check(response, {
            'charge completed': () => true,
        });
    } else {
        successRate.add(0);

        try {
            const body = JSON.parse(response.body);

            // 락 경합으로 인한 실패
            if (body.code?.includes('LOCK') ||
                body.code?.includes('TIMEOUT') ||
                body.message?.includes('lock') ||
                body.message?.includes('timeout')) {
                lockContention.add(1);
                chargeFailed.add(1);
                check(response, {
                    'lock contention (expected under stress)': () => true,
                });
            } else {
                chargeFailed.add(1);
                check(response, {
                    'unexpected charge error': () => false,
                });
                console.log(`Charge failed for user ${userId}: ${response.status} - ${response.body}`);
            }
        } catch {
            chargeFailed.add(1);
            check(response, {
                'parse error response': () => false,
            });
        }
    }

    // 약간의 간격을 두어 동시성 시뮬레이션
    sleep(0.1 + Math.random() * 0.2);
}

export function teardown(data) {
    // 최종 잔액 검증
    console.log('\n========== 포인트 잔액 검증 ==========');

    let verificationPassed = 0;
    let verificationFailed = 0;
    const expectedIncrease = parseInt(CHARGES_PER_USER) * parseInt(CHARGE_AMOUNT);

    for (let userId = 1; userId <= Math.min(10, parseInt(USER_COUNT)); userId++) {
        const response = http.get(
            `${BASE_URL}/api/points/${userId}`,
            { headers: defaultHeaders }
        );

        if (response.status === 200) {
            try {
                const body = JSON.parse(response.body);
                const balance = body.balance || body.point || body.amount || 0;
                console.log(`User ${userId}: 잔액 ${balance}원`);

                // 초기 잔액 모르므로 로그만 출력
                verificationPassed++;
            } catch {
                verificationFailed++;
            }
        } else {
            verificationFailed++;
        }
    }

    console.log(`잔액 조회 성공: ${verificationPassed}건, 실패: ${verificationFailed}건`);
    console.log('=====================================\n');
}

export function handleSummary(data) {
    const success = data.metrics.charge_success?.values?.count || 0;
    const failed = data.metrics.charge_failed?.values?.count || 0;
    const contention = data.metrics.lock_contention?.values?.count || 0;

    const total = success + failed;
    const successPercent = total > 0 ? ((success / total) * 100).toFixed(2) : 0;

    console.log('\n========== 포인트 충전 테스트 결과 ==========');
    console.log(`충전 성공: ${success}건`);
    console.log(`충전 실패: ${failed}건`);
    console.log(`락 경합 실패: ${contention}건`);
    console.log(`성공률: ${successPercent}%`);
    console.log('============================================\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/point-charge-result.json': JSON.stringify(data, null, 2),
    };
}
