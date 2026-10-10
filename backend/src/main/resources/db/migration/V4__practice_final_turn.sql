-- Free talk runs on time (up to ten minutes) instead of a fixed number of answers, so the answer Luna wrapped up on
-- is kept: a retried answer or a hint after it then knows the chat is over.
alter table practice_session
    add column final_turn integer;
