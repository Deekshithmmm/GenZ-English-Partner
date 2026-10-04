package com.genzenglish.partner.practice;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.genzenglish.partner.ai.AudioClip;
import com.genzenglish.partner.ai.stt.SpeechToText;
import com.genzenglish.partner.ai.stt.SpeechToTextRequest;
import com.genzenglish.partner.ai.stt.Transcript;
import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.common.Json;
import com.genzenglish.partner.kai.KaiPersona;
import com.genzenglish.partner.practice.PracticeDtos.AnswerResponse;
import com.genzenglish.partner.practice.PracticeDtos.CatalogResponse;
import com.genzenglish.partner.practice.PracticeDtos.HintResponse;
import com.genzenglish.partner.practice.PracticeDtos.Progress;
import com.genzenglish.partner.practice.PracticeDtos.Scorecard;
import com.genzenglish.partner.practice.PracticeDtos.ScorecardVerdict;
import com.genzenglish.partner.practice.PracticeDtos.StartRequest;
import com.genzenglish.partner.practice.PracticeDtos.StartResponse;
import com.genzenglish.partner.practice.PracticeDtos.Stats;
import com.genzenglish.partner.practice.PracticeDtos.Summary;
import com.genzenglish.partner.practice.PracticeDtos.SummaryResponse;
import com.genzenglish.partner.practice.RoleplayCatalog.Roleplay;
import com.genzenglish.partner.practice.SuccessCriteria.Criterion;
import com.genzenglish.partner.practice.TurnFeedback.Correction;
import com.genzenglish.partner.practice.TurnFeedback.FillerWord;
import com.genzenglish.partner.profile.ExplanationLanguage;
import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.ProfileService;
import com.genzenglish.partner.speech.KaiLine;
import com.genzenglish.partner.speech.KaiVoice;
import com.genzenglish.partner.user.User;
import com.genzenglish.partner.user.UserRepository;

/**
 * Runs practice chats with Luna: warm-ups, roleplays, free talk and mock interviews. Like placement, slow provider
 * calls (speech-to-text, the model) happen outside database transactions, and answers are idempotent by turn number so
 * a retried upload is harmless. Luna's reply, the feedback on the answer and the sound check are worked out in
 * parallel; when the answer is streamed, Luna's line goes out as soon as it's ready instead of waiting for the feedback.
 */
@Service
public class PracticeService {

