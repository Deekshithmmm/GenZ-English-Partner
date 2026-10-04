package com.genzenglish.partner.placement;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.genzenglish.partner.path.LearningPath;
import com.genzenglish.partner.profile.ProfileDtos.LevelInfo;
import com.genzenglish.partner.speech.KaiLine;

public final class PlacementDtos {

    private PlacementDtos() {
    }

    public record StartResponse(UUID sessionId, int questionNumber, int totalQuestions, KaiLine kai) {
    }

    public record TextAnswerRequest(
            @NotNull @Min(1) @Max(4) Integer questionNumber,
            @NotBlank(message = "Type your answer first.") @Size(max = 1500, message = "Keep your answer under 1,500 characters.") String text) {
    }

    /**
     * @param nextQuestionNumber null once the last question is answered
     */
    public record AnswerResponse(
            int questionNumber,
            String transcript,
            Integer wordsPerMinute,
            int fillerCount,
            boolean done,
            Integer nextQuestionNumber,
            KaiLine kai) {
    }

    public record Scores(int fluency, int grammar, int vocabulary) {
    }

    public record ResultResponse(
            LevelInfo level,
            Scores scores,
            List<String> strengths,
            List<String> focusAreas,
            String summary,
            LearningPath path) {
    }
}
