package com.genzenglish.partner.practice;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.AudioClip;
import com.genzenglish.partner.profile.CefrLevel;
import com.genzenglish.partner.profile.ExplanationLanguage;

/**
 * Luna's notes on a single answer outside a chat (a lesson's speaking task, for example): the same corrections, native
 * explanations, smoother version and sound check that chat answers get, worked out in parallel.
 */
@Component
public class AnswerReviewer {

    private final TurnCoach coach;
    private final SoundCheck soundCheck;

    AnswerReviewer(TurnCoach coach, SoundCheck soundCheck) {
        this.coach = coach;
        this.soundCheck = soundCheck;
    }

    /**
     * @param question what the learner was answering
     * @param audio    the recording, or null for a typed answer
     */
    public TurnFeedback review(String question, String answer, AudioClip audio, CefrLevel level,
            ExplanationLanguage language) {
        SpeechMetrics.Stats stats = SpeechMetrics.analyze(answer, audio == null ? null : audio.durationMs());
        try (ExecutorService parallel = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<TurnFeedback> notes = parallel.submit(() -> coach.review(question, answer, level, language));
            Future<SoundCheck.Result> sounds = parallel.submit(() -> soundCheck.check(audio, answer));
            TurnFeedback feedback = get(notes, null);
            if (feedback == null) {
                ExplanationLanguage explainIn = language == null ? ExplanationLanguage.ENGLISH : language;
                feedback = CommonSlips.review(answer).withNativeFallback(explainIn);
            }
            SoundCheck.Result sound = get(sounds, SoundCheck.Result.none());
            return feedback.withSpeech(PracticeService.fillerWords(stats.fillers()), sound.slips());
        }
    }

    private static <T> T get(Future<T> task, T fallback) {
        try {
            return task.get();
        } catch (ExecutionException e) {
            return fallback;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        }
    }
}
