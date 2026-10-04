package com.genzenglish.partner.ai.pronunciation;

/**
 * Scores how a learner said a known sentence, word by word and phoneme by phoneme.
 * Selected by {@code app.ai.pronunciation.provider}.
 */
public interface PronunciationScorer {

    PronunciationResult score(PronunciationRequest request);

    String providerName();
}
