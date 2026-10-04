package com.genzenglish.partner.grammar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genzenglish.partner.ai.AudioClip;
import com.genzenglish.partner.ai.stt.SpeechToText;
import com.genzenglish.partner.ai.stt.SpeechToTextRequest;
import com.genzenglish.partner.ai.stt.Transcript;
import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.common.Json;
import com.genzenglish.partner.grammar.GrammarCurriculum.Node;
import com.genzenglish.partner.grammar.GrammarCurriculum.Topic;
import com.genzenglish.partner.grammar.GrammarDtos.CompleteRequest;
import com.genzenglish.partner.grammar.GrammarDtos.Exercise;
import com.genzenglish.partner.grammar.GrammarDtos.ExerciseAnswer;
import com.genzenglish.partner.grammar.GrammarDtos.ExerciseType;
import com.genzenglish.partner.grammar.GrammarDtos.Improvement;
import com.genzenglish.partner.grammar.GrammarDtos.LessonReport;
import com.genzenglish.partner.grammar.GrammarDtos.LessonResponse;
import com.genzenglish.partner.grammar.GrammarDtos.LessonResult;
import com.genzenglish.partner.grammar.GrammarDtos.NodeItem;
import com.genzenglish.partner.grammar.GrammarDtos.NodeStatus;
import com.genzenglish.partner.grammar.GrammarDtos.PathResponse;
import com.genzenglish.partner.grammar.GrammarDtos.Progress;
import com.genzenglish.partner.grammar.GrammarDtos.SpeakItem;
import com.genzenglish.partner.grammar.GrammarDtos.SpeakResult;
import com.genzenglish.partner.grammar.GrammarDtos.Week;
import com.genzenglish.partner.grammar.GrammarLessons.Plan;
import com.genzenglish.partner.grammar.GrammarLessons.Planned;
import com.genzenglish.partner.practice.AnswerReviewer;
import com.genzenglish.partner.practice.PracticeProgress;
import com.genzenglish.partner.practice.PracticeSession;
import com.genzenglish.partner.practice.PracticeSessionRepository;
import com.genzenglish.partner.practice.SessionStatus;
import com.genzenglish.partner.practice.SessionType;
import com.genzenglish.partner.practice.TurnFeedback;
import com.genzenglish.partner.profile.CefrLevel;
import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.ProfileService;

/**
 * The learner's walk along the grammar path: lessons open one at a time from the unit that fits their level (earlier
 * ones stay open for review). Exercises are marked in the app as the learner goes and again here; spoken answers are
 * checked here as they're given. Finishing a lesson is recorded as a completed LESSON session, so its XP counts towards
 * today's XP and the streak like any other practice.
 */
@Service
public class GrammarService {

    static final int FIRST_TIME_XP = 20;
    static final int XP_PER_STAR = 5;
    static final int REPEAT_XP = 5;
    static final int PASS_SCORE = 50;
    static final int MAX_IMPROVEMENTS = 3;

    private final GrammarCurriculum curriculum;
    private final PracticeSessionRepository sessions;
    private final ProfileService profiles;
    private final SpeechToText stt;
    private final AnswerReviewer reviewer;
    private final PracticeProgress progress;
    private final Json json;
    private final Clock clock;

    public GrammarService(GrammarCurriculum curriculum, PracticeSessionRepository sessions, ProfileService profiles,
            SpeechToText stt, AnswerReviewer reviewer, PracticeProgress progress, Json json, Clock clock) {
        this.curriculum = curriculum;
        this.sessions = sessions;
        this.profiles = profiles;
        this.stt = stt;
        this.reviewer = reviewer;
        this.progress = progress;
        this.json = json;
        this.clock = clock;
    }

    /** A finished lesson as stored, with the XP it earned. */
    record Stored(LessonResult result, int xp) {
    }

    /**
     * Where the learner is on the path.
     *
     * @param stars    best stars per finished lesson id
     * @param attempts finished lessons by attempt id, so a retried completion returns the first report
     * @param start    the lesson index the learner's level starts at
     * @param current  the next lesson, or null when every lesson is done
     */
    record Standing(Map<String, Integer> stars, Map<UUID, Stored> attempts, int start, Node current) {

