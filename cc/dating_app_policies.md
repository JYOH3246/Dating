# 소개팅 앱 개발 정책 문서

- 문서 버전: v1.3
- 작성일: 2026-06-19
- 최종 수정일: 2026-06-19
- 대상 서비스: 소개팅 / 매칭 앱 MVP
- 기준: 현재까지 확정한 인증, 프로필, 추천, 매칭, 채팅, 신고, 결제, 관리자, 데이터 보존 정책

---

## 0. 변경 이력

### v1.3 (2026-06-19)

검토 결과에 따라 문서 간 충돌과 DB 반영 누락을 보완했다.

| # | 항목 | 유형 | 관련 섹션 |
|---|---|---|---|
| 1 | 가입 플로우를 `PENDING_VERIFICATION → PENDING_AGREEMENTS → ACTIVE`로 분리 | 수정 | 5.4, 21.1 |
| 2 | 차단 시 `MATCHES`/`CHAT_ROOMS` 상태를 변경하지 않고 런타임 게이트로만 차단 | 수정 | 13, 21.5 |
| 3 | PASS 30일 재노출과 LIKE 취소를 위해 `USER_REACTIONS.status` 도입 | 수정 | 11, 21 |
| 4 | 노출 기록이 생기는 Discovery 조회는 `POST /discovery/card-batches` 사용 | 수정 | 10, API 문서 |
| 5 | 로그인용 OAuth identity와 제재/감사용 fingerprint 분리, 활성 identity 부분 유니크 적용 | 수정 | 5, 6 |
| 6 | 결제 권한 projection `USER_ENTITLEMENTS` 명시 | 수정 | 17, 23 |
| 7 | `reports.reason`은 enum으로 고정하고 `reason_detail`을 별도 상세 설명으로 추가 | 수정 | 15 |
| 8 | 사진 업로드 `file_key` 멱등성, 결제/제재 CHECK, 외부 구독 식별자 보강 | 수정 | 8, 16, 17 |

### v1.2 (2026-06-19)

- 본인인증을 가입 시 **필수**로 확정했다. 단계적 도입 대안을 폐기한다. (5.4)
- 상태 전이 상세 문서, DB 제약조건 상세 문서를 별도 산출물로 분리했다. (28번 참고)

### v1.1 (2026-06-19)

출시 차단급 리스크 및 정책 공백 13개 항목을 보완했다.

| # | 항목 | 유형 | 관련 섹션 |
|---|---|---|---|
| 1 | 본인확인 / 연령 확인 정책 신설 | 신규 | 5.4 |
| 2 | 약관 / 동의 이력 정책 신설 | 신규 | 24.5, 23 |
| 3 | "익명화" 용어 정정 및 보존 근거 명시 | 수정 | 6.3, 6.5, 20.3 |
| 4 | 제재 회피 / 밴 재가입 차단 정책 | 수정 | 6.3, 16.5, 21.1 |
| 5 | 야간 푸시 제한 시간 21:00으로 정정 | 수정 | 18.3 |
| 6 | 최대 기기 수 초과 시 LRU 폐기 정책 | 수정 | 3.5, 3.6 |
| 7 | 매칭 해제(UNMATCHED) 후 재추천 제외 | 수정 | 10.1, 12.3 |
| 8 | 동시 상호 LIKE 충돌 멱등 처리 | 수정 | 12.1, 23 |
| 9 | 환불 권한 회수 트리거 명확화 | 수정 | 17.1, 17.7 |
| 10 | 사진 검수 SLA 정책 신설 | 신규 | 8.6 |
| 11 | 구독 가격 설정 분리 | 수정 | 17.2 |
| 12 | 사진 EXIF / 위치정보 제거 정책 신설 | 신규 | 8.7 |
| 13 | 불법 콘텐츠 즉시처리 트랙 신설 | 신규 | 15.5, 16.6 |

> 법적 판단이 필요한 항목(연령확인, 개인정보 보존기간, 야간 광고 시간, 불법촬영물 사전조치 의무 등)은 출시 전 법무 검토를 거친다.

---

## 1. 정책 문서의 목적

이 문서는 소개팅 앱 개발 전에 반드시 확정해야 하는 핵심 서비스 정책을 정리한 문서다.

본 문서의 목적은 다음과 같다.

1. 도메인별 상태 전이와 허용/금지 조건을 명확히 한다.
2. DB 제약조건, 인덱스, 트랜잭션 범위 설계의 기준을 제공한다.
3. API 구현 시 일관된 비즈니스 규칙을 적용한다.
4. 결제, 신고, 차단, 탈퇴 등 운영 리스크가 큰 기능의 기준을 사전에 확정한다.
5. MVP 개발 범위와 추후 확장 범위를 분리한다.

---

## 2. 전체 정책 요약

| 영역 | 확정 정책 |
|---|---|
| 인증 | PASETO |
| 본인확인 | 휴대폰 본인인증(CI/DI) + 만 19세 이상 |
| 가입 상태 | PENDING_VERIFICATION → PENDING_AGREEMENTS → ACTIVE |
| Access Token | 15분 |
| Refresh Token | 30일 |
| Refresh Token 저장소 | PostgreSQL DB |
| Refresh Token Rotation | 사용 |
| 동시 로그인 | 기기별 허용 |
| 최대 기기 수 | 5개 (초과 시 LRU 세션 폐기) |
| OAuth 제공자 | Google / Naver / Kakao / Apple |
| OAuth 계정 병합 | 자동 병합 금지, 명시적 연결만 허용 |
| 계정 복구 가능 기간 | 탈퇴 후 14일 |
| 탈퇴 14일 후 | 개인정보 익명화 |
| 프로필 공개 | 필수 정보 + 승인 사진 1장 이상 |
| 사진 | 최소 1장, 최대 6장, 승인 후 노출 |
| 추천 카드 제한 | 무료 30명 / 유료 100명 |
| LIKE 제한 | 무료 20개 / 유료 50개 |
| PASS 재노출 | 30일 제한 후 낮은 우선순위로 재노출 가능 |
| LIKE 취소 | 매칭 전만 허용 |
| 매칭 해제 후 재매칭 | 기본 불가 |
| 차단 | 기존 매칭/채팅 유지, 쌍방 채팅 불가 |
| 채팅 MVP | 텍스트만 |
| 메시지 삭제 | 나에게만 삭제 |
| 신고 대상 | 유저 / 프로필 / 사진 / 메시지 / 채팅방 |
| 제재 방식 | 초기 MVP는 관리자 수동 제재 |
| 결제 | MVP에서는 구독형 우선 |
| 약관 동의 | 버전 단위 append-only 이력 저장 |
| 사진 검수 SLA | 업로드 후 24시간 이내 |
| 불법 콘텐츠 신고 | 즉시 비공개 처리 트랙 별도 운영 |
| 관리자 기능 | 유저 / 프로필 / 사진 / 신고 / 제재 / 결제 조회 |

---

## 3. 인증 / 세션 정책

### 3.1 인증 방식

- 인증 방식은 PASETO를 사용한다.
- Access Token과 Refresh Token을 분리한다.
- Refresh Token은 원문을 저장하지 않고 해시 형태로 저장한다.
- Refresh Token 저장소는 PostgreSQL DB를 사용한다.

### 3.2 토큰 만료 시간

