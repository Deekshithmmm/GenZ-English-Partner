package com.genzenglish.partner.ai.tts;

import com.genzenglish.partner.profile.TutorVoice;

/**
 * @param rate  1.0 is normal; the learner's speaking-speed setting and the 0.75x replay both pass through here
 * @param early fetched ahead of time, so speech that's about to play may go first
 * @param live  a whole line Luna says in a conversation, with the learner waiting: the provider may use its fastest
 *              model
 */
public record TextToSpeechRequest(String text, TutorVoice voice, double rate, String languageTag, boolean early,
        boolean live) {

    public TextToSpeechRequest(String text, TutorVoice voice, double rate, String languageTag, boolean early) {
        this(text, voice, rate, languageTag, early, false);
    }
}
