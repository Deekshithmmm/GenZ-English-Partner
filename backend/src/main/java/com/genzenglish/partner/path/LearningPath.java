package com.genzenglish.partner.path;

import java.util.List;

import com.genzenglish.partner.profile.CefrLevel;
import com.genzenglish.partner.profile.LearningGoal;

public record LearningPath(LearningGoal goal, CefrLevel level, List<Unit> units) {

    public enum UnitStatus {
        CURRENT,
        UP_NEXT,
        LATER
    }

    public record Unit(String id, String title, String summary, List<String> skills, List<String> microLessons,
            RoleplayRef roleplay, UnitStatus status) {
    }

    public record RoleplayRef(String slug, String title) {
    }

    public Unit current() {
        return units.stream().filter(u -> u.status() == UnitStatus.CURRENT).findFirst().orElse(units.getFirst());
    }
}
