package com.genzenglish.partner.profile;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "learner_profile")
public class LearnerProfile {

    static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "explanation_language", nullable = false, length = 16)
    private ExplanationLanguage explanationLanguage = ExplanationLanguage.ENGLISH;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private LearningGoal goal;

    @Column(name = "daily_minutes")
    private Integer dailyMinutes;

    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private CefrLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_source", length = 16)
    private LevelSource levelSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "tutor_voice", nullable = false, length = 16)
    private TutorVoice tutorVoice = TutorVoice.KAI_WARM;

    @Column(name = "speaking_speed", nullable = false, precision = 3, scale = 2)
    private BigDecimal speakingSpeed = BigDecimal.ONE;

    @Column(name = "keep_audio", nullable = false)
    private boolean keepAudio;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = DEFAULT_TIME_ZONE;

    @Column(name = "preferences_set_at")
    private Instant preferencesSetAt;

    @Column(name = "level_set_at")
    private Instant levelSetAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Marks the entity as new until first persisted, so save() inserts instead of merging an assigned id.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected LearnerProfile() {
    }

    public LearnerProfile(UUID userId, boolean keepAudio) {
        this.userId = userId;
        this.keepAudio = keepAudio;
    }

    public void setPreferences(ExplanationLanguage language, LearningGoal goal, int dailyMinutes, String timeZone,
            Instant now) {
        this.explanationLanguage = language;
        this.goal = goal;
        this.dailyMinutes = dailyMinutes;
        if (timeZone != null && !timeZone.isBlank()) {
            this.timeZone = timeZone;
        }
        this.preferencesSetAt = now;
    }

    public void setLevel(CefrLevel level, LevelSource source, Instant now) {
        this.level = level;
        this.levelSource = source;
        this.levelSetAt = now;
    }

    public boolean hasPreferences() {
        return preferencesSetAt != null;
    }

    public boolean hasLevel() {
        return level != null;
    }

    public UUID getUserId() {
        return userId;
    }

    public ExplanationLanguage getExplanationLanguage() {
        return explanationLanguage;
    }

    public LearningGoal getGoal() {
        return goal;
    }

    public Integer getDailyMinutes() {
        return dailyMinutes;
    }

    public CefrLevel getLevel() {
        return level;
    }

    public LevelSource getLevelSource() {
        return levelSource;
    }

    public TutorVoice getTutorVoice() {
        return tutorVoice;
    }

    public BigDecimal getSpeakingSpeed() {
        return speakingSpeed;
    }

    public boolean isKeepAudio() {
        return keepAudio;
    }

    public String getTimeZone() {
        return timeZone;
    }

    /** The learner's zone, for "today" and streaks; falls back to India if the stored value is unusable. */
    public ZoneId zone() {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            return ZoneId.of(DEFAULT_TIME_ZONE);
        }
    }
}
