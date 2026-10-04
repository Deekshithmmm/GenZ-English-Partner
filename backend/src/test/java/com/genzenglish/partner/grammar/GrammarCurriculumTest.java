package com.genzenglish.partner.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.genzenglish.partner.grammar.GrammarCurriculum.Drill;
import com.genzenglish.partner.grammar.GrammarCurriculum.Kind;
import com.genzenglish.partner.grammar.GrammarCurriculum.Node;
import com.genzenglish.partner.grammar.GrammarCurriculum.Placed;
import com.genzenglish.partner.grammar.GrammarCurriculum.Round;
import com.genzenglish.partner.grammar.GrammarCurriculum.Topic;
import com.genzenglish.partner.grammar.GrammarDtos.Exercise;
import com.genzenglish.partner.grammar.GrammarDtos.ExerciseAnswer;
import com.genzenglish.partner.grammar.GrammarDtos.ExerciseType;
import com.genzenglish.partner.grammar.GrammarDtos.SpeakType;
import com.genzenglish.partner.grammar.GrammarLessons.Plan;
import com.genzenglish.partner.profile.CefrLevel;
import com.genzenglish.partner.profile.ExplanationLanguage;

import tools.jackson.databind.json.JsonMapper;

/** The curriculum is content, so these tests are its proofreading: structure, order, and checks that work. */
class GrammarCurriculumTest {

    private final GrammarCurriculum curriculum = new GrammarCurriculum(JsonMapper.builder().build());

    @Test
    void fortySixTopicsMakeFortySixWeeks() {
        assertThat(curriculum.units()).extracting(GrammarCurriculum.Unit::level).containsExactly(CefrLevel.A1,
                CefrLevel.A2, CefrLevel.A2, CefrLevel.B1, CefrLevel.B1, CefrLevel.B2, CefrLevel.B2, CefrLevel.C1);
        assertThat(curriculum.topics()).hasSize(46);
        // Learn and booster for every topic, a revision for all but the last, and a master round for the first 41.
        assertThat(curriculum.nodes()).hasSize(46 * 2 + 45 + 41);
        assertThat(curriculum.nodes().getFirst().id()).isEqualTo("learn.be-verb");
        assertThat(curriculum.nodes().get(1).id()).isEqualTo("boost.be-verb");
        assertThat(curriculum.nodes().get(2).id()).isEqualTo("learn.introduce-yourself");
        assertThat(curriculum.nodes().get(1).title()).isEqualTo("Fluency booster: Am, is, are: the verb be");
        assertThat(curriculum.nodes().getLast().week()).isEqualTo(46);
        Set<String> ids = new HashSet<>();
        assertThat(curriculum.nodes()).allSatisfy(n -> assertThat(ids.add(n.id())).isTrue());
        for (int week = 1; week <= 46; week++) {
            int w = week;
            List<Node> nodes = curriculum.nodes().stream().filter(n -> n.week() == w).toList();
            assertThat(nodes.getFirst().kind()).as("week %d starts with a new topic", week).isEqualTo(Kind.LEARN);
            assertThat(nodes.getFirst().placed().index()).isEqualTo(week - 1);
            assertThat(nodes).hasSizeBetween(2, 5);
        }
    }

    @Test
    void eachTopicComesBackToBeRevisedAndThenMastered() {
        Map<String, Map<Kind, Integer>> weeks = new HashMap<>();
        for (Node node : curriculum.nodes()) {
            weeks.computeIfAbsent(node.placed().topic().id(), id -> new HashMap<>()).put(node.kind(), node.week());
        }
        for (Placed placed : curriculum.topics()) {
            Map<Kind, Integer> topic = weeks.get(placed.topic().id());
            int learnt = placed.index() + 1;
            assertThat(topic.get(Kind.LEARN)).isEqualTo(learnt);
            assertThat(topic.get(Kind.BOOST)).isEqualTo(learnt);
            if (learnt <= 44) {
                assertThat(topic.get(Kind.REVIEW)).as("revise %s", placed.topic().id()).isEqualTo(learnt + 2);
            }
            if (learnt <= 41) {
                assertThat(topic.get(Kind.MASTER)).as("master %s", placed.topic().id()).isEqualTo(learnt + 5);
            }
        }
        // The second-to-last topic is revised in the last week; the last one only gets its first two lessons.
        assertThat(weeks.get("phrasal-verbs").get(Kind.REVIEW)).isEqualTo(46);
        assertThat(weeks.get("question-tags")).containsOnlyKeys(Kind.LEARN, Kind.BOOST);
        assertThat(curriculum.node("review.past-simple").orElseThrow().title())
                .isEqualTo("Revise: What happened: the past simple");
        assertThat(curriculum.node("master.past-simple").orElseThrow().topic().id()).isEqualTo("past-simple#3");
    }

