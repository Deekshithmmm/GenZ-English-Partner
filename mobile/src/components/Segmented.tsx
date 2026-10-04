import { Pressable, StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space, TOUCH_TARGET } from '@/theme/layout';

import { AppText } from './AppText';
import { Pressable3D } from './Pressable3D';

type Option<T extends string> = { value: T; label: string };

type Props<T extends string> = {
  /** Read out as the group name. */
  label: string;
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
};

const DEPTH = 3;

/** A compact single-select row for two or three short options (a radio group); the choice sits on a raised key. */
export function Segmented<T extends string>({ label, options, value, onChange }: Props<T>) {
  const { colors } = useTheme();
  return (
    <View
      accessibilityRole="radiogroup"
      accessibilityLabel={label}
      style={[styles.row, { backgroundColor: colors.surfaceMuted, borderColor: colors.glassBorder }]}>
      {options.map((option) =>
        option.value === value ? (
          <Pressable3D
            key={option.value}
            material={colors.plain3d}
            onPress={() => onChange(option.value)}
            depth={DEPTH}
            floating={false}
            style={styles.flex}
            faceStyle={styles.option}
            accessibilityRole="radio"
            accessibilityState={{ checked: true }}
            accessibilityLabel={option.label}>
            <AppText variant="bodyStrong" style={{ color: colors.plain3d.ink }}>
              {option.label}
            </AppText>
          </Pressable3D>
        ) : (
          <Pressable
            key={option.value}
            onPress={() => onChange(option.value)}
            accessibilityRole="radio"
            accessibilityState={{ checked: false }}
            accessibilityLabel={option.label}
            style={({ pressed }) => [
              styles.flex,
              styles.option,
              { marginBottom: DEPTH, opacity: pressed ? 0.7 : 1 },
            ]}>
            <AppText tone="muted">{option.label}</AppText>
          </Pressable>
        ),
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', borderRadius: radius.pill, padding: space.xs, gap: space.xs, borderWidth: 1 },
  flex: { flex: 1 },
  option: {
    minHeight: TOUCH_TARGET - DEPTH,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space.sm,
  },
});
