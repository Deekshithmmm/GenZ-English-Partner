package com.genzenglish.partner.path;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genzenglish.partner.grammar.GrammarCurriculum;
import com.genzenglish.partner.grammar.GrammarService;
import com.genzenglish.partner.path.TodayResponse.FocusUnit;
import com.genzenglish.partner.path.TodayResponse.PlanItem;
import com.genzenglish.partner.path.TodayResponse.PlanKind;
import com.genzenglish.partner.practice.PracticeProgress;
import com.genzenglish.partner.practice.WarmUpTopics;
import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.ProfileDtos.LevelInfo;
import com.genzenglish.partner.profile.ProfileService;

/**
 * Builds today's plan (warm-up, micro-lesson, roleplay, word review) sized to the learner's daily minutes. The warm-up
 * topic rotates by date and the micro-lesson is the learner's next grammar lesson (or the one they finished today);
 * items tick off as practice is completed.
 */
@Service
public class TodayService {

    private final ProfileService profiles;
    private final LearningPathService paths;
    private final PracticeProgress progress;
    private final GrammarService grammar;
    private final Clock clock;

    public TodayService(ProfileService profiles, LearningPathService paths, PracticeProgress progress,
            GrammarService grammar, Clock clock) {
        this.profiles = profiles;
        this.paths = paths;
        this.progress = progress;
        this.grammar = grammar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TodayResponse today(UUID userId) {
        LearnerProfile profile = profiles.get(userId);
        LocalDate date = LocalDate.now(clock.withZone(profile.zone()));
        int dailyMinutes = profile.getDailyMinutes() == null ? 10 : profile.getDailyMinutes();
        LearningPath path = paths.pathFor(profile.getGoal(), profile.getLevel());
        LearningPath.Unit unit = path.current();
        PracticeProgress.Snapshot done = progress.of(userId, date);
        // Once a lesson is finished today, the plan shows that one ticked off rather than moving on to the next.
        GrammarCurriculum.Node lesson = Optional.ofNullable(done.lastLessonToday())
                .flatMap(grammar::node)
                .or(() -> grammar.currentNode(userId, profile.getLevel()))
                .orElse(null);
        // Streak freezes aren't built yet, so there's never one to offer.
        return new TodayResponse(date, dailyMinutes, LevelInfo.of(path.level()),
                new FocusUnit(unit.id(), unit.title(), unit.summary()),
                markDone(plan(date, dailyMinutes, unit, lesson), done, unit),
                new TodayResponse.Streak(done.currentStreak(), done.longestStreak(), false, done.week()),
                new TodayResponse.Xp(done.xpToday(), done.xpTotal()));
    }

    /** Word review isn't built yet, so it never ticks off. */
    static List<PlanItem> markDone(List<PlanItem> plan, PracticeProgress.Snapshot done, LearningPath.Unit unit) {
        return plan.stream()
                .map(item -> switch (item.kind()) {
                    case WARM_UP -> item.withDone(done.warmUpDone());
                    case ROLEPLAY -> item.withDone(done.roleplaysDone().contains(unit.roleplay().slug()));
                    case MICRO_LESSON -> item.withDone(done.lessonDone());
                    case WORD_REVIEW -> item;
                })
                .toList();
    }

    /**
     * @param lesson the grammar lesson finished today, else the learner's next one; null once the whole path is done
     */
    static List<PlanItem> plan(LocalDate date, int dailyMinutes, LearningPath.Unit unit,
            GrammarCurriculum.Node lesson) {
        int[] split = switch (dailyMinutes) {
            case 5 -> new int[] {1, 1, 2, 1};
            case 20 -> new int[] {3, 4, 10, 3};
            default -> new int[] {2, 2, 4, 2};
        };
        String topic = WarmUpTopics.forDate(date);
        PlanItem micro = lesson == null
                ? new PlanItem("micro-lesson", PlanKind.MICRO_LESSON, "Review a grammar lesson",
                        "Your whole path is done. Pick any lesson to brush up.", split[1], "path", false)
                : new PlanItem("micro-lesson", PlanKind.MICRO_LESSON, lesson.title(),
                        "Grammar · Week " + lesson.week(), split[1], "lesson:" + lesson.id(), false);
        return List.of(
                new PlanItem("warm-up", PlanKind.WARM_UP, "Warm-up chat with Luna", topic, split[0], "warmup", false),
                micro,
                new PlanItem("roleplay", PlanKind.ROLEPLAY, unit.roleplay().title(), "Roleplay", split[2],
                        "roleplay:" + unit.roleplay().slug(), false),
                new PlanItem("word-review", PlanKind.WORD_REVIEW, "Review your words",
                        "Phrases you save while talking show up here.", split[3], "words", false));
    }
}