    @Test
    void learnersStartAtTheUnitForTheirLevel() {
        assertThat(curriculum.startNode(null)).isZero();
        assertThat(curriculum.startNode(CefrLevel.A1)).isZero();
        assertThat(curriculum.nodes().get(curriculum.startNode(CefrLevel.A2)).id()).isEqualTo("learn.present-simple");
        assertThat(curriculum.nodes().get(curriculum.startNode(CefrLevel.B1)).id()).isEqualTo("learn.past-simple");
        assertThat(curriculum.nodes().get(curriculum.startNode(CefrLevel.B2)).id()).isEqualTo("learn.polite-requests");
        assertThat(curriculum.nodes().get(curriculum.startNode(CefrLevel.C1)).id())
                .isEqualTo("learn.third-conditional");
        assertThat(curriculum.startNode(CefrLevel.B1)).isEqualTo(65);
    }

    @Test
    void everySampleAndCorrectionPassesItsOwnCheck() {
        Set<String> checked = new HashSet<>();
        // Soft: one run lists every problem in the content, not just the first.
        SoftAssertions.assertSoftly(softly -> {
            for (Node node : curriculum.nodes()) {
                Topic topic = node.topic();
                if (!checked.add(topic.id())) {
                    continue;
                }
                softly.assertThat(curriculum.usesRule(topic.id(), topic.speak().sample()))
                        .as("speaking sample for %s: %s", topic.id(), topic.speak().sample()).isTrue();
                for (int i = 0; i < topic.questions().size(); i++) {
                    softly.assertThat(curriculum.answers(topic.id(), i, topic.questions().get(i).sample()))
                            .as("question %d sample for %s: %s", i, topic.id(), topic.questions().get(i).sample())
                            .isTrue();
                }
                for (Drill fix : topic.fixes()) {
                    softly.assertThat(curriculum.fixes(topic.id(), fix.right(), fix.wrong(), fix.right()))
                            .as("fix for %s: %s", topic.id(), fix.right()).isTrue();
                    softly.assertThat(curriculum.fixes(topic.id(), fix.wrong(), fix.wrong(), fix.right()))
                            .as("the slip itself for %s: %s", topic.id(), fix.wrong()).isFalse();
                }
            }
        });
        assertThat(checked).hasSize(46 + 45 + 41);
    }

    @Test
    void fixChecksNeedTheCorrectionButAllowNaturalWording() {
        assertThat(curriculum.fixes("articles", "He is engineer.", "He is engineer.", "He is an engineer.")).isFalse();
        assertThat(curriculum.fixes("articles", "He's an engineer.", "He is engineer.", "He is an engineer.")).isTrue();
        assertThat(curriculum.fixes("introduce-yourself", "I am Kavya from Hubli", "Myself Kavya, from Hubli.",
                "I'm Kavya, from Hubli.")).isTrue();
        assertThat(curriculum.fixes("phrasal-verbs", "Please get back to me by evening.",
                "Please revert back by evening.", "Please get back to me by this evening.")).isTrue();
        assertThat(curriculum.fixes("question-tags", "You are from Pune, isn't it?", "You are from Pune, isn't it?",
                "You're from Pune, aren't you?")).isFalse();
        assertThat(curriculum.fixes("articles", "", "He is engineer.", "He is an engineer.")).isFalse();
    }

    @Test
    void speakingChecksCatchTheSlipEachTopicTeaches() {
        assertThat(curriculum.usesRule("introduce-yourself", "Myself Ananya from Pune.")).isFalse();
        assertThat(curriculum.usesRule("introduce-yourself", "I'm Ananya, from Pune.")).isTrue();
        assertThat(curriculum.usesRule("for-since", "I am working here since two years.")).isFalse();
        assertThat(curriculum.usesRule("for-since", "I've been working here for two years.")).isTrue();
        assertThat(curriculum.usesRule("linking-words", "Although it was raining, but we went out.")).isFalse();
        assertThat(curriculum.usesRule("linking-words", "Although I was tired, I went. But it was fun.")).isTrue();
        assertThat(curriculum.usesRule("introduce-yourself", "I'm a student. Myself very interested in Java."))
                .as("a slip in a later sentence still counts").isFalse();
        assertThat(curriculum.answers("time-prepositions", 2, "I study best in night.")).isFalse();
        assertThat(curriculum.answers("time-prepositions", 2, "I study best at night.")).isTrue();
        assertThat(curriculum.answers("time-prepositions", 99, "Anything")).isFalse();
        assertThat(curriculum.usesRule("no-such-topic", "Anything")).isFalse();
        // New topics, and a later round, catch their own slips (and the topic's).
        assertThat(curriculum.usesRule("make-do", "I did a big mistake yesterday.")).isFalse();
        assertThat(curriculum.usesRule("make-do", "I made a mistake, and then I did my homework.")).isTrue();
        assertThat(curriculum.usesRule("indian-english", "I'm Ravi. I passed out from college in 2023.")).isFalse();
        assertThat(curriculum.usesRule("indian-english", "I'm Ravi. I graduated from college in 2023.")).isTrue();
        assertThat(curriculum.usesRule("past-simple#2", "We went there, but I didn't saw anything.")).isFalse();
        assertThat(curriculum.usesRule("past-simple#2", "We reached early and met the team.")).isTrue();
    }

