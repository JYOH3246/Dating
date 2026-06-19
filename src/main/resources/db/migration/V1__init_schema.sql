-- =====================================================================
-- 소개팅 앱 PostgreSQL 스키마 DDL
-- 문서 버전: v1.1
-- 기준: 소개팅 앱 개발 정책 문서 v1.3 / 상태 전이 v1.1 / DB 제약조건 v1.1
-- DBMS: PostgreSQL 16+
--
-- 주의:
--   - 모든 DELETE는 soft delete(deleted_at) 기준. 물리 삭제는 별도 배치.
--   - enum은 CHECK 대신 native enum 사용(이식 시 CHECK로 대체 가능).
--   - 시간 컬럼은 timestamptz(UTC).
--   - 24h 신고 중복 제한, 사진 최소/최대 장수, 관심사 최소 3개 등은
--     애플리케이션 레벨 규칙이며 DDL 주석으로 표기.
-- =====================================================================

BEGIN;

-- =====================================================================
-- ENUM 타입
-- =====================================================================
CREATE TYPE user_status        AS ENUM ('PENDING_VERIFICATION','PENDING_AGREEMENTS','ACTIVE','SUSPENDED','DELETED','ANONYMIZED','BANNED');
CREATE TYPE oauth_provider      AS ENUM ('GOOGLE','NAVER','KAKAO','APPLE');
CREATE TYPE gender_type         AS ENUM ('MALE','FEMALE','UNKNOWN');
CREATE TYPE agreement_type      AS ENUM ('TERMS_OF_SERVICE','PRIVACY_POLICY','LOCATION_BASED','MARKETING','NIGHT_MARKETING');
CREATE TYPE paseto_key_status   AS ENUM ('ACTIVE','VERIFY_ONLY','RETIRED','COMPROMISED');
CREATE TYPE profile_status      AS ENUM ('DRAFT','PENDING_REVIEW','ACTIVE','REJECTED','HIDDEN','DELETED');
CREATE TYPE photo_status        AS ENUM ('PENDING','APPROVED','REJECTED','DELETED');
CREATE TYPE reaction_type       AS ENUM ('LIKE','PASS');
CREATE TYPE reaction_status     AS ENUM ('ACTIVE','CANCELLED','EXPIRED');
CREATE TYPE match_status        AS ENUM ('ACTIVE','UNMATCHED','EXPIRED');
CREATE TYPE chat_room_status    AS ENUM ('OPEN','CLOSED');
CREATE TYPE report_target_type  AS ENUM ('USER','PROFILE','PHOTO','MESSAGE','CHAT_ROOM');
CREATE TYPE report_status       AS ENUM ('RECEIVED','UNDER_REVIEW','ACTION_TAKEN','REJECTED','CLOSED');
CREATE TYPE report_reason       AS ENUM ('INAPPROPRIATE_PHOTO','ABUSIVE_LANGUAGE','HARASSMENT','SPAM','SCAM','IMPERSONATION','SEXUAL_CONTENT','ILLEGAL_CONTENT','PRIVACY_VIOLATION','OTHER');
CREATE TYPE sanction_level      AS ENUM ('WARNING','TEMP_SUSPENDED_3D','TEMP_SUSPENDED_7D','TEMP_SUSPENDED_30D','PERMANENT_BANNED');
CREATE TYPE sanction_status     AS ENUM ('ACTIVE','EXPIRED','REVOKED');
CREATE TYPE plan_code_type      AS ENUM ('FREE','PREMIUM_MONTHLY','PREMIUM_YEARLY');
CREATE TYPE subscription_status AS ENUM ('ACTIVE','GRACE_PERIOD','EXPIRED','CANCELLED','REFUNDED');
CREATE TYPE payment_provider    AS ENUM ('APPLE','GOOGLE','PG');
CREATE TYPE payment_status      AS ENUM ('PENDING','PAID','FAILED','CANCELLED','REFUNDED');
CREATE TYPE store_environment   AS ENUM ('SANDBOX','PRODUCTION');
CREATE TYPE entitlement_type    AS ENUM ('PREMIUM');
CREATE TYPE entitlement_source_type AS ENUM ('SUBSCRIPTION','PROMOTION','ADMIN_GRANT');
CREATE TYPE admin_role          AS ENUM ('SUPER_ADMIN','OPERATOR','MODERATOR','SUPPORT');