| 토큰 | 만료 시간 |
|---|---:|
| Access Token | 15분 |
| Refresh Token | 30일 |

### 3.3 Refresh Token Rotation

- Refresh Token Rotation을 사용한다.
- Refresh Token을 사용해 재발급할 때마다 기존 Refresh Token은 폐기하고 새 Refresh Token을 발급한다.
- 이전 Refresh Token이 다시 사용되면 재사용 공격 가능성으로 간주한다.

### 3.4 Refresh Token 재사용 감지 정책

- Refresh Token 재사용이 감지되면 해당 유저의 모든 세션을 폐기한다.
- 재사용 감지 이벤트는 보안 로그로 기록한다.
- 필요 시 관리자 또는 보안 알림 대상 이벤트로 확장할 수 있다.

### 3.5 동시 로그인 정책

- 동시 로그인은 기기별로 허용한다.
- 최대 동시 로그인 기기 수는 5개로 제한한다.
- 최대 기기 수를 초과하는 신규 로그인이 발생하면, 가장 오래 사용되지 않은(LRU) 세션을 자동 폐기하고 신규 로그인을 허용한다.
- 신규 로그인을 거부하지 않는다. (UX 우선)
- LRU 판단은 `last_used_at` 기준으로 한다.
- 자동 폐기된 기기는 재로그인 안내 대상이며, 폐기 이벤트는 보안 로그로 기록한다.
- 유저는 기기별 로그아웃을 할 수 있다.
- 계정 보안 변경, 제재, 탈퇴 시 전체 세션을 폐기한다.

### 3.6 권장 세션 테이블 정책

`AUTH_TOKEN_SESSIONS`는 다음 정보를 관리해야 한다.

- user_id
- refresh_token_hash
- device_id
- device_name
- user_agent
- ip_address
- expires_at
- revoked_at
- rotated_at
- last_used_at
- created_at
- updated_at

권장 제약조건:

```sql
UNIQUE (refresh_token_hash)
INDEX (user_id)
INDEX (device_id)
INDEX (expires_at)
```

---

## 4. PASETO 키 관리 정책

### 4.1 키 관리

- PASETO private key는 Secret Manager 또는 환경변수로 관리한다.
- private key를 소스코드나 로그에 남기지 않는다.
- key_id를 토큰 또는 세션에 저장해 키 교체를 지원한다.

### 4.2 Key Rotation

| 항목 | 정책 |
|---|---|
| 기본 rotation 주기 | 90일 |
| 긴급 rotation | 키 유출 의심 시 즉시 |
| 이전 public key 보존 | 최소 24시간 |

### 4.3 키 상태

```text
ACTIVE
VERIFY_ONLY
RETIRED
COMPROMISED
```

| 상태 | 의미 |
|---|---|
| ACTIVE | 새 토큰 발급 및 검증 가능 |
| VERIFY_ONLY | 기존 토큰 검증만 가능 |
| RETIRED | 발급/검증 모두 불가 |
| COMPROMISED | 유출 의심 또는 유출 확정, 즉시 폐기 |

---

## 5. OAuth / 계정 연결 정책

### 5.1 OAuth 제공자

MVP에서 지원할 OAuth 제공자는 다음과 같다.

```text
Google
Naver
Kakao
Apple
```

### 5.2 OAuth 계정 병합 정책

- 동일 이메일을 가진 OAuth 계정이라도 자동 병합하지 않는다.
- 계정 병합은 로그인 후 명시적 계정 연결을 통해서만 허용한다.
- 이메일은 보조 식별자로만 사용한다.
- 실제 OAuth 계정 식별은 `provider + provider_user_id`를 기준으로 한다.

### 5.3 계정 연결 / 해제 정책

- 최소 1개의 로그인 수단은 반드시 유지해야 한다.
- 이미 다른 계정에 연결된 OAuth identity는 연결할 수 없다.
- 계정 연결과 해제 시 재인증을 요구한다.
- Apple private relay email은 고유 식별자로 신뢰하지 않는다.

권장 제약조건:

```sql
UNIQUE (provider, provider_user_id)
INDEX (user_id)
```

### 5.4 본인확인 / 연령 확인 정책

- 본인확인은 가입 시 **필수**이며 생략할 수 없다. 본인인증을 완료하지 않은 계정은 ACTIVE로 전환하지 않는다.
- OAuth는 로그인 수단이며, 본인확인은 별도 단계로 처리한다.
- 가입 플로우는 다음 순서를 따른다.

```text
1. OAuth 로그인 → `PENDING_VERIFICATION`
2. 휴대폰 본인인증(PASS/통신사) → CI / DI 발급   // 필수, 미완료 시 가입 중단
3. 인증값 기준으로 만 나이 계산
4. 만 19세 미만이면 가입 차단
5. 본인인증 성공 → `PENDING_AGREEMENTS`
6. 필수 약관 동의 완료 → `ACTIVE`
```

- 본인인증으로 받은 생년월일/성별을 프로필 초기값으로 설정하며, 이 값이 7.4 수정 제한의 기준이 된다. (사용자 자가입력값이 아니라 인증값이 기준)
- DI는 내부 중복가입 및 재가입 차단 키로 사용한다. (CI는 타 서비스 연계용이므로 내부 식별에는 DI를 사용)
- CI와 DI는 원문을 저장하지 않고 해시 형태로 저장한다.
- 본인인증 미완료 계정의 상태는 `PENDING_VERIFICATION`으로 둔다. 본인인증이 완료되면 `PENDING_AGREEMENTS`로 전환하고, 필수 약관 최신 버전 동의가 완료된 시점에만 `ACTIVE`로 전환한다. 각 단계가 일정 시간(예: 24시간) 내 완료되지 않으면 정리 대상으로 둔다.

권장 테이블 정책:

```text
USER_IDENTITY_VERIFICATIONS
- user_id
- ci_hash
- di_hash
- verified_birth_date
- verified_gender
- carrier
- verified_at
```

권장 제약조건:

```sql
UNIQUE (di_hash) -- 활성 계정 기준, 중복가입/밴 회피 차단
INDEX (user_id)
```

> 본인인증은 미성년자 매칭 차단과 부정가입 방지의 근간이므로 MVP에서 예외 없이 적용한다. 본인인증 제공사(PASS 등) 연동 비용과 가입 이탈은 감수한다.

---

## 6. 회원 탈퇴 / 복구 / 익명화 정책

### 6.1 탈퇴 정책

- 유저가 탈퇴하면 즉시 soft delete 처리한다.
- 탈퇴 즉시 모든 토큰 세션을 폐기한다.
- 탈퇴한 유저는 추천, 매칭 생성, 채팅 전송, LIKE/PASS 등 주요 기능에서 제외된다.

### 6.2 복구 가능 기간

- 계정 복구 가능 기간은 탈퇴 후 14일이다.
- 탈퇴 후 14일 이내 로그인 시 복구 안내를 제공한다.
- 복구 시 계정 상태를 ACTIVE로 되돌릴 수 있다.

### 6.3 14일 경과 후 처리

