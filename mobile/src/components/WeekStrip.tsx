import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';

import { AppText } from './AppText';
import { Icon3D } from './Icon3D';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

type Props = {
  /** Whether the learner practised each day of this week, Monday first. */
  week: boolean[];
  /** 0 for Monday … 6 for Sunday. */
  todayIndex: number;
};

/**
 * This week at a glance: a 3D flame for every day practised, a quiet filled dot for days missed, an outline for days
 * still to come, and today ringed until it's done.
 */
export function WeekStrip({ week, todayIndex }: Props) {
  const { colors } = useTheme();
  const practised = DAYS.filter((_, i) => week[i]);
  const summary = practised.length === 0 ? 'No practice yet this week.' : `Practised on ${practised.join(', ')}.`;
  return (
    <View style={styles.row} accessible accessibilityLabel={`This week. ${summary}`}>
      {DAYS.map((day, i) => {
        const done = week[i] ?? false;
        const today = i === todayIndex;
        const future = i > todayIndex;
        return (
          <View key={day} style={styles.day}>
            <View
              style={[
                styles.dot,
                done
                  ? { backgroundColor: colors.celebrateSoft, borderColor: colors.glassBorder }
                  : today
                    ? { borderColor: colors.text, borderStyle: 'dashed' }
                    : future
                      ? { borderColor: colors.hairline }
                      : { borderColor: colors.surfaceMuted, backgroundColor: colors.surfaceMuted },
              ]}>
              {done ? <Icon3D name="fire" size={22} /> : null}
            </View>
            <AppText variant="label" tone={today ? 'default' : 'muted'}>
              {day[0]}
            </AppText>
          </View>
        );
      })}
    </View>
  );
}

const DOT = 34;

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  day: { alignItems: 'center', gap: 4 },
  dot: {
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
