package com.genzenglish.partner.speech;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.genzenglish.partner.ai.tts.SpeechOutput;
import com.genzenglish.partner.ai.tts.TextToSpeech;
import com.genzenglish.partner.ai.tts.TextToSpeechRequest;
import com.genzenglish.partner.profile.LearnerProfile;

/**
 * Voices Luna's lines in the learner's chosen voice and speed. If the TTS provider fails, the app falls back to
 * device speech rather than leaving Luna silent.
 */
@Service
public class KaiVoice {

    static final String LANGUAGE_TAG = "en-IN";
    private static final Logger log = LoggerFactory.getLogger(KaiVoice.class);

    private final TextToSpeech tts;
    private final SpeechClipStore clips;
    private final SpeechCache cache;
    /** Paid clips being made right now, by cache key. */
    private final ConcurrentHashMap<String, CompletableFuture<SpeechOutput>> making = new ConcurrentHashMap<>();

    public KaiVoice(TextToSpeech tts, SpeechClipStore clips, SpeechCache cache) {
        this.tts = tts;
        this.clips = clips;
        this.cache = cache;
    }

    /**
     * How the app should voice a whole line from Luna. A voice with a live model makes the whole line now (about half
     * a second), so the line arrives ready to play. Otherwise, with a provider that voices by sentence, the app speaks
     * the text itself, asking {@link #voice} for each sentence as it goes, so the reply isn't held up making audio.
     */
    public SpeechDto speak(String text, LearnerProfile profile) {
        if (tts.makesLiveLines()) {
            return live(text, profile);
        }
        if (tts.voicesBySentence()) {
            return device(profile, LANGUAGE_TAG);
        }
        return voice(text, LANGUAGE_TAG, profile, false);
    }

    /**
     * A whole conversation line from the live model, streamed: the line goes out at once with a clip that fills as the
     * audio is made, and the app starts playing at the first audio (about a third of a second in). Lines aren't kept
     * on disk: each is said once. If the voice fails, fetching the clip fails and the app says the line sentence by
     * sentence instead.
     */
    private SpeechDto live(String text, LearnerProfile profile) {
        TextToSpeechRequest request = new TextToSpeechRequest(text, profile.getTutorVoice(),
                profile.getSpeakingSpeed().doubleValue(), LANGUAGE_TAG, false, true);
        SpeechClipStore.LiveClip clip = clips.open("audio/mpeg");
        Thread.ofVirtual().name("live-line").start(() -> {
            try {
                tts.stream(request, clip::add);
                clip.finish();
            } catch (RuntimeException e) {
                log.warn("Live line TTS failed ({}): {}; the app says it sentence by sentence", tts.providerName(),
                        e.toString());
                clip.fail();
            }
        });
        // The speed is in the audio already, so the app plays it at normal speed.
        return new SpeechDto(SpeechOutput.Mode.AUDIO, "/api/v1/speech/live/" + clip.id(), profile.getTutorVoice(), 1.0,
                LANGUAGE_TAG);
    }

    /**
     * One piece of speech (for the app, a sentence): a clip to play, or device speech when there's no voice for it.
     * A paid voice's clips are kept, so the same sentence in the same voice is only ever made once.
     */
    public SpeechDto voice(String text, String languageTag, LearnerProfile profile, boolean early) {
        double rate = profile.getSpeakingSpeed().doubleValue();
        String tag = tts.cacheTag();
        String key = tag == null ? null : SpeechCache.key(tag, profile.getTutorVoice(), rate, languageTag, text);
        if (key != null) {
            byte[] kept = cache.get(key).orElse(null);
            if (kept != null) {
                return clip(kept, "audio/mpeg", profile, languageTag);
            }
        }
        TextToSpeechRequest request = new TextToSpeechRequest(text, profile.getTutorVoice(), rate, languageTag, early);
        SpeechOutput output;
        try {
            output = key == null ? tts.synthesize(request) : makeOnce(key, request);
        } catch (RuntimeException e) {
            log.warn("TTS failed ({}): {}; falling back to device speech", tts.providerName(), e.toString());
            return device(profile, languageTag);
        }
        if (output.mode() == SpeechOutput.Mode.AUDIO) {
            if (key != null && "audio/mpeg".equals(output.contentType())) {
                cache.put(key, output.audio());
            }
            return clip(output.audio(), output.contentType(), profile, languageTag);
        }
        return new SpeechDto(SpeechOutput.Mode.DEVICE, null, output.voice(), output.rate(), languageTag);
    }

    /**
     * A paid clip asked for twice at once (fetched ahead, then wanted now) is made once and shared, so it's paid for
     * once.
     */
    private SpeechOutput makeOnce(String key, TextToSpeechRequest request) {
        CompletableFuture<SpeechOutput> mine = new CompletableFuture<>();
        CompletableFuture<SpeechOutput> running = making.putIfAbsent(key, mine);
        if (running != null) {
            try {
                return running.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException cause ? cause : e;
            }
        }
        try {
            SpeechOutput output = tts.synthesize(request);
            mine.complete(output);
            return output;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            making.remove(key, mine);
        }
    }

    /** The languages Luna's voice speaks (primary subtags, or "*" for any); others are left to the phone. */
    public List<String> languages() {
        return tts.languages();
    }

    private SpeechDto clip(byte[] audio, String contentType, LearnerProfile profile, String languageTag) {
        String id = clips.put(audio, contentType);
        // The speed is in the audio already, so the app plays it at normal speed.
        return new SpeechDto(SpeechOutput.Mode.AUDIO, "/api/v1/speech/" + id, profile.getTutorVoice(), 1.0, languageTag);
    }

    private static SpeechDto device(LearnerProfile profile, String languageTag) {
        return new SpeechDto(SpeechOutput.Mode.DEVICE, null, profile.getTutorVoice(),
                profile.getSpeakingSpeed().doubleValue(), languageTag);
    }
}
