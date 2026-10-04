import Ionicons from '@expo/vector-icons/Ionicons';
import type { ComponentProps } from 'react';
import type { ColorValue } from 'react-native';

export type IconName = ComponentProps<typeof Ionicons>['name'];

type Props = { name: IconName; size?: number; color: ColorValue };

/** Decorative by default; give the parent the accessible label. */
export function Icon({ name, size = 22, color }: Props) {
  return <Ionicons name={name} size={size} color={color} accessibilityElementsHidden importantForAccessibility="no" />;
}
