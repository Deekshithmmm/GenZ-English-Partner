import { router } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { AppText } from './AppText';
import { IconButton } from './IconButton';

type Props = {
  step: number;
  total: number;
  /** Hide the back button on the first step. */
  canGoBack?: boolean;
  /** Replaces "Step 2 of 4", e.g. "Question 2 of 4". */
  label?: string;
  /** Spoken name of the back button, e.g. "Leave the chat". */
  backLabel?: string;
  /** Replaces going back in history; the button then shows even when there's no history (e.g. after a reload). */
  onBack?: () => void;
  backIcon?: 'arrow-back' | 'close';
};

export function StepHeader({
  step,
  total,
  canGoBack = true,
  label,
  backLabel = 'Go back',
  onBack,
  backIcon = 'arrow-back',
}: Props) {
  const { colors } = useTheme();
  const text = label ?? `Step ${step} of ${total}`;
  return (
    <View style={styles.row}>
      {canGoBack && (onBack || router.canGoBack()) ? (
        <IconButton icon={backIcon} label={backLabel} onPress={onBack ?? (() => router.back())} />
      ) : (
        <View style={styles.spacer} />
      )}
      <View
        style={styles.progressWrap}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={text}
        accessibilityValue={{ min: 0, max: total, now: step }}>
        <View style={[styles.track, { backgroundColor: colors.surfaceMuted }]}>
          <LinearGradient
            colors={colors.violet3d.face}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={[styles.fill, { width: `${(step / total) * 100}%` }]}
          />
        </View>
        <AppText variant="label" tone="muted">
          {text}
        </AppText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: space.sm, marginLeft: -space.md, marginBottom: space.lg },
  spacer: { width: space.md },
  progressWrap: { flex: 1, gap: space.xs },
  track: { height: 8, borderRadius: radius.pill, overflow: 'hidden' },
  fill: { height: 8, borderRadius: radius.pill },
});
