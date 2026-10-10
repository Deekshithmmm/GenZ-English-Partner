package com.genzenglish.partner.practice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** One sitting with Luna: a placement chat, free talk, roleplay, interview or speak-off. */
@Entity
@Table(name = "practice_session")
public class PracticeSession {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SessionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status;

    @Column(name = "opening_line", columnDefinition = "text")
    private String openingLine;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson;

    @Column(name = "speaking_ms", nullable = false)
    private int speakingMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Roleplay scenario slug, free-talk topic or grammar topic id; null for other kinds of session. */
    @Column(length = 64)
    private String scenario;

    /** The warm-up topic Luna opened with. */
    @Column(columnDefinition = "text")
    private String topic;

    @Column(nullable = false)
    private int xp;

    /** The learner's local date when the session was completed; drives streaks and "done today". */
    @Column(name = "local_date")
    private LocalDate localDate;

    /** A mock interview's {@link InterviewPlan}; null for other kinds of session. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_json", columnDefinition = "jsonb")
    private String planJson;

    @Column(name = "fluency_score")
    private Integer fluencyScore;

    @Column(name = "grammar_score")
    private Integer grammarScore;

    /** Null when nothing was spoken out loud. */
    @Column(name = "pronunciation_score")
    private Integer pronunciationScore;

    @Column(name = "vocabulary_score")
    private Integer vocabularyScore;

    /** The answer Luna wrapped up after; null while the chat is still going. */
    @Column(name = "final_turn")
    private Integer finalTurn;

    protected PracticeSession() {
    }

    public PracticeSession(UUID userId, SessionType type, String openingLine, Instant startedAt) {
        this.userId = userId;
        this.type = type;
        this.status = SessionStatus.ACTIVE;
        this.openingLine = openingLine;
        this.startedAt = startedAt;
    }

    public static PracticeSession practice(UUID userId, SessionType type, String scenario, String topic,
            String planJson, String openingLine, Instant startedAt) {
        PracticeSession session = new PracticeSession(userId, type, openingLine, startedAt);
        session.scenario = scenario;
        session.topic = topic;
        session.planJson = planJson;
        return session;
    }

    /**
     * A grammar-path topic the learner just finished: recorded complete, with its result as the summary and the topic id
     * as the scenario.
     */
    public static PracticeSession lesson(UUID userId, String topicId, String resultJson, int xp, LocalDate localDate,
            Instant now) {
        PracticeSession session = new PracticeSession(userId, SessionType.LESSON, null, now);
        session.scenario = topicId;
        session.complete(resultJson, now);
        session.xp = Math.max(0, xp);
        session.localDate = localDate;
        return session;
    }

    /** Completes a practice session, crediting its XP to the learner's local day and recording its scores. */
    public void completePractice(String summaryJson, Instant now, int xp, LocalDate localDate,
            PracticeDtos.Scores scores) {
        complete(summaryJson, now);
        this.xp = Math.max(0, xp);
        this.localDate = localDate;
        if (scores != null) {
            this.fluencyScore = scores.fluency();
            this.grammarScore = scores.grammar();
            this.pronunciationScore = scores.pronunciation();
            this.vocabularyScore = scores.vocabulary();
        }
    }

    /** Luna wrapped the chat up after answer {@code turnNumber}. */
    public void wrapUpAt(int turnNumber) {
        if (finalTurn == null) {
            finalTurn = turnNumber;
        }
    }

    public void addSpeakingTime(long durationMs) {
        if (durationMs > 0) {
            speakingMs += (int) Math.min(durationMs, Integer.MAX_VALUE - speakingMs);
        }
    }

    public void complete(String summaryJson, Instant now) {
        this.summaryJson = summaryJson;
        this.status = SessionStatus.COMPLETED;
        this.endedAt = now;
    }

    public void abandon(Instant now) {
        if (status == SessionStatus.ACTIVE) {
            status = SessionStatus.ABANDONED;
            endedAt = now;
        }
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SessionType getType() {
        return type;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public String getOpeningLine() {
        return openingLine;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public int getSpeakingMs() {
        return speakingMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getScenario() {
        return scenario;
    }

    public String getTopic() {
        return topic;
    }

    public int getXp() {
        return xp;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public String getPlanJson() {
        return planJson;
    }

    public Integer getFluencyScore() {
        return fluencyScore;
    }

    public Integer getGrammarScore() {
        return grammarScore;
    }

    public Integer getPronunciationScore() {
        return pronunciationScore;
    }

    public Integer getVocabularyScore() {
        return vocabularyScore;
    }

    public Integer getFinalTurn() {
        return finalTurn;
    }
}