-- =====================================================================
-- 1. 인증 / 계정 도메인
-- =====================================================================

-- 1.1 USERS
CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    email           TEXT,
    phone_number    TEXT,
    name            TEXT,
    status          user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    anonymized_at   timestamptz,
    deleted_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id)
);
-- 가명처리/삭제 후 이메일 재사용 허용
CREATE UNIQUE INDEX uq_users_email_active
    ON users (email)
    WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_users_status     ON users (status);
CREATE INDEX ix_users_created_at ON users (created_at);

-- 1.2 AUTH_IDENTITIES
CREATE TABLE auth_identities (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    provider            oauth_provider NOT NULL,
    provider_user_id    TEXT NOT NULL,
    provider_email      TEXT,
    deleted_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_auth_identities PRIMARY KEY (id),
    CONSTRAINT fk_auth_identities_user FOREIGN KEY (user_id) REFERENCES users(id)
);
-- 활성 OAuth identity만 로그인 식별에 사용한다. 탈퇴 14일 후 재가입 허용을 위해 부분 유니크 사용.
CREATE UNIQUE INDEX uq_auth_identities_provider_active
    ON auth_identities (provider, provider_user_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_auth_identities_user ON auth_identities (user_id);

-- 1.3 USER_IDENTITY_VERIFICATIONS (본인인증, 필수)
CREATE TABLE user_identity_verifications (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    ci_hash             TEXT NOT NULL,
    di_hash             TEXT NOT NULL,
    verified_birth_date DATE NOT NULL,
    verified_gender     gender_type NOT NULL,
    carrier             TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    verified_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_identity_verifications PRIMARY KEY (id),
    CONSTRAINT fk_uiv_user FOREIGN KEY (user_id) REFERENCES users(id)
);
-- DI는 활성 계정 기준 1개 (중복가입/밴 회피 차단)
CREATE UNIQUE INDEX uq_uiv_di_active ON user_identity_verifications (di_hash) WHERE is_active = true;
CREATE INDEX ix_uiv_user ON user_identity_verifications (user_id);

-- 영구밴 DI 차단 리스트 (재가입 차단)
CREATE TABLE banned_identities (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    di_hash     TEXT NOT NULL,
    reason      TEXT,
    banned_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_banned_identities PRIMARY KEY (id),
    CONSTRAINT uq_banned_identities_di UNIQUE (di_hash)
);

-- 1.4 USER_AGREEMENTS (append-only)
CREATE TABLE user_agreements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,
    agreement_type  agreement_type NOT NULL,
    version         TEXT NOT NULL,
    is_agreed       BOOLEAN NOT NULL,
    agreed_at       timestamptz NOT NULL DEFAULT now(),
    ip_address      INET,
    user_agent      TEXT,
    CONSTRAINT pk_user_agreements PRIMARY KEY (id),
    CONSTRAINT fk_user_agreements_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX ix_user_agreements_lookup ON user_agreements (user_id, agreement_type, version);
CREATE INDEX ix_user_agreements_user_time ON user_agreements (user_id, agreed_at);

-- 1.4.1 AGREEMENT_VERSIONS (약관 마스터)
CREATE TABLE agreement_versions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    agreement_type  agreement_type NOT NULL,
    version         TEXT NOT NULL,
    title           TEXT NOT NULL,
    is_required     BOOLEAN NOT NULL DEFAULT true,
    effective_at    timestamptz NOT NULL,
    retired_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_agreement_versions PRIMARY KEY (id),
    CONSTRAINT uq_agreement_versions UNIQUE (agreement_type, version)
);
CREATE INDEX ix_agreement_versions_effective ON agreement_versions (agreement_type, effective_at);

