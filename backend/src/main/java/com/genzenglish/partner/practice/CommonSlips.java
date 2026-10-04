package com.genzenglish.partner.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.genzenglish.partner.practice.TurnFeedback.Correction;

/**
 * Rule-based feedback for frequent slips in Indian learners' spoken English, especially patterns that confuse
 * listeners outside India. It powers the offline mock and is the fallback when the model is unavailable, so rules are
 * conservative: a missed slip is fine, a wrong correction is not ("I am having lunch" is correct and isn't flagged).
 */
public final class CommonSlips {

    private CommonSlips() {
    }

    private record Rule(Pattern pattern, Function<Matcher, String> better, Function<Matcher, String> why) {
        static Rule of(String regex, Function<Matcher, String> better, String why) {
            return of(regex, better, m -> why);
        }

        /** For reasons that quote the learner, e.g. their own name. */
        static Rule of(String regex, Function<Matcher, String> better, Function<Matcher, String> why) {
            return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), better, why);
        }
    }

    private static final Map<String, String> BASE_FORMS = Map.ofEntries(
            Map.entry("went", "go"), Map.entry("came", "come"), Map.entry("knew", "know"), Map.entry("saw", "see"),
            Map.entry("took", "take"), Map.entry("got", "get"), Map.entry("gave", "give"), Map.entry("made", "make"),
            Map.entry("said", "say"), Map.entry("told", "tell"), Map.entry("ate", "eat"), Map.entry("bought", "buy"),
            Map.entry("brought", "bring"), Map.entry("thought", "think"), Map.entry("wanted", "want"),
            Map.entry("liked", "like"), Map.entry("reached", "reach"), Map.entry("finished", "finish"),
            Map.entry("started", "start"), Map.entry("completed", "complete"), Map.entry("understood", "understand"));

    private static final Map<String, String> PLURALS = Map.of("friend", "friends", "brother", "brothers",
            "sister", "sisters", "cousin", "cousins", "colleague", "colleagues", "classmate", "classmates",
            "teacher", "teachers", "relative", "relatives");

    /**
     * Words learners often put after "a" that need "an". A list rather than "any vowel": "Plan A is good" and "a
     * university" are both fine, and a wrong correction does more harm than a missed one.
     */
    private static final Set<String> AN_WORDS = Set.of("app", "application", "attendance", "interview",
            "internship", "intern", "engineer", "engineering", "idea", "hour", "honest", "example", "offer",
            "opportunity", "apartment", "account", "office", "answer", "update", "error", "issue", "event", "exam",
            "experience", "important", "amazing", "awesome", "excellent", "easy", "extra", "early", "indian",
            "assignment", "award", "image", "item", "order", "owner", "uncle", "umbrella", "hr", "mba", "mca", "msc",
            "ngo");

    private static final Map<String, String> STATE_VERBS = Map.of("knowing", "know", "understanding", "understand",
            "liking", "like", "wanting", "want");

    private static final String NUMBER_WORDS = "\\d+|one|two|three|four|five|six|seven|eight|nine|ten|a few|few|many";

    // Order matters: specific rules come before general ones, and overlapping matches keep the first.
    private static final List<Rule> RULES = List.of(
            Rule.of("\\b(is|was|are|were) not working since (" + NUMBER_WORDS + ") (days|weeks|months|years)\\b",
                    m -> (m.group(1).equalsIgnoreCase("is") || m.group(1).equalsIgnoreCase("was") ? "hasn't" : "haven't")
                            + " been working for " + m.group(2) + " " + m.group(3),
                    "For something that started in the past and is still true, use \"has been\": \"It hasn't been "
                            + "working for two days.\""),
            Rule.of("\\bsince (" + NUMBER_WORDS + ") (days|weeks|months|years)\\b",
                    m -> "for " + m.group(1) + " " + m.group(2),
                    "Use \"for\" with a length of time and \"since\" with a starting point: for two days, since "
                            + "Monday."),
            Rule.of("\\b(i am|i'm) having (?!a |an |the |good time)(?=(good |some |no |\\d+ |one |two |three |four "
                    + "|five )?(experience|knowledge|years?|doubts?|idea|interest|brothers?|sisters?|friends?|laptop"
                    + "|phone|skills?|patience)\\b)",
                    m -> "I have ",
                    "\"Have\" doesn't take -ing when it means owning or having something: \"I have two years of "
                            + "experience.\""),
            Rule.of("\\b(i am|i'm) (knowing|understanding|liking|wanting)\\b",
                    m -> "I " + STATE_VERBS.get(m.group(2).toLowerCase(Locale.ROOT)),
                    "Verbs like \"know\", \"understand\" and \"want\" don't usually take -ing."),
            // "Myself Arjun" introduces a name; "Myself and my friend went…" is a different (and milder) slip. Only a
            // capitalised word is taken as a name, so "Myself very interested" never suggests "My name is very".
            Rule.of("(?:^|(?<=[.!?]\\s))(myself) (?!and\\b|or\\b|also\\b|too\\b|i\\b)([a-z]+)\\b",
                    m -> "I'm " + m.group(2),
                    m -> Character.isUpperCase(m.group(2).charAt(0))
                            ? "\"Myself\" can't start a sentence. Say \"I'm " + m.group(2) + "\" or \"My name is "
                                    + m.group(2) + "\"."
                            : "\"Myself\" can't start a sentence. Start with \"I'm\" instead."),
            Rule.of("\\bdoing my (final|first|second|third|fourth|last) year\\b",
                    m -> "in my " + m.group(1) + " year",
                    "We say \"I'm in my final year\" of a course, not \"doing my final year\"."),
            Rule.of("\\bdid(n't| not) (went|came|knew|saw|took|got|gave|made|said|told|ate|bought|brought|thought"
                    + "|wanted|liked|reached|finished|started|completed|understood)\\b",
                    m -> "did" + m.group(1) + " " + BASE_FORMS.get(m.group(2).toLowerCase(Locale.ROOT)),
                    "After \"didn't\", use the base verb: \"I didn't know\", not \"I didn't knew\"."),
            Rule.of("\\btoday (morning|evening)\\b",
                    m -> "this " + m.group(1).toLowerCase(Locale.ROOT),
                    "\"This morning\" and \"this evening\" are the natural ways to say these."),
            Rule.of("\\byesterday night\\b",
                    m -> "last night",
                    "We say \"last night\", not \"yesterday night\"."),
            Rule.of("\\bin night\\b",
                    m -> "at night",
                    "We say \"at night\" (but \"in the morning\" and \"in the evening\")."),
            Rule.of("\\b(discuss|discussed|discusses|discussing) about\\b",
                    m -> m.group(1),
                    "\"Discuss\" already means \"talk about\", so it doesn't need \"about\"."),
            Rule.of("\\b(explain|explained|explains) me\\b",
                    m -> m.group(1) + " to me",
                    "We explain something to someone: \"Can you explain it to me?\""),
            Rule.of("\\breturn back\\b",
                    m -> "return",
                    "\"Return\" already means \"go back\"."),
            Rule.of("\\brevert back\\b",
                    m -> "reply",
                    "Outside India, \"revert\" means \"go back to how it was\". Say \"reply\" or \"get back to you\"."),
            Rule.of("\\bcope up with\\b",
                    m -> "cope with",
                    "The phrase is \"cope with\": \"I'm learning to cope with the pressure.\""),
            Rule.of("\\bdo the needful\\b",
                    m -> "sort this out",
                    "\"Do the needful\" sounds old-fashioned to many listeners. Say exactly what you need done."),
            Rule.of("\\bmore (better|faster|easier|bigger|smaller|cheaper|harder|longer)\\b",
                    m -> m.group(1),
                    "\"Better\" and \"faster\" already mean \"more\", so drop \"more\"."),
            Rule.of("\\bone of my (friend|brother|sister|cousin|colleague|classmate|teacher|relative)\\b",
                    m -> "one of my " + PLURALS.get(m.group(1).toLowerCase(Locale.ROOT)),
                    "After \"one of my\", use the plural: \"one of my friends\"."),
            Rule.of("\\bpassed out (in|from)\\b",
                    m -> "graduated " + m.group(1),
                    "Outside India, \"passed out\" means \"fainted\". Say \"graduated\"."),
            Rule.of("\\bout of station\\b",
                    m -> "out of town",
                    "Most listeners say \"out of town\" for being away."),
            Rule.of("\\bvery much fun\\b",
                    m -> "a lot of fun",
                    "We say \"a lot of fun\" or \"really fun\"."),
            Rule.of("\\b(is|was|am|are|were) very much (interested|spicy|happy|excited|tired|nervous|busy|good)\\b",
                    m -> m.group(1) + " really " + m.group(2),
                    "Before a describing word, say \"really\" or \"very\": \"I'm really interested\"."),
            Rule.of("\\b(going|went) good\\b",
                    m -> m.group(1) + " well",
                    "Use \"well\" for how something goes: \"It's going well.\""),
            Rule.of("\\b(on time|for here|here|today|now) only\\b",
                    m -> m.group(1),
                    "\"Only\" at the end for emphasis is very Indian; most listeners expect just \"on time\" or "
                            + "\"right on time\"."),
            Rule.of("\\bwhat is your good name\\b",
                    m -> "what's your name",
                    "\"Good name\" is a translation; in English just ask \"What's your name?\""),
            Rule.of("\\b(a) ([a-z]+)\\b",
                    CommonSlips::articleFix,
                    "Use \"an\" before a vowel sound: \"an app\", \"an hour\", \"an HR round\"."));

    /**
     * Up to two corrections for what the learner said, in the order they said them, plus a stronger word to try and the
     * whole answer with the fixes applied.
     */
    public static TurnFeedback review(String learnerText) {
        if (learnerText == null || learnerText.isBlank()) {
            return TurnFeedback.none();
        }
        List<Correction> corrections = corrections(learnerText);
        List<TurnFeedback.NewWord> newWords = WordUpgrades.find(learnerText);
        String smoother = smoother(learnerText, corrections, newWords);
        return new TurnFeedback(corrections, null, smoother, newWords, List.of(), List.of());
    }

    private static List<Correction> corrections(String learnerText) {
        List<int[]> taken = new ArrayList<>();
        List<Found> found = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(learnerText);
            while (matcher.find()) {
                String better = rule.better().apply(matcher);
                if (better == null || overlaps(taken, matcher.start(), matcher.end())) {
                    continue;
                }
                String original = matcher.group();
                taken.add(new int[] {matcher.start(), matcher.end()});
                found.add(new Found(matcher.start(), new Correction(original.strip(),
                        matchCase(original, better).strip(), rule.why().apply(matcher))));
            }
        }
        return found.stream()
                .sorted((a, b) -> Integer.compare(a.position(), b.position()))
                .map(Found::correction)
                .limit(TurnFeedback.MAX_CORRECTIONS)
                .toList();
    }

    private static final Pattern HESITATION = Pattern.compile("\\s*,?\\s*\\b(?:u+m+|u+h+|uhm|erm?|h+m+)\\b\\s*,?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_BASICALLY = Pattern.compile("(^|[.!?]\\s+)basically,?\\s+(\\p{L})",
            Pattern.CASE_INSENSITIVE);

    /**
     * The learner's answer with the fixes and word upgrades applied and hesitations dropped, or null when that changes
     * nothing. It stays the learner's own answer, so it's something they can say out loud right away.
     */
    static String smoother(String learnerText, List<Correction> corrections, List<TurnFeedback.NewWord> newWords) {
        String text = learnerText.strip();
        for (Correction correction : corrections) {
            text = replaceFirstIgnoringCase(text, correction.original(), correction.better());
        }
        for (TurnFeedback.NewWord word : newWords) {
            if (word.instead() != null) {
                text = replaceFirstIgnoringCase(text, word.instead(), word.word());
            }
        }
        text = HESITATION.matcher(text).replaceAll(" ");
        text = LEADING_BASICALLY.matcher(text).replaceAll(m -> Matcher.quoteReplacement(m.group(1)
                + m.group(2).toUpperCase(Locale.ROOT)));
        text = text.replaceAll("\\s+", " ").replaceAll(" ([,.!?])", "$1").replaceAll("^[,\\s]+", "").strip();
        if (!text.isEmpty()) {
            text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
        return text.isEmpty() || TurnFeedback.normalize(text).equals(TurnFeedback.normalize(learnerText)) ? null
                : text;
    }

    private static String replaceFirstIgnoringCase(String text, String target, String replacement) {
        int at = text.toLowerCase(Locale.ROOT).indexOf(target.toLowerCase(Locale.ROOT));
        return at < 0 ? text : text.substring(0, at) + replacement + text.substring(at + target.length());
    }

    private record Found(int position, Correction correction) {
    }

    private static String articleFix(Matcher m) {
        return AN_WORDS.contains(m.group(2).toLowerCase(Locale.ROOT)) ? "an " + m.group(2) : null;
    }

    private static boolean overlaps(List<int[]> taken, int start, int end) {
        return taken.stream().anyMatch(range -> start < range[1] && end > range[0]);
    }

    /** Keeps a capital at the start of a sentence, so "Today morning" becomes "This morning". */
    private static String matchCase(String original, String better) {
        if (!original.isEmpty() && Character.isUpperCase(original.charAt(0)) && !better.isEmpty()) {
            return Character.toUpperCase(better.charAt(0)) + better.substring(1);
        }
        return better;
    }
}
