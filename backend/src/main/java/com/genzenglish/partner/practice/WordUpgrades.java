package com.genzenglish.partner.practice;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.genzenglish.partner.practice.TurnFeedback.NewWord;

/**
 * One stronger word for "very tired", "really good" and friends: the easiest vocabulary win for a learner who
 * already gets their point across. Every upgrade drops straight into the learner's sentence ("I was very tired" →
 * "I was exhausted"), so the smoother version stays correct.
 */
final class WordUpgrades {

    private WordUpgrades() {
    }

    private record Upgrade(String word, String meaning) {
    }

    private static final Map<String, Upgrade> UPGRADES = Map.ofEntries(
            Map.entry("tired", new Upgrade("exhausted", "extremely tired")),
            Map.entry("good", new Upgrade("excellent", "extremely good")),
            Map.entry("bad", new Upgrade("terrible", "extremely bad")),
            Map.entry("happy", new Upgrade("delighted", "very pleased and happy")),
            Map.entry("excited", new Upgrade("thrilled", "extremely excited and happy")),
            Map.entry("big", new Upgrade("huge", "extremely big")),
            Map.entry("small", new Upgrade("tiny", "extremely small")),
            Map.entry("hungry", new Upgrade("starving", "extremely hungry (casual)")),
            Map.entry("angry", new Upgrade("furious", "extremely angry")),
            Map.entry("scared", new Upgrade("terrified", "extremely scared")),
            Map.entry("cold", new Upgrade("freezing", "extremely cold")),
            Map.entry("important", new Upgrade("crucial", "extremely important")),
            Map.entry("interesting", new Upgrade("fascinating", "extremely interesting")),
            Map.entry("beautiful", new Upgrade("stunning", "extremely beautiful")),
            Map.entry("funny", new Upgrade("hilarious", "extremely funny")),
            Map.entry("busy", new Upgrade("swamped", "having far too much to do (casual)")),
            Map.entry("crowded", new Upgrade("packed", "completely full of people")),
            Map.entry("tasty", new Upgrade("delicious", "tasting extremely good")),
            Map.entry("surprised", new Upgrade("amazed", "extremely surprised")),
            Map.entry("clean", new Upgrade("spotless", "perfectly clean")));

    /**
     * Not after a negative ("not very good" means "okay", not "not excellent") or before "that" ("so good that I went
     * back" needs the "so").
     */
    private static final Pattern PATTERN = Pattern.compile("(?<!\\bnot )(?<!n't )\\b(?:very|really|so) ("
            + String.join("|", UPGRADES.keySet()) + ")\\b(?! that\\b)", Pattern.CASE_INSENSITIVE);

    /** At most one upgrade, for the first "very …" the learner said. */
    static List<NewWord> find(String learnerText) {
        if (learnerText == null) {
            return List.of();
        }
        Matcher matcher = PATTERN.matcher(learnerText);
        if (!matcher.find()) {
            return List.of();
        }
        Upgrade upgrade = UPGRADES.get(matcher.group(1).toLowerCase(Locale.ROOT));
        return List.of(new NewWord(upgrade.word(), upgrade.meaning(), matcher.group()));
    }
}
