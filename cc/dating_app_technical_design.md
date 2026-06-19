# 소개팅 앱 기술 설계 초안

## 1. 문서 목적

이 문서는 소개팅 앱을 자체 서비스로 개발하기 위한 초기 기술 설계 방향을 정리한 문서다.

주요 목표는 다음과 같다.

- 소개팅 앱 MVP에 필요한 핵심 기능 정리
- 서버, 클라이언트, 관리자 페이지의 기술 스택 결정
- 장기 운영을 고려한 서버 아키텍처 방향 설정
- JPA와 jOOQ 통합 전략 정리
- GraphQL 도입 가능 영역 검토
- Kotlin/Spring Boot 서버의 패키지 구조 초안 정리

---

## 2. 서비스 전제

이 서비스는 외부 고객사에 납품하는 SI성 프로젝트가 아니라, 자체 개발 및 장기 운영을 목표로 하는 소개팅 앱이다.

따라서 단순히 빠르게 CRUD를 구현하는 것뿐 아니라 다음 요소를 초기부터 고려해야 한다.

- 지속적인 기능 확장
- 사용자 신뢰와 안전
- 신고/차단/제재 정책
- 관리자 운영 기능
- 결제 및 구독 확장 가능성
- 추천 알고리즘 고도화
- 추후 AI 기능 도입 가능성
- 모바일 앱 출시 및 유지보수
- 장기적으로 안정적인 서버 구조

---

## 3. 소개팅 앱 MVP 필수 기능

소개팅 앱은 단순 프로필/채팅 앱이 아니라, 낯선 사람을 신뢰 가능한 방식으로 연결하고 안전하게 대화하게 하는 서비스다.

따라서 MVP 기준으로도 다음 기능은 필요하다.

### 3.1 회원가입 / 로그인

필수 기능:

- 이메일 또는 소셜 로그인
- 휴대폰 인증
- 나이 확인
- 약관 동의
- 개인정보 처리 동의
- 계정 탈퇴
- Access Token / Refresh Token 기반 인증
- Refresh Token Rotation
- 기기별 세션 관리

권장 OAuth 제공자:

- Kakao
- Naver
- Google
- Apple

iOS 앱 출시를 고려하면 Apple 로그인도 초기에 고려하는 것이 좋다.

---

### 3.2 프로필

필수 프로필 정보:

- 닉네임
- 생년월일 또는 나이
- 성별
- 관심 성별
- 지역
- 자기소개
- 관심사
- 연애 목적
- 사진

선택 프로필 정보:

- 키
- 직업
- 학력
- 성향
- 흡연/음주 여부
- 종교
- 가치관 문답

초기에는 입력 항목을 과도하게 늘리기보다, 필수 항목과 선택 항목을 분리하는 것이 좋다.

---

### 3.3 사진 업로드 / 검수

소개팅 앱에서 사진은 신뢰도와 직결된다.

필수 기능:

- 프로필 사진 업로드
- 대표 사진 설정
- 사진 순서 변경
- 사진 삭제
- 사진 심사 상태 관리
- 부적절 사진 신고
- 관리자 검수

권장 상태값:

```text
PENDING
APPROVED
REJECTED
```

사진이 승인되기 전에는 추천 풀에 노출하지 않는 정책이 적절하다.

---

### 3.4 추천 / 탐색

초기에는 AI 추천보다 룰 기반 추천이 적절하다.

기본 추천 조건:

- 성별 선호
- 나이 범위
- 지역
- 관심사
- 프로필 승인 여부
- 사진 승인 여부
- 차단 관계 제외
- 신고 위험 사용자 제외
- 이미 본 사용자 제외
- 최근 활동 여부

초기 추천 방식은 무한 스와이프보다 `하루 제한 추천 + 상호 호감 시 채팅 오픈` 방식이 더 안정적이다.

---

### 3.5 좋아요 / 패스 / 매칭

기본 흐름:

```text
A가 B에게 좋아요
B도 A에게 좋아요
→ 매칭 성사
→ 채팅방 생성
```

