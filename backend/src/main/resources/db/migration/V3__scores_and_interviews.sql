-- Mock interviews keep the plan Kai follows (the role, a few facts to follow up on, the questions), never the pasted
-- résumé or job description itself.
-- Session scores live in columns as well as in the summary, so progress over time can be read without parsing JSON.
alter table practice_session
    add column plan_json           jsonb,
    add column fluency_score       integer,
    add column grammar_score       integer,
    add column pronunciation_score integer,
    add column vocabulary_score    integer;

alter table practice_session
    add constraint ck_practice_session_scores check (
        (fluency_score is null or fluency_score between 0 and 100)
        and (grammar_score is null or grammar_score between 0 and 100)
        and (pronunciation_score is null or pronunciation_score between 0 and 100)
        and (vocabulary_score is null or vocabulary_score between 0 and 100));
