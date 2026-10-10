package com.genzenglish.partner.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.practice.TurnFeedback;
import com.genzenglish.partner.profile.CefrLevel;

import tools.jackson.databind.json.JsonMapper;

/**
 * The grammar path. The content ({@code grammar/curriculum.json}) is units of topics, each with a rule, examples,
 * a cheat-sheet chart, a quiz, fix-it drills, questions to answer out loud, and fresh practice for when the topic comes
 * back. The path is one new topic a week, taken lesson by lesson: <em>Learn</em> (the rule, then exercises) and a
 * <em>Fluency booster</em> (speaking practice). Each topic then returns twice, spaced out so it sticks:
 * <em>Revise</em> two weeks later and <em>Master</em> five weeks later, each with its own new questions. The content is
 * checked when the app starts, so a typo fails fast instead of reaching a learner.
 */
@Component
public class GrammarCurriculum {

    static final String RESOURCE = "grammar/curriculum.json";
    /** How many weeks after a topic is learnt it comes back to revise, and then to master. */
    static final int REVIEW_AFTER_WEEKS = 2;
    static final int MASTER_AFTER_WEEKS = 5;

    public record Example(String wrong, String right, String note) {
    }

    /** One line of a topic's cheat sheet: "at" → "7 pm", "night". */
    public record ChartRow(String label, List<String> items) {
    }

    /** @param answer the index of the right option */
    public record Question(String prompt, List<String> options, int answer, String explain) {
    }

    /** A sentence with a slip to say correctly, and why. */
    public record Drill(String wrong, String right, String tip) {
    }

    /**
     * A question to answer out loud using the topic.
     *
     * @param cues what a good answer contains; the topic's speaking cues when empty
     */
    public record Ask(String ask, String sample, List<String> cues) {

        public Ask {
            cues = cues == null ? List.of() : List.copyOf(cues);
        }
    }

    /**
     * The topic's longer speaking task. The rule counts as used when every cue appears in what the learner said and no
     * avoid pattern does; both are regexes over the lower-cased words without punctuation.
     */
    public record SpeakTask(String prompt, String sample, List<String> cues, List<String> avoid) {

        public SpeakTask {
            cues = cues == null ? List.of() : List.copyOf(cues);
            avoid = avoid == null ? List.of() : List.copyOf(avoid);
        }
    }

    /**
     * Fresh practice for when a topic comes back on the path: new questions, slips and things to say about the same
     * rule. The speaking task's slips to avoid add to the topic's own.
     */
    public record Round(List<Question> quiz, List<Drill> drills, List<Ask> questions, SpeakTask speak) {

