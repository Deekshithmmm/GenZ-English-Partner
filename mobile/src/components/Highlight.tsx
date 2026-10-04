import { useEffect, useState, type ReactNode } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';

import { useTheme } from '@/theme/ThemeProvider';

type Props = {
  children: ReactNode;
  /** Stagger reveals in a list. */
  delayMs?: number;
};

/**
 * The mango highlighter swipe: a marker stroke drawn left to right behind a word or short phrase. It sits in the
 * lower two-thirds of the line like a real highlighter, and appears instantly when reduced motion is on. In dark mode
 * it covers the whole line, because highlighted words are set in dark `onHighlight` ink that only reads on mango.
 * Text inside should use `colors.onHighlight`.
 */
export function Highlight({ children, delayMs = 0 }: Props) {
  const { colors, scheme } = useTheme();
  const reduceMotion = useReducedMotion();
  const [width, setWidth] = useState(0);
  const progress = useSharedValue(reduceMotion ? 1 : 0);

  useEffect(() => {
    if (width === 0) {
      return;
    }
    if (reduceMotion) {
      progress.set(1);
      return;
    }
    progress.set(withDelay(delayMs, withTiming(1, { duration: 420, easing: Easing.out(Easing.cubic) })));
  }, [delayMs, progress, reduceMotion, width]);

  // The stroke overhangs the text slightly on both sides, like a real marker.
  const strokeStyle = useAnimatedStyle(() => ({ width: (width + 6) * progress.get() }));

  return (
    <View style={styles.wrap} onLayout={(e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width)}>
      <Animated.View
        style={[styles.stroke, { backgroundColor: colors.highlight, top: scheme === 'dark' ? '2%' : '30%' }, strokeStyle]}
        accessibilityElementsHidden
        importantForAccessibility="no"
      />
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { alignSelf: 'flex-start', position: 'relative' },
  stroke: {
    position: 'absolute',
    left: -3,
    bottom: '4%',
    borderRadius: 4,
    transform: [{ skewX: '-8deg' }],
  },
});
