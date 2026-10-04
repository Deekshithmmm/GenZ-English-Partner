package com.genzenglish.partner.grammar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import com.genzenglish.partner.grammar.GrammarCurriculum.Ask;
import com.genzenglish.partner.grammar.GrammarCurriculum.Drill;
import com.genzenglish.partner.grammar.GrammarCurriculum.Kind;
import com.genzenglish.partner.grammar.GrammarCurriculum.Node;
import com.genzenglish.partner.grammar.GrammarCurriculum.Question;
import com.genzenglish.partner.grammar.GrammarCurriculum.Topic;
import com.genzenglish.partner.grammar.GrammarDtos.Exercise;
import com.genzenglish.partner.grammar.GrammarDtos.ExerciseType;
import com.genzenglish.partner.grammar.GrammarDtos.Phrases;
import com.genzenglish.partner.grammar.GrammarDtos.SpeakItem;
import com.genzenglish.partner.grammar.GrammarDtos.SpeakType;
import com.genzenglish.partner.grammar.GrammarDtos.TutorLine;
import com.genzenglish.partner.grammar.LessonPhrases.Phrase;
import com.genzenglish.partner.profile.ExplanationLanguage;

/**
 * Turns a topic into one of its two lessons. The result is the same every time for the same lesson (tile order
 * included), so the server can mark answers against exactly what the learner saw.
 * <ul>
 * <li>LEARN: Luna introduces the rule with its cheat sheet, then a quiz, then two "fix the sentence" tile puzzles, then
 * a little speaking: two slips to say the right way and one question.</li>
 * <li>BOOST (fluency booster): mostly speaking. Every slip to say right, every question, then the longer task.</li>
 * <li>REVIEW (round 2, a couple of weeks later): a quick reminder, the round's quiz, two tile puzzles, then its other
 * slips and questions out loud.</li>
 * <li>MASTER (round 3): a reminder, the round's quiz and one puzzle, then more speaking: its slips, its questions and
 * a longer task.</li>
 * </ul>
 */
final class GrammarLessons {

    static final int BUILD_EXERCISES = 2;
    static final int MASTER_BUILD_EXERCISES = 1;
    static final int LEARN_FIXES = 2;
    static final int BOOST_FIXES = 4;
    static final int MAX_DISTRACTORS = 2;

    private GrammarLessons() {
    }

    /** A speaking item plus which of the topic's questions it is (-1 when it isn't one). */
    record Planned(SpeakItem item, int question) {
    }

    record Plan(List<TutorLine> intro, List<Exercise> exercises, List<Planned> speaking, Phrases phrases) {

        List<SpeakItem> speakItems() {
            return speaking.stream().map(Planned::item).toList();
        }
    }

    static Plan plan(Node node, ExplanationLanguage language) {
        Topic topic = node.topic();
        List<Drill> fixes = topic.fixes();
        List<TutorLine> intro = new ArrayList<>();
        List<Exercise> exercises = new ArrayList<>();
        List<Planned> speaking = new ArrayList<>();

        if (node.kind() == Kind.LEARN) {
            intro.add(line(Phrase.LEARN_HELLO, language, topic.title()));
            intro.add(new TutorLine(topic.summary(), null));
            intro.add(new TutorLine("Why it matters: " + topic.why(), null));
            intro.add(line(Phrase.READY, language, topic.title()));
            for (Question q : topic.quiz()) {
                exercises.add(new Exercise(ExerciseType.CHOICE, "Choose the right answer", q.prompt(), q.options(),
                        q.answer(), null, null, q.explain()));
            }
            for (int i = 0; i < Math.min(BUILD_EXERCISES, fixes.size()); i++) {
                exercises.add(build(node.id(), i, fixes.get(i)));
            }
            // Speak the slips the puzzles didn't use, if there are any left.
            List<Drill> toSay = fixes.size() >= BUILD_EXERCISES + LEARN_FIXES
                    ? fixes.subList(BUILD_EXERCISES, BUILD_EXERCISES + LEARN_FIXES)
                    : fixes.subList(0, Math.min(LEARN_FIXES, fixes.size()));
            toSay.forEach(d -> speaking.add(fix(d)));
            speaking.add(answer(topic, 0));
        } else if (node.kind() == Kind.BOOST) {
            intro.add(line(Phrase.BOOST_HELLO, language, topic.title()));
            intro.add(new TutorLine("Remember: " + topic.summary(), null));
            intro.add(line(Phrase.PRACTICE_HELLO, language, topic.title()));
            fixes.stream().limit(BOOST_FIXES).forEach(d -> speaking.add(fix(d)));
            for (int i = 0; i < topic.questions().size(); i++) {
                speaking.add(answer(topic, i));
            }
            speaking.add(open(topic));
        } else {
            // A later round: the topic's content here is that round's (see GrammarCurriculum.Topic#round).
            boolean master = node.kind() == Kind.MASTER;
            intro.add(line(master ? Phrase.MASTER_HELLO : Phrase.REVIEW_HELLO, language, topic.title()));
            intro.add(new TutorLine("Remember: " + topic.summary(), null));
            intro.add(line(Phrase.READY, language, topic.title()));
            for (Question q : topic.quiz()) {
                exercises.add(new Exercise(ExerciseType.CHOICE, "Choose the right answer", q.prompt(), q.options(),
                        q.answer(), null, null, q.explain()));
            }
            int builds = Math.min(master ? MASTER_BUILD_EXERCISES : BUILD_EXERCISES, fixes.size());
            for (int i = 0; i < builds; i++) {
                exercises.add(build(node.id(), i, fixes.get(i)));
            }
            fixes.subList(builds, fixes.size()).forEach(d -> speaking.add(fix(d)));
            for (int i = 0; i < topic.questions().size(); i++) {
                speaking.add(answer(topic, i));
            }
            if (master) {
                speaking.add(open(topic));
            }
        }
        Phrases phrases = new Phrases(line(Phrase.PRACTICE_HELLO, language, topic.title()),
                line(Phrase.RIGHT, language, topic.title()), line(Phrase.ALMOST, language, topic.title()),
                line(Phrase.DONE, language, topic.title()));
        return new Plan(List.copyOf(intro), List.copyOf(exercises), List.copyOf(speaking), phrases);
    }

