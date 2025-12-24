/**
 * 시나리오 1: 선착순 쿠폰 발급 (Peak Test)
 *
 * 목적: 선착순 쿠폰 발급 시 동시성 제어 및 처리량 검증
 *
 * 테스트 조건:
 * - 쿠폰 수량: 500개
 * - 동시 사용자: 10,000명
 * - 목표: 정확히 500명만 발급 성공
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, defaultHeaders, isSuccess, randomUserId } from './common.js';

// 커스텀 메트릭
const couponIssued = new Counter('coupon_issued');
const couponSoldOut = new Counter('coupon_sold_out');
const couponDuplicate = new Counter('coupon_duplicate');
const issueLatency = new Trend('issue_latency');
const successRate = new Rate('success_rate');

// 테스트 설정
const COUPON_ID = __ENV.COUPON_ID || 1;
const MAX_USERS = __ENV.MAX_USERS || 10000;

export const options = {
    scenarios: {
        coupon_rush: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 5000 },   // 10초간 5,000명까지 증가
                { duration: '20s', target: 10000 },  // 20초간 10,000명까지 증가 (피크)
                { duration: '60s', target: 10000 },  // 60초간 10,000명 유지
                { duration: '30s', target: 0 },      // 30초간 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<3000'],   // P99 3초 미만
        http_req_failed: ['rate<0.01'],       // 시스템 에러 1% 미만
        'coupon_issued': ['count<=500'],      // 발급 수량 검증
    },
};

export default function () {
    const userId = randomUserId(MAX_USERS);

    const payload = JSON.stringify({
        userId: userId,
        couponId: parseInt(COUPON_ID),
    });

    const startTime = Date.now();

    const response = http.post(
        `${BASE_URL}/api/coupons/issue`,
        payload,
        { headers: defaultHeaders }
    );

    const latency = Date.now() - startTime;
    issueLatency.add(latency);

    // 응답 검증
    if (isSuccess(response)) {
        couponIssued.add(1);
        successRate.add(1);
        check(response, {
            'coupon issued successfully': (r) => r.status === 200 || r.status === 201,
        });
    } else {
        successRate.add(0);

        try {
            const body = JSON.parse(response.body);

            if (body.code === 'COUPON_SOLD_OUT' || body.message?.includes('sold out')) {
                couponSoldOut.add(1);
                check(response, {
                    'coupon sold out (expected)': () => true,
                });
            } else if (body.code === 'COUPON_ALREADY_ISSUED' || body.message?.includes('already')) {
                couponDuplicate.add(1);
                check(response, {
                    'duplicate issue prevented': () => true,
                });
            } else {
                check(response, {
                    'unexpected error': () => false,
                });
            }
        } catch {
            check(response, {
                'parse error response': () => false,
            });
        }
    }

    // 실제 선착순 상황 시뮬레이션을 위한 최소 대기
    sleep(0.1);
}

export function handleSummary(data) {
    const issued = data.metrics.coupon_issued?.values?.count || 0;
    const soldOut = data.metrics.coupon_sold_out?.values?.count || 0;
    const duplicate = data.metrics.coupon_duplicate?.values?.count || 0;

    console.log('\n========== 쿠폰 발급 테스트 결과 ==========');
    console.log(`발급 성공: ${issued}건`);
    console.log(`품절 거부: ${soldOut}건`);
    console.log(`중복 거부: ${duplicate}건`);
    console.log(`총 요청: ${issued + soldOut + duplicate}건`);
    console.log('==========================================\n');

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'results/coupon-issue-result.json': JSON.stringify(data, null, 2),
    };
}

// K6 내장 textSummary 사용
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';
