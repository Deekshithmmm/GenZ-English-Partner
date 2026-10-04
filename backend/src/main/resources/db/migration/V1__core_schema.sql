-- Core identity, learner profile and practice history.
-- Every child table cascades from app_user so "delete my account" removes all personal data in one statement.

create table app_user (
    id             uuid primary key,
    email          varchar(320) not null,
    password_hash  varchar(100),
    google_subject varchar(255),
    display_name   varchar(80)  not null,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    constraint uq_app_user_email unique (email),
    constraint uq_app_user_google_subject unique (google_subject),
    constraint ck_app_user_has_credential check (password_hash is not null or google_subject is not null)
);

create table refresh_token (
    id         uuid primary key,
    user_id    uuid        not null references app_user (id) on delete cascade,
    family_id  uuid        not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    revoked_at timestamptz,
    constraint uq_refresh_token_hash unique (token_hash)
);
create index ix_refresh_token_user on refresh_token (user_id);
create index ix_refresh_token_family on refresh_token (family_id);

create table learner_profile (
    user_id              uuid primary key references app_user (id) on delete cascade,
    explanation_language varchar(16)  not null default 'ENGLISH',
    goal                 varchar(32),
    daily_minutes        integer,
    level                varchar(2),
    level_source         varchar(16),
    tutor_voice          varchar(16)  not null default 'KAI_WARM',
    speaking_speed       numeric(3, 2) not null default 1.00,
    keep_audio           boolean      not null default false,
    time_zone            varchar(64)  not null default 'Asia/Kolkata',
    preferences_set_at   timestamptz,
    level_set_at         timestamptz,
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    version              bigint       not null default 0,
    constraint ck_learner_profile_daily_minutes check (daily_minutes is null or daily_minutes in (5, 10, 20)),
    constraint ck_learner_profile_level check (level is null or level in ('A1', 'A2', 'B1', 'B2', 'C1')),
    constraint ck_learner_profile_speed check (speaking_speed between 0.50 and 1.50)
);

create table practice_session (
    id           uuid primary key,
    user_id      uuid        not null references app_user (id) on delete cascade,
    type         varchar(24) not null,
    status       varchar(16) not null,
    opening_line text,
    summary_json jsonb,
    speaking_ms  integer     not null default 0,
    started_at   timestamptz not null,
    ended_at     timestamptz
);
create index ix_practice_session_user_started on practice_session (user_id, started_at desc);

create table turn (
    id                uuid primary key,
    session_id        uuid        not null references practice_session (id) on delete cascade,
    seq               integer     not null,
    prompt_text       text,
    learner_text      text        not null,
    input_mode        varchar(8)  not null,
    audio_duration_ms integer,
    audio_key         varchar(255),
    reply_text        text,
    feedback_json     jsonb,
    scores_json       jsonb,
    words_per_minute  integer,
    filler_count      integer,
    created_at        timestamptz not null,
    constraint uq_turn_session_seq unique (session_id, seq)
);