    private static TutorLine line(Phrase phrase, ExplanationLanguage language, String title) {
        LessonPhrases.Line line = LessonPhrases.line(phrase, language, title);
        return new TutorLine(line.text(), line.nativeText());
    }

    private static Planned fix(Drill drill) {
        return new Planned(new SpeakItem(SpeakType.FIX, "Say it the right way", drill.wrong(), drill.right(),
                drill.tip()), -1);
    }

    private static Planned answer(Topic topic, int index) {
        Ask ask = topic.questions().get(index);
        return new Planned(new SpeakItem(SpeakType.ANSWER, "Answer out loud", ask.ask(), ask.sample(),
                topic.summary()), index);
    }

    private static Planned open(Topic topic) {
        return new Planned(new SpeakItem(SpeakType.OPEN, "Talk about it", topic.speak().prompt(), topic.speak().sample(),
                topic.summary()), -1);
    }

    /**
     * A "fix the sentence" tile puzzle: the slip is the prompt, the tiles are the right sentence's words plus a word or
     * two from the slip that the right version doesn't use, shuffled the same way every time for this lesson.
     */
    static Exercise build(String lessonId, int index, Drill drill) {
        List<String> words = tiles(drill.right());
        Set<String> right = new LinkedHashSet<>();
        words.forEach(w -> right.add(w.toLowerCase(Locale.ROOT)));
        List<String> distractors = tiles(drill.wrong()).stream()
                .filter(w -> !right.contains(w.toLowerCase(Locale.ROOT)))
                .distinct()
                .limit(MAX_DISTRACTORS)
                .toList();
        List<String> tiles = new ArrayList<>(words);
        tiles.addAll(distractors);
        Collections.shuffle(tiles, new Random((lessonId + "#" + index).hashCode()));
        return new Exercise(ExerciseType.BUILD, "Fix the sentence: tap the words in the right order", drill.wrong(),
                null, null, List.copyOf(tiles), List.copyOf(words), drill.tip());
    }

    /** A sentence as tiles: its words with the end punctuation dropped (commas stay off too). */
    static List<String> tiles(String sentence) {
        List<String> words = new ArrayList<>();
        for (String raw : sentence.strip().split("\\s+")) {
            String word = raw.replaceAll("^[\"“(]+|[\"”),.!?;:]+$", "");
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /** Whether tiles put in this order make the sentence (ignoring case). */
    static boolean built(List<String> answer, List<String> words) {
        if (answer == null || answer.size() != words.size()) {
            return false;
        }
        for (int i = 0; i < words.size(); i++) {
            if (answer.get(i) == null || !answer.get(i).equalsIgnoreCase(words.get(i))) {
                return false;
            }
        }
        return true;
    }
}
