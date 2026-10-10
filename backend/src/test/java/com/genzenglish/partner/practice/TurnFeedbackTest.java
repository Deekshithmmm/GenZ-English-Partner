package com.genzenglish.partner.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.ai.pronunciation.PronunciationResult.ErrorType;
import com.genzenglish.partner.ai.pronunciation.PronunciationResult.WordScore;
import com.genzenglish.partner.practice.TurnFeedback.Correction;
import com.genzenglish.partner.practice.TurnFeedback.NewWord;
import com.genzenglish.partner.practice.TurnFeedback.Raw;
import com.genzenglish.partner.practice.TurnFeedback.RawCorrection;
import com.genzenglish.partner.practice.TurnFeedback.RawNewWord;
import com.genzenglish.partner.practice.TurnFeedback.SoundSlip;
import com.genzenglish.partner.profile.ExplanationLanguage;

/** Feedback from the model and from the built-in rules is shown to the learner, so both must be right or silent. */
class TurnFeedbackTest {

    @Test
    void commonSlipsAreCorrectedInTheOrderTheyWereSaid() {
        TurnFeedback feedback = CommonSlips.review("Myself Arjun. I am having two years experience in Java.");

        assertThat(feedback.corrections()).extracting(Correction::original, Correction::better)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Myself Arjun", "I'm Arjun"),
                        org.assertj.core.groups.Tuple.tuple("I am having", "I have"));
        assertThat(feedback.corrections().getFirst().why()).contains("\"I'm Arjun\" or \"My name is Arjun\"");
        assertThat(CommonSlips.review("Myself Meera from Pune.").corrections().getFirst().why())
                .contains("\"I'm Meera\"");
        Correction notAName = CommonSlips.review("I'm a student. Myself very interested in Java.").corrections()
                .getFirst();
        assertThat(notAName.better()).isEqualTo("I'm very");
        assertThat(notAName.why()).isEqualTo("\"Myself\" can't start a sentence. Start with \"I'm\" instead.");
    }

    @Test
    void commonSlipsCoverTenseTimeAndArticles() {
        assertThat(betterFor("The wifi is not working since two days.")).isEqualTo("hasn't been working for two days");
        assertThat(betterFor("I didn't knew the answer")).isEqualTo("didn't know");
        assertThat(betterFor("Today morning I had a interview")).isEqualTo("This morning");
        assertThat(betterFor("I had a interview")).isEqualTo("an interview");
        assertThat(betterFor("We discussed about the plan")).isEqualTo("discussed");
        assertThat(betterFor("I passed out from VTU in 2025")).isEqualTo("graduated from");
    }

    @Test
    void correctEnglishIsLeftAlone() {
        assertThat(CommonSlips.review("I am having lunch with a friend at a university cafe.").corrections())
                .isEmpty();
        assertThat(CommonSlips.review("Plan A is good, and I have been working here since Monday.").corrections())
                .isEmpty();
        assertThat(CommonSlips.review("  ").corrections()).isEmpty();
    }

    @Test
    void neverMoreThanTwoCorrections() {
        assertThat(CommonSlips.review("Myself Arjun. I didn't knew it. Today morning I discussed about it.")
                .corrections()).hasSize(TurnFeedback.MAX_CORRECTIONS);
    }

    @Test
    void modelCorrectionsMustQuoteTheLearnerAndChangeSomething() {
        TurnFeedback feedback = TurnFeedback.fromRaw(new Raw(List.of(
                new RawCorrection("I am having, two years", "I have two years", "Have isn't -ing here."),
                new RawCorrection("words they never said", "something else", "Invented."),
                new RawCorrection("went there", "Went there.", "Only punctuation changed."),
                new RawCorrection(" ", "blank", null)), "  Nice energy!  "),
                "I am having two years experience and I went there");

        assertThat(feedback).isNotNull();
        assertThat(feedback.corrections()).extracting(Correction::better).containsExactly("I have two years");
        assertThat(feedback.tip()).isEqualTo("Nice energy!");
    }

    @Test
    void modelOutputWithoutCorrectionsFallsBack() {
        assertThat(TurnFeedback.fromRaw(null, "anything")).isNull();
        assertThat(TurnFeedback.fromRaw(new Raw(null, "tip"), "anything")).isNull();
        assertThat(TurnFeedback.fromRaw(new Raw(List.of(), ""), "anything")).isEqualTo(TurnFeedback.none());
    }

    @Test
    void kaiRepliesAreCleanedForSpeaking() {
        assertThat(KaiTutor.sanitize("Luna: *smiles* That's great! 😊 What did you eat?", "fallback"))
                .isEqualTo("That's great! What did you eat?");
        assertThat(KaiTutor.sanitize("**Nice.** [laughs] Tell me more.", "fallback")).isEqualTo("Nice. Tell me more.");
        assertThat(KaiTutor.sanitize("  ", "fallback")).isEqualTo("fallback");
        assertThat(KaiTutor.sanitize(null, "fallback")).isEqualTo("fallback");

        String longReply = "That sounds great. ".repeat(30);
        String spoken = KaiTutor.sanitize(longReply, "fallback");
        assertThat(spoken).hasSizeLessThanOrEqualTo(KaiTutor.MAX_CHARS).endsWith(".");
    }

    @Test
    void theSmootherVersionAppliesTheFixesAndDropsHesitations() {
        TurnFeedback feedback = CommonSlips.review("Basically, um, I am having two years experience and I was very "
                + "tired after the exam.");

        assertThat(feedback.betterPhrasing())
                .isEqualTo("I have two years experience and I was exhausted after the exam.");
        assertThat(feedback.newWords()).containsExactly(new NewWord("exhausted", "extremely tired", "very tired"));
        assertThat(CommonSlips.review("We had a lot of fun at the beach.").betterPhrasing())
                .as("nothing to smooth").isNull();
    }

    @Test
    void wordUpgradesSkipNegativesAndSoThat() {
        assertThat(WordUpgrades.find("The food was really good.")).extracting(NewWord::word).containsExactly("excellent");
        assertThat(WordUpgrades.find("It was not very good.")).isEmpty();
        assertThat(WordUpgrades.find("It wasn't very good.")).isEmpty();
        assertThat(WordUpgrades.find("I was so tired that I slept early.")).isEmpty();
        assertThat(WordUpgrades.find(null)).isEmpty();
    }

    @Test
    void nativeExplanationsComeFromTheModelOrATemplate() {
        Raw raw = new Raw(List.of(new RawCorrection("since two years", "for two years", "Length of time.",
                "समय की अवधि के लिए \"for\" बोलिए।")), null, null, null);

        TurnFeedback hindi = TurnFeedback.fromRaw(raw, "I am learning since two years", ExplanationLanguage.HINDI);
        assertThat(hindi.corrections().getFirst().whyNative()).isEqualTo("समय की अवधि के लिए \"for\" बोलिए।");
        assertThat(TurnFeedback.fromRaw(raw, "I am learning since two years", ExplanationLanguage.ENGLISH)
                .corrections().getFirst().whyNative()).as("English learners don't get a second note").isNull();

        TurnFeedback filled = CommonSlips.review("I discussed about the plan").withNativeFallback(
                ExplanationLanguage.TAMIL);
        assertThat(filled.corrections().getFirst().whyNative())
                .startsWith("\"discussed about\" என்பதற்குப் பதிலாக \"discussed\"");
        assertThat(CommonSlips.review("I discussed about the plan").withNativeFallback(ExplanationLanguage.ENGLISH)
                .corrections().getFirst().whyNative()).isNull();
    }

    @Test
    void aNativeExplanationInTheWrongScriptIsReplacedByTheTemplate() {
        // A model once began a Kannada note with a Malayalam word ("ഇതുവരെ").
        Raw raw = new Raw(List.of(new RawCorrection("did not see it yet", "haven't seen it yet", "Up to now.",
                "ഇതുവരെ ಆಗಿರುವ ಕೆಲಸದ ಬಗ್ಗೆ ಹೇಳಲು 'haven't seen it yet' ಬಳಸುವುದು ಸೂಕ್ತ.")), null, null, null);

        TurnFeedback kannada = TurnFeedback.fromRaw(raw, "They did not see it yet", ExplanationLanguage.KANNADA)
                .withNativeFallback(ExplanationLanguage.KANNADA);

        String why = kannada.corrections().getFirst().whyNative();
        assertThat(why).doesNotContain("ഇതുവരെ");
        assertThat(TurnFeedback.writtenIn(why, ExplanationLanguage.KANNADA)).isTrue();
    }

    @Test
    void nativeExplanationsMayQuoteEnglishButNotOtherScripts() {
        assertThat(TurnFeedback.writtenIn("ಇಲ್ಲಿ 'scenes' ಬಹುವಚನ ಆಗಿರುವುದರಿಂದ 'were' ಬಳಸಿ.", ExplanationLanguage.KANNADA))
                .isTrue();
        assertThat(TurnFeedback.writtenIn("समय की अवधि के लिए \"for\" बोलिए।", ExplanationLanguage.HINDI)).isTrue();
        assertThat(TurnFeedback.writtenIn("Use 'were' because scenes is plural.", ExplanationLanguage.KANNADA))
                .as("all English is not a Kannada explanation").isFalse();
        assertThat(TurnFeedback.writtenIn("ಇದು தமிழ் word", ExplanationLanguage.KANNADA)).isFalse();
    }

    @Test
    void modelRewritesAndNewWordsMustFitWhatWasSaid() {
        String said = "I was very tired after the long journey";
        TurnFeedback feedback = TurnFeedback.fromRaw(new Raw(List.of(), null,
                "“I was exhausted after the long journey.”",
                List.of(new RawNewWord("tired", "already used", null),
                        new RawNewWord("exhausted", "extremely tired", "very tired"),
                        new RawNewWord("weary", "tired", "words they never said"),
                        new RawNewWord("knackered", "very tired (British)", null))), said);

        assertThat(feedback.betterPhrasing()).isEqualTo("I was exhausted after the long journey.");
        assertThat(feedback.newWords()).containsExactly(new NewWord("exhausted", "extremely tired", "very tired"),
                new NewWord("weary", "tired", null));
        assertThat(TurnFeedback.fromRaw(new Raw(List.of(), null, "I was very tired after the long journey.", null),
                said).betterPhrasing()).as("the same words aren't a rewrite").isNull();
    }

    @Test
    void theSoundCheckFlagsTheLowestContentWordsOnce() {
        List<SoundSlip> slips = SoundCheck.unclearWords(List.of(
                word("the", 20, ErrorType.MISPRONUNCIATION),
                word("three", 52, ErrorType.MISPRONUNCIATION),
                word("which", 30, ErrorType.MISPRONUNCIATION),
                word("very", 41, ErrorType.MISPRONUNCIATION),
                word("weather", 44, ErrorType.MISPRONUNCIATION),
                word("Three,", 47, ErrorType.MISPRONUNCIATION),
                word("project", 90, ErrorType.NONE),
                word("missing", 0, ErrorType.OMISSION),
                word("thirty", 58, ErrorType.MISPRONUNCIATION)));

        assertThat(slips).containsExactly(new SoundSlip("weather", 44), new SoundSlip("three", 47));
    }

    private static WordScore word(String text, int score, ErrorType error) {
        return new WordScore(text, score, error, List.of());
    }

    private static String betterFor(String said) {
        return CommonSlips.review(said).corrections().getFirst().better();
    }
}