- 탈퇴 후 14일이 지나면 개인정보를 파기하고, 보존 의무가 있는 데이터는 가명처리하여 분리 보관한다. (완전 익명화가 아님은 6.5 및 20.3 참고)
- 14일 경과 후 복구는 불가하다.
- 동일 OAuth identity는 처리 완료 이후 재가입 가능하다. 이를 위해 `AUTH_IDENTITIES`는 활성 identity 기준 부분 유니크로 관리하고, 제재/감사용 fingerprint는 별도로 보존한다.
- 단, 영구밴(PERMANENT_BANNED) 이력이 있는 DI는 재가입을 차단한다. (4번 / 16.5 참고)

### 6.4 탈퇴 유저 표시 정책

| 화면/데이터 | 처리 방식 |
|---|---|
| 닉네임 | `탈퇴한 사용자` |
| 프로필 사진 | 비노출 |
| 소개글 | 비노출 또는 null |
| 채팅 메시지 | 상대방에게 유지 가능 |
| 매칭/채팅방 | 보존하되 탈퇴 사용자로 표시 |
| 결제 기록 | 보존 |
| 신고/제재 기록 | 보존 |
| 관리자 감사 로그 | 보존 |

### 6.5 익명화 대상 컬럼

#### USERS

```text
email → null 또는 deleted_{random_token}@deleted.local  // user_id 등 식별자 미포함(재식별 방지)
phone_number → null
name → null
status → ANONYMIZED
deleted_at → 유지
anonymized_at → 기록
```

> `anonymized_{user_id}` 형태처럼 식별자를 포함하면 재식별이 가능하므로 사용하지 않는다. 결제/신고 기록이 user_id FK로 연결되는 한 이 처리는 법적으로 "익명화"가 아니라 "가명처리"에 해당한다.

#### AUTH_IDENTITIES

```text
provider_user_id → 로그인용 row는 deleted_at 처리, 제재/감사용 fingerprint는 irreversible hash로 별도 보존
provider_email → null
access_token 관련 값 → 삭제
refresh_token 관련 값 → 삭제
```

#### PROFILES

```text
nickname → "탈퇴한 사용자"
bio / introduction → null
birth_date → null
gender → UNKNOWN 또는 null
region_code → null
job → null
education → null
height → null
mbti → null
```

#### PROFILE_PHOTOS

```text
status → DELETED
file_url → null
thumbnail_url → null
deleted_at → 기록
```

---

## 7. 프로필 정책

### 7.1 프로필 공개 조건

프로필이 추천에 노출되기 위해서는 다음 조건을 모두 만족해야 한다.

1. 계정 상태가 ACTIVE이다.
2. 프로필 상태가 ACTIVE이다.
3. 필수 프로필 항목이 모두 입력되어 있다.
4. 승인된 대표 사진이 1장 이상 있다.
5. 정지, 탈퇴, 숨김 상태가 아니다.

### 7.2 필수 프로필 항목

MVP 필수 항목은 다음과 같다.

```text
닉네임
생년월일
성별
지역
자기소개
관심사 최소 3개
승인된 대표 사진 1장 이상
```

### 7.3 선택 프로필 항목

```text
키
직업
학력
흡연 여부
음주 여부
종교
MBTI
```

### 7.4 생년월일 / 성별 수정 정책

- 생년월일은 최초 설정 후 사용자가 직접 수정할 수 없다.
- 성별은 최초 설정 후 사용자가 직접 수정할 수 없다.
- 수정이 필요한 경우 고객센터 또는 관리자 승인을 통해 변경한다.
- 변경 이력은 저장한다.

### 7.5 프로필 숨김 정책

- 유저는 자신의 프로필을 숨김 처리할 수 있다.
- 숨김 상태에서는 신규 추천 노출에서 제외된다.
- 숨김 상태에서도 기존 매칭과 채팅은 유지된다.
- 숨김 상태에서는 좋아요 보내기를 제한한다.
- 숨김 상태에서도 받은 좋아요 조회는 허용한다.

---

## 8. 사진 / 검수 정책

### 8.1 사진 개수 정책

| 항목 | 정책 |
|---|---:|
| 최소 사진 수 | 1장 |
| 최대 사진 수 | 6장 |
| 대표 사진 | 필수 |

### 8.2 사진 노출 정책

- 사진은 승인 후 노출한다.
- 업로드된 사진은 기본적으로 PENDING 상태가 된다.
- APPROVED 상태의 사진만 추천 및 프로필 화면에 노출한다.
- 대표 사진은 APPROVED 상태의 사진 중에서만 설정할 수 있다.

### 8.3 사진 상태

```text
PENDING
APPROVED
REJECTED
DELETED
```

### 8.4 사진 반려 사유

```text
FACE_NOT_CLEAR
SUSPECTED_OTHER_PERSON
EXCESSIVE_EXPOSURE
VIOLENCE_OR_HATE
ADVERTISEMENT
PERSONAL_INFORMATION_INCLUDED
POLICY_VIOLATION
OTHER
```

### 8.5 파일 물리 삭제 정책

- 프로필 사진은 soft delete 후 30일 뒤 물리 삭제한다.
- 탈퇴 유저의 사진은 탈퇴 14일 경과 후 익명화 처리하고, 이후 30일 내 물리 삭제한다.
- 신고와 관련된 사진은 신고 처리 완료 전까지 물리 삭제를 보류한다.

### 8.6 사진 검수 SLA 정책

- 사진 검수 목표 시간은 업로드 후 24시간 이내로 한다. (가능하면 더 짧게)
- 대표 사진이 미검수로 0장이면 추천 노출이 불가하므로, 가입 직후 빈 경험이 발생할 수 있다.
- 이를 완화하기 위해 다음 중 하나를 적용한다.

```text
(a) 1차 자동검수(얼굴/노출 detection) 통과분만 임시 노출 + 사후 모니터링
(b) 검수 대기 중 본인은 "검수중" 상태로 프로필 확인 가능, 추천 노출만 보류
```

- MVP가 수동 검수만 운영하는 경우, 검수 큐 우선순위와 야간/주말 적체 대비 방안을 둔다.

### 8.7 사진 메타데이터(EXIF) 처리 정책

- 사진 업로드 시 서버에서 EXIF 등 메타데이터를 제거한다. (클라이언트 제거를 신뢰하지 않음)
- 원본 좌표(GPS)는 로그에도 남기지 않는다.
- 썸네일 생성 시에도 메타데이터를 포함하지 않는다.
- 위치 정보는 지역코드 중심으로 운영한다는 24.4 정책과의 일관성을 위해 정밀 좌표가 사진을 통해 노출되지 않도록 한다.

---

## 9. 관심사 / 선호 조건 정책

### 9.1 관심사 정책

- 관심사는 운영자가 관리하는 마스터 데이터로 둔다.
- 유저는 최소 3개의 관심사를 선택해야 한다.
- 관심사는 추천 점수 또는 프로필 완성도에 활용할 수 있다.

### 9.2 선호 조건 정책

- 선호 조건은 추천 후보 조회에 사용한다.
- MVP에서는 지역 조건과 나이 조건을 핵심 필터로 사용한다.
- 추후 거리 기반 추천, 관심사 유사도, 접속 시간대 등을 확장할 수 있다.

권장 제약조건:

```sql
UNIQUE (profile_id, interest_id)
UNIQUE (match_preference_id, region_code)
UNIQUE (user_id) -- match_preferences
```

---

## 10. Discovery / 추천 정책

### 10.1 추천 제외 대상

