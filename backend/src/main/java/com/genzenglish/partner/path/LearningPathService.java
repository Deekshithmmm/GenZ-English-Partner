package com.genzenglish.partner.path;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.genzenglish.partner.path.LearningPath.RoleplayRef;
import com.genzenglish.partner.path.LearningPath.Unit;
import com.genzenglish.partner.path.LearningPath.UnitStatus;
import com.genzenglish.partner.path.PathCatalog.UnitTemplate;
import com.genzenglish.partner.profile.CefrLevel;
import com.genzenglish.partner.profile.LearningGoal;

/**
 * Builds a learner's path from their goal and level: beginners get a foundation unit first, advanced learners get a
 * polish unit at the end.
 */
@Service
public class LearningPathService {

    public LearningPath pathFor(LearningGoal goal, CefrLevel level) {
        LearningGoal effectiveGoal = goal == null ? LearningGoal.EVERYDAY_CONFIDENCE : goal;
        CefrLevel effectiveLevel = level == null ? CefrLevel.B1 : level;

        List<UnitTemplate> templates = new ArrayList<>();
        if (effectiveLevel.atMost(CefrLevel.A2)) {
            templates.add(PathCatalog.FOUNDATION);
        }
        templates.addAll(PathCatalog.BY_GOAL.get(effectiveGoal));
        if (effectiveLevel.atLeast(CefrLevel.B2)) {
            templates.add(PathCatalog.POLISH);
        }

        List<Unit> units = new ArrayList<>(templates.size());
        for (int i = 0; i < templates.size(); i++) {
            UnitTemplate t = templates.get(i);
            UnitStatus status = i == 0 ? UnitStatus.CURRENT : i == 1 ? UnitStatus.UP_NEXT : UnitStatus.LATER;
            units.add(new Unit(t.id(), t.title(), t.summary(), t.skills(), t.microLessons(),
                    new RoleplayRef(t.roleplaySlug(), t.roleplayTitle()), status));
        }
        return new LearningPath(effectiveGoal, effectiveLevel, List.copyOf(units));
    }
}
