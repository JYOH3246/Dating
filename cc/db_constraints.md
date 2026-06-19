# DB 제약조건 / 인덱스 상세 문서

- 문서 버전: v1.1
- 작성일: 2026-06-19
- 기준 문서: 소개팅 앱 개발 정책 문서 v1.2
- DBMS: PostgreSQL
- 표기: `PK`(기본키) / `UQ`(유니크) / `IX`(인덱스) / `CK`(체크) / `FK`(외래키) / `PUQ`(부분 유니크 인덱스)

---

## 0. 공통 설계 원칙

- 멱등성과 정합성의 **최종 방어선은 DB 제약조건**이다. 애플리케이션 레벨 체크만 신뢰하지 않는다. (정책 22.3)
- `id`는 모든 테이블에서 surrogate PK(BIGINT identity 또는 UUID)를 사용한다.
- soft delete 대상 테이블의 유니크는 `WHERE deleted_at IS NULL` 부분 유니크로 둬서, 삭제 후 동일 값 재사용을 허용한다.
- enum류는 PostgreSQL native enum 또는 `CK` + 문자열 중 택1한다. 본 문서는 이식성을 위해 `CK`로 표기한다.
- 시간 컬럼은 `timestamptz`(UTC)를 사용한다.
- FK의 `ON DELETE`는 보존 정책(정책 20장)에 맞춰 신중히 정한다. 보존 의무 테이블은 `RESTRICT`/`NO ACTION`을 기본으로 하고, soft delete로 처리한다. (물리 CASCADE 지양)

---

## 1. 인증 / 계정 도메인

### 1.1 USERS

```sql
PK  (id)
CK  status IN ('PENDING_VERIFICATION','PENDING_AGREEMENTS','ACTIVE','SUSPENDED','DELETED','ANONYMIZED','BANNED')
PUQ (email) WHERE email IS NOT NULL AND deleted_at IS NULL
IX  (status)
IX  (created_at)
```

- 이메일은 보조 식별자다. 가명처리 시 `null`/랜덤 토큰으로 바뀌므로 부분 유니크로 둔다. (정책 5.2 / 6.5)
- 실제 계정 식별은 AUTH_IDENTITIES의 `(provider, provider_user_id)`가 기준이다.

### 1.2 AUTH_IDENTITIES

```sql
PK  (id)
PUQ (provider, provider_user_id) WHERE deleted_at IS NULL
IX  (user_id)
FK  (user_id) REFERENCES USERS(id)
CK  provider IN ('GOOGLE','NAVER','KAKAO','APPLE')
```

- OAuth identity 식별 기준. 동일 이메일이라도 자동 병합하지 않는다. (정책 5.2)
- 이미 다른 활성 계정에 연결된 identity는 연결 불가 → `PUQ (provider, provider_user_id) WHERE deleted_at IS NULL`로 보장.
- 탈퇴 14일 후 동일 OAuth 재가입을 허용하기 위해 로그인용 identity와 제재/감사용 fingerprint를 분리한다.
- "최소 1개 로그인 수단 유지"는 트랜잭션/애플리케이션 레벨에서 강제한다. (DB 단독 보장 불가)
- Apple private relay email은 고유 식별자로 신뢰하지 않으므로 식별 키에서 제외한다. (정책 5.3)

### 1.3 USER_IDENTITY_VERIFICATIONS (본인인증)

```sql
PK  (id)
PUQ (di_hash) WHERE is_active = true
IX  (user_id)
FK  (user_id) REFERENCES USERS(id)
```

- 본인인증은 **필수**다. (정책 5.4) DI 해시는 중복가입·밴 회피 차단의 핵심 키.
- 영구밴 DI는 별도 차단 리스트(또는 `is_active=false` + ban 플래그)로 관리해 재가입을 막는다. (정책 16.5)
- CI/DI는 원문 저장 금지, 해시만 저장.

