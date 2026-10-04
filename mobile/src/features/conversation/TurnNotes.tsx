import { useState, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import type { ExplanationLanguage, KaiLine, TurnFeedback } from '@/api/types';
import { speakAsKai, speakKaiParts, type SpokenPart } from '@/audio/kaiSpeech';
import { english } from '@/features/grammar/lessonVoice';
import { languageInfo } from '@/i18n/languages';
import { AppText } from '@/components/AppText';
import { Icon, type IconName } from '@/components/Icon';
import { IconButton } from '@/components/IconButton';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space, TOUCH_TARGET } from '@/theme/layout';

import { Corrections, NoteCard } from './Corrections';

type Props = {
  /** Null while Luna is still checking the answer. */
  feedback: TurnFeedback | null;
  language?: ExplanationLanguage | null;
  /** Luna's voice settings, so the smoother version can be read out. */
  voice?: KaiLine['speech'];
  /** Show nothing instead of "Sounds natural" when there are no notes, e.g. when Luna gives her own verdict. */
  hideWhenClear?: boolean;
};

/**
 * The feedback chip under each answer: a one-line summary of Luna's notes that expands into the full study note. It
 * stays closed by default so the conversation keeps moving; the slips are already marked in the learner's words.
 */
export function TurnNotes({ feedback, language, voice, hideWhenClear = false }: Props) {
  const { colors } = useTheme();
  const [open, setOpen] = useState(false);

  /** Opening the notes has Luna read them. */
  function toggle() {
    const opening = !open;
    setOpen(opening);
    if (opening && feedback && voice) {
      void speakKaiParts(notesSpeech(feedback, language), voice).catch(() => undefined);
    }
  }

  if (!feedback) {
    return (
      <View style={styles.checking} accessibilityLiveRegion="polite">
        <ActivityIndicator size="small" color={colors.textMuted} />
        <AppText variant="small" tone="muted">
          Luna is checking your answer…
        </AppText>
      </View>
    );
  }

  const parts = summary(feedback);
  if (parts.length === 0) {
    if (hideWhenClear) {
      return null;
    }
    return (
      <View style={styles.natural} testID="turn-notes-clear">
        <Icon name="checkmark-circle" size={18} color={colors.success} />
        <AppText variant="small" tone="success">
          Sounds natural. Nothing to fix!
        </AppText>
      </View>
    );
  }

  const label = parts.join(' · ');
  return (
    <View style={styles.wrap}>
      <Pressable
        onPress={toggle}
        accessibilityRole="button"
        accessibilityLabel={`Luna's notes: ${parts.join(', ')}`}
        accessibilityHint={open ? 'Hides the notes' : 'Shows the notes'}
        accessibilityState={{ expanded: open }}
        hitSlop={4}
        testID="turn-notes"
        style={({ pressed }) => [
          styles.chip,
          {
            borderColor: open ? colors.primary : colors.border,
            backgroundColor: pressed ? colors.surfaceMuted : open ? colors.primarySoft : colors.glassStrong,
            boxShadow: colors.cardShadow,
          },
        ]}>
        <Icon name="pencil" size={16} color={colors.text} />
        <AppText variant="label" style={styles.chipLabel} numberOfLines={2}>
          {label}
        </AppText>
        <Icon name={open ? 'chevron-up' : 'chevron-down'} size={16} color={colors.textMuted} />
      </Pressable>
      {open ? <Notes feedback={feedback} language={language} voice={voice} /> : null}
    </View>
  );
}

/**
 * Luna's notes as she reads them: each fix, with its reason in the learner's language (when she wrote one) and in
 * English, then the smoother version, words to try, unclear words and the tip.
 */
function notesSpeech(feedback: TurnFeedback, language: ExplanationLanguage | null | undefined): SpokenPart[] {
  const native = language && language !== 'ENGLISH' ? languageInfo(language).speechTag : null;
  const parts: SpokenPart[] = [];
  for (const correction of feedback.corrections) {
    parts.push(english(`Instead of "${correction.original}", say "${correction.better}".`));
    if (native && correction.whyNative) {
      parts.push({ text: correction.whyNative, languageTag: native });
    }
    if (correction.why) {
      parts.push(english(correction.why));
    }
  }
  if (feedback.betterPhrasing) {
    parts.push(english(`A smoother way to say it: ${feedback.betterPhrasing}`));
  }
  for (const word of feedback.newWords ?? []) {
    parts.push(english(`A word to try: ${word.word}. It means ${word.meaning}.`));
  }
  const sounds = (feedback.mispronounced ?? []).map((sound) => sound.word);
  if (sounds.length > 0) {
    parts.push(english(`Say ${sounds.length === 1 ? 'this' : 'these'} slowly a few times: ${sounds.join(', ')}.`));
  }
  if (feedback.tip) {
    parts.push(english(feedback.tip));
  }
  return parts;
}

