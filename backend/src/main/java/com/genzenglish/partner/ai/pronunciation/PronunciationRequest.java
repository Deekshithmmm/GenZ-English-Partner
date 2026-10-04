package com.genzenglish.partner.ai.pronunciation;

import com.genzenglish.partner.ai.AudioClip;

/**
 * @param referenceText what the learner was asked to say
 * @param languageTag   accent the scores are judged against; {@code en-IN} so Indian English isn't marked wrong
 */
public record PronunciationRequest(AudioClip audio, String referenceText, String languageTag) {
}