필수 기능:

- 좋아요
- 패스
- 상호 좋아요 시 매칭
- 매칭 목록 조회
- 매칭 취소
- 하루 좋아요 제한

확장 기능:

- 슈퍼 좋아요
- 받은 좋아요 보기
- 보낸 좋아요 보기
- 다시 보기
- 부스트

---

### 3.6 채팅

필수 기능:

- 매칭된 사용자끼리만 채팅 가능
- 1:1 채팅
- 메시지 저장
- 읽음 여부
- 새 메시지 푸시 알림
- 채팅방 나가기
- 상대방 신고
- 상대방 차단

초기 MVP에서는 REST 기반 메시지 조회/전송과 푸시 알림으로 시작할 수 있다.

이후 고도화 시 WebSocket을 도입한다.

---

### 3.7 신고 / 차단 / 제재

소개팅 앱에서는 안전 기능이 핵심 기능이다.

필수 기능:

- 사용자 신고
- 메시지 신고
- 사진 신고
- 사용자 차단
- 신고 처리 상태 관리
- 관리자 검토
- 경고
- 임시 정지
- 영구 정지
- 신고 증거 스냅샷 보존

권장 신고 상태:

```text
PENDING
REVIEWING
RESOLVED
REJECTED
```

권장 사용자 상태:

```text
ACTIVE
SUSPENDED
BANNED
WITHDRAWN
```

---

### 3.8 관리자 기능

관리자 기능은 MVP 초반부터 필요하다.

필수 기능:

- 회원 목록
- 회원 상세 조회
- 프로필 조회
- 사진 검수
- 신고 목록
- 신고 상세 조회
- 제재 처리
- 차단/신고 이력 확인
- 매칭/채팅 관련 운영 로그
- 결제 내역 조회
- 탈퇴/민원 대응

소개팅 앱은 운영 이슈가 반드시 발생하므로 관리자 기능을 후순위로 밀면 안 된다.

---

### 3.9 알림

필수 알림:

- 좋아요 받음
- 매칭 성사
- 새 메시지
- 프로필 승인
- 프로필 반려
- 신고 처리 결과

서버에는 푸시 발송 이력도 남기는 것이 좋다.

---

### 3.10 결제

MVP 초기에는 결제를 후순위로 둘 수 있다.

다만 스키마와 정책은 확장 가능하게 설계한다.

가능한 BM:

- 프리미엄 구독
- 좋아요 추가 구매
- 슈퍼 좋아요
- 프로필 부스트
- 받은 좋아요 보기
- 프로필 재노출

---

## 4. 전체 기술 스택 권장안

### 4.1 최종 추천 조합

```text
Mobile App:
- React Native
- Expo
- TypeScript
- Expo Router
- TanStack Query
- Zustand
- React Hook Form
- Zod

Backend:
- Kotlin
- Spring Boot
- Spring Security
- Spring Data JPA
- jOOQ
- PostgreSQL
- Redis
- Flyway

Admin:
- Next.js
- TypeScript
- Tailwind CSS
- shadcn/ui

Storage:
- AWS S3 또는 Cloudflare R2

Notification:
- FCM
- APNs
- Expo Notifications

Infra:
- Docker
- GitHub Actions
- AWS EC2 / ECS / Lightsail 중 선택
```

---

## 5. 클라이언트 기술 선택

### 5.1 React Native vs Flutter

둘 다 가능하지만, 이 프로젝트에서는 `React Native + Expo`를 우선 추천한다.

이유:

- 관리자 페이지를 Next.js로 만들 경우 TypeScript 생태계 통일 가능
- 소개팅 앱의 주요 UI는 React Native로 충분히 구현 가능
- Expo를 사용하면 앱 개발, 빌드, 배포 부담 감소
- 초기 MVP 개발 속도가 빠름
- 웹 관리자와 앱의 API 타입 및 상태관리 사고방식을 일부 공유 가능

---

### 5.2 Flutter가 더 적절한 경우

다음 조건이라면 Flutter도 좋은 선택이다.

