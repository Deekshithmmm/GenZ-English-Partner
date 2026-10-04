package com.genzenglish.partner.ai.stt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline stand-in that can't actually hear. It returns a believable learner answer for the question being asked,
 * trimmed to roughly what fits in the recording's length, complete with the kind of slips real learners make so the
 * feedback pipeline has something to work on.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.stt", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSpeechToText implements SpeechToText {

    private static final double WORDS_PER_SECOND = 2.0;

    private static final List<Answer> ANSWERS = List.of(
            new Answer(List.of("name", "yourself", "from"),
                    "Hi, my name is Arjun and I am from Mysuru. Currently I am doing my final year engineering in computer science."),
            new Answer(List.of("weekday", "normal day", "routine", "usually"),
                    "Basically I wake up at seven and I go to college by bus. After college I am doing some coding practice and, um, in night I watch one or two episodes."),
            new Answer(List.of("didn't go", "plan", "went wrong", "time when"),
                    "One time I missed my train to Chennai because the auto was very late. So I took a bus, it was so crowded, but I reached on time only."),
            new Answer(List.of("think", "opinion", "agree", "matter"),
                    "I think skills are more important now, because companies are asking for projects. But degree is also needed for the first job, like, for the filtering."),
            // Practice chats: the hint is Luna's last line.
            new Answer(List.of("get started", "order", "menu"),
                    "Can I get one cold coffee and one masala chai? And, um, what is there in the veg sandwich?"),
            new Answer(List.of("project", "résumé"),
                    "Basically we built one app for college attendance. I was handling the backend and we discussed about the database design every week."),
            new Answer(List.of("work with us", "strength", "weakness"),
                    "I am having two years of experience in our coding club, and I want to work in a team where I can learn more faster."),
            new Answer(List.of("more", "next", "else", "example", "happened"),
                    "Yesterday night I went with one of my friend to a new cafe near college and we talked for two hours."),
            new Answer(List.of(),
                    "Actually I am not sure, but I think it depends on the situation. Can you ask me one more time?"));

    @Override
    public Transcript transcribe(SpeechToTextRequest request) {
        if (request.rehearsal() != null && !request.rehearsal().isBlank()) {
            // A drill: sound like a learner who got it right.
            String said = request.rehearsal().strip();
            return new Transcript(said, 0.92, timings(said, request.audio().durationMs()));
        }
        String answer = pick(request.contextHint());
        String[] words = answer.split(" ");
        int wordsThatFit = (int) Math.max(4, Math.round(request.audio().durationMs() / 1000.0 * WORDS_PER_SECOND));
        int count = Math.min(words.length, wordsThatFit);
        String text = String.join(" ", java.util.Arrays.copyOf(words, count));
        if (count < words.length && !text.endsWith(".")) {
            text = text.replaceAll("[,]$", "") + ".";
        }
        return new Transcript(text, 0.9, timings(text, request.audio().durationMs()));
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private static String pick(String hint) {
        String lower = hint == null ? "" : hint.toLowerCase(Locale.ROOT);
        return ANSWERS.stream()
                .filter(a -> a.keywords().stream().anyMatch(lower::contains))
                .findFirst()
                .orElse(ANSWERS.getLast())
                .text();
    }

    private static List<Transcript.Word> timings(String text, long durationMs) {
        String[] words = text.split(" ");
        long slot = Math.max(1, durationMs / Math.max(1, words.length));
        List<Transcript.Word> result = new ArrayList<>(words.length);
        for (int i = 0; i < words.length; i++) {
            result.add(new Transcript.Word(words[i], i * slot, (i + 1) * slot, 0.9));
        }
        return result;
    }

    private record Answer(List<String> keywords, String text) {
    }
}
