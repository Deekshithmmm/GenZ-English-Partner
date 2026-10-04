package com.genzenglish.partner.practice;

import java.time.Instant;
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

/**
 * One exchange: what Luna asked ({@code promptText}), what the learner said, and Luna's reply. Transcripts are kept;
 * raw audio is only referenced by {@code audioKey} when the learner opted in to keep recordings.
 */
@Entity
@Table(name = "turn")
public class Turn {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @Column(name = "learner_text", nullable = false, columnDefinition = "text")
    private String learnerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode", nullable = false, length = 8)
    private InputMode inputMode;

    @Column(name = "audio_duration_ms")
    private Integer audioDurationMs;

    @Column(name = "audio_key")
    private String audioKey;

    @Column(name = "reply_text", columnDefinition = "text")
    private String replyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_json", columnDefinition = "jsonb")
    private String feedbackJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores_json", columnDefinition = "jsonb")
    private String scoresJson;

    @Column(name = "words_per_minute")
    private Integer wordsPerMinute;

    @Column(name = "filler_count")
    private Integer fillerCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Turn() {
    }

    public Turn(UUID sessionId, int seq, String promptText, String learnerText, InputMode inputMode,
            Integer audioDurationMs, Instant createdAt) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.promptText = promptText;
        this.learnerText = learnerText;
        this.inputMode = inputMode;
        this.audioDurationMs = audioDurationMs;
        this.createdAt = createdAt;
    }

    public void recordMetrics(SpeechMetrics.Stats stats) {
        this.wordsPerMinute = stats.wordsPerMinute();
        this.fillerCount = stats.fillerCount();
    }

    public void setReply(String replyText) {
        this.replyText = replyText;
    }

    public void setFeedbackJson(String feedbackJson) {
        this.feedbackJson = feedbackJson;
    }

    public void setScoresJson(String scoresJson) {
        this.scoresJson = scoresJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getSeq() {
        return seq;
    }

    public String getPromptText() {
        return promptText;
    }

    public String getLearnerText() {
        return learnerText;
    }

    public InputMode getInputMode() {
        return inputMode;
    }

    public Integer getAudioDurationMs() {
        return audioDurationMs;
    }

    public String getReplyText() {
        return replyText;
    }

    public String getFeedbackJson() {
        return feedbackJson;
    }

    public String getScoresJson() {
        return scoresJson;
    }

    public Integer getWordsPerMinute() {
        return wordsPerMinute;
    }

    public Integer getFillerCount() {
        return fillerCount;
    }
}
