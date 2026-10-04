package com.genzenglish.partner.ai.tts;

import com.genzenglish.partner.profile.TutorVoice;

/**
 * Either synthesized audio, or an instruction for the app to speak the text with the phone's own voice
 * ({@link Mode#DEVICE}). Device speech keeps the app talking when no TTS provider is configured.
 */
public record SpeechOutput(Mode mode, byte[] audio, String contentType, TutorVoice voice, double rate) {

    public enum Mode {
        AUDIO,
        DEVICE
    }

    public static SpeechOutput device(TutorVoice voice, double rate) {
        return new SpeechOutput(Mode.DEVICE, null, null, voice, rate);
    }

    public static SpeechOutput audio(byte[] audio, String contentType, TutorVoice voice, double rate) {
        return new SpeechOutput(Mode.AUDIO, audio, contentType, voice, rate);
    }
}
