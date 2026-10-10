package com.genzenglish.partner.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

/** A clip can be read while it's still being made, so Luna starts talking at the first audio. */
class SpeechClipStoreTest {

    private final SpeechClipStore clips = new SpeechClipStore(Clock.systemUTC());

    @Test
    void aReaderGetsTheAudioAsItArrivesAndThenTheClipIsKept() throws Exception {
        SpeechClipStore.LiveClip clip = clips.open("audio/mpeg");
        ByteArrayOutputStream heard = new ByteArrayOutputStream();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> reader = pool.submit(() -> {
                clip.copyTo(heard, Duration.ofSeconds(5));
                return null;
            });
            clip.add(new byte[] {1, 2});
            Thread.sleep(50);
            assertThat(heard.toByteArray()).as("the first audio is passed on before the rest exists")
                    .containsExactly(1, 2);
            clip.add(new byte[] {3});
            clip.finish();
            reader.get();
        }
        assertThat(heard.toByteArray()).containsExactly(1, 2, 3);
        assertThat(clips.live(clip.id())).isEmpty();
        assertThat(clips.get(clip.id())).hasValueSatisfying(kept -> assertThat(kept.audio()).containsExactly(1, 2, 3));
    }

    @Test
    void aClipWhoseVoiceFailsBeforeAnyAudioTellsTheReaderSo() throws Exception {
        SpeechClipStore.LiveClip clip = clips.open("audio/mpeg");

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> started = pool.submit(() -> clip.awaitStart(Duration.ofSeconds(5)));
            Thread.sleep(50);
            clip.fail();
            assertThat(started.get()).isFalse();
        }
        assertThat(clips.get(clip.id())).isEmpty();
        assertThat(clips.live(clip.id())).isEmpty();
    }

    @Test
    void waitingForAudioThatNeverComesEnds() throws Exception {
        SpeechClipStore.LiveClip clip = clips.open("audio/mpeg");

        assertThat(clip.awaitStart(Duration.ofMillis(50))).isFalse();
        ByteArrayOutputStream heard = new ByteArrayOutputStream();
        clip.add(new byte[] {9});
        clip.copyTo(heard, Duration.ofMillis(50));
        assertThat(heard.toByteArray()).as("a stalled clip sends what it has, then stops").containsExactly(9);
    }
}
