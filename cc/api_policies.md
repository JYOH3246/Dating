# API 정책 문서

- 문서 버전: v1.1
- 작성일: 2026-06-19
- 기준 문서: 소개팅 앱 개발 정책 문서 v1.2 / 상태 전이 v1.0 / DB 제약조건 v1.0
- 범위: 엔드포인트별 인증·권한, 멱등성 키, 트랜잭션 범위, Rate Limit, 웹훅 정책

> Rate Limit 수치는 초기 권장값이며 운영 데이터로 조정한다. (정책 문서에서 미확정 영역)

---

## 0. 공통 규약

### 0.1 인증

- 토큰 방식: PASETO. Access 15분, Refresh 30일, Rotation 사용. (정책 3장)
- 인증 헤더: `Authorization: Bearer <access_token>`
- 가입 진행 상태(`PENDING_VERIFICATION`, `PENDING_AGREEMENTS`) 계정은 인증·본인확인·약관 외 엔드포인트 접근 불가. (정책 5.4)
- 제재 상태별 접근:
  - `SUSPENDED`: 읽기 가능, 주요 쓰기 차단 (정책 16.4)
  - `BANNED`: 인증 자체 거부
  - `DELETED`(복구기간): 복구 엔드포인트만 허용

### 0.2 인증 레벨 표기

| 레벨 | 의미 |
|---|---|
| `PUBLIC` | 토큰 불필요 |
| `PENDING_OK` | 가입 진행 계정 허용(본인인증/약관 단계) |
| `USER` | `ACTIVE` 계정 |
| `USER_RW` | `ACTIVE` ∧ 쓰기 허용(SUSPENDED 차단) |
| `ADMIN:<role>` | 관리자 역할 필요 |
| `WEBHOOK` | 서명 검증된 외부 콜백 |

### 0.3 멱등성

- 멱등성의 최종 보장은 DB 유니크 제약이다. (DB 제약조건 문서 §8)
- 클라이언트 재시도가 잦은 쓰기는 멱등 키를 요구한다.
- 멱등 충돌(unique 위반)은 에러가 아니라 **기존 리소스를 반환**한다(2xx). (정책 12.1)

### 0.4 에러 규약

