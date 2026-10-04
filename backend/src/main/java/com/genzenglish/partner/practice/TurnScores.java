package com.genzenglish.partner.practice;

/**
 * Scores measured for one answer, stored as the turn's {@code scores_json}.
 *
 * @param pronunciation 0..100 from the pronunciation scorer, or null for typed answers
 */
record TurnScores(Integer pronunciation) {
}
