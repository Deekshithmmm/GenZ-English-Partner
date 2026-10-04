import { useCallback, useEffect, useMemo, useState } from 'react';

import type { ChartRow, ExplanationLanguage, GrammarExample, GrammarLesson, Speech, TutorLine } from '@/api/types';
import { canSpeak, prefetchKaiParts, speakKaiParts, stopKai, type SpokenPart } from '@/audio/kaiSpeech';
import { languageInfo } from '@/i18n/languages';
import { useSession } from '@/stores/session';

const ENGLISH_TAG = 'en-IN';

/** Luna's voice for lesson lines, which come as text: the learner's chosen voice and speed on the phone's engine. */
export function useLessonVoice(): Speech {
  const tutorVoice = useSession((s) => s.me?.profile.tutorVoice ?? 'KAI_WARM');
  const rate = useSession((s) => s.me?.profile.speakingSpeed ?? 1);
  return useMemo(
    () => ({ mode: 'DEVICE', audioUrl: null, voice: tutorVoice, rate, languageTag: ENGLISH_TAG }),
    [rate, tutorVoice],
  );
}

export function english(text: string): SpokenPart {
  return { text, languageTag: ENGLISH_TAG };
}

/** Ends a line with a full stop if it has no end mark, so Luna pauses after it. */
const sentence = (text: string) => (/[.!?:]["”’)]*$/.test(text.trim()) ? text.trim() : `${text.trim()}.`);

/**
 * The cheat sheet as Luna reads it: each row's items ("I am, I'm."), led by the row's label when the items don't say
 * it themselves ("at: 7 pm, night.").
 */
export function chartSpeech(chart: ChartRow[]): SpokenPart[] {
  if (chart.length === 0) {
    return [];
  }
  const rows = chart.map((row) => {
    const label = row.label.toLowerCase();
    const named = row.items.every((item) => item.toLowerCase().split(/[^a-z']+/).includes(label));
    return sentence(named ? row.items.join(', ') : `${row.label}: ${row.items.join(', ')}`);
  });
  return [english(rows.join(' '))];
}

/**
 * What Luna says when intro line `index` comes up: the line (in the learner's language, then English), and with it
 * the cards that appear under it, read once: the cheat sheet with the second line, and "How it works" with the third
 * line of a Learn lesson.
 */
export function introSpeech(
  lesson: GrammarLesson,
  index: number,
  language: ExplanationLanguage | null | undefined,
): SpokenPart[] {
  const line = lesson.intro[index];
  if (!line) {
    return [];
  }
  return [
    ...partsFor(line, language),
    ...(index === 1 ? chartSpeech(lesson.chart) : []),
    ...(index === 2 && lesson.kind === 'LEARN' ? rulesSpeech(lesson.rules, lesson.examples) : []),
  ];
}

/** "How it works" as Luna reads it: the rules, then each example the right way (after the slip, when there is one). */
export function rulesSpeech(rules: string[], examples: GrammarExample[]): SpokenPart[] {
  const lines = rules.map(sentence);
  for (const example of examples) {
    // One sentence per example, as a teacher says it: Don't say "My sister a teacher", say "My sister is a teacher."
    const wrong = example.wrong?.trim().replace(/[.!?]+$/, '');
    lines.push(wrong ? `Don't say "${wrong}", say "${sentence(example.right)}"` : sentence(example.right));
    if (example.note) {
      lines.push(sentence(example.note));
    }
  }
  return lines.length > 0 ? [english(lines.join(' '))] : [];
}

/**
 * What Luna reads for a line: the learner's language first when the line is shown in it (as it is on screen), then
 * the English.
 */
export function partsFor(line: TutorLine, language: ExplanationLanguage | null | undefined): SpokenPart[] {
  if (line.nativeText && language && language !== 'ENGLISH') {
    return [{ text: line.nativeText, languageTag: languageInfo(language).speechTag }, english(line.text)];
  }
  return [english(line.text)];
}

/**
 * Luna reads lines one after another. Saying something new, `hush`, or leaving the screen stops whatever she was
 * saying, so she never talks over the learner or carries on after they've left. `prefetch` gets lines she'll say soon
 * ready in her server voice, so she starts them at once.
 */
export function useKaiLines(voice: Speech) {
  useEffect(() => () => stopKai(), []);

  const say = useCallback((parts: SpokenPart[]) => speakKaiParts(parts, voice), [voice]);
  const hush = useCallback(() => stopKai(), []);
  const prefetch = useCallback((parts: SpokenPart[]) => prefetchKaiParts(parts, voice), [voice]);

  return { say, hush, prefetch };
}

/**
 * True when the learner's language has no voice on this phone, so Luna can only read the English. Null while
 * checking, and for English.
 */
export function useMissingVoice(language: ExplanationLanguage | null | undefined): boolean | null {
  const [missing, setMissing] = useState<boolean | null>(null);
  useEffect(() => {
    if (!language || language === 'ENGLISH') {
      return;
    }
    let cancelled = false;
    canSpeak(languageInfo(language).speechTag)
      .then((ok) => {
        // Unknown (no voices listed) isn't missing: only say so when the phone's voices are known.
        if (!cancelled && ok != null) {
          setMissing(!ok);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [language]);
  return language && language !== 'ENGLISH' ? missing : null;
}
