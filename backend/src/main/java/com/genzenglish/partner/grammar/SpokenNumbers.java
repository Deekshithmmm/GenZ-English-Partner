package com.genzenglish.partner.grammar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numbers the way speech recognition writes them ("I'm 19", "at 9:00 AM", "the 12th", "2004") and the way the lessons
 * do ("nineteen", "9 am", "twelfth", "two thousand four"), brought to one form so a right answer isn't marked wrong
 * for how a number was written down. Works on normalised words: lower case, no punctuation ("9:00 a.m." arrives as
 * "9 00 a m").
 */
final class SpokenNumbers {

    private static final String[] UNIT_WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
            "nineteen"};
    private static final String[] TENS_WORDS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy",
            "eighty", "ninety"};
    private static final String[] ORDINAL_WORDS = {"", "first", "second", "third", "fourth", "fifth", "sixth",
            "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth",
            "sixteenth", "seventeenth", "eighteenth", "nineteenth"};

    private static final Map<String, Integer> UNITS = index(UNIT_WORDS);
    private static final Map<String, Integer> TENS = tens();
    private static final Map<String, Integer> ORDINALS = ordinals();
    private static final Pattern ORDINAL_DIGITS = Pattern.compile("(\\d{1,4})(?:st|nd|rd|th)");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private SpokenNumbers() {
    }

    /**
     * Every number as digits: "nineteen" and "19th" become "19", "two thousand four" becomes "2004", "twenty twenty
     * two" becomes "2022", and "nine a m" or "9 00 am" become "9 am".
     */
    static List<String> asDigits(List<String> words) {
        List<String> out = new ArrayList<>(words.size());
        int i = 0;
        while (i < words.size()) {
            int[] number = number(words, i);
            if (number != null) {
                out.add(Integer.toString(number[0]));
                i = number[1];
                continue;
            }
            String word = words.get(i);
            Matcher ordinal = ORDINAL_DIGITS.matcher(word);
            if (ordinal.matches()) {
                word = ordinal.group(1);
            }
            boolean afterNumber = !out.isEmpty() && DIGITS.matcher(out.getLast()).matches();
            if (afterNumber && word.equals("00")) {
                // "9:00" is just 9.
                i++;
                continue;
            }
            if (afterNumber && (word.equals("a") || word.equals("p")) && is(words, i + 1, "m")) {
                // "a.m." loses its dots on the way in.
                out.add(word + "m");
                i += 2;
                continue;
            }
            out.add(word);
            i++;
        }
        return out;
    }

    /**
     * The same text with small numbers in words ("there is 2 cafes" becomes "there is two cafes", "2nd" becomes
     * "second"), so patterns written with words also catch numbers recognised as digits.
     */
    static String spelledOut(String text) {
        if (!text.matches(".*\\d.*")) {
            return text;
        }
        List<String> out = new ArrayList<>();
        for (String word : text.split(" ")) {
            Matcher ordinal = ORDINAL_DIGITS.matcher(word);
            if (DIGITS.matcher(word).matches() && word.length() <= 2) {
                out.add(cardinal(Integer.parseInt(word)));
            } else if (ordinal.matches() && Integer.parseInt(ordinal.group(1)) <= 39
                    && Integer.parseInt(ordinal.group(1)) >= 1) {
                out.add(ordinal(Integer.parseInt(ordinal.group(1))));
            } else {
                out.add(word);
            }
        }
        return String.join(" ", out);
    }

    /** The number spelled in words from {@code start}, as {value, index after it}, or null if none starts there. */
    private static int[] number(List<String> words, int start) {
        int[] first = group(words, start);
        if (first == null) {
            if (!is(words, start, "thousand")) {
                return null;
            }
            first = new int[] {1, start};
        }
        int value = first[0];
        int end = first[1];
        if (is(words, end, "thousand")) {
            value *= 1000;
            end++;
            int[] rest = group(words, end);
            if (rest == null && is(words, end, "and")) {
                rest = group(words, end + 1);
            }
            if (rest != null && rest[0] < 1000) {
                value += rest[0];
                end = rest[1];
            }
            return new int[] {value, end};
        }
        // A year read in pairs: "nineteen ninety nine", "twenty twenty two" (but "ten thirty" is a time).
        boolean century = (value == 19 || value == 20) && !words.subList(start, end).contains("hundred");
        if (century) {
            int[] second = upTo99(words, end);
            if (second != null && second[0] >= 10) {
                return new int[] {value * 100 + second[0], second[1]};
            }
        }
        return new int[] {value, end};
    }

    /** Up to 999: "seven", "twenty two", "two hundred and five", or a bare "hundred". */
    private static int[] group(List<String> words, int start) {
        int[] small = upTo99(words, start);
        int value;
        int end;
        if (small != null) {
            value = small[0];
            end = small[1];
        } else if (is(words, start, "hundred")) {
            value = 1;
            end = start;
        } else {
            return null;
        }
        if (is(words, end, "hundred")) {
            value *= 100;
            end++;
            int[] rest = upTo99(words, end);
            if (rest == null && is(words, end, "and")) {
                rest = upTo99(words, end + 1);
            }
            if (rest != null) {
                value += rest[0];
                end = rest[1];
            }
        }
        return new int[] {value, end};
    }

    /** 0 to 99 in words, ordinals too ("seven", "twenty two", "twelfth", "twenty first"). */
    private static int[] upTo99(List<String> words, int start) {
        if (start >= words.size()) {
            return null;
        }
        String word = words.get(start);
        Integer unit = UNITS.containsKey(word) ? UNITS.get(word) : ORDINALS.get(word);
        if (unit != null) {
            return new int[] {unit, start + 1};
        }
        Integer tens = TENS.get(word);
        if (tens == null) {
            return null;
        }
        if (start + 1 < words.size()) {
            String next = words.get(start + 1);
            Integer ones = UNITS.containsKey(next) ? UNITS.get(next) : ORDINALS.get(next);
            if (ones != null && ones >= 1 && ones <= 9) {
                return new int[] {tens + ones, start + 2};
            }
        }
        return new int[] {tens, start + 1};
    }

    private static boolean is(List<String> words, int index, String word) {
        return index >= 0 && index < words.size() && words.get(index).equals(word);
    }

    private static String cardinal(int n) {
        if (n < 20) {
            return UNIT_WORDS[n];
        }
        return TENS_WORDS[n / 10] + (n % 10 == 0 ? "" : " " + UNIT_WORDS[n % 10]);
    }

    private static String ordinal(int n) {
        if (n < 20) {
            return ORDINAL_WORDS[n];
        }
        if (n % 10 == 0) {
            return TENS_WORDS[n / 10].replaceAll("y$", "ieth");
        }
        return TENS_WORDS[n / 10] + " " + ORDINAL_WORDS[n % 10];
    }

    private static Map<String, Integer> index(String[] words) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                map.put(words[i], i);
            }
        }
        return Map.copyOf(map);
    }

    /** "twenty" is 20, "thirty" 30, and so on. */
    private static Map<String, Integer> tens() {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 2; i < TENS_WORDS.length; i++) {
            map.put(TENS_WORDS[i], i * 10);
        }
        return Map.copyOf(map);
    }

    private static Map<String, Integer> ordinals() {
        Map<String, Integer> map = new HashMap<>(index(ORDINAL_WORDS));
        map.put("twentieth", 20);
        map.put("thirtieth", 30);
        return Map.copyOf(map);
    }
}
