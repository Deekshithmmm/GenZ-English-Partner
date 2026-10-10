package com.genzenglish.partner.practice;

import java.util.ArrayList;
import java.util.List;

import com.genzenglish.partner.practice.PracticeDtos.XpItem;

/**
 * XP for a finished session. Simple enough to explain on the summary screen: every answer counts, speaking it out loud
 * counts more, and finishing (and reaching a roleplay's goal) earns a bonus.
 */
final class PracticeRewards {

    static final int PER_ANSWER = 10;
    static final int SPOKEN_BONUS = 5;
    static final int FINISH_BONUS = 20;
    static final int GOAL_BONUS = 15;

    private PracticeRewards() {
    }

    record Reward(int total, List<XpItem> items) {
    }

    static Reward of(SessionStats stats, boolean finished) {
        return of(stats, finished, false);
    }

    /**
     * @param finished    the chat ran to its end (Luna wrapped up) rather than being finished early
     * @param goalReached every success criterion of a roleplay or interview was met
     */
    static Reward of(SessionStats stats, boolean finished, boolean goalReached) {
        List<XpItem> items = new ArrayList<>();
        items.add(new XpItem(stats.answers() == 1 ? "1 answer" : stats.answers() + " answers",
                stats.answers() * PER_ANSWER));
        if (stats.voiceAnswers() > 0) {
            items.add(new XpItem("Spoken out loud", stats.voiceAnswers() * SPOKEN_BONUS));
        }
        if (finished) {
            items.add(new XpItem("Finished the session", FINISH_BONUS));
        }
        if (goalReached) {
            items.add(new XpItem("Reached the goal", GOAL_BONUS));
        }
        return new Reward(items.stream().mapToInt(XpItem::xp).sum(), List.copyOf(items));
    }
}