추천 후보에서 다음 유저는 제외한다.

```text
본인
차단한 유저
나를 차단한 유저
탈퇴 유저
정지 유저
프로필 숨김 유저
이미 매칭된 유저
매칭 해제(UNMATCHED) 이력이 있는 유저
이미 LIKE/PASS 반응한 유저
최근 노출된 유저
신고 처리 중인 위험 유저
```

### 10.2 추천 정렬 기준

MVP 추천 정렬 기준은 다음과 같다.

```text
지역 조건
+ 나이 조건
+ 최근 활동순
+ 일부 랜덤 섞기
```

추후 확장 가능한 요소:

```text
관심사 유사도
응답률
프로필 완성도
Premium 부스트
신고/차단 리스크 점수
```

### 10.3 추천 카드 제한

| 사용자 유형 | 하루 추천 카드 조회 수 |
|---|---:|
| 무료 사용자 | 30명 |
| 유료 사용자 | 100명 |

### 10.4 Premium 우선 노출 정책

MVP 추천 점수 예시:

```text
지역 일치: +30
나이 조건 일치: +20
최근 24시간 접속: +20
프로필 완성도 높음: +10
Premium 사용자: +15
랜덤 보정: 0~10
```

- Premium 사용자는 추천 점수에 +15 가중치를 받는다.
- Premium 사용자를 무조건 최상단에 고정하지 않는다.
- 사용자 선호 조건과 추천 품질을 완전히 무시하지 않는다.


### 10.6 노출 기록 생성 API 정책

추천 카드를 실제로 발급하고 노출 이력을 남기는 작업은 quota 차감이라는 부수효과가 있으므로 조회성 `GET`이 아니라 `POST /discovery/card-batches`로 처리한다. 이미 발급된 카드 묶음을 다시 조회하는 경우에만 `GET`을 사용한다.

---

## 11. LIKE / PASS 정책

### 11.1 반응 타입

MVP 반응 타입:

```text
LIKE
PASS
```

반응 상태:

```text
ACTIVE
CANCELLED
EXPIRED
```

활성 반응은 동일한 `(actor_user_id, target_user_id)` 쌍에 1개만 허용한다. 단, PASS가 30일 후 `EXPIRED` 되거나 매칭 전 LIKE가 `CANCELLED` 되면 새 반응이 가능하다.

추후 확장:

```text
SUPER_LIKE
```

### 11.2 LIKE 제한

| 사용자 유형 | 하루 LIKE 수 |
|---|---:|
| 무료 사용자 | 20개 |
| 유료 사용자 | 50개 |

### 11.3 LIKE 취소 정책

- LIKE 취소는 매칭 전만 허용한다.
- 매칭 후에는 LIKE 취소가 불가하다.
- 매칭 후 관계 종료는 매칭 해제로 처리한다.

### 11.4 LIKE 만료 정책

- MVP에서는 LIKE 만료를 두지 않는다.
- 추후 운영 데이터 확인 후 30일 또는 90일 만료 정책을 검토할 수 있다.

### 11.5 PASS 정책

- PASS한 유저는 30일 동안 재노출하지 않는다.
- 30일이 지나면 기존 PASS를 `EXPIRED` 처리하고 재노출 가능하게 한다.
- 30일 이후 재노출 시 추천 우선순위는 낮게 설정한다.
- PASS 취소 기능은 MVP에서 제외한다.

권장 제약조건:

```sql
UNIQUE (actor_user_id, target_user_id) WHERE status = ACTIVE
INDEX (actor_user_id, target_user_id, status)
INDEX (actor_user_id, created_at)
INDEX (target_user_id, reaction_type, status, created_at)
```

---

## 12. 매칭 정책

### 12.1 매칭 생성 조건

- 매칭은 양방향 LIKE가 성립했을 때 생성한다.
- 상호 LIKE 발생 시 즉시 매칭을 생성한다.
- 매칭 생성과 채팅방 생성은 하나의 트랜잭션으로 처리한다.
- 두 유저가 거의 동시에 서로 LIKE를 누르면 동일한 `(user1_id, user2_id)` row를 동시에 INSERT하려다 unique 위반이 발생할 수 있다. 이 충돌은 예외가 아니라 정상 경로로 간주하고, 기존 MATCH를 SELECT해 양쪽 모두 동일한 매칭 결과를 반환한다. (멱등 처리, 23번 참고)

### 12.2 매칭 중복 방지

- 두 유저 사이에는 하나의 매칭만 존재해야 한다.
- `user1_id < user2_id` 규칙을 사용해 중복 매칭을 방지한다.

권장 제약조건:

```sql
UNIQUE (user1_id, user2_id)
CHECK (user1_id < user2_id)
CHECK (user1_id <> user2_id)
```

### 12.3 매칭 해제 정책

- 매칭 해제 후 재매칭은 기본적으로 불가하다.
- 매칭 해제 시 상대방에게 별도 푸시 알림을 보내지 않는다.
- 채팅방은 남기되 메시지 전송은 불가하다.
- 채팅방 상태는 CLOSED로 변경한다.
- UNMATCHED 시 USER_REACTIONS row는 삭제하지 않고 유지하여, 10.1 추천 제외 규칙으로 양쪽 모두 재노출되지 않도록 한다. (한쪽만 해제한 경우에도 양쪽 모두 제외)

### 12.4 매칭 만료 정책

- MVP에서는 매칭 만료를 도입하지 않는다.
- 추후 운영 데이터에 따라 첫 메시지 제한 시간 또는 장기 미대화 만료 정책을 검토한다.

### 12.5 매칭 상태

```text
ACTIVE
UNMATCHED
EXPIRED
```

---

## 13. 차단 정책

### 13.1 기본 정책

- 차단 시 기존 매칭과 채팅은 남겨둔다.
- 차단 후에는 쌍방 채팅이 불가하다.
- 차단은 `MATCHES.status`와 `CHAT_ROOMS.status`를 변경하지 않고 `USER_BLOCKS` 기반 런타임 게이트로 처리한다.
- 차단한 유저와 차단당한 유저는 서로 추천되지 않는다.
- 상대에게 차단 여부를 직접 노출하지 않는다.

### 13.2 차단 해제 정책

- 차단 해제 시 기존 매칭이 ACTIVE라면 채팅을 재개할 수 있다.
- 유저는 차단 목록에서 차단 대상을 관리할 수 있다.
- MATCHES.status는 차단 여부로 변경하지 않는다.
- 채팅 가능 여부 판단 시 USER_BLOCKS를 함께 검사한다.

### 13.3 채팅 가능 조건

```text
매칭 ACTIVE
AND 채팅방 OPEN
AND 양쪽 모두 차단 관계 없음
AND 양쪽 모두 ACTIVE 상태
```

권장 제약조건:

```sql
UNIQUE (blocker_user_id, blocked_user_id)
CHECK (blocker_user_id <> blocked_user_id)
```

---

## 14. 채팅 정책

### 14.1 MVP 채팅 범위

- MVP에서는 텍스트 메시지만 지원한다.
- 이미지 전송은 추후 도입한다.
- 일반 파일 전송은 허용하지 않는다.

### 14.2 메시지 삭제 정책

- 메시지 삭제는 `나에게만 삭제`를 허용한다.
- 전체 삭제는 MVP에서 제외한다.
- 사용자별 메시지 숨김 상태를 별도로 관리할 수 있다.

