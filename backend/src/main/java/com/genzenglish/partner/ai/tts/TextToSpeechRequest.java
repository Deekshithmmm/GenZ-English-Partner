package com.genzenglish.partner.ai.tts;

import com.genzenglish.partner.profile.TutorVoice;

/**
 * @param rate  1.0 is normal; the learner's speaking-speed setting and the 0.75x replay both pass through here
 * @param early fetched ahead of time, so speech that's about to play may go first
 */
public record TextToSpeechRequest(String text, TutorVoice voice, double rate, String languageTag, boolean early) {
}