-- 1.5 AUTH_TOKEN_SESSIONS
CREATE TABLE auth_token_sessions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    device_id           TEXT NOT NULL,
    refresh_token_hash  TEXT NOT NULL,
    expires_at          timestamptz NOT NULL,
    last_used_at        timestamptz NOT NULL DEFAULT now(),
    revoked_at          timestamptz,
    rotated_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_auth_token_sessions PRIMARY KEY (id),
    CONSTRAINT uq_ats_refresh_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_ats_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX ix_ats_user ON auth_token_sessions (user_id);
CREATE INDEX ix_ats_device ON auth_token_sessions (device_id);
CREATE INDEX ix_ats_expires ON auth_token_sessions (expires_at);
-- 같은 기기 활성 세션 1개
CREATE UNIQUE INDEX uq_ats_user_device_active
    ON auth_token_sessions (user_id, device_id)
    WHERE revoked_at IS NULL;
-- 최대 5기기 + 초과 시 LRU(last_used_at) 폐기는 애플리케이션 레벨

-- 1.6 PASETO_KEYS
CREATE TABLE paseto_keys (
    key_id      TEXT NOT NULL,
    public_key  TEXT NOT NULL,
    secret_ref  TEXT NOT NULL,
    status      paseto_key_status NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_paseto_keys PRIMARY KEY (key_id)
);
-- 발급용 활성 키 1개
CREATE UNIQUE INDEX uq_paseto_active ON paseto_keys ((status)) WHERE status = 'ACTIVE';
CREATE INDEX ix_paseto_status ON paseto_keys (status);

-- =====================================================================
-- 2. 프로필 도메인
-- =====================================================================

-- 2.1 PROFILES
CREATE TABLE profiles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    nickname    TEXT,
    gender      gender_type NOT NULL DEFAULT 'UNKNOWN',
    birth_date  DATE,
    region_code TEXT,
    bio         TEXT,
    status      profile_status NOT NULL DEFAULT 'DRAFT',
    deleted_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_profiles PRIMARY KEY (id),
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users(id)
);
-- 1:1 (삭제분 제외)
CREATE UNIQUE INDEX uq_profiles_user_active ON profiles (user_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_profiles_status ON profiles (status);
CREATE INDEX ix_profiles_region_status ON profiles (region_code, status);

-- 2.1.1 PROFILE_CHANGE_HISTORIES (생년월일/성별 등 관리자 승인 변경 이력)
CREATE TABLE profile_change_histories (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    profile_id      BIGINT NOT NULL,
    changed_by      TEXT NOT NULL, -- USER_REQUEST, ADMIN, SYSTEM 등
    field_name      TEXT NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    reason          TEXT,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_profile_change_histories PRIMARY KEY (id),
    CONSTRAINT fk_pch_profile FOREIGN KEY (profile_id) REFERENCES profiles(id)
);
CREATE INDEX ix_pch_profile_time ON profile_change_histories (profile_id, created_at);

-- 2.2 PROFILE_PHOTOS
CREATE TABLE profile_photos (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    profile_id      BIGINT NOT NULL,
    file_key        TEXT NOT NULL,
    display_order   SMALLINT NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    status          photo_status NOT NULL DEFAULT 'PENDING',
    deleted_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_profile_photos PRIMARY KEY (id),
    CONSTRAINT fk_profile_photos_profile FOREIGN KEY (profile_id) REFERENCES profiles(id),
    CONSTRAINT ck_profile_photos_order CHECK (display_order BETWEEN 1 AND 6)
);
-- 슬롯 유일성(삭제분 제외)
CREATE UNIQUE INDEX uq_profile_photos_order
    ON profile_photos (profile_id, display_order)
    WHERE deleted_at IS NULL;
-- 파일 업로드 멱등성. 파일 키는 같은 프로필 내에서 중복 등록하지 않는다.
CREATE UNIQUE INDEX uq_profile_photos_profile_file
    ON profile_photos (profile_id, file_key)
    WHERE deleted_at IS NULL;
-- 대표 사진은 활성 프로필당 최대 1장. APPROVED 여부는 애플리케이션/트리거에서 강제한다.
CREATE UNIQUE INDEX uq_profile_photos_primary
    ON profile_photos (profile_id)
    WHERE is_primary = true AND deleted_at IS NULL;
CREATE INDEX ix_profile_photos_status ON profile_photos (profile_id, status);
-- 최소 1장 / 최대 6장, 대표 사진 APPROVED 조건은 애플리케이션 레벨(또는 트리거)

-- 2.3 INTERESTS / PROFILE_INTERESTS
CREATE TABLE interests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    code        TEXT NOT NULL,
    label       TEXT NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT pk_interests PRIMARY KEY (id),
    CONSTRAINT uq_interests_code UNIQUE (code)
);