### 14.3 읽음 처리

- 읽음 처리는 `last_read_at` 또는 `last_read_message_id`로 관리한다.
- 입력 중 표시는 MVP에서 제외한다.

### 14.4 신고 메시지 열람 정책

- 메시지가 신고되면 운영자는 신고 대상 메시지를 열람할 수 있다.
- 기본 열람 범위는 신고 메시지 기준 이전 10개, 이후 10개까지다.
- 추가 열람이 필요한 경우 운영자는 사유를 입력해야 한다.
- 추가 열람 기록은 ADMIN_AUDIT_LOGS에 저장한다.

권장 제약조건:

```sql
UNIQUE (chat_room_id, user_id) -- chat_room_members
INDEX (chat_room_id, created_at) -- chat_messages
INDEX (sender_user_id, created_at)
```

---

## 15. 신고 정책

### 15.1 신고 대상

```text
USER
PROFILE
PHOTO
MESSAGE
CHAT_ROOM
```

### 15.2 신고 사유

신고 사유는 enum으로 고정한다.

```text
INAPPROPRIATE_PHOTO
ABUSIVE_LANGUAGE
HARASSMENT
SPAM
SCAM
IMPERSONATION
SEXUAL_CONTENT
ILLEGAL_CONTENT
PRIVACY_VIOLATION
OTHER
```

사용자가 입력한 상세 설명은 `reason_detail`에 별도 저장한다. `reason_detail`은 신고 사유 enum을 대체하지 않으며, 운영자 검토 맥락을 보강하는 선택 입력값이다. `OTHER`를 선택한 경우에는 `reason_detail` 입력을 권장하거나 필수로 둘 수 있다.

### 15.3 신고 상태

```text
RECEIVED
UNDER_REVIEW
ACTION_TAKEN
REJECTED
CLOSED
```

### 15.4 신고 중복 제한

- 동일 신고자 + 동일 대상 + 동일 사유는 24시간 내 중복 신고할 수 없다.
- 동일 대상에 대한 재신고는 가능하다.
- 반복적인 허위 신고는 제재 대상이 될 수 있다.

권장 중복 제한 기준:

```text
reporter_user_id
target_type
target_id
reason
24시간 window
```

### 15.5 불법 콘텐츠 즉시처리 트랙

- `ILLEGAL_CONTENT`, `SEXUAL_CONTENT` 신고는 일반 신고와 분리된 즉시처리 트랙으로 운영한다.
- 해당 신고 접수 시 콘텐츠를 즉시 비공개 처리한다. (타 사유는 검토 후 처리)
- 관련 자료는 신고 처리 완료 전까지 물리 삭제를 보류한다. (8.5와 연계)
- 전기통신사업법상 불법촬영물 등 유통방지를 위한 사전 조치(기술적 필터링) 의무 사업자에 해당하는지 규모 기준으로 검토하고, 도달 시점 기준으로 도입 계획을 둔다.

> 사전 필터링 의무 대상 여부 및 시점은 법무 검토를 거친다.

---

## 16. 제재 정책

### 16.1 제재 방식

- MVP에서는 관리자 수동 제재를 기본으로 한다.
- 추후 신고 누적 기반 자동 숨김 또는 자동 임시 제한을 검토할 수 있다.

### 16.2 제재 단계

```text
WARNING
TEMP_SUSPENDED_3D
TEMP_SUSPENDED_7D
TEMP_SUSPENDED_30D
PERMANENT_BANNED
```

### 16.3 제재 기준

| 상황 | 권장 제재 |
|---|---|
| 1차 경미 위반 | WARNING |
| 반복 위반 | TEMP_SUSPENDED_3D 또는 TEMP_SUSPENDED_7D |
| 명백한 괴롭힘 | TEMP_SUSPENDED_7D 또는 TEMP_SUSPENDED_30D |
| 심각한 위반 | TEMP_SUSPENDED_30D |
| 사기, 불법 행위, 반복 성적 괴롭힘, 사칭 | PERMANENT_BANNED |

### 16.4 제재 중 이용 가능 범위

| 제재 | 이용 가능 범위 |
|---|---|
| WARNING | 서비스 이용 가능, 경고 알림 |
| TEMP_SUSPENDED | 로그인 가능, 주요 쓰기 기능 제한 |
| PERMANENT_BANNED | 로그인 불가 또는 제한된 안내 화면만 접근 가능 |

### 16.5 제재 회피 방지 정책

- 제재 이력은 user_id뿐 아니라 DI 해시에도 귀속시켜, 탈퇴·재가입으로 회피할 수 없게 한다.
- 정지(SUSPENDED) 상태에서 탈퇴하더라도 활성 제재의 잔여기간은 SANCTIONS에 보존한다. (21.1의 `SUSPENDED → DELETED` 전이 참고)
- 재가입 시 DI 해시를 대조하여 다음과 같이 처리한다.

```text
영구밴(PERMANENT_BANNED) 이력 → 재가입 차단
활성 임시제재 잔여        → 재가입 시 잔여 제재 승계(리셋 금지)
그 외                     → 재가입 허용
```

### 16.6 불법 콘텐츠 제재 연계

- 15.5 즉시처리 트랙에서 위반이 확정되면 16.3 기준에 따라 제재한다.
- 사기, 불법 행위, 반복 성적 괴롭힘, 사칭은 PERMANENT_BANNED 대상이며, 16.5에 따라 DI 차단한다.

---

## 17. 결제 / 구독 정책

### 17.1 결제 모델

- MVP에서는 구독형 결제를 우선 도입한다.
- 소모성 아이템은 추후 도입한다.
- 결제 내역과 권한 지급은 분리한다.
- 디지털 구독은 애플/구글 인앱결제(IAP)가 강제되므로, 가격은 스토어 티어에 맞추고 수수료(15~30%)를 반영해 설계한다.

### 17.2 구독 상품

MVP 구독 단계:

```text
Free
Premium
```

- 가격 숫자는 정책 문서에 하드코딩하지 않고, 설정/스토어 콘솔에서 관리한다.
- 문서에는 단계와 기능 차이(17.3)만 남긴다.

권장 테이블 정책:

```text
SUBSCRIPTION_PLANS
- plan_code            (FREE / PREMIUM_MONTHLY / PREMIUM_YEARLY)
- apple_product_id
- google_product_id
- price / currency
- billing_period
- is_active
```

- `apple_product_id` / `google_product_id`는 결제 웹훅 검증 시 필요하므로 매핑은 필수다.

### 17.3 무료 / 유료 기능 차이

| 기능 | Free | Premium |
|---|---:|---:|
| 하루 추천 카드 | 30명 | 100명 |
| 하루 LIKE | 20개 | 50개 |
| 받은 LIKE 개수 확인 | 가능 | 가능 |
| 받은 LIKE 목록 확인 | 제한 | 가능 |
| 받은 LIKE 상세 프로필 확인 | 제한 | 가능 |
| 프로필 우선 노출 | 불가 | 가능 |

### 17.4 받은 LIKE 조회 정책

무료 사용자:

- 받은 LIKE 개수만 확인 가능하다.
- 상대 목록과 상세 프로필은 제한한다.
- 블러 처리 또는 일부 정보만 노출할 수 있다.