| HTTP | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_ARGUMENT` | 검증 실패 |
| 401 | `UNAUTHENTICATED` | 토큰 없음/만료 |
| 403 | `PERMISSION_DENIED` | 권한/상태 부족(SUSPENDED 등) |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 멱등 충돌(반환 처리 외), 상태 전이 위반 |
| 429 | `RATE_LIMITED` | Rate Limit 초과 |

### 0.5 Rate Limit 적용 단위

- 기본 키: `user_id` (비인증은 IP).
- 응답에 `X-RateLimit-Remaining`, `Retry-After` 포함.

---

## 1. 인증 / 계정 API

| 메서드 · 경로 | 인증 | 멱등성 키 | 트랜잭션 범위 | Rate Limit(권장) | 비고 |
|---|---|---|---|---|---|
| `POST /auth/oauth/{provider}` | PUBLIC | — | USERS·AUTH_IDENTITIES 조회/생성 | 10/분/IP | OAuth 로그인. 신규면 `PENDING_VERIFICATION` 생성 |
| `POST /auth/identity/verify` | PENDING_OK | `di_hash` (UQ) | UIV insert + USERS→PENDING_AGREEMENTS | 5/분/user | 본인인증 콜백. 만19세↑·DI중복·밴DI 검사 |
| `POST /auth/agreements` | PENDING_OK | — | USER_AGREEMENTS append + 필수동의 검증 + USERS→ACTIVE | 10/분/user | 약관 동의 이력 저장. 본인인증 완료 계정만 ACTIVE 전환 |
| `POST /auth/token/refresh` | PUBLIC(refresh) | `refresh_token_hash`(UQ) | 세션 rotate(폐기+신규) | 30/분/user | 재사용 감지 시 전 세션 폐기 |
| `POST /auth/logout` | USER | — | 현재 세션 revoke | 30/분/user | |
| `GET /auth/sessions` | USER | — | 조회 | 30/분/user | 기기 목록 |
| `DELETE /auth/sessions/{id}` | USER | — | 세션 revoke | 30/분/user | 기기별 로그아웃 |

### 1.1 가입 플로우 (필수 순서)

```text
POST /auth/oauth/{provider}      → PENDING_VERIFICATION
POST /auth/identity/verify       → 본인인증(필수), 만19세↑ 검사, PENDING_AGREEMENTS
POST /auth/agreements            → 필수약관 동의, USERS → ACTIVE
이후 서비스 이용 가능
```

- 본인인증과 필수약관 동의가 모두 완료되기 전에는 `ACTIVE` 전이를 막는다. (상태 전이 문서 §1.3)
- 6번째 기기 로그인 시 LRU(`last_used_at`) 세션을 폐기하고 허용. (정책 3.5)

### 1.2 계정 상태 API

| 메서드 · 경로 | 인증 | 트랜잭션 범위 | 비고 |
|---|---|---|---|
| `DELETE /users/me` | USER | USERS→DELETED, 전 세션 폐기, 프로필 제외 | 14일 복구기간. SUSPENDED도 탈퇴 가능(제재 잔여 보존) |
| `POST /users/me/restore` | DELETED | USERS→ACTIVE | 14일 이내만 |

---

## 2. 프로필 / 사진 API

| 메서드 · 경로 | 인증 | 멱등성 키 | 트랜잭션 범위 | Rate Limit | 비고 |
|---|---|---|---|---|---|
| `POST /profiles` | USER_RW | `user_id`(1:1 UQ) | PROFILES insert | 5/분 | DRAFT 생성 |
| `PATCH /profiles/me` | USER_RW | — | PROFILES update | 20/분 | 생년월일·성별 수정 불가 |
| `POST /profiles/me/submit` | USER_RW | — | PROFILES→PENDING_REVIEW | 10/분 | 검수 큐 등록 |
| `POST /profiles/me/photos` | USER_RW | `file_key` | PROFILE_PHOTOS insert(PENDING) | 20/시간 | 서버에서 EXIF 제거(정책 8.7). 최대 6장 |
| `DELETE /profiles/me/photos/{id}` | USER_RW | — | soft delete | 20/시간 | ACTIVE 프로필은 승인 대표사진 1장 유지. 마지막 승인사진 삭제 시 HIDDEN/PENDING_REVIEW 전환 |
| `PATCH /profiles/me/photos/{id}/primary` | USER_RW | 대표 1장(PUQ) | is_primary 전환 | 20/시간 | APPROVED만 대표 가능 |
| `GET /profiles/{id}` | USER | — | 조회 | 60/분 | 차단/탈퇴 유저 비노출 |
| `PATCH /profiles/me/visibility` | USER_RW | — | ACTIVE↔HIDDEN | 10/분 | 숨김/해제 |

- 사진 검수 SLA: 업로드 후 24시간 목표. 미검수 동안 추천 노출 보류. (정책 8.6)

---

## 3. Discovery / 반응 / 매칭 API

| 메서드 · 경로 | 인증 | 멱등성 키 | 트랜잭션 범위 | Rate Limit | 비고 |
|---|---|---|---|---|---|
| `POST /discovery/card-batches` | USER | `client_request_id` 권장 | 후보 조회 + 노출기록 + quota 차감 | 60/분 | 차단·반응·UNMATCH·최근노출 제외(정책 10.1). 조회성 GET 대신 부수효과가 있는 POST 사용 |
| `POST /reactions` | USER_RW | `(actor_id,target_id)` UQ | 반응 insert + (상호 LIKE 시)매칭·채팅방 생성 | 60/분 | 멱등. 신규가입 24h LIKE 제한(정책 25.1) |
| `GET /matches` | USER | — | 조회 | 60/분 | |
| `DELETE /matches/{id}` | USER | — | MATCH→UNMATCHED, CHAT_ROOM→CLOSED | 30/분 | USER_REACTIONS 유지(재추천 제외) |

### 3.1 POST /reactions 트랜잭션 (상호 LIKE)

```text
BEGIN
  1. USER_REACTIONS insert/upsert -- 활성 pair 부분 UQ, 중복은 멱등 반환
  2. 상대의 LIKE 존재 확인
  3. 있으면 MATCHES insert    -- user1<user2 정규화, UQ
  4. UQ 위반 시 → 기존 MATCH SELECT 후 동일 결과 반환(예외 아님)
  5. CHAT_ROOMS insert        -- UNIQUE(match_id)
  6. CHAT_ROOM_MEMBERS insert