        NodeStatus status(Node node) {
            if (stars.containsKey(node.id())) {
                return NodeStatus.DONE;
            }
            if (current != null && current.index() == node.index()) {
                return NodeStatus.CURRENT;
            }
            return node.index() < start ? NodeStatus.OPEN : NodeStatus.LOCKED;
        }

        int starsFor(String nodeId) {
            return stars.getOrDefault(nodeId, 0);
        }
    }

    @Transactional(readOnly = true)
    public PathResponse path(UUID userId) {
        Standing standing = standing(userId, profiles.get(userId).getLevel());
        Map<Integer, List<NodeItem>> weeks = new LinkedHashMap<>();
        Map<Integer, Node> firstOfWeek = new HashMap<>();
        for (Node node : curriculum.nodes()) {
            firstOfWeek.putIfAbsent(node.week(), node);
            weeks.computeIfAbsent(node.week(), w -> new ArrayList<>())
                    .add(new NodeItem(node.id(), node.kind(), node.index() + 1, node.title(), node.topic().title(),
                            node.topic().summary(), node.topic().icon(), standing.status(node),
                            standing.starsFor(node.id())));
        }
        List<Week> list = weeks.entrySet().stream()
                .map(e -> {
                    Node first = firstOfWeek.get(e.getKey());
                    return new Week(e.getKey(), first.placed().unit().title(), first.placed().unit().level(),
                            List.copyOf(e.getValue()));
                })
                .toList();
        return new PathResponse(list, standing.current() == null ? null : standing.current().id(),
                standing.stars().size(), curriculum.nodes().size());
    }

    /** The learner's next lesson. Empty once the whole path is done. */
    @Transactional(readOnly = true)
    public Optional<Node> currentNode(UUID userId, CefrLevel level) {
        return Optional.ofNullable(standing(userId, level).current());
    }

    /** A lesson by id, if it's still on the path. */
    public Optional<Node> node(String nodeId) {
        return curriculum.node(nodeId);
    }

    @Transactional(readOnly = true)
    public LessonResponse lesson(UUID userId, String nodeId) {
        LearnerProfile profile = profiles.get(userId);
        Standing standing = standing(userId, profile.getLevel());
        Node node = open(standing, nodeId);
        Plan plan = GrammarLessons.plan(node, profile.getExplanationLanguage());
        Topic topic = node.topic();
        return new LessonResponse(node.id(), node.kind(), node.index() + 1, node.week(), node.title(), topic.title(),
                topic.summary(), topic.why(), topic.icon(), plan.intro(), topic.chart(), topic.rules(),
                topic.examples(), plan.exercises(), plan.speakItems(), plan.phrases(), standing.status(node),
                standing.starsFor(node.id()));
    }

    public SpeakResult speakByText(UUID userId, String nodeId, int index, String text) {
        return speak(userId, nodeId, index, text.strip(), null);
    }

    public SpeakResult speakByVoice(UUID userId, String nodeId, int index, AudioClip audio) {
        return speak(userId, nodeId, index, null, audio);
    }

    /** Checks one spoken (or typed) answer in a lesson's speaking part, with Luna's usual notes. */
    private SpeakResult speak(UUID userId, String nodeId, int index, String typed, AudioClip audio) {
        LearnerProfile profile = profiles.get(userId);
        Node node = open(standing(userId, profile.getLevel()), nodeId);
        List<Planned> items = GrammarLessons.plan(node, profile.getExplanationLanguage()).speaking();
        if (index < 0 || index >= items.size()) {
            throw ApiException.notFound("item_missing", "That question isn't part of this lesson.");
        }
        Planned planned = items.get(index);
        SpeakItem item = planned.item();
        String said = audio == null ? typed : transcribe(audio, item);
        TurnFeedback feedback = reviewer.review(item.prompt(), said, audio, profile.getLevel(),
                profile.getExplanationLanguage());
        return new SpeakResult(said, correct(node, planned, said), item.sample(), feedback);
    }