    static final Set<SessionType> PRACTICE_TYPES = EnumSet.of(SessionType.WARM_UP, SessionType.ROLEPLAY,
            SessionType.FREE_TALK, SessionType.INTERVIEW);
    static final int MAX_SUMMARY_CORRECTIONS = 3;
    static final int MAX_SUMMARY_UNCLEAR_WORDS = 3;
    static final String INTERVIEW_TITLE = "Mock interview";
    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final PracticeSessionRepository sessions;
    private final TurnRepository turns;
    private final ProfileService profiles;
    private final UserRepository users;
    private final SpeechToText stt;
    private final KaiVoice voice;
    private final KaiTutor tutor;
    private final TurnCoach coach;
    private final SoundCheck soundCheck;
    private final SessionSummarizer summarizer;
    private final KaiHinter hinter;
    private final InterviewPlanner planner;
    private final PracticeProgress progress;
    private final Json json;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PracticeService(PracticeSessionRepository sessions, TurnRepository turns, ProfileService profiles,
            UserRepository users, SpeechToText stt, KaiVoice voice, KaiTutor tutor, TurnCoach coach,
            SoundCheck soundCheck, SessionSummarizer summarizer, KaiHinter hinter, InterviewPlanner planner,
            PracticeProgress progress, Json json, Clock clock, TransactionTemplate tx) {
        this.sessions = sessions;
        this.turns = turns;
        this.profiles = profiles;
        this.users = users;
        this.stt = stt;
        this.voice = voice;
        this.tutor = tutor;
        this.coach = coach;
        this.soundCheck = soundCheck;
        this.summarizer = summarizer;
        this.hinter = hinter;
        this.planner = planner;
        this.progress = progress;
        this.json = json;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * What one session is about, rebuilt from the catalog and the session row rather than stored.
     *
     * @param brief     the warm-up topic, the learner's goal in a roleplay, or the job in a mock interview
     * @param prompt    the scene description added to Luna's system prompt
     * @param criteria  what counts as success; empty for warm-ups and free talk
     * @param questions a mock interview's planned questions, in order; empty for other chats
     */
    private record Scene(PracticeKind kind, String scenario, String title, String brief, int targetTurns,
            String prompt, List<Criterion> criteria, List<String> questions) {

        /** Luna's scene for the reply to answer {@code turnNumber}, pointing at the next planned question if any. */
        String promptFor(int turnNumber) {
            return turnNumber < questions.size() ? prompt + KaiTutor.nextQuestion(questions.get(turnNumber)) + "\n"
                    : prompt;
        }

        List<String> criteriaTexts() {
            return criteria.stream().map(Criterion::text).toList();
        }
    }

    /** A new session's scene and Luna's first line. {@code topic} is only stored for warm-ups, the plan for interviews. */
    private record Opening(Scene scene, String topic, String planJson, String line) {
    }

    /** Free-talk topics and roleplays the learner can pick from. */
    public static CatalogResponse catalog() {
        return new CatalogResponse(
                FreeTalkTopics.all().stream().map(t -> new PracticeDtos.TopicItem(t.slug(), t.title())).toList(),
                RoleplayCatalog.all().stream()
                        .map(r -> new PracticeDtos.RoleplayItem(r.slug(), r.title(), r.goal(), r.difficulty(),
                                r.targetTurns()))
                        .toList());
    }

    public StartResponse start(UUID userId, StartRequest request) {
        LearnerProfile profile = profiles.get(userId);
        Opening start = switch (request.kind()) {
            case WARM_UP -> {
                String topic = WarmUpTopics.forDate(LocalDate.now(clock.withZone(profile.zone())));
                yield new Opening(warmUp(topic), topic, null, warmUpOpening(firstName(userId), topic));
            }
            case ROLEPLAY -> {
                Roleplay roleplay = RoleplayCatalog.find(request.scenario()).orElseThrow(() -> ApiException.notFound(
                        "roleplay_missing", "We couldn't find that roleplay. Pick another one."));
                yield new Opening(roleplay(roleplay), null, null, roleplay.opening());
            }
            case FREE_TALK -> {
                FreeTalkTopics.Topic topic = request.scenario() == null ? FreeTalkTopics.OPEN
                        : FreeTalkTopics.find(request.scenario()).orElseThrow(() -> ApiException.notFound(
                                "topic_missing", "We couldn't find that topic. Pick another one."));
                yield new Opening(freeTalk(topic), null, null, freeTalkOpening(firstName(userId), topic));
            }
            case INTERVIEW -> {
                String pasted = request.context() == null ? "" : request.context().strip();
                if (pasted.length() < InterviewPlanner.MIN_CONTEXT) {
                    throw ApiException.badRequest("interview_context",
                            "Paste a few lines of your résumé or the job description so Luna can tailor the questions.");
                }
                // The pasted text is only used to plan; the session keeps the plan, not the résumé.
                InterviewPlan plan = planner.plan(pasted, profile.getLevel());
                yield new Opening(interview(plan), null, json.write(plan), interviewOpening(firstName(userId), plan));
            }
        };
        Scene scene = start.scene();
        String opening = start.line();
        PracticeSession session = tx.execute(status -> {
            Instant now = clock.instant();
            // One practice chat at a time: starting another ends any that was left open.
            sessions.findByUserIdAndStatusAndTypeIn(userId, SessionStatus.ACTIVE, PRACTICE_TYPES)
                    .forEach(old -> old.abandon(now));
            return sessions.save(PracticeSession.practice(userId, request.kind().sessionType(), scene.scenario(),
                    start.topic(), start.planJson(), opening, now));
        });
        return new StartResponse(session.getId(), scene.kind(), scene.scenario(), scene.title(), scene.brief(), 1,
                scene.targetTurns(), kaiLine(opening, profile), scene.criteriaTexts());
    }

    /** Two ideas for what the learner could say next. Doesn't count as an answer. */
    public HintResponse hint(UUID userId, UUID sessionId) {
        PracticeSession session = activeSession(userId, sessionId);
        Scene scene = scene(session);
        List<Turn> previous = turns.findBySessionIdOrderBySeqAsc(sessionId);
        if (previous.size() >= scene.targetTurns()) {
            throw ApiException.conflict("session_done", "Luna has wrapped up this chat. See how you did.");
        }
        LearnerProfile profile = profiles.get(userId);
        return new HintResponse(hinter.ideas(new KaiHinter.Input(scene.prompt(),
                previous.stream().map(t -> new KaiTutor.Exchange(t.getLearnerText(), t.getReplyText())).toList(),
                kaiLineBefore(session, previous), profile.getLevel() == null ? null : profile.getLevel().name())));
    }

    public AnswerResponse answerByVoice(UUID userId, UUID sessionId, int turnNumber, AudioClip audio) {
        return answer(accept(userId, sessionId, turnNumber, null, audio));
    }

    public AnswerResponse answerByText(UUID userId, UUID sessionId, int turnNumber, String text) {
        return answer(accept(userId, sessionId, turnNumber, text, null));
    }

    /**
     * The same answer as server-sent events while it's worked out: {@code transcript}, then {@code reply} with Luna's
     * words so far, {@code kai} once Luna's line is ready to play, {@code feedback}, and {@code done} with the full
     * {@link AnswerResponse}. Problems found before streaming starts (a missing session, nothing heard) are ordinary
     * error responses; later ones arrive as a {@code problem} event.
     */
    public SseEmitter streamByVoice(UUID userId, UUID sessionId, int turnNumber, AudioClip audio) {
        return stream(accept(userId, sessionId, turnNumber, null, audio));
    }

    public SseEmitter streamByText(UUID userId, UUID sessionId, int turnNumber, String text) {
        return stream(accept(userId, sessionId, turnNumber, text, null));
    }

    /**
     * Ends the session with Luna's review (note, scores, what to work on, and a scorecard for roleplays and interviews)
     * and credits its XP to the learner's local day. Completing again returns the same summary.
     */
    public SummaryResponse complete(UUID userId, UUID sessionId) {
        PracticeSession session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PracticeService::missing);
        if (!PRACTICE_TYPES.contains(session.getType()) || session.getStatus() == SessionStatus.ABANDONED) {
            throw missing();
        }
        LearnerProfile profile = profiles.get(userId);
        LocalDate today = LocalDate.now(clock.withZone(profile.zone()));
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return new SummaryResponse(json.read(session.getSummaryJson(), Summary.class), progress(userId, today));
        }
        List<Turn> answered = turns.findBySessionIdOrderBySeqAsc(sessionId);
        if (answered.isEmpty()) {
            throw ApiException.badRequest("answer_more", "Answer Luna at least once before you finish.");
        }

