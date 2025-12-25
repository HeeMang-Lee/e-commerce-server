/**
 * K6 부하 테스트 공통 설정
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const defaultHeaders = {
    'Content-Type': 'application/json',
};

/**
 * 테스트 결과 검증을 위한 thresholds 공통 설정
 */
export const commonThresholds = {
    http_req_duration: ['p(50)<100', 'p(95)<500', 'p(99)<2000'],
    http_req_failed: ['rate<0.01'], // 에러율 1% 미만
};

/**
 * 응답 상태 확인 유틸리티
 */
export function isSuccess(response) {
    return response.status >= 200 && response.status < 300;
}

export function isBusinessError(response, expectedCode) {
    if (response.status !== 400 && response.status !== 409) return false;
    try {
        const body = JSON.parse(response.body);
        return body.code === expectedCode;
    } catch {
        return false;
    }
}

/**
 * 랜덤 사용자 ID 생성 (1 ~ maxUserId)
 */
export function randomUserId(maxUserId = 10000) {
    return Math.floor(Math.random() * maxUserId) + 1;
}

/**
 * 랜덤 상품 ID 생성 (1 ~ maxProductId)
 */
export function randomProductId(maxProductId = 10) {
    return Math.floor(Math.random() * maxProductId) + 1;
}
