import { LinearGradient } from 'expo-linear-gradient';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import type { KaiLine } from '@/api/types';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space, TOUCH_TARGET } from '@/theme/layout';

import { AppText } from './AppText';
import { IconButton } from './IconButton';
import { KaiFace } from './Kai3D';

type KaiProps = {
  line: KaiLine;
  onReplay: () => void;
  onReplaySlow: () => void;
};

function KaiBubble({ children }: { children: ReactNode }) {
  const { colors } = useTheme();
  return (
    <View style={styles.kaiRow}>
      <KaiFace size={34} />
      <View
        style={[
          styles.kaiBubble,
          { backgroundColor: colors.glassStrong, borderColor: colors.glassBorder, boxShadow: colors.cardShadow },
        ]}>
        {children}
      </View>
    </View>
  );
}

/** Luna's words while they're still arriving; replaced by a KaiMessage once the line is finished. */
export function KaiDraftMessage({ text }: { text: string }) {
  return (
    // Screen readers get the finished line instead of every partial update.
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <KaiBubble>
        <AppText variant="body" style={styles.kaiText}>
          {text}
        </AppText>
      </KaiBubble>
    </View>
  );
}

/** A line from Luna. The text doubles as the caption for Luna's voice. */
export function KaiMessage({ line, onReplay, onReplaySlow }: KaiProps) {
  const { colors } = useTheme();
  return (
    <KaiBubble>
      <AppText variant="label" tone="muted" accessibilityElementsHidden>
        Luna
      </AppText>
      <AppText variant="body" style={styles.kaiText} accessibilityLabel={`Luna: ${line.text}`}>
        {line.text}
      </AppText>
      <View style={styles.controls}>
        <IconButton icon="volume-high-outline" label="Play Luna's message again" onPress={onReplay} size={20} />
        <Pressable
          onPress={onReplaySlow}
          accessibilityRole="button"
          accessibilityLabel="Play Luna's message slower"
          style={({ pressed }) => [
            styles.slowButton,
            { borderColor: colors.border, backgroundColor: pressed ? colors.accentSoft : 'transparent' },
          ]}>
          <AppText variant="label">0.75×</AppText>
        </Pressable>
      </View>
    </KaiBubble>
  );
}

type LearnerProps = {
  text: string;
  /** Shown while the answer uploads and gets transcribed. */
  pending?: boolean;
  /** The learner's own words that Luna has a note on; each gets a highlighter mark where it was said. */
  marks?: string[];
};

/** What the learner said, in their own violet bubble on the right, with slips marked by the mango highlighter. */
export function LearnerMessage({ text, pending = false, marks = [] }: LearnerProps) {
  const { colors } = useTheme();
  return (
    <View style={styles.learnerRow} accessible accessibilityLabel={`You said: ${text}`}>
      <LinearGradient
        colors={colors.learnerBubble}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={[styles.learnerBubble, { boxShadow: colors.cardShadow, opacity: pending ? 0.75 : 1 }]}>
        <AppText variant="body" style={{ color: colors.onLearnerBubble }}>
          {splitMarks(text, marks).map((part, index) =>
            part.marked ? (
              <AppText
                key={index}
                style={{ backgroundColor: colors.highlight, color: colors.onHighlight, borderRadius: 4 }}>
                {part.text}
              </AppText>
            ) : (
              part.text
            ),
          )}
        </AppText>
      </LinearGradient>
    </View>
  );
}

/**
 * Cuts the text around the first place each mark was said (ignoring case), so the marks can be highlighted in line.
 * Marks that weren't said word for word, or that overlap an earlier one, are skipped.
 */
export function splitMarks(text: string, marks: string[]): { text: string; marked: boolean }[] {
  const lower = text.toLowerCase();
  const ranges: [number, number][] = [];
  for (const mark of marks) {
    const needle = mark.trim().toLowerCase();
    const at = needle ? lower.indexOf(needle) : -1;
    const end = at + needle.length;
    if (at >= 0 && !ranges.some(([start, stop]) => at < stop && end > start)) {
      ranges.push([at, end]);
    }
  }
  ranges.sort((a, b) => a[0] - b[0]);
  const parts: { text: string; marked: boolean }[] = [];
  let cursor = 0;
  for (const [start, stop] of ranges) {
    if (start > cursor) {
      parts.push({ text: text.slice(cursor, start), marked: false });
    }
    parts.push({ text: text.slice(start, stop), marked: true });
    cursor = stop;
  }
  if (cursor < text.length) {
    parts.push({ text: text.slice(cursor), marked: false });
  }
  return parts;
}

const styles = StyleSheet.create({
  kaiRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm, paddingRight: space.xxl },
  kaiBubble: {
    flexShrink: 1,
    borderWidth: 1,
    borderRadius: radius.lg + 2,
    borderTopLeftRadius: 6,
    paddingHorizontal: space.lg,
    paddingTop: space.sm,
    paddingBottom: space.xs,
    gap: 2,
  },
  kaiText: { fontSize: 17, lineHeight: 26 },
  controls: { flexDirection: 'row', alignItems: 'center', gap: space.xs, marginLeft: -space.md },
  slowButton: {
    minHeight: TOUCH_TARGET - 12,
    minWidth: TOUCH_TARGET,
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 6,
  },
  learnerRow: { flexDirection: 'row', justifyContent: 'flex-end', paddingLeft: space.xxxl },
  learnerBubble: {
    flexShrink: 1,
    borderRadius: radius.lg + 2,
    borderTopRightRadius: 6,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
  },
});
