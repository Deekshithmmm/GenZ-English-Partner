import { useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import type { Correction, ExplanationLanguage, KaiLine } from '@/api/types';
import { speakKaiParts } from '@/audio/kaiSpeech';
import { AppText } from '@/components/AppText';
import { Highlight } from '@/components/Highlight';
import { Icon } from '@/components/Icon';
import { languageInfo } from '@/i18n/languages';
import { useLanguageFont } from '@/theme/fonts';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space, TOUCH_TARGET } from '@/theme/layout';

type Props = {
  corrections: Correction[];
  tip?: string | null;
  /** Small heading above the list, e.g. "Say it like this". */
  title?: string;
  /** The learner's explanation language. Offers "Explain in Hindi" (or theirs) when Luna wrote the reasons in it. */
  language?: ExplanationLanguage | null;
  /** Leaves out the card, for when the list sits inside another note. */
  bare?: boolean;
  /** Luna's voice settings: with them, switching to the learner's language has Luna read the reasons in it. */
  voice?: KaiLine['speech'];
};

/**
 * Luna's notes on what the learner said: their own words under the highlighter, then a more natural way to say it.
 * They sit beside the conversation rather than in it; Luna reads them when the learner opens them.
 */
export function Corrections({ corrections, tip, title = 'Say it like this', language, bare = false, voice }: Props) {
  const { colors } = useTheme();
  const [native, setNative] = useState(false);
  const canExplain = language != null && language !== 'ENGLISH' && corrections.some((c) => c.whyNative);
  const nativeFont = useLanguageFont(canExplain ? language : null);

  function toggleNative() {
    const explaining = !native;
    setNative(explaining);
    if (explaining && voice && canExplain) {
      const languageTag = languageInfo(language).speechTag;
      const reasons = corrections.flatMap((c) => (c.whyNative ? [{ text: c.whyNative, languageTag }] : []));
      void speakKaiParts(reasons, voice).catch(() => undefined);
    }
  }
  if (corrections.length === 0 && !tip) {
    return null;
  }
  const showNative = canExplain && native;
  const body = (
    <>
      {corrections.length > 0 ? (
        <View style={styles.titleRow}>
          <Icon name="pencil" size={14} color={colors.textMuted} />
          <AppText variant="label" tone="muted" style={styles.title}>
            {title}
          </AppText>
        </View>
      ) : null}
      {corrections.map((correction, index) => {
        const why = showNative ? (correction.whyNative ?? correction.why) : correction.why;
        return (
          <View
            key={`${correction.original}-${index}`}
            style={styles.item}
            accessible
            accessibilityLabel={`Instead of "${correction.original}", say "${correction.better}".${why ? ` ${why}` : ''}`}>
            <View style={styles.swap}>
              <Highlight delayMs={150 + index * 200}>
                <AppText style={{ color: colors.onHighlight }}>{correction.original}</AppText>
              </Highlight>
              <Icon name="arrow-forward" size={16} color={colors.textMuted} />
              <AppText variant="bodyStrong" style={styles.better}>
                {correction.better}
              </AppText>
            </View>
            {why ? (
              <AppText
                variant="small"
                tone={showNative ? 'default' : 'muted'}
                fontFamily={showNative ? nativeFont : null}
                style={showNative ? styles.nativeWhy : null}>
                {why}
              </AppText>
            ) : null}
          </View>
        );
      })}
      {canExplain ? (
        <ExplainToggle language={language} native={native} onToggle={toggleNative} />
      ) : null}
      {tip ? (
        <View style={styles.tip}>
          <Icon name="bulb-outline" size={18} color={colors.text} />
          <AppText variant="small" style={styles.tipText}>
            {tip}
          </AppText>
        </View>
      ) : null}
    </>
  );
  if (bare) {
    return <View style={styles.bare}>{body}</View>;
  }
  return <NoteCard>{body}</NoteCard>;
}

/** "Explain in हिन्दी", and back to English. The language name is shown in its own script, as on the picker. */
function ExplainToggle({
  language,
  native,
  onToggle,
}: {
  language: ExplanationLanguage;
  native: boolean;
  onToggle: () => void;
}) {
  const { colors } = useTheme();
  const info = languageInfo(language);
  const font = useLanguageFont(language);
  return (
    <Pressable
      onPress={onToggle}
      accessibilityRole="button"
      accessibilityLabel={native ? 'Show the reasons in English' : `Explain in ${info.english}`}
      accessibilityState={{ selected: native }}
      hitSlop={4}
      testID="explain-toggle"
      style={({ pressed }) => [
        styles.explain,
        { borderColor: colors.border, backgroundColor: pressed ? colors.surfaceMuted : 'transparent' },
      ]}>
      <Icon name="language-outline" size={18} color={colors.text} />
      {native ? (
        <AppText variant="label">Show in English</AppText>
      ) : (
        <AppText variant="label">
          Explain in{' '}
          <AppText variant="label" fontFamily={font}>
            {info.native}
          </AppText>
        </AppText>
      )}
    </Pressable>
  );
}

/** A study note pinned beside the conversation: white paper with a mango edge. */
export function NoteCard({ children }: { children: ReactNode }) {
  const { colors } = useTheme();
  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: colors.glassStrong,
          borderColor: colors.glassBorder,
          borderLeftColor: colors.primary,
          boxShadow: colors.cardShadow,
        },
      ]}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderLeftWidth: 5,
    borderRadius: radius.lg,
    padding: space.md,
    gap: space.md,
  },
  bare: { gap: space.md },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  title: { flex: 1 },
  item: { gap: 2 },
  // Wraps on narrow screens, so a long slip pushes the better version onto the next line.
  swap: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', columnGap: space.sm, rowGap: 2 },
  better: { flexShrink: 1 },
  // Indian scripts need more room above and below than Latin text.
  nativeWhy: { fontSize: 15, lineHeight: 24 },
  explain: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    minHeight: TOUCH_TARGET - 8,
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
  },
  tip: { flexDirection: 'row', gap: space.sm, alignItems: 'flex-start' },
  tipText: { flex: 1 },
});
