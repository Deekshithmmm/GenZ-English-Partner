import { LinearGradient } from 'expo-linear-gradient';
import type { ReactNode } from 'react';
import {
  Pressable,
  StyleSheet,
  View,
  type AccessibilityRole,
  type AccessibilityState,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import type { Material3D } from '@/theme/colors';
import { useTheme } from '@/theme/ThemeProvider';

type Props = {
  material: Material3D;
  children: ReactNode | ((state: { pressed: boolean }) => ReactNode);
  onPress?: () => void;
  onPressIn?: () => void;
  onPressOut?: () => void;
  disabled?: boolean;
  /** How far the face sits above its edge; it sinks by this much when pressed. */
  depth?: number;
  radius?: number;
  /** Lifts the whole object off the page with a soft coloured shadow. */
  floating?: boolean;
  /** Layout of the outer box (width, margins, alignSelf). */
  style?: StyleProp<ViewStyle>;
  /** Padding and layout inside the face. */
  faceStyle?: StyleProp<ViewStyle>;
  accessibilityRole?: AccessibilityRole;
  accessibilityLabel?: string;
  accessibilityHint?: string;
  accessibilityState?: AccessibilityState;
  hitSlop?: number;
  testID?: string;
};

/**
 * A glossy 3D object you can press: a lit gradient face over a darker edge, which sinks onto the edge while pressed,
 * like a physical key. Buttons, chips, choices and path nodes are all built on it, so the whole app feels tactile.
 * Without onPress it renders as a static 3D surface (no button role).
 */
export function Pressable3D({
  material,
  children,
  onPress,
  onPressIn,
  onPressOut,
  disabled = false,
  depth = 5,
  radius = 999,
  floating = true,
  style,
  faceStyle,
  accessibilityRole,
  accessibilityLabel,
  accessibilityHint,
  accessibilityState,
  hitSlop,
  testID,
}: Props) {
  const { colors } = useTheme();
  const interactive = Boolean(onPress || onPressIn || onPressOut);

  const render = (pressed: boolean) => {
    const sunk = pressed && interactive && !disabled;
    return (
      <>
        <View
          style={[
            StyleSheet.absoluteFill,
            {
              top: depth,
              borderRadius: radius,
              backgroundColor: material.edge,
              boxShadow: floating && !sunk ? colors.raisedShadow : colors.cardShadow,
            },
          ]}
        />
        <LinearGradient
          colors={material.face}
          start={{ x: 0.5, y: 0 }}
          end={{ x: 0.5, y: 1 }}
          style={[
            styles.face,
            { borderRadius: radius, transform: [{ translateY: sunk ? depth - 1 : 0 }] },
            faceStyle,
          ]}>
          {/* Light catching the top of the face. */}
          <LinearGradient
            colors={[`rgba(255,255,255,${material.gloss})`, 'rgba(255,255,255,0)']}
            style={[styles.gloss, { borderTopLeftRadius: radius, borderTopRightRadius: radius }]}
          />
          {typeof children === 'function' ? children({ pressed: sunk }) : children}
        </LinearGradient>
      </>
    );
  };

  if (!interactive) {
    return (
      <View style={[{ paddingBottom: depth }, style]} accessibilityLabel={accessibilityLabel} testID={testID}>
        {render(false)}
      </View>
    );
  }
  return (
    <Pressable
      onPress={onPress}
      onPressIn={onPressIn}
      onPressOut={onPressOut}
      disabled={disabled}
      hitSlop={hitSlop}
      accessibilityRole={accessibilityRole ?? 'button'}
      accessibilityLabel={accessibilityLabel}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled, ...accessibilityState }}
      testID={testID}
      style={[{ paddingBottom: depth, opacity: disabled ? 0.55 : 1 }, style]}>
      {({ pressed }) => render(pressed)}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  face: { overflow: 'hidden' },
  gloss: { position: 'absolute', top: 0, left: 0, right: 0, height: '52%', pointerEvents: 'none' },
});
