import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import type { ExplanationLanguage, KaiLine, TutorLine } from '@/api/types';
import { speakKaiParts } from '@/audio/kaiSpeech';
import { AppText } from '@/components/AppText';
import { Icon } from '@/components/Icon';
import { KaiFace } from '@/components/Kai3D';
import { useLanguageFont } from '@/theme/fonts';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { english, partsFor } from './lessonVoice';

type Props = {
  line: TutorLine;
  /** Show the learner's language first (with the English underneath) when Luna has the line in it. */
  native: boolean;
  language?: ExplanationLanguage | null;
  /** Luna's voice settings for the play button, which reads what's shown: the learner's language, then English. */
  voice?: KaiLine['speech'];
  /** What the play button reads, when it isn't the line itself (e.g. just the sentence to say). */
  speak?: string;
  children?: ReactNode;
};

/** One message from Luna in a lesson: her face, a glass bubble with a play button, and the line. */
export function TutorBubble({ line, native, language, voice, speak, children }: Props) {
  const { colors } = useTheme();
  const font = useLanguageFont(native && line.nativeText ? language : null);
  const showNative = native && Boolean(line.nativeText);
  return (
    <View style={styles.row}>
      <KaiFace size={34} />
      <View
        style={[styles.bubble, { backgroundColor: colors.glassStrong, borderColor: colors.glassBorder, boxShadow: colors.cardShadow }]}>
        {voice ? (
          <Pressable
            onPress={() =>
              void speakKaiParts(speak ? [english(speak)] : partsFor(line, showNative ? language : null), voice).catch(
                () => undefined,
              )
            }
            accessibilityRole="button"
            accessibilityLabel="Play"
            hitSlop={8}
            style={({ pressed }) => [styles.play, { backgroundColor: pressed ? colors.accent : colors.accentSoft }]}>
            <Icon name="volume-high" size={14} color={colors.text} />
            <AppText variant="label">Play</AppText>
          </Pressable>
        ) : null}
        {showNative ? (
          <>
            <AppText variant="bodyStrong" fontFamily={font} style={styles.native}>
              {line.nativeText}
            </AppText>
            <AppText variant="small" tone="muted">
              {line.text}
            </AppText>
          </>
        ) : (
          <AppText variant="bodyStrong">{line.text}</AppText>
        )}
        {children}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm, paddingRight: space.lg },
  bubble: {
    flexShrink: 1,
    borderWidth: 1,
    borderRadius: radius.lg + 2,
    borderTopLeftRadius: 6,
    padding: space.md,
    gap: space.xs,
  },
  play: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderRadius: radius.pill,
    paddingHorizontal: space.sm,
    paddingVertical: 2,
  },
  native: { lineHeight: 26 },
});
