package com.genzenglish.partner.ai.stt;

import com.genzenglish.partner.ai.AudioClip;

/**
 * @param languageTag BCP 47 tag, {@code en-IN} for learner speech
 * @param contextHint what the learner is probably answering (e.g. Luna's last question); improves accuracy for
 *                    providers that accept a prompt, and lets the mock pick a plausible answer
 * @param rehearsal   in a drill, the sentence the learner is practising. Only the mock uses it (to sound like a good
 *                    try); real providers must ignore it, so the transcript is what was actually said and slips stay
 *                    visible
 */
public record SpeechToTextRequest(AudioClip audio, String languageTag, String contextHint, String rehearsal) {

    public static SpeechToTextRequest indianEnglish(AudioClip audio, String contextHint) {
        return new SpeechToTextRequest(audio, "en-IN", contextHint, null);
    }

    /** A drill answer: the learner is trying to say {@code rehearsal} in response to {@code contextHint}. */
    public static SpeechToTextRequest rehearsing(AudioClip audio, String contextHint, String rehearsal) {
        return new SpeechToTextRequest(audio, "en-IN", contextHint, rehearsal);
    }
}
