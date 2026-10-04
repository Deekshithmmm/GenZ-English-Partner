import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import type { Material3D } from '@/theme/colors';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space, TOUCH_TARGET } from '@/theme/layout';

import { AppText } from './AppText';
import { Icon, type IconName } from './Icon';
import { Pressable3D } from './Pressable3D';

type Variant = 'primary' | 'secondary' | 'accent' | 'ghost' | 'danger';

type Props = {
  /** Says what happens: "Start speaking", "Try again". */
  label: string;
  onPress: () => void;
  variant?: Variant;
  icon?: IconName;
  loading?: boolean;
  disabled?: boolean;
  accessibilityHint?: string;
  testID?: string;
};

/**
 * A glossy 3D key that sinks when pressed: mango for the screen's main action, white (deep glass in dark mode) for the
 * second choice, violet for the learning path. Ghost buttons stay flat, for quiet actions next to the main one.
 */
export function Button({
  label,
  onPress,
  variant = 'primary',
  icon,
  loading = false,
  disabled = false,
  accessibilityHint,
  testID,
}: Props) {
  const { colors } = useTheme();
  const inactive = disabled || loading;

  if (variant === 'ghost') {
    return (
      <Pressable
        testID={testID}
        onPress={onPress}
        disabled={inactive}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityHint={accessibilityHint}
        accessibilityState={{ disabled: inactive, busy: loading }}
        style={({ pressed }) => [
          styles.ghost,
          { backgroundColor: pressed ? colors.accentSoft : 'transparent', opacity: disabled ? 0.5 : 1 },
        ]}>
        <View style={styles.content}>
          {loading ? (
            <ActivityIndicator color={colors.text} />
          ) : icon ? (
            <Icon name={icon} size={20} color={colors.text} />
          ) : null}
          <AppText variant="bodyStrong" style={styles.label}>
            {label}
          </AppText>
        </View>
      </Pressable>
    );
  }

  const material: Material3D = {
    primary: colors.mango3d,
    secondary: colors.plain3d,
    accent: colors.violet3d,
    danger: { ...colors.plain3d, ink: colors.error },
  }[variant];

  return (
    <Pressable3D
      material={material}
      onPress={onPress}
      disabled={inactive}
      radius={radius.pill}
      depth={variant === 'primary' ? 6 : 5}
      floating={variant !== 'secondary' && variant !== 'danger'}
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ busy: loading }}
      faceStyle={styles.face}
      testID={testID}>
      <View style={styles.content}>
        {loading ? (
          <ActivityIndicator color={material.ink} />
        ) : icon ? (
          <Icon name={icon} size={20} color={material.ink} />
        ) : null}
        <AppText variant="bodyStrong" style={[styles.label, { color: material.ink }]}>
          {label}
        </AppText>
      </View>
    </Pressable3D>
  );
}

const styles = StyleSheet.create({
  face: {
    minHeight: 54,
    paddingHorizontal: space.xl,
    justifyContent: 'center',
  },
  ghost: {
    minHeight: TOUCH_TARGET,
    paddingHorizontal: space.md,
    alignSelf: 'flex-start',
    justifyContent: 'center',
    borderRadius: radius.pill,
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.sm,
  },
  label: {
    fontSize: 17,
  },
});