### 1.4 USER_AGREEMENTS (약관 동의, append-only)

```sql
PK  (id)
IX  (user_id, agreement_type, version)
IX  (user_id, agreed_at)
FK  (user_id) REFERENCES USERS(id)
CK  agreement_type IN ('TERMS_OF_SERVICE','PRIVACY_POLICY','LOCATION_BASED','MARKETING','NIGHT_MARKETING')
```

- append-only 이력이므로 유니크를 두지 않는다. (재동의/철회로 동일 버전이 여러 행 생길 수 있음)
- 최신 동의 상태는 `(user_id, agreement_type)`별 최신 `agreed_at` 행으로 조회한다.

### 1.5 AUTH_TOKEN_SESSIONS

```sql
PK  (id)
UQ  (refresh_token_hash)
IX  (user_id)
IX  (device_id)
IX  (expires_at)
PUQ (user_id, device_id) WHERE revoked_at IS NULL
FK  (user_id) REFERENCES USERS(id)
```

- Refresh Token은 해시만 저장. `UQ (refresh_token_hash)`가 재사용 감지의 기반. (정책 3.1 / 3.4)
- 최대 5기기 제한과 초과 시 LRU 폐기는 `last_used_at` 기준 애플리케이션 로직으로 처리한다. (정책 3.5)
- `(user_id, device_id)` 활성 세션 1개 유지를 부분 유니크로 보강(같은 기기 중복 세션 방지).

### 1.6 PASETO_KEYS (키 관리)

```sql
PK  (key_id)
PUQ (status) WHERE status = 'ACTIVE'   -- 발급용 활성 키는 1개
CK  status IN ('ACTIVE','VERIFY_ONLY','RETIRED','COMPROMISED')
IX  (status)
```

- 발급에 사용하는 `ACTIVE` 키는 동시에 1개만 존재하도록 부분 유니크로 보강. 검증용 `VERIFY_ONLY`는 다수 허용. (정책 4.3)

---

## 2. 프로필 도메인

### 2.1 PROFILES

```sql
PK  (id)
PUQ (user_id) WHERE deleted_at IS NULL     -- 1:1
IX  (status)
IX  (region_code, status)
FK  (user_id) REFERENCES USERS(id)
CK  status IN ('DRAFT','PENDING_REVIEW','ACTIVE','REJECTED','HIDDEN','DELETED')
CK  gender IN ('MALE','FEMALE','UNKNOWN')
```

- 추천 후보 조회가 `region_code + status` 중심이므로 복합 인덱스 필수. (정책 9.2 / 10.2)
- 생년월일/성별은 본인인증 값 기준이며 사용자 직접 수정 불가. 변경 이력은 별도 테이블에 보관. (정책 7.4)

### 2.2 PROFILE_PHOTOS

```sql
PK  (id)
UQ  (profile_id, display_order)
PUQ (profile_id) WHERE is_primary = true AND deleted_at IS NULL   -- 대표사진 1장
PUQ (profile_id, file_key) WHERE deleted_at IS NULL                 -- 사진 업로드 멱등성
IX  (profile_id, status)
FK  (profile_id) REFERENCES PROFILES(id)
CK  status IN ('PENDING','APPROVED','REJECTED','DELETED')
CK  display_order BETWEEN 1 AND 6
```

- **대표 사진 1장 제약**은 `is_primary = true AND deleted_at IS NULL` 부분 유니크 인덱스로 보장.
- 대표 사진이 `APPROVED` 상태여야 한다는 규칙은 애플리케이션 또는 트리거로 강제한다.
- `file_key` 부분 유니크로 사진 업로드 재시도 멱등성을 보강한다.
- 최소 1장 / 최대 6장은 `display_order` CHECK + 애플리케이션 검증(또는 트리거)으로 강제.

### 2.3 INTERESTS / PROFILE_INTERESTS