COMMIT
```

- 동시 상호 LIKE 충돌은 정상 경로로 멱등 처리한다. (정책 12.1)

---

## 4. 차단 / 채팅 API

| 메서드 · 경로 | 인증 | 멱등성 키 | 트랜잭션 범위 | Rate Limit | 비고 |
|---|---|---|---|---|---|
| `POST /blocks` | USER_RW | `(blocker,blocked)` UQ | BLOCK insert | 30/분 | MATCH/CHAT_ROOM 상태 변경 없이 런타임 게이트로 채팅 즉시 차단 |
| `DELETE /blocks/{id}` | USER_RW | — | BLOCK delete | 30/분 | 매칭 ACTIVE면 채팅 재개 |
| `GET /chat/rooms` | USER | — | 조회 | 60/분 | |
| `GET /chat/rooms/{id}/messages` | USER | — | 조회 | 120/분 | 멤버만, 차단 또는 CLOSED 시 읽기만 |
| `POST /chat/rooms/{id}/messages` | USER_RW | `client_message_id` UQ | MESSAGE insert | 120/분 | 멱등. 채팅 가능 게이트 검사 |

### 4.1 채팅 전송 게이트 (런타임)

```text
매칭 ACTIVE ∧ 채팅방 OPEN ∧ 양쪽 차단 없음 ∧ 양쪽 USERS=ACTIVE
```

- 실시간은 WebSocket, 영속화 시 동일 멱등 키 적용. (정책 14장)

---

## 5. 신고 API

| 메서드 · 경로 | 인증 | 멱등성/제한 | 트랜잭션 범위 | Rate Limit | 비고 |
|---|---|---|---|---|---|
| `POST /reports` | USER | 24h 내 동일대상·사유 중복 제한 | REPORT insert(reason enum + reason_detail 저장) + 불법콘텐츠 즉시 비공개 | 20/시간 | reason은 enum, reason_detail은 사용자 상세 설명. 불법콘텐츠는 즉시처리 트랙(정책 15.5) |
| `GET /reports/me` | USER | — | 조회 | 30/분 | 내 신고 상태 |

---

## 6. 결제 / 구독 API

| 메서드 · 경로 | 인증 | 멱등성 키 | 트랜잭션 범위 | Rate Limit | 비고 |
|---|---|---|---|---|---|
| `GET /subscriptions/me` | USER | — | 조회 | 30/분 | 현재 권한 |
| `GET /plans` | USER | — | 조회 | 30/분 | 가격은 설정/스토어 기준 |
| `POST /payments/verify` | USER_RW | `(provider,tx_id)` UQ | 영수증 검증 + 구독/권한 반영 | 20/분 | 서버 검증 필수. USER_ENTITLEMENTS projection 갱신 |
| `POST /webhooks/{provider}` | WEBHOOK | `(provider,event_id)` UQ | PAYMENT_EVENTS + 구독상태 전이 + 권한 회수/지급 | — | 서명검증. 환불 권한회수 트리거 |

### 6.1 웹훅 정책

- 서명 검증 실패 시 즉시 거부.
- `PAYMENT_EVENTS UNIQUE(provider,event_id)`로 중복 웹훅 멱등 처리.
- 권한 회수 트리거는 **환불 웹훅 수신 시점**. 클라이언트 환불 성공 미신뢰. (정책 17.7)
- 갱신 실패→`GRACE_PERIOD`(3일)→복구/`EXPIRED`. (상태 전이 문서 §7)
- 처리 결과와 무관하게 빠르게 2xx 응답 후 비동기 처리(재시도 대비)를 권장.

---

## 7. 관리자 API (권한 매트릭스)

| 메서드 · 경로 | 최소 권한 | 감사로그 | 비고 |
|---|---|---|---|
| `GET /admin/users` | `ADMIN:SUPPORT` | 조회 기록 | |
| `GET /admin/reports` | `ADMIN:MODERATOR` | — | 신고 큐 |
| `PATCH /admin/reports/{id}` | `ADMIN:MODERATOR` | 필수 | 조치/반려 |
| `GET /admin/reports/{id}/messages` | `ADMIN:MODERATOR` | 필수 | 전후 10개 열람, 사유 기록(정책 14.4) |
| `POST /admin/sanctions` | `ADMIN:OPERATOR` | 필수 | 제재 등록, DI 귀속(정책 16.5) |
| `DELETE /admin/sanctions/{id}` | `ADMIN:OPERATOR` | 필수 | 제재 해제 |
| `POST /admin/users/{id}/unban` | `ADMIN:SUPER_ADMIN` | 필수 | BANNED→ACTIVE, DI 차단 해제 |
| `GET /admin/photos/review` | `ADMIN:MODERATOR` | — | 사진 검수 큐 |
| `PATCH /admin/photos/{id}` | `ADMIN:MODERATOR` | 필수 | 승인/반려 |

- 모든 관리자 쓰기 작업은 `ADMIN_AUDIT_LOGS`에 기록. (정책 19.4)
- 결제 환불 처리 등 고위험 액션은 `SUPER_ADMIN` 한정 권장.

---

## 8. 알림 API

| 메서드 · 경로 | 인증 | 비고 |
|---|---|---|
| `GET /notifications/settings` | USER | |
| `PATCH /notifications/settings` | USER | 마케팅/야간(NIGHT_MARKETING) 동의 토글 |

- 야간(21:00~08:00) 광고성 알림은 NIGHT_MARKETING 동의자에게만 발송. 정보성(메시지·결제·보안·제재)은 예외. (정책 18.3)

---

## 9. 멱등성·Rate Limit 요약

### 9.1 멱등 엔드포인트

| 엔드포인트 | 멱등 키 |
|---|---|
| `POST /auth/token/refresh` | `refresh_token_hash` |
| `POST /reactions` | 활성 `(actor_id, target_id)` pair |
| `POST /chat/.../messages` | `client_message_id` |
| `POST /payments/verify` | `(provider, transaction_id)` |
| `POST /webhooks/{provider}` | `(provider, event_id)` |

### 9.2 강한 제한 대상

| 동작 | 제한(권장) | 근거 |
|---|---|---|
| LIKE/PASS | 60/분 + 신규 24h 제한 | 어뷰징 방지(정책 25.1) |
| 신고 | 20/시간 + 24h 중복 제한 | 악용 방지(정책 15.4) |
| 사진 업로드 | 20/시간 | 검수 부하 관리 |
| 본인인증 | 5/분 | 무차별 시도 방지 |