유료 사용자:

- 받은 LIKE 전체 목록을 조회할 수 있다.
- 받은 LIKE 유저의 상세 프로필을 조회할 수 있다.
- 받은 LIKE 목록에서 바로 LIKE/PASS 할 수 있다.

### 17.5 구독 상태

```text
ACTIVE
GRACE_PERIOD
EXPIRED
CANCELLED
REFUNDED
```

### 17.6 Grace Period

- 구독 갱신 실패 시 Grace Period는 3일로 한다.
- Grace Period 동안은 Premium 권한을 임시 유지할 수 있다.
- Grace Period 종료 후에도 결제가 복구되지 않으면 EXPIRED로 전환한다.

상태 흐름:

```text
ACTIVE → GRACE_PERIOD → EXPIRED
```

### 17.7 환불 정책

- 환불 권한 회수의 트리거는 스토어 환불 웹훅 수신 시점으로 정의한다. (클라이언트 환불 성공은 신뢰하지 않음)
- 애플은 환불 통지가 지연될 수 있으므로 웹훅만 신뢰한다.
- 기간제(구독) 권한은 환불 웹훅 수신 시 SUBSCRIPTION을 REFUNDED로 전환하고 권한을 즉시 만료시킨다.
- 이미 소진한 소비성 혜택(열람한 받은 LIKE, 본 상세 프로필 등)은 회수가 불가능하므로 잔여 권한만 회수한다.
- 환불은 Grace Period와 별도로 처리한다.

```text
환불 웹훅 수신 → SUBSCRIPTION = REFUNDED → 잔여 권한 즉시 회수
소진성 혜택 → 회수 불가
```

### 17.8 결제 정합성 정책

- 클라이언트 결제 성공만 신뢰하지 않는다.
- 서버가 Apple/Google/PG 거래를 검증한다.
- 결제 웹훅은 멱등 처리한다.
- `provider_transaction_id`와 `provider_event_id`는 unique 처리한다.

권장 제약조건:

```sql
UNIQUE (provider, provider_transaction_id)
UNIQUE (provider, provider_event_id)
INDEX (user_id, status, created_at)
INDEX (subscription_id, status)
```

### 17.8 USER_ENTITLEMENTS 정책

- `USER_ENTITLEMENTS`는 현재 사용 가능한 권한을 빠르게 조회하기 위한 projection이다.
- 원장은 `PAYMENTS`, `PAYMENT_EVENTS`, `SUBSCRIPTIONS`에 남기고, 권한 부여/회수 결과를 `USER_ENTITLEMENTS`에 반영한다.
- 환불, 구독 만료, 관리자 회수 시 `revoked_at`을 기록한다.

---

## 18. 알림 정책

### 18.1 MVP 알림 대상

```text
매칭 성사
새 메시지
프로필 승인/반려
신고 처리 결과
결제 성공/실패
구독 만료 예정
```

### 18.2 알림 설정

```text
채팅 알림 on/off
매칭 알림 on/off
좋아요 알림 on/off
마케팅 알림 별도 동의
서비스 필수 알림은 항상 발송
```

### 18.3 야간 푸시 제한

- 야간 푸시 제한 시간은 21:00 ~ 08:00으로 한다. (정보통신망법 제50조 제3항 기준)
- 광고성 정보는 야간 시간대에 별도의 야간 수신동의(NIGHT_MARKETING) 없이 전송할 수 없다.
- 마케팅성 알림과 추천 유도 알림은 야간 제한 대상이다.
- "받은 LIKE" 알림은 광고성 해당 여부가 모호하므로 보수적으로 야간 제한군에 두고, 야간 수신동의를 받은 사용자에게만 야간 발송한다.
- 메시지, 결제, 보안, 제재, 신고 처리 알림은 정보성으로 즉시 발송할 수 있다.

즉시 발송 가능(정보성):

```text
새 메시지
매칭 성사
결제 성공/실패
보안 알림
제재 알림
신고 처리 알림
```

야간 제한(광고성, 야간 수신동의 필요):

```text
받은 LIKE
프로필 방문
마케팅
추천 유도 알림
```

---

## 19. 관리자 정책

### 19.1 관리자 MVP 기능

```text
유저 조회
프로필 조회
사진 승인/반려
신고 목록 조회
신고 처리
유저 정지/해제
결제 내역 조회
```

### 19.2 관리자 역할

```text
SUPER_ADMIN
OPERATOR
MODERATOR
SUPPORT
```

### 19.3 역할별 권한 매트릭스

| 기능 | SUPER_ADMIN | OPERATOR | MODERATOR | SUPPORT |
|---|---:|---:|---:|---:|
| 관리자 계정 관리 | 가능 | 불가 | 불가 | 불가 |
| 유저 상세 조회 | 가능 | 가능 | 제한 가능 | 제한 가능 |
| 프로필 조회 | 가능 | 가능 | 가능 | 가능 |
| 프로필 수정 | 가능 | 가능 | 불가 | 불가 |
| 사진 승인/반려 | 가능 | 가능 | 가능 | 불가 |
| 신고 처리 | 가능 | 가능 | 가능 | 조회만 |
| 유저 제재 | 가능 | 가능 | 제한 가능 | 불가 |
| 결제 내역 조회 | 가능 | 가능 | 불가 | 가능 |
| 환불 처리 | 가능 | 제한 가능 | 불가 | 불가 |
| 관리자 감사 로그 조회 | 가능 | 불가 | 불가 | 불가 |

### 19.4 관리자 감사 로그 대상

```text
유저 상세 조회
프로필 수정
사진 승인/반려
신고 처리
제재 등록/해제
결제 내역 조회
관리자 로그인
관리자 권한 변경
신고 메시지 추가 열람
```

감사 로그 권장 필드:

```text
admin_user_id
action
target_type
target_id
reason
ip_address
user_agent
created_at
```

---

## 20. Soft Delete / 보존 정책

### 20.1 테이블별 정책

| 테이블 | 정책 |
|---|---|
| USERS | soft delete 후 14일 뒤 파기, 보존 의무분은 가명처리 |
| AUTH_IDENTITIES | 탈퇴 14일 후 식별자 가명처리 또는 해시 보존 |
| AUTH_TOKEN_SESSIONS | 탈퇴/제재 시 폐기 |
| PROFILES | soft delete |
| PROFILE_PHOTOS | soft delete 후 파일 삭제 예약 |
| MATCHES | 보존 |
| CHAT_ROOMS | 보존, 매칭 해제 시 CLOSED |
| CHAT_MESSAGES | 보존 또는 사용자별 숨김 |
| PAYMENTS | 삭제 금지 |
| PAYMENT_EVENTS | 삭제 금지 |
| SUBSCRIPTIONS | 삭제 금지 |
| USER_ENTITLEMENTS | 삭제 금지, revoked_at으로 회수 |
| REPORTS | 삭제 금지 |
| SANCTIONS | 삭제 금지 |
| ADMIN_AUDIT_LOGS | 장기 보존 |

### 20.2 공통 soft delete 컬럼

주요 도메인 테이블은 다음 컬럼을 갖는 것을 권장한다.

```text
created_at
updated_at
deleted_at
```

감사성이 필요한 테이블은 다음 컬럼을 추가할 수 있다.

