## :pushpin: PR 제목 규칙
[STEP08] 이희망

---
## **과제 체크리스트** :white_check_mark:

### ✅ **STEP07: DB 설계 개선 및 구현** (필수)
- [x] 기존 설계된 테이블 구조에 대한 개선점이 반영되었는가? (선택)
- [x] Repository 및 데이터 접근 계층이 역할에 맞게 분리되어 있는가?
- [x] MySQL 기반으로 연동되고 동작하는가?
- [x] infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?
- [x] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?
- [x] 기존에 작성된 동시성 테스트가 잘 통과하는가?

### 🔥 **STEP08: 쿼리 및 인덱스 최적화** (심화)
- [x] 조회 성능 저하 가능성이 있는 기능을 식별하였는가?
- [x] 쿼리 실행계획(Explain) 기반으로 문제를 분석하였는가?
- [x] 인덱스 설계 또는 쿼리 구조 개선 등 해결방안을 도출하였는가?

---
## 🔗 **주요 구현 커밋**

### 1. PopularProduct 쿼리 최적화
- 인덱스 설계: [`e32fa46`](https://github.com/HeeMang-Lee/e-commerce-server/commit/e32fa46)
- FORCE INDEX 적용: [`46b08a7`](https://github.com/HeeMang-Lee/e-commerce-server/commit/46b08a7)
- EXPLAIN 분석 문서: [`0579f9f`](https://github.com/HeeMang-Lee/e-commerce-server/commit/0579f9f)

### 2. N+1 문제 해결
- IN 절 조회 메서드 추가: [`f2e29f1`](https://github.com/HeeMang-Lee/e-commerce-server/commit/f2e29f1)
- OrderService 개선: [`a9261c6`](https://github.com/HeeMang-Lee/e-commerce-server/commit/a9261c6)
- 문제 해결 과정 문서: [`9cabaac`](https://github.com/HeeMang-Lee/e-commerce-server/commit/9cabaac)
- 성능 테스트: [`6c41e05`](https://github.com/HeeMang-Lee/e-commerce-server/commit/6c41e05)

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1. PopularProduct 쿼리에서 FORCE INDEX를 써야 했는데, 이게 MySQL 옵티마이저가 잘못된 인덱스를 선택해서 그런 건지, 아니면 인덱스 설계 자체에 문제가 있는 건지 궁금합니다.
2. N+1 해결할 때 IN 절 방식을 선택했는데, JOIN으로 한 번에 가져오는 게 더 나을지 고민이 됩니다. 일단 코드 명확성 때문에 IN 절을 선택했어요.

### 특별히 리뷰받고 싶은 부분
- `docs/EXPLAIN_ANALYSIS_POPULAR_PRODUCT.md`에서 EXPLAIN 결과 분석이 제대로 되었는지 확인 부탁드립니다.
- OrderService의 IN 절 배치 조회 방식이 실무에서도 괜찮은 패턴인지 궁금합니다.

---
## 📊 **주요 개선 결과**

### 1. PopularProduct 쿼리 최적화
| 항목 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| 실행 시간 (평균) | 21ms | 10ms | **52% 개선** |
| 사용 인덱스 | PRIMARY | idx_order_product_quantity (FORCE) | - |
| 접근 행 수 | 20,000+ | 필요한 행만 | - |

**핵심 개선 사항:**
- Covering Index 설계로 테이블 접근 최소화
- FORCE INDEX로 옵티마이저의 잘못된 인덱스 선택 강제 수정

### 2. N+1 문제 해결 (주문 내역 조회)
| 주문 수 | 개선 전 쿼리 | 개선 후 쿼리 | 감소율 |
|---------|-------------|-------------|--------|
| 10개 | 11번 | 2번 | 82% |
| 100개 | 101번 | 2번 | **98%** |
| 1,000개 | 1,001번 | 2번 | 99.8% |

**핵심 개선 사항:**
- 반복문 안의 DB 호출을 IN 절 배치 조회로 변경
- 메모리 그룹핑으로 데이터 매핑 (DB 왕복 최소화)

---
## 📝 **회고**

### ✨ 잘한 점
- EXPLAIN을 직접 돌려보면서 쿼리 실행 계획을 분석하니까 문제가 명확하게 보였어요. 이론으로만 알던 것들을 실제로 확인하니 재밌었습니다.
- N+1 문제를 발견하고 해결하는 과정을 문서로 남겨서, 나중에 비슷한 문제가 생겼을 때 참고할 수 있게 했습니다.
- 성능 테스트를 작성해서 개선 효과를 객관적으로 측정했어요. Hibernate Statistics로 쿼리 수를 세는 방법이 유용했습니다.

### 😓 어려웠던 점
- MySQL 옵티마이저가 예상과 다른 인덱스를 선택해서 당황했어요. FORCE INDEX를 써야 하는지 고민이 많았습니다.
- 간접 참조로 설계했는데도 N+1이 발생할 수 있다는 걸 몰랐어요. JPA Lazy Loading만 막으면 되는 줄 알았는데, Service 레벨에서도 조심해야 하더라구요.
- IN 절 vs JOIN 중에서 어떤 걸 선택해야 할지 명확한 기준을 잡기 어려웠습니다. 일단 코드 명확성을 우선했는데, 성능이 더 중요하면 JOIN으로 바꿔야 할 것 같아요.

### 🚀 다음에 시도할 것
- 실제 운영 환경 수준의 데이터(수십만~수백만 건)로 성능 테스트를 해보고 싶습니다.
- Slow Query Log를 모니터링하는 방법을 공부해서, 운영 중에도 문제를 빨리 발견할 수 있게 하고 싶어요.
- JOIN 방식으로도 구현해보고 IN 절과 성능을 직접 비교해볼 예정입니다.

---
## 📚 **참고 자료**

- [MySQL 공식 문서 - EXPLAIN Output Format](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html)
- [Use The Index, Luke - Covering Index](https://use-the-index-luke.com/sql/explain-plan/mysql/covering-index)
- [N+1 쿼리 문제와 해결 방법들](https://incheol-jung.gitbook.io/docs/q-and-a/spring/n+1)
- Hibernate Statistics 활용법

---
## ✋ **체크리스트 (제출 전 확인)**

- [x] 적절한 ORM을 사용하였는가? (JPA, TypeORM, Prisma, Sequelize 등)
- [x] Repository 전환 간 서비스 로직의 변경은 없는가?
- [x] docker-compose, testcontainers 등 로컬 환경에서 실행하고 테스트할 수 있는 환경을 구성했는가?
- [x] 성능 개선 효과를 측정하고 문서화했는가?
- [x] 쿼리 실행 계획(EXPLAIN)을 분석하고 기록했는가?