        Scene scene = scene(session);
        SessionStats stats = SessionStats.of(answered, session.getSpeakingMs());
        List<TurnFeedback> feedback = answered.stream().map(this::feedbackOf).toList();
        List<Correction> corrections = corrections(feedback);
        SessionSummarizer.Review review = summarizer.review(new SessionSummarizer.Input(scene.kind().name(),
                scene.title(), scene.brief(), profile.getLevel() == null ? null : profile.getLevel().name(),
                stats.speakingSeconds(), stats.averageWpm(), stats.fillerCount(), stats.topFiller(),
                answered.stream()
                        .map(t -> new SessionSummarizer.Input.Answer(t.getPromptText(), t.getLearnerText(),
                                t.getWordsPerMinute(), t.getFillerCount() == null ? 0 : t.getFillerCount()))
                        .toList(),
                corrections, feedback.stream().mapToInt(f -> f.corrections().size()).sum(), unclearWords(feedback),
                scene.criteriaTexts()), scene.criteria(), pronunciation(answered));
        Scorecard scorecard = scene.criteria().isEmpty() ? null : SuccessCriteria.scorecard(review.criteria());
        PracticeRewards.Reward reward = PracticeRewards.of(stats, scene.targetTurns(),
                scorecard != null && scorecard.verdict() == ScorecardVerdict.REACHED);
        Summary summary = new Summary(sessionId, scene.kind(), scene.scenario(), scene.title(), Stats.of(stats),
                reward.total(), reward.items(), review.note(), corrections, review.scores(), review.focus(),
                scorecard);

