package com.genzenglish.partner.path;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.grammar.GrammarCurriculum;
import com.genzenglish.partner.path.LearningPath.UnitStatus;
import com.genzenglish.partner.path.TodayResponse.PlanItem;
import com.genzenglish.partner.path.TodayResponse.PlanKind;
import com.genzenglish.partner.practice.PracticeProgress;
import com.genzenglish.partner.practice.RoleplayCatalog;
import com.genzenglish.partner.profile.CefrLevel;
import com.genzenglish.partner.profile.LearningGoal;

import tools.jackson.databind.json.JsonMapper;

class LearningPathServiceTest {

    private static final List<Boolean> WEEK = List.of(false, false, true, false, false, false, false);

    private final LearningPathService service = new LearningPathService();

    @Test
    void beginnersStartWithTheFoundationUnit() {
        LearningPath path = service.pathFor(LearningGoal.JOB_INTERVIEWS, CefrLevel.A2);

        assertThat(path.units().getFirst().id()).isEqualTo("find-your-voice");
        assertThat(path.units().getFirst().status()).isEqualTo(UnitStatus.CURRENT);
        assertThat(path.units().get(1).status()).isEqualTo(UnitStatus.UP_NEXT);
        assertThat(path.units()).noneMatch(u -> u.id().equals("sound-sharp"));
    }

    @Test
    void advancedLearnersGetThePolishUnitAtTheEnd() {
        LearningPath path = service.pathFor(LearningGoal.CASUAL_SOCIAL, CefrLevel.C1);

        assertThat(path.units().getFirst().id()).isEqualTo("start-a-conversation");
        assertThat(path.units().getLast().id()).isEqualTo("sound-sharp");
    }

    @Test
    void everyGoalHasAFullPathWithRoleplays() {
        for (LearningGoal goal : LearningGoal.values()) {
            LearningPath path = service.pathFor(goal, CefrLevel.B1);
            assertThat(path.units()).hasSize(6);
            assertThat(path.units()).allSatisfy(unit -> {
                assertThat(unit.microLessons()).isNotEmpty();
                assertThat(unit.roleplay().slug()).isNotBlank();
            });
        }
    }

    @Test
    void dailyPlanFitsTheLearnersTime() {
        LearningPath.Unit unit = service.pathFor(LearningGoal.JOB_INTERVIEWS, CefrLevel.B1).current();
        for (int minutes : new int[] {5, 10, 20}) {
            List<PlanItem> plan = TodayService.plan(LocalDate.of(2026, 9, 30), minutes, unit, null);
            assertThat(plan).extracting(PlanItem::kind).containsExactly(PlanKind.WARM_UP, PlanKind.MICRO_LESSON,
                    PlanKind.ROLEPLAY, PlanKind.WORD_REVIEW);
            assertThat(plan.stream().mapToInt(PlanItem::minutes).sum()).isEqualTo(minutes);
        }
    }

    @Test
    void warmUpTopicRotatesDaily() {
        LearningPath.Unit unit = service.pathFor(LearningGoal.EVERYDAY_CONFIDENCE, CefrLevel.B1).current();
        String monday = TodayService.plan(LocalDate.of(2026, 9, 28), 10, unit, null).getFirst().subtitle();
        String tuesday = TodayService.plan(LocalDate.of(2026, 9, 29), 10, unit, null).getFirst().subtitle();
        assertThat(monday).isNotEqualTo(tuesday);
    }

    @Test
    void everyRoleplayOnAPathExistsInTheCatalog() {
        for (LearningGoal goal : LearningGoal.values()) {
            for (CefrLevel level : CefrLevel.values()) {
                assertThat(service.pathFor(goal, level).units()).allSatisfy(unit ->
                        assertThat(RoleplayCatalog.find(unit.roleplay().slug()))
                                .as("roleplay %s", unit.roleplay().slug())
                                .hasValueSatisfying(r -> assertThat(r.title()).isEqualTo(unit.roleplay().title())));
            }
        }
    }

    @Test
    void completedPracticeTicksOffTheMatchingPlanItems() {
        LearningPath.Unit unit = service.pathFor(LearningGoal.JOB_INTERVIEWS, CefrLevel.B1).current();
        List<PlanItem> plan = TodayService.plan(LocalDate.of(2026, 9, 30), 10, unit, null);

        List<PlanItem> warmedUp = TodayService.markDone(plan,
                new PracticeProgress.Snapshot(1, 1, 50, 50, true, Set.of("some-other-roleplay"), null, WEEK), unit);
        assertThat(warmedUp).extracting(PlanItem::done).containsExactly(true, false, false, false);

        List<PlanItem> all = TodayService.markDone(plan,
                new PracticeProgress.Snapshot(1, 1, 120, 120, true, Set.of(unit.roleplay().slug()), "learn.past-simple",
                        WEEK),
                unit);
        assertThat(all).extracting(PlanItem::done).containsExactly(true, true, true, false);
    }

    @Test
    void theMicroLessonIsTheNextGrammarLesson() {
        LearningPath.Unit unit = service.pathFor(LearningGoal.JOB_INTERVIEWS, CefrLevel.B1).current();
        GrammarCurriculum curriculum = new GrammarCurriculum(JsonMapper.builder().build());
        GrammarCurriculum.Node next = curriculum.nodes().get(curriculum.startNode(CefrLevel.B1));

        PlanItem lesson = TodayService.plan(LocalDate.of(2026, 9, 30), 10, unit, next).get(1);
        assertThat(lesson.title()).isEqualTo("Learn: What happened: the past simple");
        assertThat(lesson.subtitle()).isEqualTo("Grammar · Week 19");
        assertThat(lesson.action()).isEqualTo("lesson:learn.past-simple");

        PlanItem allDone = TodayService.plan(LocalDate.of(2026, 9, 30), 10, unit, null).get(1);
        assertThat(allDone.action()).isEqualTo("path");
    }
}
