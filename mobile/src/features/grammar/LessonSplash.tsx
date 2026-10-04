import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useEffectEvent } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Icon3D, type Icon3DName } from '@/components/Icon3D';
import { Kai3D } from '@/components/Kai3D';
import { Screen } from '@/components/Screen';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

/** Long enough to read the title; the button moves on sooner. */
const AUTO_ADVANCE_MS = 2600;

type Props = {
  /** The big word: "LEARN", "FLUENCY BOOSTER", "PRACTICE". */
  kicker: string;
  title: string;
  detail: string;
  icon: Icon3DName;
  buttonLabel: string;
  onDone: () => void;
};

/**
 * The title card at the start of each part of a lesson: the part's name pops in big over Luna, then the lesson carries
 * on by itself (or straight away with the button).
 */
export function LessonSplash({ kicker, title, detail, icon, buttonLabel, onDone }: Props) {
  const { colors } = useTheme();
  const { height } = useWindowDimensions();
  const kaiSize = Math.round(Math.min(330, Math.max(200, height * 0.34)));
  const reduceMotion = useReducedMotion();
  const pop = useSharedValue(reduceMotion ? 1 : 0);

  const advance = useEffectEvent(onDone);
  useEffect(() => {
    if (!reduceMotion) {
      pop.set(withDelay(120, withSpring(1, { damping: 9, stiffness: 140 })));
    }
    const timer = setTimeout(advance, AUTO_ADVANCE_MS);
    return () => clearTimeout(timer);
  }, [pop, reduceMotion]);

  const kickerStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, pop.get() * 1.4),
    transform: [{ scale: 0.6 + pop.get() * 0.4 }],
  }));
  const rest = useSharedValue(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (!reduceMotion) {
      rest.set(withDelay(380, withTiming(1, { duration: 360 })));
    }
  }, [rest, reduceMotion]);
  const restStyle = useAnimatedStyle(() => ({ opacity: rest.get(), transform: [{ translateY: (1 - rest.get()) * 12 }] }));

  return (
    <Screen scroll={false} footer={<Button label={buttonLabel} icon="arrow-forward" onPress={onDone} testID="splash-go" />}>
      <LinearGradient
        colors={colors.heroGradient}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        // The words sit centred in the space above Luna.
        style={[styles.card, { boxShadow: colors.raisedShadow, paddingBottom: Math.round(kaiSize * 0.83) }]}>
        <View style={[styles.ring, { borderColor: 'rgba(255,255,255,0.14)' }]} />
        <View style={[styles.ring, styles.ringInner, { borderColor: 'rgba(255,255,255,0.2)' }]} />
        <View style={styles.centered}>
          <Animated.View style={[styles.top, kickerStyle]}>
            <View style={styles.iconPlate}>
              <Icon3D name={icon} size={44} />
            </View>
            <AppText accessibilityRole="header" style={[styles.kicker, { color: colors.onHero }]}>
              {kicker}
            </AppText>
          </Animated.View>
          <Animated.View style={[styles.text, restStyle]}>
            <AppText variant="heading" style={[styles.center, { color: colors.onHero }]}>
              {title}
            </AppText>
            <AppText style={[styles.center, { color: colors.onHeroMuted }]}>{detail}</AppText>
          </Animated.View>
        </View>
        {/* Anchored to the bottom edge, which hides where her torso is cut off. */}
        <Kai3D mood="happy" size={kaiSize} glow={false} style={[styles.kai, { bottom: -Math.round(kaiSize * 0.17) }]} />
      </LinearGradient>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    borderRadius: radius.lg + 12,
    overflow: 'hidden',
    alignItems: 'center',
    paddingTop: space.xl,
    paddingHorizontal: space.xl,
    marginBottom: space.sm,
  },
  // Auto margins centre the words, but on a short screen they start at the top instead of being cut off.
  centered: { marginVertical: 'auto', alignItems: 'center' },
  ring: {
    position: 'absolute',
    width: 520,
    height: 520,
    borderRadius: 260,
    borderWidth: 40,
    top: -150,
    alignSelf: 'center',
  },
  ringInner: { width: 300, height: 300, borderRadius: 150, top: -40, borderWidth: 24 },
  top: { alignItems: 'center', gap: space.md },
  iconPlate: {
    width: 76,
    height: 76,
    borderRadius: 38,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.2)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.4)',
  },
  // A heavy word with a deeper copy under it, so it reads as 3D lettering.
  kicker: {
    fontFamily: fonts.headingHeavy,
    fontSize: 44,
    lineHeight: 52,
    letterSpacing: 1.5,
    textAlign: 'center',
    textShadowColor: 'rgba(30, 14, 110, 0.55)',
    textShadowOffset: { width: 0, height: 4 },
    textShadowRadius: 0,
  },
  text: { gap: space.xs, marginTop: space.lg, maxWidth: 420 },
  center: { textAlign: 'center' },
  kai: { position: 'absolute', alignSelf: 'center' },
});