- 고급 애니메이션이 서비스의 핵심 차별점인 경우
- Android/iOS UI 일관성을 극대화하고 싶은 경우
- 커스텀 UI가 매우 많은 경우
- React/TypeScript 경험을 활용할 필요가 없는 경우

---

### 5.3 클라이언트 최종 추천

```text
React Native + Expo
```

단, 단순 Expo Go 기준이 아니라 `Expo Development Build`를 기준으로 잡는 것이 좋다.

이유:

- 소셜 로그인
- 푸시 알림
- 결제
- 딥링크
- 네이티브 모듈
- 앱스토어 심사 대응

등이 들어갈 가능성이 높기 때문이다.

---

## 6. 서버 아키텍처 방향

### 6.1 결론

완전한 헥사고날/클린 아키텍처를 처음부터 엄격히 도입하기보다는, 다음 방향을 추천한다.

```text
모듈러 모놀리스 + 가벼운 클린 아키텍처
```

즉, 다음 구조를 지향한다.

```text
비즈니스 모듈 중심 패키징
+ application/domain/infrastructure 경계
+ 필요한 곳에 Port/Adapter 도입
+ JPA와 jOOQ 역할 분리
```

---

### 6.2 왜 완전한 헥사고날을 강하게 적용하지 않는가?

모든 기능에 대해 다음 요소를 강제하면 MVP 개발 속도가 느려진다.

```text
InputPort
OutputPort
UseCase
Command
DomainModel
PersistencePort
Adapter
Mapper
DTO
```

초기에는 파일 수와 추상화가 과도해질 수 있다.

---

### 6.3 왜 단순 레이어드만으로는 부족한가?

소개팅 앱은 도메인 규칙이 빠르게 복잡해진다.

예:

- 차단한 사용자는 추천되면 안 된다
- 이미 패스한 사용자는 일정 기간 다시 추천되면 안 된다
- 신고 누적 사용자는 노출 제한된다
- 상호 좋아요일 때만 매칭된다
- 매칭된 사용자끼리만 채팅할 수 있다
- 프로필 승인 전에는 추천 풀에 들어갈 수 없다
- 탈퇴 사용자의 개인정보와 운영 로그를 어떻게 처리할 것인가

이런 규칙이 단순 `UserService`, `MatchService`, `ChatService`에 섞이면 유지보수가 어려워진다.

---

### 6.4 권장 구조

각 비즈니스 모듈은 기본적으로 다음 계층을 가진다.

```text
presentation
application
domain
infrastructure
```

역할:

| 계층 | 역할 |
|---|---|
| presentation | REST Controller, GraphQL Resolver, WebSocket Handler |
| application | 유스케이스, 트랜잭션, 흐름 제어 |
| domain | 도메인 모델, 정책, 상태값, 비즈니스 규칙 |
| infrastructure | JPA, jOOQ, 외부 연동 구현 |

---

## 7. JPA + jOOQ 통합 전략

### 7.1 결론

이 프로젝트에서는 JPA와 jOOQ를 함께 도입하는 것이 적절하다.

권장 역할 분담:

```text
JPA  = 상태 변경 / 도메인 중심 쓰기 모델
jOOQ = 복잡한 조회 / 추천 / 관리자 검색 / 통계 / 리포트
```

---

### 7.2 JPA가 담당할 영역

JPA는 상태 변경 중심 기능에 사용한다.

예:

- 회원 가입
- 로그인 세션 저장
- 프로필 생성/수정
- 사진 상태 변경
- 좋아요 생성
- 매칭 생성
- 채팅 메시지 저장
- 신고 생성
- 차단 생성
- 계정 정지
- 탈퇴 처리
- 결제 상태 변경

---

### 7.3 jOOQ가 담당할 영역

jOOQ는 복잡한 조회에 사용한다.

예:

- 추천 후보 조회
- 받은 좋아요 목록
- 매칭 목록
- 채팅방 목록
- 마지막 메시지 포함 조회
- 읽지 않은 메시지 수
- 관리자 회원 검색
- 관리자 신고 검색
- 관리자 통계
- 결제 통계
- 추천 노출 대비 좋아요 전환율

