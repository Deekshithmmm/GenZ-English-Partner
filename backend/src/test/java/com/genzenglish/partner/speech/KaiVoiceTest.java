package com.genzenglish.partner.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.genzenglish.partner.ai.tts.SpeechOutput;
import com.genzenglish.partner.ai.tts.TextToSpeech;
import com.genzenglish.partner.ai.tts.TextToSpeechRequest;
import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.TutorVoice;

class KaiVoiceTest {

    @TempDir
    Path cacheDir;

    private final LearnerProfile profile = new LearnerProfile(UUID.randomUUID(), false);
    private final SpeechClipStore clips = new SpeechClipStore(Clock.systemUTC());
    private final List<TextToSpeechRequest> asked = new CopyOnWriteArrayList<>();

    /** A provider that answers every English request with the same tiny clip. */
    private TextToSpeech provider(boolean bySentence, boolean fails, String cacheTag) {
        return new TextToSpeech() {
            @Override
            public SpeechOutput synthesize(TextToSpeechRequest request) {
                asked.add(request);
                if (fails) {
                    throw new IllegalStateException("down");
                }
                return request.languageTag().startsWith("en")
                        ? SpeechOutput.audio(new byte[] {1, 2, 3}, "audio/mpeg", request.voice(), 1.0)
                        : SpeechOutput.device(request.voice(), request.rate());
            }

            @Override
            public String providerName() {
                return "fake";
            }

            @Override
            public boolean voicesBySentence() {
                return bySentence;
            }

            @Override
            public String cacheTag() {
                return cacheTag;
            }
        };
    }

    private KaiVoice voice(boolean bySentence, boolean fails) {
        return voice(provider(bySentence, fails, null));
    }

    private KaiVoice voice(TextToSpeech tts) {
        return new KaiVoice(tts, clips, new SpeechCache(cacheDir.toString()));
    }

    @Test
    void aSentenceBecomesAClipTheAppFetchesById() {
        SpeechDto dto = voice(true, false).voice("Use am with I.", "en-IN", profile, true);

        assertThat(dto.mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
        assertThat(dto.audioUrl()).startsWith("/api/v1/speech/");
        String id = dto.audioUrl().substring("/api/v1/speech/".length());
        assertThat(clips.get(id)).hasValueSatisfying(clip -> assertThat(clip.audio()).containsExactly(1, 2, 3));
        assertThat(asked.getFirst().early()).isTrue();
        assertThat(asked.getFirst().voice()).isEqualTo(TutorVoice.KAI_WARM);
    }

    @Test
    void withAVoiceThatGoesBySentenceAWholeLineIsLeftForTheAppToVoice() {
        SpeechDto dto = voice(true, false).speak("Nice! Tell me more about your weekend.", profile);

        assertThat(dto.mode()).isEqualTo(SpeechOutput.Mode.DEVICE);
        assertThat(dto.audioUrl()).isNull();
        assertThat(asked).isEmpty();
    }

    @Test
    void withAFastVoiceAWholeLineGetsOneClip() {
        SpeechDto dto = voice(false, false).speak("Nice! Tell me more.", profile);

        assertThat(dto.mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
        assertThat(asked.getFirst().early()).isFalse();
    }

    @Test
    void aFailingVoiceFallsBackToThePhone() {
        SpeechDto dto = voice(true, true).voice("Hello.", "en-IN", profile, false);

        assertThat(dto.mode()).isEqualTo(SpeechOutput.Mode.DEVICE);
        assertThat(dto.languageTag()).isEqualTo("en-IN");
    }

    @Test
    void aLanguageTheVoiceCantSpeakStaysOnThePhone() {
        SpeechDto dto = voice(true, false).voice("ನಮಸ್ತೆ", "kn-IN", profile, false);

        assertThat(dto.mode()).isEqualTo(SpeechOutput.Mode.DEVICE);
        assertThat(dto.languageTag()).isEqualTo("kn-IN");
    }

    @Test
    void theSameSentenceAskedForTwiceAtOnceIsMadeOnce() throws Exception {
        CountDownLatch bothAsked = new CountDownLatch(1);
        TextToSpeech slow = new TextToSpeech() {
            @Override
            public SpeechOutput synthesize(TextToSpeechRequest request) {
                asked.add(request);
                try {
                    bothAsked.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return SpeechOutput.audio(new byte[] {7}, "audio/mpeg", request.voice(), 1.0);
            }

            @Override
            public String providerName() {
                return "slow";
            }

            @Override
            public String cacheTag() {
                return "paid/slow";
            }
        };
        KaiVoice voice = voice(slow);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<SpeechDto> ahead = pool.submit(() -> voice.voice("Ready? Let's begin!", "en-IN", profile, true));
            Thread.sleep(100);
            Future<SpeechDto> now = pool.submit(() -> voice.voice("Ready? Let's begin!", "en-IN", profile, false));
            Thread.sleep(100);
            bothAsked.countDown();
            assertThat(ahead.get().mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
            assertThat(now.get().mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
        }
        assertThat(asked).hasSize(1);
    }

    @Test
    void aPaidVoiceMakesEachSentenceOnceAndReplaysItFromDisk() {
        KaiVoice voice = voice(provider(true, false, "paid/voice-1"));

        SpeechDto first = voice.voice("Use am with I.", "en-IN", profile, false);
        SpeechDto again = voice.voice("Use am with I.", "en-IN", profile, false);

        assertThat(asked).hasSize(1);
        assertThat(again.mode()).isEqualTo(SpeechOutput.Mode.AUDIO);
        assertThat(again.audioUrl()).isNotEqualTo(first.audioUrl());
        String id = again.audioUrl().substring("/api/v1/speech/".length());
        assertThat(clips.get(id)).hasValueSatisfying(clip -> assertThat(clip.audio()).containsExactly(1, 2, 3));

        // Another voice (or another sentence) is a different clip.
        voice(provider(true, false, "paid/voice-2")).voice("Use am with I.", "en-IN", profile, false);
        voice.voice("Use is with he.", "en-IN", profile, false);
        assertThat(asked).hasSize(3);
    }
}