```text
created_by
updated_by
deleted_by
delete_reason
```

### 20.3 보존 데이터 법정 근거 (법무 확인 필요)

아래는 일반적 기준이며, 실제 적용 기간과 근거는 출시 전 법무 검토로 확정한다.

| 데이터 | 근거(예시) | 보존기간(예시) |
|---|---|---|
| 결제/계약/청약철회 기록 | 전자상거래법 | 5년 |
| 소비자 불만·분쟁 처리 | 전자상거래법 | 3년 |
| 표시·광고 기록 | 전자상거래법 | 6개월 |
| 접속 로그 | 통신비밀보호법 | 3개월 |
| 재가입 차단용 DI 해시 | 부정이용 방지 | 별도 명시 |

- 탈퇴 후 14일 보관은 "지체 없이 파기" 원칙의 예외이므로, 보관 근거(분쟁 대비/부정가입 방지)를 개인정보처리방침에 명시한다.
- 결제·신고가 user_id FK로 연결되는 한 완전 익명화는 불가능하며, 해당 처리는 가명처리로 분류한다. (6.5 참고)

---

## 21. 상태 전이 정책

### 21.1 USERS.status

```text
PENDING_VERIFICATION
PENDING_AGREEMENTS
ACTIVE
SUSPENDED
DELETED
ANONYMIZED
BANNED
```

권장 전이:

```text
PENDING_VERIFICATION → PENDING_AGREEMENTS  // 본인인증 완료(필수)
PENDING_AGREEMENTS → ACTIVE                // 필수 약관 동의 완료
PENDING_VERIFICATION → DELETED             // 인증 미완료 정리(예: 24시간 경과)
PENDING_AGREEMENTS → DELETED               // 약관 미완료 정리(예: 24시간 경과)
ACTIVE → SUSPENDED
ACTIVE → DELETED
SUSPENDED → ACTIVE
SUSPENDED → DELETED      // 제재 중 탈퇴, 활성 제재 잔여는 SANCTIONS에 보존(16.5)
SUSPENDED → BANNED
DELETED → ACTIVE         // 탈퇴 14일 이내 복구
DELETED → ANONYMIZED     // 탈퇴 14일 경과
ACTIVE → BANNED
BANNED → ACTIVE          // SUPER_ADMIN 수동 해제(오판 구제)
```

### 21.2 PROFILES.status

```text
DRAFT
PENDING_REVIEW
ACTIVE
REJECTED
HIDDEN
DELETED
```

권장 전이:

```text
DRAFT → PENDING_REVIEW
PENDING_REVIEW → ACTIVE
PENDING_REVIEW → REJECTED
REJECTED → PENDING_REVIEW
ACTIVE → HIDDEN
HIDDEN → ACTIVE
ACTIVE → DELETED
```

### 21.3 PROFILE_PHOTOS.status

```text
PENDING
APPROVED
REJECTED
DELETED
```

권장 전이:

```text
PENDING → APPROVED
PENDING → REJECTED
APPROVED → DELETED
REJECTED → DELETED
```

### 21.4 USER_REACTIONS.status

```text
ACTIVE
CANCELLED
EXPIRED
```

권장 전이:

```text
[*] → ACTIVE              // LIKE/PASS 생성
ACTIVE → CANCELLED        // 매칭 전 LIKE 취소
ACTIVE → EXPIRED          // PASS 30일 만료
```

### 21.5 MATCHES.status

```text
ACTIVE
UNMATCHED
EXPIRED
```

권장 전이:

```text
ACTIVE → UNMATCHED
ACTIVE → EXPIRED
```

### 21.6 CHAT_ROOMS.status

```text
OPEN
CLOSED
```

권장 전이:

```text
OPEN → CLOSED    // 매칭 해제/만료
CLOSED → OPEN    // 운영 복구 등 예외적 상황만 허용

차단은 CHAT_ROOMS.status를 변경하지 않고 USER_BLOCKS 기반 런타임 게이트로 처리한다.
```

### 21.7 REPORTS.status

```text
RECEIVED
UNDER_REVIEW
ACTION_TAKEN
REJECTED
CLOSED
```

권장 전이:

```text
RECEIVED → UNDER_REVIEW
UNDER_REVIEW → ACTION_TAKEN
UNDER_REVIEW → REJECTED
ACTION_TAKEN → CLOSED
REJECTED → CLOSED
```

### 21.8 SUBSCRIPTIONS.status

```text
ACTIVE
GRACE_PERIOD
EXPIRED
CANCELLED
REFUNDED
```

권장 전이:

```text
ACTIVE → GRACE_PERIOD
GRACE_PERIOD → ACTIVE
GRACE_PERIOD → EXPIRED
ACTIVE → CANCELLED
ACTIVE → REFUNDED
CANCELLED → EXPIRED
```

---

## 22. API 멱등성 정책

### 22.1 멱등성 적용 대상

```text
Refresh Token Rotation
LIKE 생성
MATCH 생성
채팅 메시지 전송
결제 승인 처리
결제 웹훅 처리
구독 갱신 이벤트 처리
사진 업로드 완료 처리
```

### 22.2 멱등성 기준

| 작업 | 멱등성 기준 |
|---|---|
| LIKE | actor_user_id + target_user_id |
| MATCH | user1_id + user2_id |
| CHAT_ROOM | match_id |
| MESSAGE | client_message_id |
| PAYMENT | provider_transaction_id |
| WEBHOOK | provider_event_id |
| PHOTO_UPLOAD_COMPLETE | upload_id 또는 file_key |

### 22.3 구현 원칙

- 애플리케이션 레벨 체크만 신뢰하지 않는다.
- DB unique constraint를 최종 방어선으로 둔다.
- 클라이언트 재시도 시 같은 요청은 같은 결과를 반환하도록 설계한다.

---

## 23. 트랜잭션 정책

다음 작업은 하나의 트랜잭션으로 처리해야 한다.

| 작업 | 트랜잭션 범위 |
|---|---|
| 회원가입 | USERS + AUTH_IDENTITIES + USER_IDENTITY_VERIFICATIONS + USER_AGREEMENTS(필수 동의) + 기본 설정 |
| 프로필 생성 | PROFILES + MATCH_PREFERENCES |
| LIKE 처리 | USER_REACTIONS + MATCHES 생성 여부 |
| 매칭 생성 | MATCHES + CHAT_ROOMS + CHAT_ROOM_MEMBERS |
| 결제 성공 처리 | PAYMENTS + SUBSCRIPTIONS + USER_ENTITLEMENTS |
| 매칭 해제 | MATCHES + CHAT_ROOMS 상태 변경 |
| 탈퇴 | USERS + PROFILES + 토큰 세션 폐기 |

LIKE 처리 권장 흐름:

```text
1. 내 반응 저장
2. 상대방이 나에게 LIKE 했는지 확인
3. 있으면 MATCHES 생성 (user1_id < user2_id 정규화)
4. MATCHES unique 위반 시 → 예외가 아니라 "이미 매칭됨"으로 처리하고 기존 MATCH를 SELECT해 동일 결과 반환
5. CHAT_ROOMS 생성 (UNIQUE(match_id)로 동일 멱등 처리)
6. CHAT_ROOM_MEMBERS 생성
7. 커밋
```

