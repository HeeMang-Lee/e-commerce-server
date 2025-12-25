/**
 * 대시보드 검증용 혼합 부하 테스트
 *
 * 여러 API를 동시에 호출하여 대시보드 패널 검증
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, defaultHeaders } from './common.js';

// 커스텀 메트릭
const successCount = new Counter('success_count');
const failureCount = new Counter('failure_count');
const responseLatency = new Trend('response_latency');

export const options = {
    scenarios: {
        mixed_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },   // 10초간 50명까지 증가
                { duration: '30s', target: 100 },  // 30초간 100명 유지
                { duration: '10s', target: 0 },    // 10초간 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],
    },
};

// API 엔드포인트 목록
const endpoints = [
    { name: '상품 목록', method: 'GET', url: '/api/products', weight: 40 },
    { name: '상품 상세', method: 'GET', url: '/api/products/1', weight: 30 },
    { name: '상품 상세2', method: 'GET', url: '/api/products/2', weight: 15 },
    { name: '상품 상세3', method: 'GET', url: '/api/products/3', weight: 15 },
];

function selectEndpoint() {
    const rand = Math.random() * 100;
    let cumulative = 0;
    for (const ep of endpoints) {
        cumulative += ep.weight;
        if (rand < cumulative) return ep;
    }
    return endpoints[0];
}

export default function () {
    const endpoint = selectEndpoint();
    const startTime = Date.now();

    let response;
    if (endpoint.method === 'GET') {
        response = http.get(`${BASE_URL}${endpoint.url}`, { headers: defaultHeaders });
    } else {
        response = http.post(`${BASE_URL}${endpoint.url}`, endpoint.body, { headers: defaultHeaders });
    }

    const latency = Date.now() - startTime;
    responseLatency.add(latency);

    const success = response.status >= 200 && response.status < 300;

    if (success) {
        successCount.add(1);
    } else {
        failureCount.add(1);
    }

    check(response, {
        'status is success': (r) => r.status >= 200 && r.status < 300,
    });

    sleep(0.1 + Math.random() * 0.2);
}

export function handleSummary(data) {
    const success = data.metrics.success_count?.values?.count || 0;
    const failure = data.metrics.failure_count?.values?.count || 0;
    const total = success + failure;

    console.log('\n========== 대시보드 테스트 결과 ==========');
    console.log(`총 요청: ${total}건`);
    console.log(`성공: ${success}건 (${((success/total)*100).toFixed(1)}%)`);
    console.log(`실패: ${failure}건 (${((failure/total)*100).toFixed(1)}%)`);
    console.log(`TPS: ${(total / 50).toFixed(1)} req/s`);
    console.log('==========================================\n');

    return {
        'stdout': '\n',
    };
}