    @Test
    void everyQuizHasOneRightAnswerAmongDistinctOptions() {
        for (Placed placed : curriculum.topics()) {
            assertThat(placed.topic().quiz()).as(placed.topic().id()).hasSize(4);
            for (Round round : placed.topic().rounds()) {
                assertThat(round.quiz()).as("%s round", placed.topic().id()).hasSize(3);
            }
        }
        for (Node node : curriculum.nodes()) {
            assertThat(node.topic().quiz()).allSatisfy(q -> {
                assertThat(q.answer()).isBetween(0, q.options().size() - 1);
                assertThat(new HashSet<>(q.options())).hasSameSizeAs(q.options());
            });
        }
    }

    @Test
    void aLearnLessonTeachesThenTestsThenSpeaks() {
        Node learn = curriculum.node("learn.introduce-yourself").orElseThrow();
        Plan plan = GrammarLessons.plan(learn, ExplanationLanguage.KANNADA);

        assertThat(plan).isEqualTo(GrammarLessons.plan(learn, ExplanationLanguage.KANNADA));
        assertThat(plan.intro()).hasSize(4);
        assertThat(plan.intro().getFirst().text()).isEqualTo("Let's learn \"Introducing yourself\" today!");
        assertThat(plan.intro().getFirst().nativeText()).startsWith("ಇಂದು ನಾವು \"Introducing yourself\"");
        assertThat(plan.exercises()).extracting(Exercise::type).containsExactly(ExerciseType.CHOICE,
                ExerciseType.CHOICE, ExerciseType.CHOICE, ExerciseType.CHOICE, ExerciseType.BUILD, ExerciseType.BUILD);
        Exercise build = plan.exercises().get(4);
        assertThat(build.prompt()).isEqualTo("Myself Arjun from Mysuru.");
        assertThat(build.words()).containsExactly("I'm", "Arjun", "and", "I'm", "from", "Mysuru");
        assertThat(build.tiles()).containsAll(build.words()).contains("Myself").hasSize(7);
        assertThat(plan.speakItems()).extracting(i -> i.type()).containsExactly(SpeakType.FIX, SpeakType.FIX,
                SpeakType.ANSWER);
        assertThat(GrammarLessons.plan(learn, ExplanationLanguage.ENGLISH).intro().getFirst().nativeText()).isNull();
    }

    @Test
    void aFluencyBoosterIsAllSpeaking() {
        Node boost = curriculum.node("boost.past-simple").orElseThrow();
        assertThat(boost.kind()).isEqualTo(Kind.BOOST);
        Plan plan = GrammarLessons.plan(boost, ExplanationLanguage.HINDI);

        assertThat(plan.exercises()).isEmpty();
        assertThat(plan.speakItems()).extracting(i -> i.type()).containsExactly(SpeakType.FIX, SpeakType.FIX,
                SpeakType.FIX, SpeakType.FIX, SpeakType.ANSWER, SpeakType.ANSWER, SpeakType.ANSWER, SpeakType.ANSWER,
                SpeakType.OPEN);
        assertThat(plan.phrases().right().nativeText()).isEqualTo("बहुत बढ़िया! आपने सही कहा।");
    }

