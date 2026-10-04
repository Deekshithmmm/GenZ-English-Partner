package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.practice.PracticeDtos.CatalogResponse;
import com.genzenglish.partner.practice.PracticeDtos.RoleplayItem;
import com.genzenglish.partner.practice.PracticeDtos.TopicItem;

/** Luna's streamed words, hints and openings are shown to the learner as-is, so they're checked here. */
class KaiLinesTest {

    @Test
    void streamedWordsHideUnfinishedActionsButKeepEverythingElse() {
        assertThat(KaiTutor.sanitizePartial("*smi")).isEmpty();
        assertThat(KaiTutor.sanitizePartial("*smiles* That's gre")).isEqualTo("That's gre");
        assertThat(KaiTutor.sanitizePartial("**Nice.** Tell me [laugh")).isEqualTo("Nice. Tell me");
        assertThat(KaiTutor.sanitizePartial("**Grea")).isEqualTo("Grea");
        assertThat(KaiTutor.sanitizePartial("Luna: Oh nice")).isEqualTo("Oh nice");
    }

    @Test
    void hintsAreTrimmedDeduplicatedAndCapped() {
        List<String> ideas = KaiHinter.clean(new KaiHinter.Raw(List.of(" \"I went to Goa.\" ", "I went to Goa.", " ",
                "x".repeat(KaiHinter.MAX_IDEA_CHARS + 1), "I stayed home and slept.", "A third idea.")));

        assertThat(ideas).containsExactly("I went to Goa.", "I stayed home and slept.");
        assertThat(KaiHinter.clean(null)).isEmpty();
        assertThat(KaiHinter.clean(new KaiHinter.Raw(null))).isEmpty();
    }

    @Test
    void sentenceStartersFitKaisQuestion() {
        assertThat(KaiHinter.starters("Why do you like it?")).first().isEqualTo("I think the main reason is…");
        assertThat(KaiHinter.starters("What did you do last weekend?")).first().isEqualTo("Well, last time I…");
        assertThat(KaiHinter.starters("If you could live anywhere, where?")).first().isEqualTo("I think I would…");
        assertThat(KaiHinter.starters(null)).hasSize(2);
    }

    @Test
    void freeTalkOpensWithAGreetingAndTheTopicsQuestion() {
        assertThat(PracticeService.freeTalkOpening("Meera", FreeTalkTopics.find("music").orElseThrow()))
                .isEqualTo("Hey Meera! What have you been listening to on repeat lately?");
        assertThat(PracticeService.freeTalkOpening(null, FreeTalkTopics.OPEN))
                .isEqualTo("Hey! What's on your mind today? We can talk about anything.");
    }

    @Test
    void catalogListsEveryTopicAndRoleplayOnce() {
        CatalogResponse catalog = PracticeService.catalog();

        assertThat(catalog.topics()).first().extracting(TopicItem::slug).isEqualTo(FreeTalkTopics.OPEN.slug());
        assertThat(catalog.topics()).extracting(TopicItem::slug).doesNotHaveDuplicates().hasSize(12);
        assertThat(catalog.roleplays()).extracting(RoleplayItem::slug).doesNotHaveDuplicates().hasSize(12);
        assertThat(catalog.roleplays()).allSatisfy(r -> {
            assertThat(r.difficulty()).isNotNull();
            assertThat(r.targetTurns()).isBetween(1, PracticeDtos.MAX_TURNS);
        });
        assertThat(PracticeKind.FREE_TALK.defaultTargetTurns()).isLessThanOrEqualTo(PracticeDtos.MAX_TURNS);
    }
}