동시에 서로 LIKE를 누를 수 있으므로 `MATCHES(user1_id, user2_id)` unique 제약이 반드시 필요하며, 충돌은 정상 경로로 간주해 멱등 처리한다.

---

## 24. 보안 / 개인정보 정책

### 24.1 Refresh Token

- Refresh Token은 원문 저장을 금지한다.
- DB에는 해시 형태로만 저장한다.
- 로그에도 Refresh Token 원문을 남기지 않는다.

### 24.2 관리자 개인정보 마스킹

- 관리자 화면에서 이메일과 전화번호는 기본 마스킹 처리한다.
- 상세 조회 또는 전체 표시가 필요한 경우 감사 로그를 남긴다.

### 24.3 로그 정책

운영 로그에는 다음 정보를 남기지 않는다.

```text
Access Token
Refresh Token
OAuth Access Token
OAuth Refresh Token
주민등록번호 등 고위험 개인정보
불필요한 전화번호/이메일 원문
```

### 24.4 위치 정보

- 위치 정보는 서비스에 필요한 최소 단위로 저장한다.
- MVP에서는 정밀 좌표보다는 지역 코드 중심으로 운영하는 것을 기본으로 한다.

### 24.5 약관 / 동의 이력 정책

- 동의는 append-only 이력으로 버전 단위 저장한다.
- 약관 문서 자체도 `version + effective_date`로 관리하고, 개정 시 재동의 플로우를 둔다.
- 회원가입 트랜잭션(23번)에 필수 동의 저장을 포함한다.

약관 타입:

```text
TERMS_OF_SERVICE     (필수)
PRIVACY_POLICY       (필수)
LOCATION_BASED       (위치기반 사용 시 필수)
MARKETING            (선택)
NIGHT_MARKETING      (선택, 18.3 야간 발송과 연계)
```

권장 테이블 정책:

```text
USER_AGREEMENTS  (append-only)
- user_id
- agreement_type
- version
- is_agreed
- agreed_at
- ip_address
- user_agent
```

---

## 25. 신규 가입자 / 운영 리스크 제한 정책

### 25.1 신규 가입자 활동 제한

- 가입 후 24시간 동안 LIKE 사용량을 일부 제한하거나 rate limit을 적용한다.
- 과도한 LIKE/PASS 반복 요청은 서버 rate limit으로 제한한다.
- 동일 기기 또는 동일 IP에서 과도한 가입이 발생하면 제한할 수 있다.

권장 MVP:

```text
가입 후 24시간 동안 LIKE 한도의 50%만 허용
또는 서버 rate limit만 우선 적용
```

### 25.2 신고 누적 유저 처리

- 신고가 누적된 유저는 관리자 확인 전까지 추천에서 임시 제외할 수 있다.
- MVP에서는 자동 제재보다는 관리자 수동 검토를 우선한다.

---

## 26. MVP 제외 / 추후 도입 기능

MVP에서 제외하고 추후 도입할 기능은 다음과 같다.

```text
SUPER_LIKE
이미지 메시지
일반 파일 전송
메시지 전체 삭제
입력 중 표시
욕설/스팸 자동 필터링
소모성 아이템
복잡한 추천 랭킹 모델
매칭 만료
PASS 취소
자동 제재
```

---

## 27. 구현 시 우선 반영할 DB 제약조건 요약

```sql
-- OAuth identity
UNIQUE (provider, provider_user_id)

-- Identity verification
UNIQUE (di_hash)          -- 활성 계정 기준, 중복가입/밴 회피 차단
INDEX (user_id)

-- User agreements
INDEX (user_id, agreement_type, version)

-- Token session
UNIQUE (refresh_token_hash)
INDEX (user_id)
INDEX (device_id)

-- Profile
UNIQUE (user_id)
INDEX (status)
INDEX (region_code, status)

-- Profile interests
UNIQUE (profile_id, interest_id)

-- Profile photos
UNIQUE (profile_id, display_order)
-- 대표 사진 1장 제약은 부분 unique index 검토

-- Discovery exposures
INDEX (viewer_user_id, exposed_at)
INDEX (viewer_user_id, shown_user_id)

-- User reactions
UNIQUE (actor_user_id, target_user_id) WHERE status = ACTIVE
INDEX (actor_user_id, target_user_id, status)
INDEX (actor_user_id, created_at)
INDEX (target_user_id, reaction_type, status, created_at)

-- Matches
UNIQUE (user1_id, user2_id)
CHECK (user1_id < user2_id)
CHECK (user1_id <> user2_id)

-- Blocks
UNIQUE (blocker_user_id, blocked_user_id)
CHECK (blocker_user_id <> blocked_user_id)

-- Chat
UNIQUE (match_id) -- chat_rooms
UNIQUE (chat_room_id, user_id) -- chat_room_members
INDEX (chat_room_id, created_at) -- chat_messages

-- Payments
UNIQUE (provider, provider_transaction_id)
UNIQUE (provider, provider_event_id)
INDEX (user_id, status, created_at)

-- Reports
INDEX (reporter_user_id, target_type, target_id)
INDEX (status, created_at)

-- Subscription plans
UNIQUE (plan_code)
UNIQUE (apple_product_id)
UNIQUE (google_product_id)
```

---

## 28. 다음 산출물 후보

이 정책 문서를 기준으로 다음 문서를 추가로 만들 수 있다.

1. DB 제약조건 / 인덱스 상세 문서
2. 상태 전이표 문서
3. API 정책 문서
4. 관리자 권한 매트릭스 상세 문서
5. 결제 정합성 / 웹훅 처리 문서
6. Soft Delete / 익명화 배치 처리 문서
7. MVP 기능 범위 명세서

---

## 29. 최종 원칙

이 서비스의 핵심 원칙은 다음과 같다.

```text
1. 인증과 세션은 기기 단위로 관리한다.
2. Refresh Token은 반드시 rotation하고 원문 저장하지 않는다.
3. OAuth 계정은 자동 병합하지 않는다.
4. 탈퇴 후 14일은 복구 가능 기간으로 둔다.
5. 14일 경과 후 개인정보는 익명화한다.
6. 프로필은 필수 정보와 승인 사진이 있어야만 공개된다.
7. 추천 후보에서 차단/탈퇴/정지/숨김/매칭/반응 완료 유저는 제외한다.
8. 매칭은 양방향 LIKE로만 생성된다.
9. 매칭 생성과 채팅방 생성은 하나의 트랜잭션으로 처리한다.
10. 차단 시 기존 매칭과 채팅은 보존하되 쌍방 채팅은 불가하다.
11. 신고와 제재는 MVP에서 관리자 수동 처리 중심으로 운영한다.
12. 결제 내역과 권한 지급은 분리한다.
13. 환불 시 권한은 즉시 회수한다.
14. 관리자 작업은 감사 로그로 남긴다.
15. 중요한 쓰기 작업은 멱등하게 처리한다.
16. 가입 시 본인확인으로 만 19세 이상만 받고, 인증값을 프로필 기준으로 둔다.
17. 약관 동의는 버전 단위 이력으로 보존한다.
18. 영구밴 유저는 DI 해시 대조로 재가입을 차단한다.
19. 불법 콘텐츠 신고는 즉시 비공개 처리한다.
20. 개인정보 처리 용어는 파기/가명처리를 정확히 구분해 사용한다.
```
