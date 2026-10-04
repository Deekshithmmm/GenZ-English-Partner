package com.genzenglish.partner.placement;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.genzenglish.partner.ai.AudioClip;
import com.genzenglish.partner.ai.stt.SpeechToText;
import com.genzenglish.partner.ai.stt.SpeechToTextRequest;
import com.genzenglish.partner.ai.stt.Transcript;
import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.common.Json;
import com.genzenglish.partner.kai.KaiPersona;
import com.genzenglish.partner.path.LearningPathService;
import com.genzenglish.partner.placement.PlacementDtos.AnswerResponse;
import com.genzenglish.partner.placement.PlacementDtos.ResultResponse;
import com.genzenglish.partner.placement.PlacementDtos.Scores;
import com.genzenglish.partner.placement.PlacementDtos.StartResponse;
import com.genzenglish.partner.practice.InputMode;
import com.genzenglish.partner.practice.PracticeSession;
import com.genzenglish.partner.practice.PracticeSessionRepository;
import com.genzenglish.partner.practice.SessionStatus;
import com.genzenglish.partner.practice.SessionType;
import com.genzenglish.partner.practice.SpeechMetrics;
import com.genzenglish.partner.practice.Turn;
import com.genzenglish.partner.practice.TurnRepository;
import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.LevelSource;
import com.genzenglish.partner.profile.ProfileDtos.LevelInfo;
import com.genzenglish.partner.profile.ProfileService;
import com.genzenglish.partner.speech.KaiLine;
import com.genzenglish.partner.speech.KaiVoice;
import com.genzenglish.partner.user.User;
import com.genzenglish.partner.user.UserRepository;

/**
 * Runs the spoken placement chat. Slow provider calls (speech-to-text, the model) happen outside database
 * transactions; answers are idempotent by question number so a retried upload on a flaky connection is harmless.
 * Raw audio is never stored here: it's transcribed in memory and dropped.
 */
@Service
public class PlacementService {

    private final PracticeSessionRepository sessions;
    private final TurnRepository turns;
    private final ProfileService profiles;
    private final UserRepository users;
    private final SpeechToText stt;
    private final KaiVoice voice;
    private final PlacementReactor reactor;
    private final PlacementAssessor assessor;
    private final LearningPathService paths;
    private final Json json;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PlacementService(PracticeSessionRepository sessions, TurnRepository turns, ProfileService profiles,
            UserRepository users, SpeechToText stt, KaiVoice voice, PlacementReactor reactor,
            PlacementAssessor assessor, LearningPathService paths, Json json, Clock clock, TransactionTemplate tx) {
        this.sessions = sessions;
        this.turns = turns;
        this.profiles = profiles;
        this.users = users;
        this.stt = stt;
        this.voice = voice;
        this.reactor = reactor;
        this.assessor = assessor;
        this.paths = paths;
        this.json = json;
        this.clock = clock;
        this.tx = tx;
    }

    public StartResponse start(UUID userId) {
        LearnerProfile profile = profiles.get(userId);
        String opening = PlacementScript.question(1);
        PracticeSession session = tx.execute(status -> {
            Instant now = clock.instant();
            sessions.findByUserIdAndTypeAndStatus(userId, SessionType.PLACEMENT, SessionStatus.ACTIVE)
                    .forEach(old -> old.abandon(now));
            return sessions.save(new PracticeSession(userId, SessionType.PLACEMENT, opening, now));
        });
        return new StartResponse(session.getId(), 1, PlacementScript.TOTAL, kaiLine(opening, profile));
    }