        Summary saved = tx.execute(status -> {
            PracticeSession current = sessions.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(PracticeService::missing);
            if (current.getStatus() == SessionStatus.COMPLETED) {
                // Another request finished it while the review was being written.
                return json.read(current.getSummaryJson(), Summary.class);
            }
            if (!current.isActive()) {
                throw missing();
            }
            current.completePractice(json.write(summary), clock.instant(), reward.total(), today, summary.scores());
            return summary;
        });
        return new SummaryResponse(saved, progress(userId, today));
    }

    /** An answer that was already stored, or a new one that's checked, transcribed and ready for Luna. */
    private record Accepted(AnswerResponse stored, Pending pending) {
    }

    /**
     * @param prompt      what Luna said just before this answer
     * @param scenePrompt Luna's scene for this reply
     * @param last        true when Luna should wrap up after this answer
     * @param audio       the recording, held only until it's been scored; null for typed answers
     */
    private record Pending(PracticeSession session, Scene scene, int turnNumber, String text, InputMode mode,
            Long durationMs, AudioClip audio, SpeechMetrics.Stats stats, String prompt, String scenePrompt,
            List<KaiTutor.Exchange> history, boolean last, KaiPersona.LearnerContext learner, LearnerProfile profile) {
    }

    /** @param typed the typed answer, or null when {@code audio} holds a recording */
    private Accepted accept(UUID userId, UUID sessionId, int turnNumber, String typed, AudioClip audio) {
        PracticeSession session = activeSession(userId, sessionId);
        Scene scene = scene(session);
        Optional<AnswerResponse> stored = replayIfAnswered(session, scene, turnNumber);
        if (stored.isPresent()) {
            return new Accepted(stored.get(), null);
        }
        List<Turn> previous = turns.findBySessionIdOrderBySeqAsc(sessionId);
        String prompt = kaiLineBefore(session, previous);
        String text = audio == null ? typed.strip() : transcribe(audio, prompt);
        Long durationMs = audio == null ? null : audio.durationMs();
        LearnerProfile profile = profiles.get(userId);
        return new Accepted(null, new Pending(session, scene, turnNumber, text,
                audio == null ? InputMode.TEXT : InputMode.VOICE, durationMs, audio,
                SpeechMetrics.analyze(text, durationMs), prompt, scene.promptFor(turnNumber),
                previous.stream().map(t -> new KaiTutor.Exchange(t.getLearnerText(), t.getReplyText())).toList(),
                turnNumber >= scene.targetTurns(),
                new KaiPersona.LearnerContext(displayName(userId), profile.getLevel(), profile.getGoal()), profile));
    }

    private String transcribe(AudioClip audio, String prompt) {
        Transcript transcript;
        try {
            transcript = stt.transcribe(SpeechToTextRequest.indianEnglish(audio, prompt));
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

    /** The feedback and the sound check, running alongside Luna's reply. */
    private record Review(Future<TurnFeedback> notes, Future<SoundCheck.Result> sounds) {
    }

    /** Everything Luna noticed about one answer, and its pronunciation score if it was spoken. */
    private record Checked(TurnFeedback feedback, Integer pronunciation) {
    }

    private Review startReview(Pending p, ExecutorService parallel) {
        return new Review(
                parallel.submit(() -> coach.review(p.prompt(), p.text(), p.profile().getLevel(), language(p))),
                parallel.submit(() -> soundCheck.check(p.audio(), p.text())));
    }

    private Checked finishReview(Review review, Pending p) {
        TurnFeedback notes = await(review.notes(), () -> CommonSlips.review(p.text())
                .withNativeFallback(language(p)));
        SoundCheck.Result sounds = await(review.sounds(), SoundCheck.Result::none);
        return new Checked(notes.withSpeech(fillerWords(p.stats().fillers()), sounds.slips()), sounds.overall());
    }

    private AnswerResponse answer(Accepted accepted) {
        if (accepted.stored() != null) {
            return accepted.stored();
        }
        Pending p = accepted.pending();
        String reply;
        Checked checked;
        try (ExecutorService parallel = Executors.newVirtualThreadPerTaskExecutor()) {
            Review review = startReview(p, parallel);
            reply = tutor.reply(p.learner(), p.scenePrompt(), p.session().getOpeningLine(), p.history(), p.text(),
                    p.last());
            checked = finishReview(review, p);
        }
        return save(p, reply, checked, kaiLine(reply, p.profile()));
    }

    private SseEmitter stream(Accepted accepted) {
        TurnEvents events = new TurnEvents();
        if (accepted.stored() != null) {
            events.replay(accepted.stored());
        } else {
            Thread.ofVirtual().name("turn-stream").start(() -> stream(accepted.pending(), events));
        }
        return events.emitter();
    }

    private void stream(Pending p, TurnEvents events) {
        try {
            events.transcript(p.turnNumber(), p.text(), p.stats().wordsPerMinute(), p.stats().fillerCount());
            String reply;
            KaiLine kai;
            Checked checked;
            try (ExecutorService parallel = Executors.newVirtualThreadPerTaskExecutor()) {
                Review review = startReview(p, parallel);
                reply = tutor.streamReply(p.learner(), p.scenePrompt(), p.session().getOpeningLine(), p.history(),
                        p.text(), p.last(), events::reply);
                kai = kaiLine(reply, p.profile());
                // Luna can start talking while the feedback is still being written.
                events.send("kai", kai);
                checked = finishReview(review, p);
                events.send("feedback", checked.feedback());
            }
            events.send("done", save(p, reply, checked, kai));
        } catch (ApiException e) {
            events.problem(e.code(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Streamed answer {} in session {} failed", p.turnNumber(), p.session().getId(), e);
            events.problem("reply_failed", "Luna couldn't reply just now. Send your answer again.");
        } finally {
            events.complete();
        }
    }

    private AnswerResponse save(Pending p, String reply, Checked checked, KaiLine kai) {
        try {
            tx.executeWithoutResult(status -> {
                Turn turn = new Turn(p.session().getId(), p.turnNumber(), p.prompt(), p.text(), p.mode(),
                        p.durationMs() == null ? null : Math.toIntExact(p.durationMs()), clock.instant());
                turn.recordMetrics(p.stats());
                turn.setReply(reply);
                turn.setFeedbackJson(json.write(checked.feedback()));
                if (checked.pronunciation() != null) {
                    turn.setScoresJson(json.write(new TurnScores(checked.pronunciation())));
                }
                turns.saveAndFlush(turn);
                if (p.durationMs() != null) {
                    sessions.findById(p.session().getId()).ifPresent(s -> s.addSpeakingTime(p.durationMs()));
                }
            });
        } catch (DataIntegrityViolationException e) {
            // A concurrent retry stored this answer first; return what was stored.
            return replayIfAnswered(p.session(), p.scene(), p.turnNumber()).orElseThrow(() -> e);
        }
        return answerResponse(p.scene(), p.turnNumber(), p.text(), p.stats().wordsPerMinute(), p.stats().fillerCount(),
                checked.feedback(), kai);
    }

    /** The reviewers never throw for provider problems, so this only falls back if the task itself blew up. */
    private static <T> T await(Future<T> task, Supplier<T> fallback) {
        try {
            return task.get();
        } catch (ExecutionException e) {
            return fallback.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.get();
        }
    }

    private static ExplanationLanguage language(Pending p) {
        return p.profile().getExplanationLanguage();
    }

    /** Most-used first. */
    static List<FillerWord> fillerWords(Map<String, Integer> fillers) {
        return fillers.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(e -> new FillerWord(e.getKey(), e.getValue()))
                .toList();
    }

    private Optional<AnswerResponse> replayIfAnswered(PracticeSession session, Scene scene, int turnNumber) {
        if (turnNumber > scene.targetTurns()) {
            throw ApiException.conflict("session_done", "Luna has wrapped up this chat. See how you did.");
        }
        int answered = turns.countBySessionId(session.getId());
        if (turnNumber > answered + 1) {
            throw ApiException.conflict("out_of_order", "Answer Luna's latest message first.");
        }
        if (turnNumber > answered) {
            return Optional.empty();
        }
        Turn turn = turns.findBySessionIdAndSeq(session.getId(), turnNumber).orElseThrow(PracticeService::missing);
        return Optional.of(answerResponse(scene, turnNumber, turn.getLearnerText(), turn.getWordsPerMinute(),
                turn.getFillerCount() == null ? 0 : turn.getFillerCount(), feedbackOf(turn),
                kaiLine(turn.getReplyText(), profiles.get(session.getUserId()))));
    }

    private static AnswerResponse answerResponse(Scene scene, int turnNumber, String transcript, Integer wpm,
            int fillers, TurnFeedback feedback, KaiLine kai) {
        boolean done = turnNumber >= scene.targetTurns();
        return new AnswerResponse(turnNumber, transcript, wpm, fillers, feedback, done, done ? null : turnNumber + 1,
                kai);
    }

    private TurnFeedback feedbackOf(Turn turn) {
        return Optional.ofNullable(json.read(turn.getFeedbackJson(), TurnFeedback.class)).orElseGet(TurnFeedback::none);
    }

    /** Corrections from across the session, first time each slip came up, for the summary screen. */
    private static List<Correction> corrections(List<TurnFeedback> feedback) {
        List<Correction> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TurnFeedback turn : feedback) {
            for (Correction correction : turn.corrections()) {
                if (seen.add(TurnFeedback.normalize(correction.original()))) {
                    result.add(correction);
                }
                if (result.size() == MAX_SUMMARY_CORRECTIONS) {
                    return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<String> unclearWords(List<TurnFeedback> feedback) {
        Set<String> words = new LinkedHashSet<>();
        feedback.forEach(f -> f.mispronounced().forEach(slip -> words.add(slip.word())));
        return words.stream().limit(MAX_SUMMARY_UNCLEAR_WORDS).toList();
    }

    /** The average over spoken answers, or null when nothing was spoken. */
    private Integer pronunciation(List<Turn> answered) {
        OptionalDouble average = answered.stream()
                .map(t -> json.read(t.getScoresJson(), TurnScores.class))
                .filter(Objects::nonNull)
                .map(TurnScores::pronunciation)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
        return average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
    }

    private Progress progress(UUID userId, LocalDate today) {
        PracticeProgress.Snapshot snapshot = progress.of(userId, today);
        return new Progress(snapshot.currentStreak(), snapshot.xpToday(), snapshot.xpTotal());
    }

    private PracticeSession activeSession(UUID userId, UUID sessionId) {
        PracticeSession session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PracticeService::missing);
        if (!PRACTICE_TYPES.contains(session.getType()) || !session.isActive()) {
            throw missing();
        }
        return session;
    }

    private Scene scene(PracticeSession session) {
        PracticeKind kind = PracticeKind.of(session.getType());
        return switch (kind) {
            case WARM_UP -> warmUp(session.getTopic());
            // A roleplay removed from the catalog mid-session can't continue.
            case ROLEPLAY -> roleplay(RoleplayCatalog.find(session.getScenario()).orElseThrow(PracticeService::missing));
            case FREE_TALK -> freeTalk(FreeTalkTopics.find(session.getScenario()).orElse(FreeTalkTopics.OPEN));
            case INTERVIEW -> interview(Optional.ofNullable(json.read(session.getPlanJson(), InterviewPlan.class))
                    .orElseThrow(PracticeService::missing));
        };
    }

    private static Scene warmUp(String topic) {
        return new Scene(PracticeKind.WARM_UP, null, "Warm-up chat", topic, PracticeKind.WARM_UP.defaultTargetTurns(),
                KaiTutor.warmUpScene(topic), List.of(), List.of());
    }

    private static Scene roleplay(Roleplay roleplay) {
        return new Scene(PracticeKind.ROLEPLAY, roleplay.slug(), roleplay.title(), roleplay.goal(),
                roleplay.targetTurns(), KaiTutor.roleplayScene(roleplay), roleplay.criteria(), List.of());
    }

    private static Scene freeTalk(FreeTalkTopics.Topic topic) {
        return new Scene(PracticeKind.FREE_TALK, topic.slug(), "Talk with Luna", topic.title(),
                PracticeKind.FREE_TALK.defaultTargetTurns(), KaiTutor.freeTalkScene(topic), List.of(), List.of());
    }

    private static Scene interview(InterviewPlan plan) {
        return new Scene(PracticeKind.INTERVIEW, null, INTERVIEW_TITLE, plan.position(), plan.questions().size(),
                KaiTutor.interviewScene(plan), InterviewPlanner.CRITERIA, plan.questions());
    }

    static String warmUpOpening(String firstName, String topic) {
        return greeting(firstName) + ", let's warm up. " + WarmUpTopics.askedByKai(topic);
    }

    static String freeTalkOpening(String firstName, FreeTalkTopics.Topic topic) {
        return greeting(firstName) + "! " + topic.opening();
    }

    /** Luna greets the candidate in character and asks the first planned question. */
    static String interviewOpening(String firstName, InterviewPlan plan) {
        String hello = firstName == null ? "Hello" : "Hello " + firstName;
        String about = plan.role().equals(InterviewPlanner.GENERIC_ROLE) ? ""
                : " for the " + InterviewPlanner.inSentence(plan.role()) + " role"
                        + (plan.company() == null ? "" : " at " + plan.company());
        return hello + ", thanks for coming in today" + about + ". " + plan.questions().getFirst();
    }

    private static String greeting(String firstName) {
        return firstName == null ? "Hey" : "Hey " + firstName;
    }

    /** What Luna said just before the learner's next answer. */
    private static String kaiLineBefore(PracticeSession session, List<Turn> previous) {
        return previous.isEmpty() ? session.getOpeningLine() : previous.getLast().getReplyText();
    }

    private String displayName(UUID userId) {
        return users.findById(userId).map(User::getDisplayName).orElse(null);
    }

    private String firstName(UUID userId) {
        String name = displayName(userId);
        return name == null || name.isBlank() ? null : name.strip().split("\\s+")[0];
    }

    private KaiLine kaiLine(String text, LearnerProfile profile) {
        return new KaiLine(text, voice.speak(text, profile));
    }

    private static ApiException missing() {
        return ApiException.notFound("practice_missing", "That chat has ended. Start a new one from today's plan.");
    }
}
