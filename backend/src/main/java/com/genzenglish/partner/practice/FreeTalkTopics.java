package com.genzenglish.partner.practice;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** What learners can pick to talk about with Luna when they just want to chat. */
public final class FreeTalkTopics {

    private FreeTalkTopics() {
    }

    /**
     * @param opening Luna's first question, asked after a short greeting
     */
    public record Topic(String slug, String title, String opening) {
    }

    /** No topic: Luna asks what's on the learner's mind. */
    static final Topic OPEN = new Topic("anything", "Anything you like",
            "What's on your mind today? We can talk about anything.");

    static final List<Topic> ALL = List.of(
            OPEN,
            new Topic("college-life", "College life",
                    "What's one thing about your college that people from outside wouldn't believe?"),
            new Topic("movies-and-series", "Movies and series",
                    "What's the last movie or series you'd actually recommend to a friend?"),
            new Topic("food", "Food you love", "What's one dish you could happily eat every single day?"),
            new Topic("cricket-and-sports", "Cricket and sports",
                    "Do you follow cricket, or is there another sport you're really into?"),
            new Topic("career-plans", "Career plans", "Where do you see yourself working in a couple of years?"),
            new Topic("travel", "Travel", "If you could take a trip anywhere next month, where would you go?"),
            new Topic("tech-and-gadgets", "Tech and gadgets",
                    "Which app or gadget could you not live without, and why?"),
            new Topic("festivals", "Festivals", "Which festival do you look forward to the most every year?"),
            new Topic("music", "Music", "What have you been listening to on repeat lately?"),
            new Topic("social-media", "Social media",
                    "How much time do you think you spend on social media every day?"),
            new Topic("weekend-plans", "Weekend plans", "Got any plans for this weekend?"));

    private static final Map<String, Topic> BY_SLUG = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Topic::slug, Function.identity()));

    public static List<Topic> all() {
        return ALL;
    }

    public static Optional<Topic> find(String slug) {
        return slug == null ? Optional.empty() : Optional.ofNullable(BY_SLUG.get(slug));
    }
}
