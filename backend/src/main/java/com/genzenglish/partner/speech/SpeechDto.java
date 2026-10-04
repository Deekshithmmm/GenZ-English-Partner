package com.genzenglish.partner.speech;

import com.genzenglish.partner.ai.tts.SpeechOutput;
import com.genzenglish.partner.profile.TutorVoice;

/**
 * How the app should voice a line from Luna: play {@code audioUrl}, or speak the text on the device.
 */
public record SpeechDto(SpeechOutput.Mode mode, String audioUrl, TutorVoice voice, double rate, String languageTag) {
}
