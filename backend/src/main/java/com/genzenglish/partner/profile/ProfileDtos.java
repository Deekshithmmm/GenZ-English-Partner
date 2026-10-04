package com.genzenglish.partner.profile;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record PreferencesRequest(
            @NotNull(message = "Pick the language for explanations.") ExplanationLanguage explanationLanguage,
            @NotNull(message = "Pick what you're practising for.") LearningGoal goal,
            @NotNull(message = "Pick how much time you have each day.") Integer dailyMinutes,
            @Size(max = 64) String timeZone) {
    }

    public record LevelRequest(@NotNull(message = "Pick a level.") CefrLevel level) {
    }

    public record LevelInfo(CefrLevel code, String label, String description) {
        public static LevelInfo of(CefrLevel level) {
            return level == null ? null : new LevelInfo(level, level.label(), level.description());
        }
    }

    public record ProfileResponse(
            ExplanationLanguage explanationLanguage,
            LearningGoal goal,
            Integer dailyMinutes,
            LevelInfo level,
            LevelSource levelSource,
            TutorVoice tutorVoice,
            BigDecimal speakingSpeed,
            boolean keepAudio,
            String timeZone) {

        public static ProfileResponse of(LearnerProfile p) {
            return new ProfileResponse(p.getExplanationLanguage(), p.getGoal(), p.getDailyMinutes(),
                    LevelInfo.of(p.getLevel()), p.getLevelSource(), p.getTutorVoice(), p.getSpeakingSpeed(),
                    p.isKeepAudio(), p.getTimeZone());
        }
    }
}
