package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpeechMetricsTest {

    @Test
    void countsHesitationsAndFillerWords() {
        SpeechMetrics.Stats stats = SpeechMetrics.analyze(
                "Um, basically I was, uh, actually thinking umm that it's fine", 6000L);

        assertThat(stats.fillers()).containsEntry("um", 2).containsEntry("uh", 1)
                .containsEntry("basically", 1).containsEntry("actually", 1);
        assertThat(stats.fillerCount()).isEqualTo(5);
    }

    @Test
    void likeIsOnlyAFillerWhenItIsNotDoingGrammaticalWork() {
        assertThat(SpeechMetrics.analyze("I like coding and I would like to travel", null).fillerCount()).isZero();
        assertThat(SpeechMetrics.analyze("It looks like rain", null).fillerCount()).isZero();
        assertThat(SpeechMetrics.analyze("It was, like, so crowded", null).fillers()).containsEntry("like", 1);
    }

    @Test
    void youKnowAndIMeanCountOnlyAsDiscourseMarkers() {
        assertThat(SpeechMetrics.analyze("You know what I did yesterday", null).fillerCount()).isZero();
        assertThat(SpeechMetrics.analyze("It was fun, you know, and I mean, really fun", null).fillers())
                .containsEntry("you know", 1).containsEntry("i mean", 1);
    }

    @Test
    void computesWordsPerMinuteOnlyWithAUsableDuration() {
        assertThat(SpeechMetrics.analyze("one two three four five six seven eight nine ten", 6000L).wordsPerMinute())
                .isEqualTo(100);
        assertThat(SpeechMetrics.analyze("hello there", 800L).wordsPerMinute()).isNull();
        assertThat(SpeechMetrics.analyze("typed answer", null).wordsPerMinute()).isNull();
    }

    @Test
    void handlesBlankInput() {
        SpeechMetrics.Stats stats = SpeechMetrics.analyze("   ", 5000L);
        assertThat(stats.wordCount()).isZero();
        assertThat(stats.wordsPerMinute()).isNull();
        assertThat(stats.fillerCount()).isZero();
    }
}
