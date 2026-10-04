import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { AppText } from './AppText';
import { Icon } from './Icon';
import { Icon3D, type Icon3DName } from './Icon3D';
import { Pressable3D } from './Pressable3D';

export type Choice<T extends string | number> = {
  value: T;
  title: string;
  description?: string;
  /** For descriptions in another script, e.g. a sample line in Kannada. */
  descriptionFontFamily?: string | null;
  /** Shown before the title, e.g. a native-script name. */
  leading?: ReactNode;
  /** A 3D icon at the start of the card. */
  icon3d?: Icon3DName;
  testID?: string;
};

type Props<T extends string | number> = {
  label: string;
  choices: Choice<T>[];
  value: T | null;
  onChange: (value: T) => void;
};

/** A single-select list of 3D cards (a radio group). The chosen card turns violet and gets a tick. */
export function ChoiceList<T extends string | number>({ label, choices, value, onChange }: Props<T>) {
  const { colors } = useTheme();
  return (
    <View accessibilityRole="radiogroup" accessibilityLabel={label} style={styles.list}>
      {choices.map((choice) => {
        const selected = choice.value === value;
        const material = selected ? colors.selected3d : colors.plain3d;
        return (
          <Pressable3D
            key={String(choice.value)}
            material={material}
            onPress={() => onChange(choice.value)}
            radius={radius.lg + 2}
            depth={selected ? 3 : 5}
            floating={false}
            accessibilityRole="radio"
            accessibilityState={{ checked: selected }}
            accessibilityLabel={choice.description ? `${choice.title}. ${choice.description}` : choice.title}
            testID={choice.testID}
            faceStyle={[styles.row, selected && { borderColor: colors.accent, borderWidth: 2 }]}>
            {choice.icon3d ? <Icon3D name={choice.icon3d} size={40} /> : null}
            <View style={styles.text}>
              {choice.leading}
              <AppText variant="bodyStrong" style={{ color: material.ink }}>
                {choice.title}
              </AppText>
              {choice.description ? (
                <AppText variant="small" tone="muted" fontFamily={choice.descriptionFontFamily}>
                  {choice.description}
                </AppText>
              ) : null}
            </View>
            <View
              style={[
                styles.radio,
                selected
                  ? { backgroundColor: colors.accent, borderColor: colors.accent }
                  : { borderColor: colors.border },
              ]}>
              {selected ? <Icon name="checkmark" size={16} color={colors.onAccent} /> : null}
            </View>
          </Pressable3D>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: space.sm },
  row: {
    minHeight: 64,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  text: { flex: 1, gap: 2 },
  radio: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