```sql
-- INTERESTS (운영자 마스터 데이터)
PK  (id)
UQ  (code)
CK  is_active IN (true, false)

-- PROFILE_INTERESTS
PK  (id)
UQ  (profile_id, interest_id)
IX  (interest_id)
FK  (profile_id) REFERENCES PROFILES(id)
FK  (interest_id) REFERENCES INTERESTS(id)
```

- 최소 3개 선택은 애플리케이션 레벨에서 강제. (정책 9.1)

### 2.4 MATCH_PREFERENCES / MATCH_PREFERENCE_REGIONS

```sql
-- MATCH_PREFERENCES
PK  (id)
UQ  (user_id)
FK  (user_id) REFERENCES USERS(id)
CK  age_min <= age_max
CK  age_min >= 19            -- 19세 미만 매칭 차단

-- MATCH_PREFERENCE_REGIONS
PK  (id)
UQ  (match_preference_id, region_code)
FK  (match_preference_id) REFERENCES MATCH_PREFERENCES(id)
```

- 지역/나이를 핵심 필터로 사용. (정책 9.2)

---

## 3. Discovery / 반응 / 매칭 도메인

### 3.1 DISCOVERY_EXPOSURES (노출 이력)

```sql
PK  (id)
IX  (viewer_user_id, exposed_at)
IX  (viewer_user_id, shown_user_id)
FK  (viewer_user_id) REFERENCES USERS(id)
FK  (shown_user_id)  REFERENCES USERS(id)
```

- "최근 노출 유저 제외"와 일일 카드 제한 집계에 사용. (정책 10.1 / 10.3)

### 3.2 USER_REACTIONS (LIKE / PASS)

```sql
PK  (id)
PUQ (actor_user_id, target_user_id) WHERE status = 'ACTIVE'
IX  (actor_user_id, created_at)
IX  (actor_user_id, target_user_id, status)
IX  (target_user_id, reaction_type, status, created_at)
FK  (actor_user_id)  REFERENCES USERS(id)
FK  (target_user_id) REFERENCES USERS(id)
CK  reaction_type IN ('LIKE','PASS')
CK  status IN ('ACTIVE','CANCELLED','EXPIRED')
CK  actor_user_id <> target_user_id
```

- 활성 반응은 한 쌍에 1개만 허용한다. PASS가 30일 후 `EXPIRED` 되거나 매칭 전 LIKE가 `CANCELLED` 되면 새 반응이 가능하다.
- `UQ (actor_user_id, target_user_id)`를 영구 유니크로 두면 PASS 재노출/LIKE 취소 정책과 충돌하므로 부분 유니크를 사용한다.
- UNMATCHED 후 재추천 제외가 필요한 경우 기존 LIKE를 ACTIVE로 유지하거나 별도 매칭/노출 제외 조건을 사용한다.
- 받은 LIKE 목록/카운트 조회는 `(target_user_id, reaction_type, status, created_at)` 인덱스로 처리한다.

### 3.3 MATCHES

```sql
PK  (id)
UQ  (user1_id, user2_id)
CK  user1_id < user2_id
CK  user1_id <> user2_id
IX  (user1_id)
IX  (user2_id)
FK  (user1_id) REFERENCES USERS(id)
FK  (user2_id) REFERENCES USERS(id)
CK  status IN ('ACTIVE','UNMATCHED','EXPIRED')
```

- `user1_id < user2_id` 정규화 + 유니크로 양방향 중복 매칭을 차단. (정책 12.2)
- 동시 상호 LIKE 시 unique 위반을 멱등 처리(예외 아님)하여 기존 MATCH 반환. (정책 12.1 / 23)

### 3.4 USER_BLOCKS

```sql
PK  (id)
UQ  (blocker_user_id, blocked_user_id)
CK  blocker_user_id <> blocked_user_id
IX  (blocked_user_id)
FK  (blocker_user_id) REFERENCES USERS(id)
FK  (blocked_user_id) REFERENCES USERS(id)
```