    @Test
    void aRevisionUsesTheRoundsNewQuestions() {
        Node review = curriculum.node("review.articles").orElseThrow();
        Plan plan = GrammarLessons.plan(review, ExplanationLanguage.KANNADA);

        assertThat(review.topic().id()).isEqualTo("articles#2");
        assertThat(plan.intro()).hasSize(3);
        assertThat(plan.intro().getFirst().text()).startsWith("Revision time! Let's practise \"A, an and the\" again");
        assertThat(plan.intro().getFirst().nativeText()).startsWith("ಪುನರಾವರ್ತನೆಯ ಸಮಯ!");
        assertThat(plan.exercises()).extracting(Exercise::type).containsExactly(ExerciseType.CHOICE,
                ExerciseType.CHOICE, ExerciseType.CHOICE, ExerciseType.BUILD, ExerciseType.BUILD);
        assertThat(plan.exercises().getFirst().prompt()).isEqualTo(review.topic().quiz().getFirst().prompt())
                .isNotEqualTo(curriculum.topic("articles").orElseThrow().topic().quiz().getFirst().prompt());
        assertThat(plan.exercises().get(3).prompt()).isEqualTo("I need an pen.");
        assertThat(plan.speakItems()).extracting(i -> i.type()).containsExactly(SpeakType.FIX, SpeakType.ANSWER,
                SpeakType.ANSWER);
        assertThat(review.topic().chart()).isEqualTo(curriculum.topic("articles").orElseThrow().topic().chart());
    }

    @Test
    void aMasterRoundIsHarderAndSpeaksMore() {
        Plan plan = GrammarLessons.plan(curriculum.node("master.past-simple").orElseThrow(), ExplanationLanguage.ENGLISH);

        assertThat(plan.intro().getFirst().text()).startsWith("Master it!");
        assertThat(plan.exercises()).extracting(Exercise::type).containsExactly(ExerciseType.CHOICE,
                ExerciseType.CHOICE, ExerciseType.CHOICE, ExerciseType.BUILD);
        assertThat(plan.speakItems()).extracting(i -> i.type()).containsExactly(SpeakType.FIX, SpeakType.FIX,
                SpeakType.ANSWER, SpeakType.ANSWER, SpeakType.OPEN);
    }

    @Test
    void exercisesAreMarkedOnTheServerToo() {
        Plan plan = GrammarLessons.plan(curriculum.node("learn.articles").orElseThrow(), ExplanationLanguage.ENGLISH);
        Exercise choice = plan.exercises().getFirst();
        Exercise build = plan.exercises().get(4);

        assertThat(GrammarService.marked(choice, new ExerciseAnswer(choice.answer(), null))).isTrue();
        assertThat(GrammarService.marked(choice, new ExerciseAnswer((choice.answer() + 1) % 3, null))).isFalse();
        assertThat(GrammarService.marked(choice, null)).isFalse();
        assertThat(GrammarService.marked(build, new ExerciseAnswer(null,
                build.words().stream().map(String::toLowerCase).toList()))).as("case doesn't matter").isTrue();
        assertThat(GrammarService.marked(build, new ExerciseAnswer(null, build.words().reversed()))).isFalse();
    }

    @Test
    void starsFollowTheScore() {
        assertThat(GrammarService.stars(100)).isEqualTo(3);
        assertThat(GrammarService.stars(90)).isEqualTo(3);
        assertThat(GrammarService.stars(75)).isEqualTo(2);
        assertThat(GrammarService.stars(55)).isEqualTo(1);
    }

    @Test
    void brokenContentFailsAtStartup() {
        Topic good = curriculum.topics().getFirst().topic();
        Topic badAnswer = new Topic("bad", good.title(), good.icon(), good.summary(), good.why(), good.rules(),
                good.examples(), good.chart(),
                List.of(new GrammarCurriculum.Question("Pick one", List.of("a", "b"), 5, "Why."), good.quiz().get(1),
                        good.quiz().get(2)),
                good.drills(), good.questions(), good.speak(), List.of());
        assertThatThrownBy(() -> new GrammarCurriculum(List.of(new GrammarCurriculum.Unit("u", "Unit", "Sub",
                CefrLevel.A2, "seedling", List.of(badAnswer))))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quiz question");
        assertThatThrownBy(() -> new GrammarCurriculum(List.of(new GrammarCurriculum.Unit("u", "Unit", "Sub",
                CefrLevel.A2, "seedling", List.of(good, good))))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate topic");
        Round thin = new Round(good.quiz(), good.drills().subList(0, 1), good.questions(), good.speak());
        Topic badRound = new Topic("thin", good.title(), good.icon(), good.summary(), good.why(), good.rules(),
                good.examples(), good.chart(), good.quiz(), good.drills(), good.questions(), good.speak(), List.of(thin));
        assertThatThrownBy(() -> new GrammarCurriculum(List.of(new GrammarCurriculum.Unit("u", "Unit", "Sub",
                CefrLevel.A2, "seedling", List.of(badRound))))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("round 2").hasMessageContaining("fix-it sentences");
    }
}
