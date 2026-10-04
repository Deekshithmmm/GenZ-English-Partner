package com.genzenglish.partner.speech;

/** A line from Luna, shown as text (the caption) and voiced as described by {@code speech}. */
public record KaiLine(String text, SpeechDto speech) {
}