CREATE TABLE profile_interests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    profile_id  BIGINT NOT NULL,
    interest_id BIGINT NOT NULL,
    CONSTRAINT pk_profile_interests PRIMARY KEY (id),
    CONSTRAINT uq_profile_interests UNIQUE (profile_id, interest_id),
    CONSTRAINT fk_pi_profile FOREIGN KEY (profile_id) REFERENCES profiles(id),
    CONSTRAINT fk_pi_interest FOREIGN KEY (interest_id) REFERENCES interests(id)
);
CREATE INDEX ix_profile_interests_interest ON profile_interests (interest_id);
-- 최소 3개는 애플리케이션 레벨

-- 2.4 MATCH_PREFERENCES / REGIONS
CREATE TABLE match_preferences (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    age_min     SMALLINT NOT NULL DEFAULT 19,
    age_max     SMALLINT NOT NULL DEFAULT 99,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_preferences PRIMARY KEY (id),
    CONSTRAINT uq_match_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_mp_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_mp_age_range CHECK (age_min <= age_max),
    CONSTRAINT ck_mp_age_min CHECK (age_min >= 19)
);

CREATE TABLE match_preference_regions (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY,
    match_preference_id  BIGINT NOT NULL,
    region_code          TEXT NOT NULL,
    CONSTRAINT pk_mpr PRIMARY KEY (id),
    CONSTRAINT uq_mpr UNIQUE (match_preference_id, region_code),
    CONSTRAINT fk_mpr_pref FOREIGN KEY (match_preference_id) REFERENCES match_preferences(id)
);

-- =====================================================================
-- 3. Discovery / 반응 / 매칭 도메인
-- =====================================================================

-- 3.1 DISCOVERY_EXPOSURES
CREATE TABLE discovery_exposures (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    viewer_user_id  BIGINT NOT NULL,
    shown_user_id   BIGINT NOT NULL,
    exposed_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_discovery_exposures PRIMARY KEY (id),
    CONSTRAINT fk_de_viewer FOREIGN KEY (viewer_user_id) REFERENCES users(id),
    CONSTRAINT fk_de_shown FOREIGN KEY (shown_user_id) REFERENCES users(id)
);
CREATE INDEX ix_de_viewer_time ON discovery_exposures (viewer_user_id, exposed_at);
CREATE INDEX ix_de_viewer_shown ON discovery_exposures (viewer_user_id, shown_user_id);

-- 3.2 USER_REACTIONS (LIKE/PASS)
CREATE TABLE user_reactions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    actor_user_id   BIGINT NOT NULL,
    target_user_id  BIGINT NOT NULL,
    reaction_type   reaction_type NOT NULL,
    status          reaction_status NOT NULL DEFAULT 'ACTIVE',
    expires_at      timestamptz,
    cancelled_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_reactions PRIMARY KEY (id),
    CONSTRAINT fk_ur_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT fk_ur_target FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT ck_ur_self CHECK (actor_user_id <> target_user_id),
    CONSTRAINT ck_ur_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);