- 차단은 MATCHES.status와 CHAT_ROOMS.status를 바꾸지 않고, 채팅 가능 여부 판단 시 USER_BLOCKS를 함께 검사. (정책 13.2)
- `(blocked_user_id)` 인덱스로 "나를 차단한 유저" 추천 제외 조회. (정책 10.1)

---

## 4. 채팅 도메인

### 4.1 CHAT_ROOMS

```sql
PK  (id)
UQ  (match_id)
FK  (match_id) REFERENCES MATCHES(id)
CK  status IN ('OPEN','CLOSED')
```

- `UQ (match_id)`가 채팅방 생성 멱등성 기준. 매칭당 채팅방 1개. (정책 22.2)

### 4.2 CHAT_ROOM_MEMBERS

```sql
PK  (id)
UQ  (chat_room_id, user_id)
IX  (user_id)
FK  (chat_room_id) REFERENCES CHAT_ROOMS(id)
FK  (user_id)      REFERENCES USERS(id)
```

### 4.3 CHAT_MESSAGES

```sql
PK  (id)
UQ  (chat_room_id, client_message_id)
IX  (chat_room_id, created_at)
IX  (sender_user_id, created_at)
FK  (chat_room_id)   REFERENCES CHAT_ROOMS(id)
FK  (sender_user_id) REFERENCES USERS(id)
```

- `UQ (chat_room_id, client_message_id)`가 메시지 전송 멱등성 기준(클라이언트 재시도 방어). (정책 22.2)
- "나에게만 삭제"는 별도 `CHAT_MESSAGE_HIDES(message_id, user_id)` 또는 멤버별 숨김 상태로 관리. (정책 14.2)

---

## 5. 신고 / 제재 도메인

### 5.1 REPORTS

```sql
PK  (id)
IX  (reporter_user_id, target_type, target_id)
IX  (status, created_at)
IX  (target_type, target_id)
FK  (reporter_user_id) REFERENCES USERS(id)
CK  target_type IN ('USER','PROFILE','PHOTO','MESSAGE','CHAT_ROOM')
CK  status IN ('RECEIVED','UNDER_REVIEW','ACTION_TAKEN','REJECTED','CLOSED')
CK  reason IN ('INAPPROPRIATE_PHOTO','ABUSIVE_LANGUAGE','HARASSMENT','SPAM','SCAM','IMPERSONATION','SEXUAL_CONTENT','ILLEGAL_CONTENT','PRIVACY_VIOLATION','OTHER')
```

- 24시간 내 `(reporter, target_type, target_id, reason)` 중복 신고 제한은 애플리케이션 레벨에서 처리.
- `reason`은 enum/check로 고정하고, 자유 입력 상세 설명은 `reason_detail TEXT`로 별도 저장한다.
  - 강제하려면 생성컬럼 `report_day = date_trunc('day', created_at)` + `UQ (reporter_user_id, target_type, target_id, reason, report_day)`로 보강 가능(요건에 따라 선택).

### 5.2 SANCTIONS

```sql
PK  (id)
IX  (user_id, status)
IX  (di_hash)
IX  (expires_at) WHERE status = 'ACTIVE'
FK  (user_id) REFERENCES USERS(id)
CK  level IN ('WARNING','TEMP_SUSPENDED_3D','TEMP_SUSPENDED_7D','TEMP_SUSPENDED_30D','PERMANENT_BANNED')
CK  status IN ('ACTIVE','EXPIRED','REVOKED')
CK  (level IN ('TEMP_SUSPENDED_3D','TEMP_SUSPENDED_7D','TEMP_SUSPENDED_30D') AND expires_at IS NOT NULL)
    OR (level IN ('WARNING','PERMANENT_BANNED') AND expires_at IS NULL)
```

