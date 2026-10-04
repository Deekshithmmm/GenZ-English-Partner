import { StyleSheet, View } from 'react-native';

import type { ChartRow } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Icon3D } from '@/components/Icon3D';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

/**
 * A topic's cheat sheet: each label ("at", "on", "in") beside the words it goes with. Shown when Luna explains the rule
 * and behind the hint button during exercises.
 */
export function ChartCard({ chart, title = 'Cheat sheet' }: { chart: ChartRow[]; title?: string }) {
  const { colors } = useTheme();
  return (
    <View
      style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.glassBorder, boxShadow: colors.cardShadow }]}
      accessible
      accessibilityLabel={`${title}. ${chart.map((row) => `${row.label}: ${row.items.join(', ')}`).join('. ')}.`}>
      <View style={styles.title}>
        <Icon3D name="bulb" size={20} />
        <AppText variant="label" tone="muted">
          {title}
        </AppText>
      </View>
      {chart.map((row, index) => (
        <View
          key={row.label}
          style={[styles.row, index > 0 && { borderTopColor: colors.hairline, borderTopWidth: StyleSheet.hairlineWidth }]}>
          <View style={[styles.label, { backgroundColor: colors.accentSoft }]}>
            <AppText variant="label" style={{ color: colors.accent }}>
              {row.label}
            </AppText>
          </View>
          <View style={styles.items}>
            {row.items.map((item) => (
              <AppText key={item} variant="small" style={styles.item}>
                {item}
              </AppText>
            ))}
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.lg + 2, borderWidth: 1, padding: space.lg, gap: space.sm },
  title: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md, paddingTop: space.sm },
  label: { borderRadius: radius.pill, paddingHorizontal: space.md, paddingVertical: 3, maxWidth: '42%' },
  items: { flex: 1, flexDirection: 'row', flexWrap: 'wrap', columnGap: space.md, rowGap: 2, paddingTop: 2 },
  item: { flexShrink: 1 },
});