---

### 7.4 통합 원칙

jOOQ와 JPA를 통합한다는 것은 다음을 공유한다는 뜻이다.

```text
같은 DB
같은 DataSource
같은 Transaction
같은 Migration
같은 Application Service 내부에서 사용 가능
```

하지만 다음은 피한다.

```text
jOOQ 결과를 JPA Entity로 억지 매핑
JPA Entity와 jOOQ Record를 강하게 결합
영속성 컨텍스트와 jOOQ 조회 결과를 무분별하게 혼용
```

권장 방식:

```text
JPA Entity  = 쓰기 모델
jOOQ Result = 조회 DTO
```

---

### 7.5 Flyway 기준 스키마 관리

JPA와 jOOQ를 같이 쓰려면 DB 스키마의 기준점을 명확히 해야 한다.

권장:

```text
Flyway migration file = schema source of truth
JPA ddl-auto          = validate
jOOQ code generation  = DB schema 기준
```

예:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  jooq:
    sql-dialect: postgres
```

---

### 7.6 주의사항

JPA로 저장한 직후 같은 트랜잭션 안에서 jOOQ로 바로 조회해야 하는 경우, Hibernate flush 타이밍 때문에 jOOQ 조회 결과에 반영되지 않을 수 있다.

해결 방법:

- `entityManager.flush()` 사용
- `saveAndFlush()` 사용
- Command Service에서 저장 직후 jOOQ 재조회를 피함
- 조회는 별도 Query Service에서 수행

권장 흐름:

```text
Command Service = 상태 변경
Query Service   = 화면 조회
```

---

## 8. GraphQL 도입 검토

### 8.1 결론

GraphQL은 전면 도입보다 `복잡한 조회 API`에 제한적으로 도입하는 것이 적절하다.

권장 구분:

```text
Command API       → REST
복합 조회 API     → GraphQL 고려
실시간 채팅       → WebSocket 권장
파일 업로드       → REST + Presigned URL
결제 / Webhook    → REST
```

---

### 8.2 GraphQL이 잘 맞는 영역

GraphQL을 고려할 만한 영역:

- 앱 홈 화면 조회
- 추천 카드 조회
- 프로필 상세 조회
- 마이페이지 조회
- 관리자 회원 상세
- 관리자 신고 상세
- 통계 대시보드
- 여러 도메인을 조합해야 하는 복합 조회

예:

```graphql
query Home {
  me {
    id
    nickname
    profileStatus
  }

  dailyQuota {
    remainingLikes
    remainingSuperLikes
  }

  recommendations(limit: 10) {
    userId
    nickname
    age
    region
    mainPhotoUrl
    introduction
    interests
  }

  counters {
    receivedLikes
    newMatches
    unreadMessages
  }
}
```

---

### 8.3 GraphQL이 적절하지 않은 영역

REST가 더 적절한 영역:

- 회원가입
- 로그인
- 토큰 재발급
- 로그아웃
- 좋아요
- 패스
- 매칭 취소
- 신고
- 차단
- 사진 업로드
- 결제
- 웹훅

채팅은 GraphQL Subscription보다 WebSocket이 더 적절하다.

---

### 8.4 권장 도입 순서

1차 MVP:

```text
REST 중심 개발
```

1.5차:

```text
앱 홈 화면 / 프로필 상세 / 관리자 상세 조회에 GraphQL 도입 검토
```

2차:

```text
관리자 검색, 통계 대시보드, 복합 조회를 GraphQL로 확장
```

---

## 9. 최종 패키지 구조

### 9.1 문제의식

다음 구조는 어색하다.

```text
com.dating
  global
  user
  profile
  photo
  discovery
  match
  chat