- `di_hash` 인덱스로 탈퇴·재가입 시 제재 승계/차단 조회. (정책 16.5)
- 만료 배치는 `expires_at` 부분 인덱스로 효율 처리.
- 삭제 금지 테이블(보존). (정책 20.1)

---

## 6. 결제 / 구독 도메인

### 6.1 SUBSCRIPTION_PLANS

```sql
PK  (id)
UQ  (plan_code)
UQ  (apple_product_id)
UQ  (google_product_id)
CK  plan_code IN ('FREE','PREMIUM_MONTHLY','PREMIUM_YEARLY')
CK  is_active IN (true, false)
```

- 가격은 정책 문서가 아니라 이 테이블/스토어 콘솔에서 관리. (정책 17.2)
- 스토어 product id 매핑은 웹훅 검증에 필수.

### 6.2 SUBSCRIPTIONS

```sql
PK  (id)
PUQ (user_id) WHERE status IN ('ACTIVE','GRACE_PERIOD')
PUQ (provider, provider_subscription_id) WHERE provider_subscription_id IS NOT NULL
PUQ (provider, original_transaction_id)  WHERE original_transaction_id IS NOT NULL
PUQ (provider, purchase_token)           WHERE purchase_token IS NOT NULL
IX  (user_id, status)
IX  (plan_id)
FK  (user_id) REFERENCES USERS(id)
FK  (plan_id) REFERENCES SUBSCRIPTION_PLANS(id)
CK  status IN ('ACTIVE','GRACE_PERIOD','EXPIRED','CANCELLED','REFUNDED')
CK  environment IN ('SANDBOX','PRODUCTION')
```

- 동시 유효 구독은 유저당 1개로 제한한다.
- Apple/Google 구독 갱신과 웹훅 추적을 위해 `original_transaction_id`, `purchase_token`, `provider_subscription_id`, `environment`, `current_period_start/end`, `auto_renew_status`를 둔다.

### 6.3 PAYMENTS

```sql
PK  (id)
UQ  (provider, provider_transaction_id)
IX  (user_id, status, created_at)
IX  (subscription_id, status)
FK  (user_id)         REFERENCES USERS(id)
FK  (subscription_id) REFERENCES SUBSCRIPTIONS(id)
CK  provider IN ('APPLE','GOOGLE','PG')
CK  status IN ('PENDING','PAID','FAILED','CANCELLED','REFUNDED')
```

- `UQ (provider, provider_transaction_id)`가 결제 승인 멱등성 기준. (정책 17.8 / 22.2)
- 결제 내역과 권한 지급은 분리. 삭제 금지 테이블. (정책 17.1 / 20.1)

### 6.4 PAYMENT_EVENTS (웹훅)

```sql
PK  (id)
UQ  (provider, provider_event_id)
IX  (subscription_id, created_at)
FK  (subscription_id) REFERENCES SUBSCRIPTIONS(id)
```

- `UQ (provider, provider_event_id)`가 웹훅 멱등성 기준. 중복 웹훅 방어. (정책 17.8 / 22.2)
- 삭제 금지 테이블. (정책 20.1)

---

### 6.5 USER_ENTITLEMENTS

```sql
PK  (id)
IX  (user_id, entitlement_type, expires_at) WHERE revoked_at IS NULL
FK  (user_id) REFERENCES USERS(id)
CK  entitlement_type IN ('PREMIUM')
CK  source_type IN ('SUBSCRIPTION','PROMOTION','ADMIN_GRANT')
```

- 결제/구독 원장과 현재 권한 조회를 분리하기 위한 projection 테이블이다.
- 환불, 구독 만료, 관리자 회수 시 `revoked_at`을 기록하고 현재 권한에서 제외한다.

---

## 7. 관리자 / 알림 도메인

### 7.1 ADMIN_USERS

```sql
PK  (id)
UQ  (email)
CK  role IN ('SUPER_ADMIN','OPERATOR','MODERATOR','SUPPORT')
CK  is_active IN (true, false)
```

