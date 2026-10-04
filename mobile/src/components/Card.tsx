import { LinearGradient } from 'expo-linear-gradient';
import type { ReactNode } from 'react';
import { StyleSheet, View, type StyleProp, type ViewProps, type ViewStyle } from 'react-native';

import type { Gradient } from '@/theme/colors';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

export type CardTone = 'surface' | 'primary' | 'celebrate' | 'muted' | 'accent';

type Props = Omit<ViewProps, 'style'> & {
  children: ReactNode;
  /**
   * surface: a frosted glass panel (the default). primary: a mango wash for Luna's notes and what's next. celebrate: a
   * rani-to-violet wash for streaks. accent: a violet wash for the learning path. muted: quiet glass.
   */
  tone?: CardTone;
  padding?: number;
  style?: StyleProp<ViewStyle>;
};

const WASHES: Record<'light' | 'dark', Partial<Record<CardTone, Gradient>>> = {
  light: {
    primary: ['#FFF7E0', '#FFE6A6'],
    celebrate: ['#FFE6F1', '#EEE6FF'],
    accent: ['#F1ECFF', '#DCD1FF'],
  },
  dark: {
    primary: ['rgba(255, 196, 64, 0.26)', 'rgba(255, 150, 20, 0.1)'],
    celebrate: ['rgba(255, 95, 162, 0.26)', 'rgba(124, 92, 255, 0.18)'],
    accent: ['rgba(150, 124, 255, 0.32)', 'rgba(150, 124, 255, 0.1)'],
  },
};

/** A resting surface over the aurora backdrop: frosted glass, or a soft gradient wash for emphasis. */
export function Card({ children, tone = 'surface', padding = space.lg, style, ...rest }: Props) {
  const { colors, scheme } = useTheme();
  const wash = WASHES[scheme][tone];
  if (wash) {
    return (
      <LinearGradient
        colors={wash}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={[styles.card, { padding, borderColor: colors.glassBorder }, style]}
        {...rest}>
        {children}
      </LinearGradient>
    );
  }
  return (
    <View
      style={[
        styles.card,
        {
          padding,
          backgroundColor: tone === 'muted' ? colors.surfaceMuted : colors.glass,
          borderColor: colors.glassBorder,
          boxShadow: tone === 'muted' ? undefined : colors.cardShadow,
        },
        style,
      ]}
      {...rest}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.lg + 4, gap: space.sm, borderWidth: 1 },
});