    public AnswerResponse answerByVoice(UUID userId, UUID sessionId, int questionNumber, AudioClip audio) {
        PracticeSession session = activeSession(userId, sessionId);
        Optional<AnswerResponse> replay = replayIfAnswered(session, questionNumber);
        if (replay.isPresent()) {
            return replay.get();
        }
        Transcript transcript;
        try {
            transcript = stt.transcribe(
                    SpeechToTextRequest.indianEnglish(audio, PlacementScript.question(questionNumber)));
        } catch (RuntimeException e) {
            throw ApiException.unavailable("stt_failed",
                    "We couldn't process your recording. Try again, or type your answer instead.");
        }
        if (transcript.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "no_speech",
                    "We couldn't hear anything. Hold the mic, speak, then let go.");
        }
        return record(session, questionNumber, transcript.text(), InputMode.VOICE, audio.durationMs());
    }

    public AnswerResponse answerByText(UUID userId, UUID sessionId, int questionNumber, String text) {
        PracticeSession session = activeSession(userId, sessionId);
        Optional<AnswerResponse> replay = replayIfAnswered(session, questionNumber);
        if (replay.isPresent()) {
            return replay.get();
        }
        return record(session, questionNumber, text.strip(), InputMode.TEXT, null);
    }

    public ResultResponse complete(UUID userId, UUID sessionId) {
        PracticeSession session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PlacementService::missing);
        LearnerProfile profile = profiles.get(userId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return result(json.read(session.getSummaryJson(), PlacementAssessment.class), profile);
        }
        if (session.getStatus() == SessionStatus.ABANDONED) {
            throw missing();
        }
        List<Turn> answered = turns.findBySessionIdOrderBySeqAsc(sessionId);
        if (answered.size() < PlacementScript.MIN_ANSWERS_TO_ASSESS) {
            throw ApiException.badRequest("answer_more",
                    "Answer at least two questions so Luna can work out your level.");
        }

        PlacementAssessment assessment = assessor.assess(new PlacementAssessor.Input(answered.stream()
                .map(t -> new PlacementAssessor.Input.Answer(t.getPromptText(), t.getLearnerText(),
                        t.getAudioDurationMs() == null ? null : t.getAudioDurationMs().longValue(),
                        t.getWordsPerMinute(), t.getFillerCount() == null ? 0 : t.getFillerCount()))
                .toList()));

        LearnerProfile updated = tx.execute(status -> {
            PracticeSession current = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PlacementService::missing);
            current.complete(json.write(assessment), clock.instant());
            return profiles.setLevel(userId, assessment.level(), LevelSource.PLACEMENT);
        });
        return result(assessment, updated);
    }

    private AnswerResponse record(PracticeSession session, int questionNumber, String text, InputMode mode,
            Long durationMs) {
        LearnerProfile profile = profiles.get(session.getUserId());
        SpeechMetrics.Stats stats = SpeechMetrics.analyze(text, durationMs);
        boolean last = questionNumber == PlacementScript.TOTAL;
        String reaction = reactor.react(text, learnerContext(session.getUserId(), profile));
        String reply = reaction + " " + (last ? PlacementScript.CLOSING : PlacementScript.question(questionNumber + 1));

        try {
            tx.executeWithoutResult(status -> {
                Turn turn = new Turn(session.getId(), questionNumber, PlacementScript.question(questionNumber), text,
                        mode, durationMs == null ? null : Math.toIntExact(durationMs), clock.instant());
                turn.recordMetrics(stats);
                turn.setReply(reply);
                turns.saveAndFlush(turn);
                if (durationMs != null) {
                    sessions.findById(session.getId()).ifPresent(s -> s.addSpeakingTime(durationMs));
                }
            });
        } catch (DataIntegrityViolationException e) {
            // A concurrent retry stored this answer first; return what was stored.
            return replayIfAnswered(session, questionNumber).orElseThrow(() -> e);
        }
        return answerResponse(questionNumber, text, stats.wordsPerMinute(), stats.fillerCount(), reply, profile);
    }

    private Optional<AnswerResponse> replayIfAnswered(PracticeSession session, int questionNumber) {
        int answered = turns.countBySessionId(session.getId());
        if (questionNumber > answered + 1) {
            throw ApiException.conflict("out_of_order", "Answer the current question first.");
        }
        if (questionNumber > answered) {
            return Optional.empty();
        }
        Turn turn = turns.findBySessionIdAndSeq(session.getId(), questionNumber).orElseThrow(PlacementService::missing);
        LearnerProfile profile = profiles.get(session.getUserId());
        return Optional.of(answerResponse(questionNumber, turn.getLearnerText(), turn.getWordsPerMinute(),
                turn.getFillerCount() == null ? 0 : turn.getFillerCount(), turn.getReplyText(), profile));
    }

    private AnswerResponse answerResponse(int questionNumber, String transcript, Integer wpm, int fillers,
            String reply, LearnerProfile profile) {
        boolean done = questionNumber == PlacementScript.TOTAL;
        return new AnswerResponse(questionNumber, transcript, wpm, fillers, done, done ? null : questionNumber + 1,
                kaiLine(reply, profile));
    }

    private PracticeSession activeSession(UUID userId, UUID sessionId) {
        PracticeSession session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PlacementService::missing);
        if (session.getType() != SessionType.PLACEMENT || !session.isActive()) {
            throw missing();
        }
        return session;
    }

    private ResultResponse result(PlacementAssessment a, LearnerProfile profile) {
        return new ResultResponse(LevelInfo.of(a.level()), new Scores(a.fluency(), a.grammar(), a.vocabulary()),
                a.strengths(), a.focusAreas(), a.summary(), paths.pathFor(profile.getGoal(), a.level()));
    }

    private KaiPersona.LearnerContext learnerContext(UUID userId, LearnerProfile profile) {
        String name = users.findById(userId).map(User::getDisplayName).orElse(null);
        return new KaiPersona.LearnerContext(name, profile.getLevel(), profile.getGoal());
    }

    private KaiLine kaiLine(String text, LearnerProfile profile) {
        return new KaiLine(text, voice.speak(text, profile));
    }

    private static ApiException missing() {
        return ApiException.notFound("placement_missing", "That placement chat has ended. Start a new one.");
    }
}