        public Round {
            quiz = quiz == null ? List.of() : List.copyOf(quiz);
            drills = drills == null ? List.of() : List.copyOf(drills);
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    /**
     * @param icon   a key from the app's 3D icon set
     * @param rounds practice for the second and third time the topic comes up (revise, then master)
     */
    public record Topic(String id, String title, String icon, String summary, String why, List<String> rules,
            List<Example> examples, List<ChartRow> chart, List<Question> quiz, List<Drill> drills,
            List<Ask> questions, SpeakTask speak, List<Round> rounds) {

        public Topic {
            chart = chart == null ? List.of() : List.copyOf(chart);
            drills = drills == null ? List.of() : List.copyOf(drills);
            questions = questions == null ? List.of() : List.copyOf(questions);
            rounds = rounds == null ? List.of() : List.copyOf(rounds);
        }

        /** Every wrong-and-right pair: the examples that have a slip, then the drills. */
        public List<Drill> fixes() {
            List<Drill> fixes = new ArrayList<>();
            for (Example example : examples) {
                if (example.wrong() != null && !example.wrong().isBlank()) {
                    fixes.add(new Drill(example.wrong(), example.right(), example.note() != null ? example.note()
                            : summary));
                }
            }
            fixes.addAll(drills);
            return fixes;
        }

        /**
         * The topic as practised the {@code number}th time (2 or 3): the same rule, examples and cheat sheet, with that
         * round's exercises. The examples keep only their right versions, so the round's own slips are the ones
         * practised.
         */
        Topic round(int number) {
            Round round = rounds.get(number - 2);
            List<Example> shown = examples.stream().map(e -> new Example(null, e.right(), e.note())).toList();
            List<String> avoid = new ArrayList<>(speak.avoid());
            avoid.addAll(round.speak().avoid());
            SpeakTask task = new SpeakTask(round.speak().prompt(), round.speak().sample(), round.speak().cues(), avoid);
            return new Topic(id + "#" + number, title, icon, summary, why, rules, shown, chart, round.quiz(),
                    round.drills(), round.questions(), task, List.of());
        }
    }

    /** @param level the learner level this unit starts at */
    public record Unit(String id, String title, String subtitle, CefrLevel level, String icon, List<Topic> topics) {
    }

    record Content(List<Unit> units) {
    }

    /** A topic with where it sits in the content. */
    public record Placed(Topic topic, Unit unit, int index) {
    }

    /**
     * LEARN: the rule and exercises. BOOST: a fluency booster, speaking the topic out loud. REVIEW: the topic again a
     * couple of weeks later, with new exercises. MASTER: a last, harder round with more speaking.
     */
    public enum Kind {
        LEARN("learn", "Learn: ", 1),
        BOOST("boost", "Fluency booster: ", 1),
        REVIEW("review", "Revise: ", 2),
        MASTER("master", "Master: ", 3);

        private final String prefix;
        private final String titlePrefix;
        /** Which time the topic comes up: 1 when it's learnt, then 2 and 3. */
        final int round;

        Kind(String prefix, String titlePrefix, int round) {
            this.prefix = prefix;
            this.titlePrefix = titlePrefix;
            this.round = round;
        }

        String idFor(String topicId) {
            return prefix + "." + topicId;
        }

        String title(Topic topic) {
            return titlePrefix + topic.title();
        }
    }

    /**
     * One stop on the path: a lesson of some kind about a topic.
     *
     * @param content the topic with the exercises for this lesson's round
     */
    public record Node(String id, Kind kind, Placed placed, Topic content, int index, int week) {

        public Topic topic() {
            return content;
        }

        public String title() {
            return kind.title(placed.topic());
        }
    }

    private final List<Unit> units;
    private final List<Placed> topics;
    private final List<Node> nodes;
    private final Map<String, Placed> topicsById;
    private final Map<String, Node> nodesById;
    private final Map<String, Checks> checks;

    @Autowired
    public GrammarCurriculum(JsonMapper mapper) {
        this(load(mapper));
    }

    /** For tests: a curriculum from units built in code. */
    GrammarCurriculum(List<Unit> units) {
        this.units = List.copyOf(units);
        List<Placed> placedTopics = new ArrayList<>();
        Map<String, Placed> topicIds = new HashMap<>();
        Map<String, Checks> compiled = new HashMap<>();
        Set<String> unitIds = new HashSet<>();
        for (Unit unit : units) {
            require(unitIds.add(unit.id()), "duplicate unit " + unit.id());
            require(unit.level() != null && unit.topics() != null && !unit.topics().isEmpty(),
                    "unit " + unit.id() + " needs a level and topics");
            for (Topic topic : unit.topics()) {
                validate(topic);
                Placed placed = new Placed(topic, unit, placedTopics.size());
                require(topicIds.put(topic.id(), placed) == null, "duplicate topic " + topic.id());
                placedTopics.add(placed);
                compiled.put(topic.id(), Checks.of(topic));
            }
        }
        List<Node> path = schedule(placedTopics);
        Map<String, Node> nodeIds = new HashMap<>();
        for (Node node : path) {
            nodeIds.put(node.id(), node);
            compiled.putIfAbsent(node.content().id(), Checks.of(node.content()));
        }
        this.topics = List.copyOf(placedTopics);
        this.nodes = List.copyOf(path);
        this.topicsById = Map.copyOf(topicIds);
        this.nodesById = Map.copyOf(nodeIds);
        this.checks = Map.copyOf(compiled);
    }

    /**
     * Lays the topics out one a week. Week {@code w} starts with topic {@code w}'s Learn lesson and Fluency booster,
     * then revises the topic from {@link #REVIEW_AFTER_WEEKS} weeks before and masters the one from
     * {@link #MASTER_AFTER_WEEKS} weeks before, when those have the practice for it. A revision that would fall after the
     * last week is done in the last week instead (unless the topic is new that week); a late master round is left out.
     */
    private static List<Node> schedule(List<Placed> topics) {
        List<Node> path = new ArrayList<>();
        int weeks = topics.size();
        for (int week = 1; week <= weeks; week++) {
            Placed fresh = topics.get(week - 1);
            add(path, Kind.LEARN, fresh, week);
            add(path, Kind.BOOST, fresh, week);
            List<Placed> revise = new ArrayList<>();
            if (week - REVIEW_AFTER_WEEKS >= 1) {
                revise.add(topics.get(week - REVIEW_AFTER_WEEKS - 1));
            }
            if (week == weeks) {
                for (int late = weeks - REVIEW_AFTER_WEEKS + 1; late < weeks; late++) {
                    revise.add(topics.get(late - 1));
                }
            }
            for (Placed placed : revise) {
                if (!placed.topic().rounds().isEmpty()) {
                    add(path, Kind.REVIEW, placed, week);
                }
            }
            if (week - MASTER_AFTER_WEEKS >= 1) {
                Placed placed = topics.get(week - MASTER_AFTER_WEEKS - 1);
                if (placed.topic().rounds().size() >= 2) {
                    add(path, Kind.MASTER, placed, week);
                }
            }
        }
        return path;
    }

    private static void add(List<Node> path, Kind kind, Placed placed, int week) {
        Topic content = kind.round == 1 ? placed.topic() : placed.topic().round(kind.round);
        path.add(new Node(kind.idFor(placed.topic().id()), kind, placed, content, path.size(), week));
    }

    public List<Unit> units() {
        return units;
    }

    /** Every topic, in order. */
    public List<Placed> topics() {
        return topics;
    }

    /** Every lesson on the path, in the order the learner takes them. */
    public List<Node> nodes() {
        return nodes;
    }

    public Optional<Placed> topic(String id) {
        return Optional.ofNullable(id == null ? null : topicsById.get(id));
    }

    public Optional<Node> node(String id) {
        return Optional.ofNullable(id == null ? null : nodesById.get(id));
    }

    /** The first lesson for a learner at {@code level}: the first unit at or above their level. Earlier ones stay open. */
    public int startNode(CefrLevel level) {
        if (level == null) {
            return 0;
        }
        for (Node node : nodes) {
            if (node.placed().unit().level().compareTo(level) >= 0) {
                return node.index();
            }
        }
        return 0;
    }

    /** Whether the learner's answer to the topic's speaking task uses its structure. */
    public boolean usesRule(String topicId, String said) {
        Checks check = checks.get(topicId);
        return check != null && check.open().matches(said);
    }

    /** Whether the answer to the topic's question {@code index} uses the structure the question is about. */
    public boolean answers(String topicId, int index, String said) {
        Checks check = checks.get(topicId);
        return check != null && index >= 0 && index < check.questions().size() && check.questions().get(index)
                .matches(said);
    }

    /**
     * Whether the learner said the slip the right way: close to the corrected sentence word for word (speech
     * recognition drops and mishears small words) and closer to it than to the slip, with at least half of the words
     * the correction adds ("an"), none of the words it takes out ("myself"), and none of the slips the topic teaches.
     */
    public boolean fixes(String topicId, String said, String wrong, String right) {
        Checks check = checks.get(topicId);
        double toRight = similarity(said, right);
        // Closer to the correction than to the slip, which also catches word-order slips ("Why she is crying?").
        if (check == null || toRight < FIX_SIMILARITY || toRight <= similarity(said, wrong)
                || !check.open().clean(said)) {
            return false;
        }
        Set<String> saidWords = new HashSet<>(List.of(expanded(said)));
        Set<String> rightWords = new HashSet<>(List.of(expanded(right)));
        Set<String> wrongWords = new HashSet<>(List.of(expanded(wrong)));
        Set<String> added = new HashSet<>(rightWords);
        added.removeAll(wrongWords);
        Set<String> removed = new HashSet<>(wrongWords);
        removed.removeAll(rightWords);
        long kept = added.stream().filter(saidWords::contains).count();
        return removed.stream().noneMatch(saidWords::contains) && kept * 2 >= added.size();
    }

    static final double FIX_SIMILARITY = 0.75;

    /** Contractions spelled out, so "He's an engineer" and "He is an engineer" compare as the same words. */
    private static final Map<String, String> CONTRACTIONS = Map.ofEntries(
            Map.entry("i'm", "i am"), Map.entry("you're", "you are"), Map.entry("we're", "we are"),
            Map.entry("they're", "they are"), Map.entry("he's", "he is"), Map.entry("she's", "she is"),
            Map.entry("it's", "it is"), Map.entry("that's", "that is"), Map.entry("there's", "there is"),
            Map.entry("what's", "what is"), Map.entry("i've", "i have"), Map.entry("you've", "you have"),
            Map.entry("we've", "we have"), Map.entry("they've", "they have"), Map.entry("i'll", "i will"),
            Map.entry("you'll", "you will"), Map.entry("we'll", "we will"), Map.entry("they'll", "they will"),
            Map.entry("he'll", "he will"), Map.entry("she'll", "she will"), Map.entry("it'll", "it will"),
            Map.entry("i'd", "i would"), Map.entry("you'd", "you would"), Map.entry("he'd", "he would"),
            Map.entry("she'd", "she would"), Map.entry("we'd", "we would"), Map.entry("they'd", "they would"),
            Map.entry("don't", "do not"), Map.entry("doesn't", "does not"), Map.entry("didn't", "did not"),
            Map.entry("isn't", "is not"), Map.entry("aren't", "are not"), Map.entry("wasn't", "was not"),
            Map.entry("weren't", "were not"), Map.entry("haven't", "have not"), Map.entry("hasn't", "has not"),
            Map.entry("hadn't", "had not"), Map.entry("won't", "will not"), Map.entry("wouldn't", "would not"),
            Map.entry("can't", "can not"), Map.entry("couldn't", "could not"), Map.entry("shouldn't", "should not"),
            Map.entry("mustn't", "must not"), Map.entry("let's", "let us"));

    /** Words with contractions spelled out and numbers as digits, however speech recognition wrote them. */
    static String[] expanded(String text) {
        List<String> out = new ArrayList<>();
        for (String word : words(text)) {
            String spelled = CONTRACTIONS.get(word);
            out.addAll(spelled == null ? List.of(word) : List.of(spelled.split(" ")));
        }
        return SpokenNumbers.asDigits(out).toArray(String[]::new);
    }

    /** 0..1: how many words the two have in common, in order (the longest common subsequence, as a Dice score). */
    static double similarity(String a, String b) {
        String[] x = expanded(a);
        String[] y = expanded(b);
        if (x.length == 0 || y.length == 0) {
            return 0;
        }
        int[][] lcs = new int[x.length + 1][y.length + 1];
        for (int i = 1; i <= x.length; i++) {
            for (int j = 1; j <= y.length; j++) {
                lcs[i][j] = x[i - 1].equals(y[j - 1]) ? lcs[i - 1][j - 1] + 1 : Math.max(lcs[i - 1][j], lcs[i][j - 1]);
            }
        }
        return 2.0 * lcs[x.length][y.length] / (x.length + y.length);
    }

    static String[] words(String text) {
        String normal = TurnFeedback.normalize(text);
        return normal.isEmpty() ? new String[0] : normal.split(" ");
    }

    /** A topic's compiled patterns: the open task, and each question (with the topic's slips to avoid). */
    record Checks(Check open, List<Check> questions) {

        static Checks of(Topic topic) {
            List<Pattern> avoid = topic.speak().avoid().stream().map(Check::compile).toList();
            List<Pattern> openCues = topic.speak().cues().stream().map(Check::compile).toList();
            List<Check> questions = topic.questions().stream()
                    .map(q -> new Check(q.cues().isEmpty() ? openCues : q.cues().stream().map(Check::compile).toList(),
                            avoid))
                    .toList();
            return new Checks(new Check(openCues, avoid), questions);
        }
    }

    record Check(List<Pattern> cues, List<Pattern> avoid) {

        static Pattern compile(String regex) {
            try {
                return Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                throw new IllegalStateException("Bad pattern in " + RESOURCE + ": " + regex, e);
            }
        }

        /** Cues may be anywhere in the answer; slips are looked for one sentence at a time. */
        /** Patterns are written with words or digits; speech recognition may write a number either way. */
        boolean matches(String said) {
            String text = TurnFeedback.normalize(said);
            String spelled = SpokenNumbers.spelledOut(text);
            return !text.isEmpty()
                    && cues.stream().allMatch(p -> p.matcher(text).find() || p.matcher(spelled).find())
                    && clean(said);
        }

        boolean clean(String said) {
            if (said == null) {
                return false;
            }
            for (String sentence : said.strip().split("(?<=[.!?])\\s+")) {
                String words = TurnFeedback.normalize(sentence);
                String spelled = SpokenNumbers.spelledOut(words);
                if (avoid.stream().anyMatch(p -> p.matcher(words).find() || p.matcher(spelled).find())) {
                    return false;
                }
            }
            return true;
        }
    }

    private static void validate(Topic topic) {
        String where = "topic " + topic.id();
        require(topic.id() != null && topic.id().matches("[a-z0-9-]{2,64}"), where + ": id must be a slug");
        require(notBlank(topic.title()) && notBlank(topic.summary()) && notBlank(topic.why())
                && notBlank(topic.icon()), where + ": title, summary, why and icon are required");
        require(topic.rules() != null && topic.rules().size() >= 2, where + ": needs at least two rules");
        require(topic.examples() != null && topic.examples().size() >= 2, where + ": needs at least two examples");
        topic.examples().forEach(e -> require(notBlank(e.right()), where + ": every example needs a right version"));
        require(!topic.chart().isEmpty(), where + ": needs a chart");
        topic.chart().forEach(r -> require(notBlank(r.label()) && r.items() != null && !r.items().isEmpty(),
                where + ": every chart row needs a label and items"));
        require(topic.quiz() != null && topic.quiz().size() >= 3 && topic.quiz().size() <= 6,
                where + ": needs three to six quiz questions");
        topic.quiz().forEach(q -> validate(where, q));
        topic.drills().forEach(d -> validate(where, d));
        require(topic.fixes().size() >= 2, where + ": needs at least two fix-it sentences");
        require(topic.questions().size() >= 3, where + ": needs at least three questions to answer");
        topic.questions().forEach(q -> require(notBlank(q.ask()) && notBlank(q.sample()),
                where + ": every question needs a sample answer"));
        validate(where, topic.speak());
        require(topic.rounds().size() <= 2, where + ": at most two more rounds (revise, then master)");
        for (int i = 0; i < topic.rounds().size(); i++) {
            validate(where + " round " + (i + 2), topic.rounds().get(i));
        }
    }

    /** A later round needs enough for its lesson: three quiz questions, three slips, two questions and a task. */
    private static void validate(String where, Round round) {
        require(round.quiz().size() >= 3 && round.quiz().size() <= 6, where + ": needs three to six quiz questions");
        round.quiz().forEach(q -> validate(where, q));
        require(round.drills().size() >= 3, where + ": needs at least three fix-it sentences");
        round.drills().forEach(d -> validate(where, d));
        require(round.questions().size() >= 2, where + ": needs at least two questions to answer");
        round.questions().forEach(q -> require(notBlank(q.ask()) && notBlank(q.sample()),
                where + ": every question needs a sample answer"));
        validate(where, round.speak());
    }

    private static void validate(String where, Question q) {
        require(notBlank(q.prompt()) && q.options() != null && q.options().size() >= 2
                && q.answer() >= 0 && q.answer() < q.options().size() && notBlank(q.explain()),
                where + ": a quiz question is incomplete: " + q.prompt());
        require(new HashSet<>(q.options()).size() == q.options().size(), where + ": duplicate options");
    }

    private static void validate(String where, Drill drill) {
        require(notBlank(drill.wrong()) && notBlank(drill.right()) && notBlank(drill.tip()),
                where + ": every drill needs wrong, right and tip");
    }

    private static void validate(String where, SpeakTask speak) {
        require(speak != null && notBlank(speak.prompt()) && notBlank(speak.sample()) && !speak.cues().isEmpty(),
                where + ": the speaking task needs a prompt, sample and cues");
    }

    private static List<Unit> load(JsonMapper mapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Content content = mapper.readValue(in, Content.class);
            require(content != null && content.units() != null && !content.units().isEmpty(), "no units");
            return content.units();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + RESOURCE, e);
        }
    }

    private static void require(boolean ok, String problem) {
        if (!ok) {
            throw new IllegalStateException("Grammar curriculum: " + problem);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
