package com.genzenglish.partner.placement;

import java.util.List;

/**
 * The two-minute placement chat: four questions that climb from A1 to C1 territory, about 30 seconds each.
 */
final class PlacementScript {

    private PlacementScript() {
    }

    static final List<String> QUESTIONS = List.of(
            "Hi, I'm Luna, your AI speaking partner. There are no wrong answers here, so just talk. "
                    + "What's your name, and where are you from?",
            "What does a normal weekday look like for you?",
            "Tell me about a time something didn't go as planned. What happened?",
            "Some people say skills matter more than degrees now. What do you think, and why?");

    static final String CLOSING = "That's everything I need. Give me a moment to work out your level.";

    static final int TOTAL = QUESTIONS.size();

    static final int MIN_ANSWERS_TO_ASSESS = 2;

    /** @param questionNumber 1-based */
    static String question(int questionNumber) {
        return QUESTIONS.get(questionNumber - 1);
    }
}