    private boolean correct(Node node, Planned planned, String said) {
        if (said == null || said.isBlank()) {
            return false;
        }
        String topicId = node.topic().id();
        return switch (planned.item().type()) {
            case FIX -> curriculum.fixes(topicId, said, planned.item().prompt(), planned.item().sample());
            case ANSWER -> curriculum.answers(topicId, planned.question(), said);
            case OPEN -> curriculum.usesRule(topicId, said);
        };
    }

    /**
     * Marks the whole lesson and, when at least half of it is right, records it as done. Stars: 3 from 90%, 2 from
     * 70%, else 1. XP is full the first time and a small bonus for a repeat. Retrying the same attempt returns the first
     * report.
     */
    @Transactional
    public LessonReport complete(UUID userId, String nodeId, CompleteRequest request) {
        LearnerProfile profile = profiles.get(userId);
        Standing before = standing(userId, profile.getLevel());
        Node node = open(before, nodeId);
        LocalDate today = LocalDate.now(clock.withZone(profile.zone()));

        Stored repeat = request.attemptId() == null ? null : before.attempts().get(request.attemptId());
        if (repeat != null) {
            return report(userId, today, nodeId, true, repeat.result(), repeat.xp(), before.current());
        }

        Plan plan = GrammarLessons.plan(node, profile.getExplanationLanguage());
        List<Improvement> improvements = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < plan.exercises().size(); i++) {
            Exercise exercise = plan.exercises().get(i);
            ExerciseAnswer answer = i < request.answers().size() ? request.answers().get(i) : null;
            if (marked(exercise, answer)) {
                correct++;
            } else {
                improvements.add(improvement(exercise, answer));
            }
        }
        int words = 0;
        for (int i = 0; i < plan.speaking().size(); i++) {
            Planned planned = plan.speaking().get(i);
            String said = i < request.spoken().size() ? request.spoken().get(i) : null;
            words += GrammarCurriculum.words(said).length;
            if (correct(node, planned, said)) {
                correct++;
            } else if (said != null && !said.isBlank()) {
                improvements.add(new Improvement(planned.item().tip(), said.strip(), planned.item().sample()));
            }
        }
        int total = plan.exercises().size() + plan.speaking().size();
        int score = total == 0 ? 0 : Math.round(100f * correct / total);
        List<Improvement> top = improvements.stream()
                .filter(distinctBy(Improvement::tip))
                .limit(MAX_IMPROVEMENTS)
                .toList();
        if (score < PASS_SCORE) {
            // Not passed: nothing is recorded, so the lesson stays where it is.
            LessonResult missed = new LessonResult(score, correct, total, 0, false, words, request.seconds(),
                    note(node, score), top, request.attemptId());
            return report(userId, today, nodeId, false, missed, 0, before.current());
        }
        int stars = stars(score);
        boolean firstTime = !before.stars().containsKey(nodeId);
        int xp = firstTime ? FIRST_TIME_XP + XP_PER_STAR * stars : REPEAT_XP;
        LessonResult result = new LessonResult(score, correct, total, stars, firstTime, words, request.seconds(),
                note(node, score), top, request.attemptId());
        sessions.save(PracticeSession.lesson(userId, nodeId, json.write(result), xp, today, clock.instant()));
        return report(userId, today, nodeId, true, result, xp, standing(userId, profile.getLevel()).current());
    }

    static int stars(int score) {
        return score >= 90 ? 3 : score >= 70 ? 2 : 1;
    }

    static boolean marked(Exercise exercise, ExerciseAnswer answer) {
        if (answer == null) {
            return false;
        }
        return exercise.type() == ExerciseType.CHOICE
                ? answer.choice() != null && answer.choice().equals(exercise.answer())
                : GrammarLessons.built(answer.words(), exercise.words());
    }

    private static Improvement improvement(Exercise exercise, ExerciseAnswer answer) {
        if (exercise.type() == ExerciseType.CHOICE) {
            Integer picked = answer == null ? null : answer.choice();
            String wrong = picked != null && picked >= 0 && picked < exercise.options().size()
                    ? exercise.options().get(picked) : exercise.prompt();
            return new Improvement(exercise.explain(), wrong, exercise.options().get(exercise.answer()));
        }
        String built = answer == null || answer.words() == null || answer.words().isEmpty() ? exercise.prompt()
                : String.join(" ", answer.words());
        return new Improvement(exercise.explain(), built, String.join(" ", exercise.words()));
    }

    /** Luna's note: specific to the lesson, warm whatever the score. */
    static String note(Node node, int score) {
        String topic = node.topic().title();
        if (score >= 90) {
            return "Excellent work on \"" + topic + "\"! You used it confidently. Keep it up and it'll soon feel "
                    + "automatic.";
        }
        if (score >= 70) {
            return "Good job on \"" + topic + "\"! A couple of slips, but you're getting there. Have a look at the "
                    + "tips below.";
        }
        if (score >= PASS_SCORE) {
            return "Nice effort on \"" + topic + "\". Go through the tips below; the next lesson will make it stick.";
        }
        return "\"" + topic + "\" needs one more go. Read the rule again, then try the lesson once more.";
    }

    private static <T> Predicate<T> distinctBy(Function<T, Object> key) {
        Set<Object> seen = new HashSet<>();
        return item -> seen.add(key.apply(item));
    }

    private LessonReport report(UUID userId, LocalDate today, String nodeId, boolean passed, LessonResult result,
            int xp, Node next) {
        PracticeProgress.Snapshot snapshot = progress.of(userId, today);
        return new LessonReport(nodeId, passed, result.score(), result.correct(), result.total(), result.stars(), xp,
                result.firstTime(), result.wordsUsed(), result.seconds(), result.note(), result.improvements(),
                next == null ? null : next.id(),
                new Progress(snapshot.currentStreak(), snapshot.xpToday(), snapshot.xpTotal()));
    }

    private Standing standing(UUID userId, CefrLevel level) {
        Map<String, Integer> stars = new HashMap<>();
        Map<UUID, Stored> attempts = new HashMap<>();
        for (PracticeSession session : sessions.findByUserIdAndTypeAndStatus(userId, SessionType.LESSON,
                SessionStatus.COMPLETED)) {
            // Lessons that are no longer on the path are skipped.
            if (curriculum.node(session.getScenario()).isEmpty()) {
                continue;
            }
            LessonResult result = json.read(session.getSummaryJson(), LessonResult.class);
            if (result == null) {
                continue;
            }
            stars.merge(session.getScenario(), result.stars(), Math::max);
            if (result.attemptId() != null) {
                attempts.put(result.attemptId(), new Stored(result, session.getXp()));
            }
        }
        int start = curriculum.startNode(level);
        Node current = curriculum.nodes().stream()
                .filter(n -> n.index() >= start && !stars.containsKey(n.id()))
                .findFirst()
                // Everything from their level on is done: point them back at an earlier lesson they skipped.
                .or(() -> curriculum.nodes().stream().filter(n -> !stars.containsKey(n.id())).findFirst())
                .orElse(null);
        return new Standing(stars, attempts, start, current);
    }

    private Node open(Standing standing, String nodeId) {
        Node node = curriculum.node(nodeId).orElseThrow(() -> ApiException.notFound("lesson_missing",
                "We couldn't find that lesson. Pick one from your path."));
        if (standing.status(node) == NodeStatus.LOCKED) {
            throw ApiException.conflict("lesson_locked", "This lesson opens after the ones before it. Finish those first.");
        }
        return node;
    }

    private String transcribe(AudioClip audio, SpeakItem item) {
        Transcript transcript;
        try {
            transcript = stt.transcribe(SpeechToTextRequest.rehearsing(audio, item.prompt(), item.sample()));
        } catch (RuntimeException e) {
            throw ApiException.unavailable("stt_failed",
                    "We couldn't process your recording. Try again, or type your answer instead.");
        }
        if (transcript.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "no_speech",
                    "We couldn't hear anything. Hold the mic, speak, then let go.");
        }
        return transcript.text();
    }
}