-- 현재 활성 반응은 한 쌍에 1개만 허용. PASS 만료/LIKE 취소 후 새 반응 가능.
CREATE UNIQUE INDEX uq_user_reactions_active_pair
    ON user_reactions (actor_user_id, target_user_id)
    WHERE status = 'ACTIVE';
CREATE INDEX ix_ur_actor_time ON user_reactions (actor_user_id, created_at);
CREATE INDEX ix_ur_actor_status ON user_reactions (actor_user_id, target_user_id, status);
CREATE INDEX ix_ur_target ON user_reactions (target_user_id, reaction_type, status, created_at);

-- 3.3 MATCHES
CREATE TABLE matches (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user1_id    BIGINT NOT NULL,
    user2_id    BIGINT NOT NULL,
    status      match_status NOT NULL DEFAULT 'ACTIVE',
    matched_at  timestamptz NOT NULL DEFAULT now(),
    unmatched_at timestamptz,
    CONSTRAINT pk_matches PRIMARY KEY (id),
    CONSTRAINT uq_matches_pair UNIQUE (user1_id, user2_id),
    CONSTRAINT fk_matches_u1 FOREIGN KEY (user1_id) REFERENCES users(id),
    CONSTRAINT fk_matches_u2 FOREIGN KEY (user2_id) REFERENCES users(id),
    CONSTRAINT ck_matches_order CHECK (user1_id < user2_id)
);
CREATE INDEX ix_matches_u1 ON matches (user1_id);
CREATE INDEX ix_matches_u2 ON matches (user2_id);

-- 3.4 USER_BLOCKS
CREATE TABLE user_blocks (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    blocker_user_id     BIGINT NOT NULL,
    blocked_user_id     BIGINT NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_blocks PRIMARY KEY (id),
    CONSTRAINT uq_user_blocks UNIQUE (blocker_user_id, blocked_user_id),
    CONSTRAINT fk_ub_blocker FOREIGN KEY (blocker_user_id) REFERENCES users(id),
    CONSTRAINT fk_ub_blocked FOREIGN KEY (blocked_user_id) REFERENCES users(id),
    CONSTRAINT ck_ub_self CHECK (blocker_user_id <> blocked_user_id)
);
CREATE INDEX ix_ub_blocked ON user_blocks (blocked_user_id);

-- =====================================================================
-- 4. 채팅 도메인
-- =====================================================================

-- 4.1 CHAT_ROOMS
CREATE TABLE chat_rooms (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    match_id    BIGINT NOT NULL,
    status      chat_room_status NOT NULL DEFAULT 'OPEN',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_chat_rooms PRIMARY KEY (id),
    CONSTRAINT uq_chat_rooms_match UNIQUE (match_id),
    CONSTRAINT fk_chat_rooms_match FOREIGN KEY (match_id) REFERENCES matches(id)
);