/** "1 fix · smoother version · 2 sounds", in the order the note shows them. */
function summary(feedback: TurnFeedback): string[] {
  const parts: string[] = [];
  const fixes = feedback.corrections.length;
  if (fixes > 0) {
    parts.push(fixes === 1 ? '1 fix' : `${fixes} fixes`);
  }
  if (feedback.betterPhrasing) {
    parts.push('smoother version');
  }
  if (feedback.newWords?.length) {
    parts.push('word to try');
  }
  const sounds = feedback.mispronounced?.length ?? 0;
  if (sounds > 0) {
    parts.push(sounds === 1 ? '1 unclear word' : `${sounds} unclear words`);
  }
  const fillers = (feedback.fillerWords ?? []).reduce((sum, f) => sum + f.count, 0);
  if (fillers > 0) {
    parts.push(fillers === 1 ? '1 filler' : `${fillers} fillers`);
  }
  if (parts.length === 0 && feedback.tip) {
    parts.push('a tip');
  }
  return parts;
}

function Notes({ feedback, language, voice }: Props & { feedback: TurnFeedback }) {
  const { colors } = useTheme();
  const words = feedback.newWords ?? [];
  const sounds = feedback.mispronounced ?? [];
  const fillers = feedback.fillerWords ?? [];
  return (
    <NoteCard>
      <Corrections corrections={feedback.corrections} tip={null} language={language} voice={voice} bare />

      {feedback.betterPhrasing ? (
        <Section icon="sparkles-outline" title="Smoother">
          <View style={styles.quoteRow}>
            <AppText variant="bodyStrong" style={styles.quote}>
              “{feedback.betterPhrasing}”
            </AppText>
            {voice ? (
              <IconButton
                icon="volume-high-outline"
                label="Hear the smoother version"
                size={20}
                onPress={() => void speakAsKai(feedback.betterPhrasing ?? '', voice).catch(() => undefined)}
              />
            ) : null}
          </View>
          <AppText variant="small" tone="muted">
            Try saying it out loud once.
          </AppText>
        </Section>
      ) : null}

      {words.length > 0 ? (
        <Section icon="book-outline" title="Word to try">
          {words.map((word) => (
            <View key={word.word} style={styles.word}>
              <AppText>
                <AppText variant="bodyStrong">{word.word}</AppText>
                <AppText tone="muted"> · {word.meaning}</AppText>
              </AppText>
              {word.instead ? (
                <AppText variant="small" tone="muted">
                  Instead of “{word.instead}”
                </AppText>
              ) : null}
            </View>
          ))}
        </Section>
      ) : null}

      {sounds.length > 0 ? (
        <Section icon="ear-outline" title="Didn't come through clearly">
          <View style={styles.pills}>
            {sounds.map((sound) => (
              <View key={sound.word} style={[styles.pill, { backgroundColor: colors.surfaceMuted }]}>
                <AppText variant="bodyStrong">{sound.word}</AppText>
              </View>
            ))}
          </View>
          <AppText variant="small" tone="muted">
            Say {sounds.length === 1 ? 'it' : 'them'} slowly a few times, then at normal speed.
          </AppText>
        </Section>
      ) : null}

      {fillers.length > 0 ? (
        <Section icon="ellipsis-horizontal" title="Filler words">
          <AppText variant="small">
            {fillers.map((f) => `“${f.word}” ×${f.count}`).join(', ')}. A short pause sounds calmer than a filler.
          </AppText>
        </Section>
      ) : null}

      {feedback.tip ? (
        <View style={styles.tip}>
          <Icon name="bulb-outline" size={18} color={colors.text} />
          <AppText variant="small" style={styles.flex}>
            {feedback.tip}
          </AppText>
        </View>
      ) : null}
    </NoteCard>
  );
}

function Section({ icon, title, children }: { icon: IconName; title: string; children: ReactNode }) {
  const { colors } = useTheme();
  return (
    <View style={styles.section}>
      <View style={styles.sectionTitle}>
        <Icon name={icon} size={14} color={colors.textMuted} />
        <AppText variant="label" tone="muted">
          {title}
        </AppText>
      </View>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: space.sm },
  checking: { flexDirection: 'row', alignItems: 'center', gap: space.sm, minHeight: 32, alignSelf: 'flex-end' },
  natural: { flexDirection: 'row', alignItems: 'center', gap: space.xs, minHeight: 32, alignSelf: 'flex-end' },
  chip: {
    alignSelf: 'flex-end',
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    minHeight: TOUCH_TARGET - 8,
    maxWidth: '100%',
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
  },
  chipLabel: { flexShrink: 1 },
  section: { gap: space.xs },
  sectionTitle: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  quoteRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  quote: { flex: 1 },
  word: { gap: 2 },
  pills: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm },
  pill: { borderRadius: radius.pill, paddingHorizontal: space.md, paddingVertical: space.xs },
  tip: { flexDirection: 'row', gap: space.sm, alignItems: 'flex-start' },
  flex: { flex: 1 },
});
