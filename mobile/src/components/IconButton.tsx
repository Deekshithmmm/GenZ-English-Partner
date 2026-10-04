import { Pressable, StyleSheet } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { TOUCH_TARGET } from '@/theme/layout';

import { Icon, type IconName } from './Icon';

type Props = {
  icon: IconName;
  /** Required: icon-only controls need a spoken name. */
  label: string;
  onPress: () => void;
  disabled?: boolean;
  size?: number;
};

export function IconButton({ icon, label, onPress, disabled = false, size = 22 }: Props) {
  const { colors } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      hitSlop={4}
      style={({ pressed }) => [
        styles.base,
        { backgroundColor: pressed ? colors.surfaceMuted : 'transparent', opacity: disabled ? 0.4 : 1 },
      ]}>
      <Icon name={icon} size={size} color={colors.text} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    width: TOUCH_TARGET,
    height: TOUCH_TARGET,
    borderRadius: TOUCH_TARGET / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