-- 4.2 CHAT_ROOM_MEMBERS
CREATE TABLE chat_room_members (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    chat_room_id    BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    joined_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_chat_room_members PRIMARY KEY (id),
    CONSTRAINT uq_chat_room_members UNIQUE (chat_room_id, user_id),
    CONSTRAINT fk_crm_room FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_crm_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX ix_crm_user ON chat_room_members (user_id);

-- 4.3 CHAT_MESSAGES
CREATE TABLE chat_messages (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    chat_room_id        BIGINT NOT NULL,
    sender_user_id      BIGINT NOT NULL,
    client_message_id   TEXT NOT NULL,
    content             TEXT,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT uq_chat_messages_client UNIQUE (chat_room_id, client_message_id),
    CONSTRAINT fk_cm_room FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_cm_sender FOREIGN KEY (sender_user_id) REFERENCES users(id)
);
CREATE INDEX ix_cm_room_time ON chat_messages (chat_room_id, created_at);
CREATE INDEX ix_cm_sender_time ON chat_messages (sender_user_id, created_at);

-- =====================================================================
-- 5. 신고 / 제재 도메인
-- =====================================================================

-- 5.1 REPORTS
CREATE TABLE reports (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    reporter_user_id    BIGINT NOT NULL,
    target_type         report_target_type NOT NULL,
    target_id           BIGINT NOT NULL,
    reason              report_reason NOT NULL,
    reason_detail       TEXT,
    status              report_status NOT NULL DEFAULT 'RECEIVED',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(id)
);
CREATE INDEX ix_reports_reporter_target ON reports (reporter_user_id, target_type, target_id);
CREATE INDEX ix_reports_status_time ON reports (status, created_at);
CREATE INDEX ix_reports_target ON reports (target_type, target_id);
-- 24h 중복 신고 제한은 애플리케이션 레벨.
-- 강제 시: report_day 생성컬럼 + UNIQUE(reporter,target_type,target_id,reason,report_day)
-- reason_detail은 사용자가 입력한 상세 설명이며, enum reason을 대체하지 않는다.

-- 5.2 SANCTIONS
CREATE TABLE sanctions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    di_hash     TEXT,
    level       sanction_level NOT NULL,
    status      sanction_status NOT NULL DEFAULT 'ACTIVE',
    reason      TEXT,
    expires_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_sanctions PRIMARY KEY (id),
    CONSTRAINT fk_sanctions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_sanctions_expiry CHECK (
        (level IN ('TEMP_SUSPENDED_3D','TEMP_SUSPENDED_7D','TEMP_SUSPENDED_30D') AND expires_at IS NOT NULL)
        OR (level IN ('WARNING','PERMANENT_BANNED') AND expires_at IS NULL)
    )
);
CREATE INDEX ix_sanctions_user_status ON sanctions (user_id, status);
CREATE INDEX ix_sanctions_di ON sanctions (di_hash);
CREATE INDEX ix_sanctions_expires_active ON sanctions (expires_at) WHERE status = 'ACTIVE';

-- =====================================================================
-- 6. 결제 / 구독 도메인
-- =====================================================================

-- 6.1 SUBSCRIPTION_PLANS
CREATE TABLE subscription_plans (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    plan_code           plan_code_type NOT NULL,
    apple_product_id    TEXT,
    google_product_id   TEXT,
    price               NUMERIC(12,2),
    currency            TEXT NOT NULL DEFAULT 'KRW',
    billing_period      TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT pk_subscription_plans PRIMARY KEY (id),
    CONSTRAINT uq_sp_plan_code UNIQUE (plan_code),
    CONSTRAINT uq_sp_apple UNIQUE (apple_product_id),
    CONSTRAINT uq_sp_google UNIQUE (google_product_id)
);

-- 6.2 SUBSCRIPTIONS
CREATE TABLE subscriptions (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id                     BIGINT NOT NULL,
    plan_id                     BIGINT NOT NULL,
    provider                    payment_provider,
    provider_subscription_id    TEXT,
    original_transaction_id     TEXT,
    purchase_token              TEXT,
    store_account_token         TEXT,
    environment                 store_environment NOT NULL DEFAULT 'PRODUCTION',
    status                      subscription_status NOT NULL DEFAULT 'ACTIVE',
    current_period_start        timestamptz,
    current_period_end          timestamptz,
    auto_renew_status           BOOLEAN,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subs_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_subs_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
);
-- 동시 유효 구독 1개
CREATE UNIQUE INDEX uq_subscriptions_active
    ON subscriptions (user_id)
    WHERE status IN ('ACTIVE','GRACE_PERIOD');
CREATE UNIQUE INDEX uq_subs_provider_subscription
    ON subscriptions (provider, provider_subscription_id)
    WHERE provider IS NOT NULL AND provider_subscription_id IS NOT NULL;
