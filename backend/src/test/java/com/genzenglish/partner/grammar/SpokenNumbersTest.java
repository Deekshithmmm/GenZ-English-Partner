package com.genzenglish.partner.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.genzenglish.partner.practice.TurnFeedback;

class SpokenNumbersTest {

    private static List<String> digits(String text) {
        return SpokenNumbers.asDigits(List.of(TurnFeedback.normalize(text).split(" ")));
    }

    @Test
    void numbersInWordsAndInDigitsComeOutTheSame() {
        // What the lesson says, and what speech recognition wrote down when it was said aloud.
        assertThat(digits("I'm nineteen, and my brother is twenty-two.")).isEqualTo(digits("I'm 19 and my brother is 22"));
        assertThat(digits("The class starts at nine a.m.")).isEqualTo(digits("The class starts at 9:00 AM"));
        assertThat(digits("The class starts at 9 am.")).isEqualTo(digits("The class starts at 9:00 AM"));
        assertThat(digits("I was born in two thousand four.")).isEqualTo(digits("I was born in 2004"));
        assertThat(digits("My birthday is on the twelfth of March.")).isEqualTo(digits("My birthday is on the 12th of March"));
        assertThat(digits("I finished school in twenty twenty-two.")).isEqualTo(digits("I finished school in 2022"));
        assertThat(digits("It costs one hundred and twenty rupees.")).isEqualTo(digits("It costs 120 rupees"));
        assertThat(digits("I'm in my second year.")).isEqualTo(digits("I'm in my 2nd year"));
    }

    @Test
    void separateNumbersStaySeparate() {
        assertThat(digits("I have two sisters and one brother")).containsExactly("i", "have", "2", "sisters", "and",
                "1", "brother");
        assertThat(digits("We meet at six thirty")).containsExactly("we", "meet", "at", "6", "30");
        assertThat(digits("We meet at ten thirty")).isEqualTo(digits("We meet at 10:30"));
    }

    @Test
    void wordsThatAreNotNumbersAreLeftAlone() {
        assertThat(digits("Myself Arjun from Mysuru")).containsExactly("myself", "arjun", "from", "mysuru");
        assertThat(digits("a big team")).containsExactly("a", "big", "team");
    }

    @Test
    void smallNumbersInDigitsCanBeReadAsWordsForPatterns() {
        assertThat(SpokenNumbers.spelledOut("there is 2 cafes nearby")).isEqualTo("there is two cafes nearby");
        assertThat(SpokenNumbers.spelledOut("i am 22")).isEqualTo("i am twenty two");
        assertThat(SpokenNumbers.spelledOut("my 2nd year")).isEqualTo("my second year");
        assertThat(SpokenNumbers.spelledOut("the 21st and the 30th")).isEqualTo("the twenty first and the thirtieth");
        assertThat(SpokenNumbers.spelledOut("born in 2004")).isEqualTo("born in 2004");
        assertThat(SpokenNumbers.spelledOut("no numbers here")).isEqualTo("no numbers here");
    }

    @Test
    void aRightAnswerWithNumbersInDigitsCountsAsTheSameSentence() {
        assertThat(GrammarCurriculum.similarity("I'm nineteen, and my brother is twenty-two.",
                "I'm 19 and my brother is 22")).isEqualTo(1.0);
        assertThat(GrammarCurriculum.similarity("I'll meet you at 6 pm.", "I'll meet you at 6:00 PM")).isEqualTo(1.0);
    }
}