### 7.2 ADMIN_AUDIT_LOGS (append-only, 장기 보존)

```sql
PK  (id)
IX  (admin_user_id, created_at)
IX  (target_type, target_id)
IX  (action, created_at)
FK  (admin_user_id) REFERENCES ADMIN_USERS(id)
```

- 감사 대상 액션은 정책 19.4 참고. 추가 열람(신고 메시지 등)도 기록. (정책 14.4)

### 7.3 NOTIFICATION_SETTINGS

```sql
PK  (id)
UQ  (user_id)
FK  (user_id) REFERENCES USERS(id)
```

- 야간(21:00~08:00) 광고성 발송은 `NIGHT_MARKETING` 동의 + 설정 확인 후 처리. (정책 18.3 / 24.5)

---

## 8. 멱등성 키 매핑 요약

| 작업 | 멱등성 보장 제약 |
|---|---|
| Refresh Token Rotation | `AUTH_TOKEN_SESSIONS UQ (refresh_token_hash)` |
| LIKE 생성 | `USER_REACTIONS PUQ (actor_user_id, target_user_id) WHERE status = ACTIVE` |
| MATCH 생성 | `MATCHES UQ (user1_id, user2_id)` |
| CHAT_ROOM 생성 | `CHAT_ROOMS UQ (match_id)` |
| 메시지 전송 | `CHAT_MESSAGES UQ (chat_room_id, client_message_id)` |
| 결제 승인 | `PAYMENTS UQ (provider, provider_transaction_id)` |
| 결제 웹훅 | `PAYMENT_EVENTS UQ (provider, provider_event_id)` |
| 사진 업로드 완료 | `PROFILE_PHOTOS` 내 `upload_id`/`file_key` 유니크(요건에 따라 추가) |

---

## 9. 부분 유니크 인덱스(PUQ) 모음

DB 단독으로 보장해야 하는 "조건부 유일성"을 한 곳에 모았다.

```sql
-- 가명처리/삭제 후 이메일 재사용 허용
UNIQUE (email)        WHERE email IS NOT NULL AND deleted_at IS NULL;     -- USERS

-- 본인인증 DI는 활성 계정 기준 1개
UNIQUE (di_hash)      WHERE is_active = true;                            -- USER_IDENTITY_VERIFICATIONS

-- 같은 기기 활성 세션 1개
UNIQUE (user_id, device_id) WHERE revoked_at IS NULL;                   -- AUTH_TOKEN_SESSIONS

-- 발급용 활성 PASETO 키 1개
UNIQUE (status)       WHERE status = 'ACTIVE';                          -- PASETO_KEYS

-- 프로필 1:1 (삭제분 제외)
UNIQUE (user_id)      WHERE deleted_at IS NULL;                         -- PROFILES

-- 대표 사진 1장 (승인본 한정)
UNIQUE (profile_id)   WHERE is_primary = true AND status = 'APPROVED';  -- PROFILE_PHOTOS

-- 동시 유효 구독 1개
UNIQUE (user_id)      WHERE status IN ('ACTIVE','GRACE_PERIOD');        -- SUBSCRIPTIONS
```

---

## 10. FK ON DELETE 전략 메모

- USERS는 물리 삭제하지 않는다(가명처리). 따라서 하위 FK는 대부분 `ON DELETE NO ACTION` + soft delete로 운영한다.
- 보존 금지 테이블(PAYMENTS / PAYMENT_EVENTS / SUBSCRIPTIONS / REPORTS / SANCTIONS / ADMIN_AUDIT_LOGS)은 어떤 경우에도 CASCADE 삭제 대상이 되면 안 된다. (정책 20.1)
- 마스터 데이터(INTERESTS, SUBSCRIPTION_PLANS)는 `is_active=false`로 비활성화하고 참조 무결성을 위해 물리 삭제하지 않는다.