CREATE UNIQUE INDEX uq_subs_original_transaction
    ON subscriptions (provider, original_transaction_id)
    WHERE provider IS NOT NULL AND original_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX uq_subs_purchase_token
    ON subscriptions (provider, purchase_token)
    WHERE provider IS NOT NULL AND purchase_token IS NOT NULL;
CREATE INDEX ix_subs_user_status ON subscriptions (user_id, status);
CREATE INDEX ix_subs_plan ON subscriptions (plan_id);

-- 6.3 PAYMENTS
CREATE TABLE payments (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id                 BIGINT NOT NULL,
    subscription_id         BIGINT,
    provider                payment_provider NOT NULL,
    provider_transaction_id TEXT NOT NULL,
    amount                  NUMERIC(12,2) NOT NULL,
    currency                TEXT NOT NULL DEFAULT 'KRW',
    status                  payment_status NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_provider_tx UNIQUE (provider, provider_transaction_id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_payments_sub FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);
CREATE INDEX ix_payments_user_status_time ON payments (user_id, status, created_at);
CREATE INDEX ix_payments_sub_status ON payments (subscription_id, status);

-- 6.4 PAYMENT_EVENTS (웹훅)
CREATE TABLE payment_events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    subscription_id     BIGINT,
    provider            payment_provider NOT NULL,
    provider_event_id   TEXT NOT NULL,
    event_type          TEXT NOT NULL,
    payload             JSONB,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_payment_events PRIMARY KEY (id),
    CONSTRAINT uq_payment_events UNIQUE (provider, provider_event_id),
    CONSTRAINT fk_pe_sub FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);
CREATE INDEX ix_pe_sub_time ON payment_events (subscription_id, created_at);

-- 6.5 USER_ENTITLEMENTS (현재 권한 projection)
CREATE TABLE user_entitlements (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    entitlement_type    entitlement_type NOT NULL,
    source_type         entitlement_source_type NOT NULL,
    source_id           BIGINT,
    starts_at           timestamptz NOT NULL DEFAULT now(),
    expires_at          timestamptz,
    revoked_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_entitlements PRIMARY KEY (id),
    CONSTRAINT fk_entitlements_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX ix_entitlements_user_active
    ON user_entitlements (user_id, entitlement_type, expires_at)
    WHERE revoked_at IS NULL;

-- =====================================================================
-- 7. 관리자 / 알림 도메인
-- =====================================================================

-- 7.1 ADMIN_USERS
CREATE TABLE admin_users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    email       TEXT NOT NULL,
    name        TEXT,
    role        admin_role NOT NULL DEFAULT 'SUPPORT',
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_admin_users PRIMARY KEY (id),
    CONSTRAINT uq_admin_users_email UNIQUE (email)
);

-- 7.2 ADMIN_AUDIT_LOGS (append-only)
CREATE TABLE admin_audit_logs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    admin_user_id   BIGINT NOT NULL,
    action          TEXT NOT NULL,
    target_type     TEXT,
    target_id       BIGINT,
    detail          JSONB,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_admin_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_aal_admin FOREIGN KEY (admin_user_id) REFERENCES admin_users(id)
);
CREATE INDEX ix_aal_admin_time ON admin_audit_logs (admin_user_id, created_at);
CREATE INDEX ix_aal_target ON admin_audit_logs (target_type, target_id);
CREATE INDEX ix_aal_action_time ON admin_audit_logs (action, created_at);

-- 7.3 NOTIFICATION_SETTINGS
CREATE TABLE notification_settings (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    push_message        BOOLEAN NOT NULL DEFAULT true,
    push_match          BOOLEAN NOT NULL DEFAULT true,
    push_like           BOOLEAN NOT NULL DEFAULT true,
    marketing           BOOLEAN NOT NULL DEFAULT false,
    night_marketing     BOOLEAN NOT NULL DEFAULT false,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_settings PRIMARY KEY (id),
    CONSTRAINT uq_notification_settings_user UNIQUE (user_id),
    CONSTRAINT fk_ns_user FOREIGN KEY (user_id) REFERENCES users(id)
);

COMMIT;