```

이유:

- `global`은 공통/기반 코드
- `user`, `profile`, `match`는 비즈니스 기능 모듈
- 서로 본질적으로 다른 계층인데 같은 레벨에 놓임
- `global`이 잡동사니 폴더가 될 위험이 큼

또한 다음 구조도 어색하다.

```text
com.dating.domain.profile.domain.Profile
```

`domain`이라는 이름이 상위와 하위에 반복되기 때문이다.

---

### 9.2 최종 추천 상위 구조

```text
src/main/kotlin/com/dating
  DatingApplication.kt

  app
  shared
  platform
  modules
```

역할:

| 패키지 | 역할 |
|---|---|
| app | 애플리케이션 실행, 설정, 스케줄러, 부트스트랩 |
| shared | 특정 도메인/기술에 덜 의존하는 순수 공통 코드 |
| platform | DB, Redis, S3, JWT, 외부 API 등 기술 기반 코드 |
| modules | 실제 소개팅 서비스의 비즈니스 기능 모듈 |

---

### 9.3 전체 구조 예시

```text
src/main/kotlin/com/dating
  DatingApplication.kt

  app
    config
    scheduler
    bootstrap

  shared
    exception
    response
    pagination
    time
    validation

  platform
    security
    persistence
    redis
    storage
    notification
    external

  modules
    identity
    profile
    discovery
    interaction
    match
    chat
    moderation
    notification
    payment
    admin
```

---

## 10. app 패키지

`app`은 애플리케이션 실행과 조립에 가까운 코드를 둔다.

예:

```text
app
  config
    WebConfig.kt
    CorsConfig.kt
    JacksonConfig.kt
    OpenApiConfig.kt

  scheduler
    MatchExpireScheduler.kt
    DailyQuotaResetScheduler.kt
    DormantUserScheduler.kt

  bootstrap
    AdminAccountInitializer.kt
```

---

### 10.1 app.scheduler

`scheduler`는 정해진 시간마다 application service를 실행하는 트리거다.

예:

- 매칭 만료 처리
- 휴면 사용자 처리
- 일일 좋아요 횟수 초기화
- 추천 풀 갱신
- 신고 누적 사용자 자동 점검
- 결제 구독 만료 확인
- 예약 푸시 발송

주의:

Scheduler에 비즈니스 로직을 넣지 않는다.

권장 흐름:

```text
app.scheduler.MatchExpireScheduler
  → modules.match.application.ExpireMatchService
```

Controller와 Scheduler의 차이:

```text
REST Controller = HTTP 요청으로 유스케이스 실행
Scheduler       = 시간 조건으로 유스케이스 실행
```

---

## 11. shared 패키지

`shared`는 어떤 도메인에도 속하지 않고, 특정 기술에도 덜 묶인 순수 공통 코드를 둔다.

예:

```text
shared
  exception
  response
  pagination
  time
  validation
```

---

### 11.1 shared.time

`shared.time`은 현재 시간, 날짜 계산, 시간 테스트를 위한 공통 추상화를 제공한다.

예:

```kotlin
interface TimeProvider {
    fun now(): LocalDateTime
    fun today(): LocalDate
}
```

이점:

- 테스트에서 현재 시간을 고정할 수 있음
- KST/UTC 정책을 한 곳에서 관리 가능
- 만료일, 정지 기간, 구독 기간 계산을 일관되게 처리 가능
- `LocalDateTime.now()`가 서비스 곳곳에 흩어지는 것을 방지

소개팅 앱에서 시간 정책이 중요한 기능:

- 하루 좋아요 제한 초기화
- 매칭 만료
- 채팅방 만료
- 정지 기간
- 구독 만료
- 최근 접속 기준
- 프로필 노출 기간

---

### 11.2 shared.response

`shared.response`는 API 응답 형식을 통일하는 역할을 한다.

예:

```text
shared.response.ApiResponse
shared.response.PageResponse
shared.response.CursorResponse
shared.response.ErrorResponse
```

공통 응답 예:

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String? = null
)
```

모바일 앱에서는 커서 기반 응답도 중요하다.

```kotlin
data class CursorResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean
)
```

커서 기반 응답이 잘 맞는 기능:

- 추천 카드 목록
- 채팅 메시지 목록
- 매칭 목록
- 받은 좋아요 목록
- 관리자 신고 목록

