import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { AppText } from './AppText';

type Props = {
  label: string;
  /** 0..100 */
  score: number;
  /** One plain sentence about what the score covers. */
  hint?: string;
};

/**
 * A labelled 0-100 bar. The value is always printed, so the bar is never the only way to read it. The fill uses the
 * ink colour (13:1 on the track in light mode, 10.6:1 in dark); mango would fail the 3:1 minimum for graphics.
 */
export function ScoreMeter({ label, score, hint }: Props) {
  const { colors } = useTheme();
  const value = Math.max(0, Math.min(100, Math.round(score)));
  return (
    <View
      style={styles.wrap}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={hint ? `${label}. ${hint}` : label}
      accessibilityValue={{ min: 0, max: 100, now: value, text: `${value} out of 100` }}>
      <View style={styles.labelRow}>
        <AppText variant="bodyStrong">{label}</AppText>
        <AppText variant="bodyStrong">
          {value}
          <AppText variant="small" tone="muted">
            {' '}
            / 100
          </AppText>
        </AppText>
      </View>
      <View style={[styles.track, { backgroundColor: colors.surfaceMuted }]}>
        {value > 0 ? (
          <LinearGradient
            colors={colors.violet3d.face}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={[styles.fill, { width: `${value}%` }]}
          />
        ) : null}
      </View>
      {hint ? (
        <AppText variant="small" tone="muted">
          {hint}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: space.xs },
  labelRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  track: { height: 12, borderRadius: radius.pill, overflow: 'hidden' },
  fill: { height: 12, borderRadius: radius.pill },
});
