-- Practice sessions record what was practised, the XP earned, and the learner's local date when the session was
-- completed. Storing the date (rather than deriving it from ended_at) keeps streaks stable if the learner later
-- changes time zone.
alter table practice_session
    add column scenario   varchar(64),
    add column topic      text,
    add column xp         integer not null default 0,
    add column local_date date;

alter table practice_session
    add constraint ck_practice_session_xp check (xp >= 0);

-- Streaks, today's XP and "done today" all read completed sessions by learner and day.
create index ix_practice_session_progress on practice_session (user_id, local_date)
    where status = 'COMPLETED' and local_date is not null;