주의:

`shared.response`에는 비즈니스 응답 DTO를 넣지 않는다.

비즈니스 DTO는 각 모듈에 둔다.

예:

```text
modules.profile.application.dto.ProfileResult
modules.match.application.dto.MatchResult
```

---

## 12. platform 패키지

`platform`은 외부 기술과 연결되는 기반 인프라 코드를 둔다.

예:

```text
platform
  security
  persistence
  redis
  storage
  notification
  external
```

---

### 12.1 platform.persistence

`platform.persistence`는 JPA, jOOQ, DB 접근 기술의 공통 기반을 담당한다.

예:

```text
platform.persistence
  JpaAuditingConfig.kt
  BaseJpaEntity.kt
  JooqConfig.kt
  TransactionConfig.kt
  SoftDelete.kt
```

예시:

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseJpaEntity {
    @CreatedDate
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    var updatedAt: LocalDateTime? = null
        protected set
}
```

주의:

`platform.persistence`에는 특정 모듈의 Repository를 두지 않는다.

나쁜 예:

```text
platform.persistence.UserJpaRepository
platform.persistence.ProfileJpaRepository
```

좋은 예:

```text
modules.profile.infrastructure.jpa.ProfileJpaRepository
modules.match.infrastructure.jpa.MatchJpaRepository
platform.persistence.BaseJpaEntity
```

---

### 12.2 platform.external

`platform.external`은 외부 서비스와 통신하는 기술 어댑터를 둔다.

예:

```text
platform.external
  kakao
  naver
  google
  apple
  sms
  payment
  moderation
```

외부 연동 예:

- 카카오 로그인
- 네이버 로그인
- 구글 로그인
- 애플 로그인
- SMS 인증
- 본인인증
- 결제 검증
- 이미지 검수 API
- AI 검수 API
- 지도/위치 API

초기에는 외부 클라이언트 인터페이스와 구현을 모두 `platform.external`에 둘 수 있다.

장기적으로 헥사고날에 더 가깝게 가려면, 모듈의 application port에 인터페이스를 두고 `platform.external`이 이를 구현하는 방식이 좋다.

예:

```text
modules.identity.application.port.SmsSender
  ← platform.external.sms.CoolSmsSender
```

---

## 13. modules 패키지

`modules`는 실제 소개팅 서비스의 비즈니스 기능을 담당한다.

권장 모듈:

```text
modules
  identity
  profile
  discovery
  interaction
  match
  chat
  moderation
  notification
  payment
  admin
