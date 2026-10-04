package com.genzenglish.partner.practice;

import java.time.LocalDate;
import java.util.List;

/** The daily warm-up topic. It rotates by date, so today's plan and the warm-up chat agree all day. */
public final class WarmUpTopics {

    private WarmUpTopics() {
    }

    static final List<String> TOPICS = List.of(
            "What's one thing you ate today that you'd happily eat again?",
            "Tell Luna about the last show you couldn't stop watching.",
            "What's a small win from this week?",
            "Describe your room in three sentences.",
            "What would you do with a completely free Saturday?",
            "Which app do you open first in the morning, and why?",
            "Talk about a teacher you still remember.",
            "What's your usual chai or coffee order?",
            "Describe how you got to college or work today.",
            "What's one skill you want to learn this year?",
            "How does your family celebrate your favourite festival?",
            "What's the best advice a friend has given you?",
            "Which song has been stuck in your head lately?",
            "If you could live in any Indian city for a year, which one?",
            "What did you do last weekend?",
            "Describe a place near your home you really like.",
            "What's something you changed your mind about recently?",
            "Talk about a game or sport you enjoy.",
            "What does a perfect day off look like for you?",
            "What's a small habit that makes your day better?");

    /** The topic as the plan shows it, e.g. "Tell Luna about the last show you couldn't stop watching." */
    public static String forDate(LocalDate date) {
        return TOPICS.get(date.getDayOfYear() % TOPICS.size());
    }

    /** The same topic in Luna's own voice ("Tell me about…"), for opening the warm-up chat. */
    static String askedByKai(String topic) {
        return topic.replace("Tell Luna about", "Tell me about");
    }
}