```

---

### 13.1 identity

`identity`는 사용자의 인증과 계정 식별을 담당한다.

담당 기능:

- 회원가입
- 로그인
- OAuth 로그인
- 휴대폰 인증
- 이메일 인증
- 토큰 발급
- 토큰 재발급
- 로그아웃
- 계정 상태
- 탈퇴

`user`라는 이름보다 `identity`가 더 적절할 수 있다.

이유:

```text
identity = 이 사용자가 누구인가 / 로그인 가능한가
profile  = 소개팅에서 어떻게 보이는가
```

---

### 13.2 profile

`profile`은 소개팅에서 사용자에게 보여지는 정보를 담당한다.

담당 기능:

- 프로필 생성
- 프로필 수정
- 프로필 심사 제출
- 프로필 공개/숨김
- 프로필 사진 업로드
- 대표 사진 설정
- 사진 순서 변경
- 사진 검수 상태

초기에는 `photo`를 별도 모듈로 두기보다 `profile` 내부에 `ProfilePhoto`로 포함하는 것이 좋다.

예:

```text
modules.profile.domain.ProfilePhoto
```

나중에 채팅 이미지, 신고 첨부, 인증 이미지까지 확장되면 `media` 모듈로 승격할 수 있다.

---

### 13.3 discovery

`discovery`는 추천 후보 조회를 담당한다.

담당 기능:

- 추천 카드 조회
- 추천 조건 적용
- 이미 본 사용자 제외
- 차단 사용자 제외
- 신고 위험 사용자 제외
- 추천 노출 기록 저장

이 모듈은 jOOQ가 가장 잘 맞는 영역이다.

---

### 13.4 interaction

`interaction`은 사용자가 추천 카드에 대해 수행하는 반응을 담당한다.

`like`보다 `interaction`이 더 확장성 있는 이름이다.

담당 기능:

- 좋아요
- 패스
- 슈퍼 좋아요
- 좋아요 취소
- 받은 좋아요 조회
- 보낸 좋아요 조회

권장 타입:

```text
LIKE
PASS
SUPER_LIKE
```

---

### 13.5 match

`match`는 상호 좋아요로 생성된 관계를 담당한다.

좋아요는 행위이고, 매칭은 관계다.

담당 기능:

- 매칭 생성
- 매칭 목록 조회
- 매칭 취소
- 매칭 만료
- 매칭 상태 관리

권장 상태:

```text
ACTIVE
UNMATCHED
EXPIRED
```

---

### 13.6 chat

`chat`은 매칭된 사용자 간 대화를 담당한다.

담당 기능:

- 채팅방 생성
- 메시지 전송
- 메시지 조회
- 읽음 처리
- 채팅방 나가기
- 차단 시 채팅 제한
- 신고 시 메시지 스냅샷 보존

초기에는 REST + Push로 시작하고, 이후 WebSocket을 도입한다.

---

### 13.7 moderation

`moderation`은 신고, 차단, 제재를 담당한다.

`report`보다 `moderation`이 장기적으로 더 적절하다.

담당 기능:

- 사용자 신고
- 메시지 신고
- 사진 신고
- 사용자 차단
- 신고 검토
- 제재 처리
- 제재 이력 관리

포함 도메인:

- Report
- Block
- Sanction
- ModerationPolicy

---

### 13.8 notification

`notification`은 알림을 담당한다.

담당 기능:

- 푸시 알림 발송
- 알림 이력 저장
- 알림 읽음 처리
- 알림 설정 관리

단, FCM/APNs 자체 구현은 `platform.notification`에 둔다.

`modules.notification`은 알림이라는 비즈니스 기능을 담당한다.

---

### 13.9 payment

`payment`는 결제와 구독을 담당한다.

담당 기능:

- 결제 준비
- 결제 검증
- 구독 상태 관리
- 환불 처리
- App Store / Google Play 웹훅 처리
- 포인트 또는 이용권 관리

---

### 13.10 admin

`admin`은 관리자 기능을 담당한다.

담당 기능:

- 회원 검색
- 프로필 검수
- 사진 검수
- 신고 처리
- 제재 처리
- 결제 내역 조회
- 통계 조회

관리자 조회는 jOOQ와 GraphQL을 도입하기 좋은 영역이다.

---

## 14. 모듈 내부 구조

각 모듈은 기본적으로 다음 구조를 가진다.

```text
modules.profile
  presentation
  application
  domain
  infrastructure
```

JPA와 jOOQ, REST와 GraphQL을 고려하면 다음처럼 확장할 수 있다.

```text
modules.profile
  presentation
    rest
    graphql

  application
    command
    query
    dto

  domain

  infrastructure
    jpa
    query
```

예:

```text
modules.profile
  presentation
    rest
      ProfileController.kt
      ProfilePhotoController.kt
    graphql
      ProfileGraphqlController.kt

  application
    command
      CreateProfileService.kt
      UpdateProfileService.kt
      UploadProfilePhotoService.kt
    query
      GetMyProfileService.kt
      GetProfileDetailService.kt
    dto
      CreateProfileCommand.kt
      ProfileResult.kt

  domain
    Profile.kt
    ProfilePhoto.kt
    ProfileStatus.kt
    ProfilePhotoStatus.kt
    ProfilePolicy.kt
    ProfilePhotoPolicy.kt

  infrastructure
    jpa
      ProfileJpaEntity.kt
      ProfilePhotoJpaEntity.kt
      ProfileJpaRepository.kt
      ProfilePhotoJpaRepository.kt
      ProfileRepositoryAdapter.kt
    query
      ProfileQueryRepository.kt
```

---

## 15. 의존성 방향

권장 의존성 방향:

```text
presentation → application → domain
infrastructure → application/domain
modules → shared
modules → platform
```

주의할 점:

```text
shared → modules 의존 금지
platform → modules 의존 최소화
domain → infrastructure 의존 금지
application → presentation 의존 금지
controller → repository 직접 호출 금지
```

더 엄격한 헥사고날 구조에서는 다음 방향을 지향한다.

```text
modules.{module}.application.port
  ← platform 또는 infrastructure adapter 구현
```

---

## 16. 호출 흐름 예시

### 16.1 매칭 만료 스케줄러

```text
app.scheduler.MatchExpireScheduler
  → modules.match.application.ExpireMatchService
    → shared.time.TimeProvider
    → modules.match.infrastructure.jpa.MatchRepositoryAdapter
      → platform.persistence.BaseJpaEntity
```

역할:

- `app.scheduler`: 매시간 실행 트리거
- `modules.match.application`: 매칭 만료 유스케이스
- `shared.time`: 현재 시간 제공
- `modules.match.infrastructure`: 데이터 조회/저장
- `platform.persistence`: JPA 공통 기반

---

### 16.2 휴대폰 인증번호 발송

간단한 초기 구조:

```text
modules.identity.application.SendPhoneVerificationCodeService
  → platform.external.sms.SmsClient
```

더 헥사고날스러운 구조:

```text
modules.identity.application.SendPhoneVerificationCodeService
  → modules.identity.application.port.SmsSender
    ← platform.external.sms.CoolSmsSender
```

---

### 16.3 프로필 사진 업로드

```text
modules.profile.presentation.rest.ProfilePhotoController
  → modules.profile.application.UploadProfilePhotoService
    → platform.storage.StorageClient
    → modules.profile.infrastructure.jpa.ProfilePhotoJpaRepository
```

역할:

- `profile.application`: 프로필 사진 업로드 유스케이스
- `platform.storage`: S3/R2 업로드
- `profile.infrastructure.jpa`: 사진 메타데이터 저장

---

## 17. 피해야 할 구조

### 17.1 global 잡동사니 구조

비추천:

```text
global
  config
  exception
  util
  jwt
  s3
  redis
  email
  validator
  response
  security
```

문제:

- 공통 코드와 기술 코드와 비즈니스 코드가 섞임
- 시간이 지나면 무엇이든 들어가는 폴더가 됨
- 의존성 방향이 흐려짐

---

### 17.2 레이어 기준 최상위 패키징

비추천:

```text
controller
service
repository
entity
dto
```

문제:

- 기능 단위 응집도가 낮음
- 도메인별 변경 범위를 파악하기 어려움
- `UserService`, `MatchService` 등이 비대해질 가능성이 높음

---

### 17.3 domain 중복 구조

비추천:

```text
com.dating.domain.profile.domain.Profile
```

추천:

```text
com.dating.modules.profile.domain.Profile
```

---

## 18. 최종 결론

이 프로젝트의 초기 설계 방향은 다음과 같이 잡는 것이 적절하다.

```text
Backend:
Kotlin + Spring Boot

Architecture:
모듈러 모놀리스 + 가벼운 클린 아키텍처

DB:
PostgreSQL

Write Model:
JPA

Read Model:
jOOQ

Migration:
Flyway

Client:
React Native + Expo

Admin:
Next.js

API:
REST 중심
복잡한 조회는 GraphQL 선택 도입
실시간 채팅은 WebSocket

Package Structure:
app / shared / platform / modules
```

가장 중요한 기준은 다음이다.

```text
비즈니스 기능은 modules 안에 둔다.
기술 기반 코드는 platform에 둔다.
순수 공통 코드는 shared에 둔다.
실행/조립/스케줄링은 app에 둔다.
```

이 구조는 MVP 개발 속도를 크게 해치지 않으면서도, 장기 운영과 기능 확장에 대응하기 좋은 균형점이다.
