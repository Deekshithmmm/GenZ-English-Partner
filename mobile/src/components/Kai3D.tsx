import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { useTheme } from '@/theme/ThemeProvider';

import { KAI_FACE, KAI_FIGURE_SIDE, KAI_LOOPS, KAI_PORTAL, KAI_STILL } from './kaiArt';

export type KaiMood = 'idle' | 'talk' | 'listen' | 'think' | 'happy';

type Props = {
  /** idle: waiting. talk: Luna is speaking. listen: the learner is talking. think: Luna is working out a reply. */
  mood?: KaiMood;
  /** open: the width and height of the figure. portal: the width of the round window. */
  size?: number;
  /** A soft glow behind Luna, so she stands out from the page. */
  glow?: boolean;
  /**
   * open: Luna waving, with the background cut away (her body is cut off at the bottom, so anchor her to an edge).
   * portal: head and shoulders leaning out of a glowing round window, like a video call. Her hair rises above the
   * window, so the component is taller than `size` by that much.
   */
  frame?: 'open' | 'portal';
  style?: StyleProp<ViewStyle>;
};

/**
 * Luna, from her picture (tools/kai-2d). Her face turns and nods in 3D, she blinks, and her mouth moves while she talks
 * (animated loops made from the picture and a depth map); on top of that she breathes, sways, leans in while she
 * listens, tilts her head while she thinks and bounces when she's happy. Decorative: what Luna says is always on screen
 * as text too. Still when reduced motion is on.
 */
export function Kai3D({ mood = 'idle', size = 220, glow = true, frame = 'open', style }: Props) {
  return frame === 'portal' ? (
    <Portal mood={mood} size={size} style={style} />
  ) : (
    <Standing mood={mood} size={size} glow={glow} style={style} />
  );
}

/** How far her hair rises above the round window, for a window `size` wide. */
function kaiPortalRise(size: number): number {
  const [, top, side] = KAI_PORTAL;
  return Math.round((top * size) / side);
}

function Standing({ mood, size, glow, style }: { mood: KaiMood; size: number; glow: boolean; style: Props['style'] }) {
  const { scheme } = useTheme();
  return (
    <View
      style={[{ width: size, height: size }, style]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants">
      {glow ? (
        <View
          style={[
            styles.glow,
            {
              width: size * 0.78,
              height: size * 0.78,
              borderRadius: size,
              left: size * 0.11,
              top: size * 0.08,
              backgroundColor: scheme === 'dark' ? 'rgba(140, 110, 255, 0.35)' : 'rgba(255, 255, 255, 0.75)',
              boxShadow:
                scheme === 'dark'
                  ? `0px 0px ${Math.round(size * 0.22)}px rgba(140, 110, 255, 0.55)`
                  : `0px 0px ${Math.round(size * 0.2)}px rgba(160, 130, 255, 0.45)`,
            },
          ]}
        />
      ) : null}
      <KaiBody mood={mood} width={size} />
      {mood === 'think' ? <ThinkingDots size={size} top={size * 0.04} /> : null}
    </View>
  );
}

/**
 * The round window: a glossy disc behind her, her body fading into it at the bottom, and a bright rim in front of her
 * everywhere but the top, where her head comes out of it.
 */
function Portal({ mood, size, style }: { mood: KaiMood; size: number; style: Props['style'] }) {
  const { colors, scheme } = useTheme();
  const [left, top, side] = KAI_PORTAL;
  const scale = size / side;
  const rise = kaiPortalRise(size);
  const disc = scheme === 'dark' ? colors.violet3d.face : colors.selected3d.face;
  const round = { width: size, height: size, borderRadius: size / 2, top: rise };
  return (
    <View
      style={[{ width: size, height: size + rise }, style]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants">
      {mood === 'talk' || mood === 'listen' ? (
        <Ring size={size} top={rise} color={mood === 'listen' ? colors.success : colors.accent} />
      ) : null}
      <LinearGradient
        colors={disc}
        start={{ x: 0.2, y: 0 }}
        end={{ x: 0.8, y: 1 }}
        style={[
          styles.layer,
          round,
          { boxShadow: scheme === 'dark' ? '0px 0px 36px rgba(140, 110, 255, 0.55)' : colors.raisedShadow },
        ]}
      />
      {/* Light falling on the disc from above, which makes it read as a ball rather than a flat circle. */}
      <LinearGradient
        colors={scheme === 'dark' ? ['rgba(255,255,255,0.28)', 'rgba(255,255,255,0)'] : ['#FFFFFF', 'rgba(255,255,255,0)']}
        style={[
          styles.layer,
          { left: size * 0.16, top: rise + size * 0.05, width: size * 0.68, height: size * 0.42, borderRadius: size },
        ]}
      />
      <View
        style={[
          styles.window,
          { width: size, height: size + rise, borderBottomLeftRadius: size / 2, borderBottomRightRadius: size / 2 },
        ]}>
        <KaiBody
          mood={mood}
          width={KAI_FIGURE_SIDE * scale}
          style={{ position: 'absolute', left: -left * scale, top: rise - top * scale }}
        />
        <LinearGradient
          colors={[`${disc[disc.length - 1]}00`, disc[disc.length - 1]]}
          style={[styles.fade, { height: size * 0.24 }]}
        />
      </View>
      <View
        style={[
          styles.layer,
          styles.rim,
          round,
          { borderWidth: Math.max(2, size * 0.022), borderColor: colors.glassBorder, borderTopColor: 'transparent' },
        ]}
      />
      {mood === 'think' ? <ThinkingDots size={size} top={rise} /> : null}
    </View>
  );
}

/** Luna's face in a round frame, for chat lines and small spots. */
export function KaiFace({ size = 40 }: { size?: number }) {
  const { colors, scheme } = useTheme();
  return (
    <View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[
        styles.face,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          borderWidth: Math.max(1.5, size * 0.05),
          borderColor: colors.glassBorder,
          boxShadow: colors.cardShadow,
        },
      ]}>
      <LinearGradient
        colors={scheme === 'dark' ? colors.violet3d.face : colors.selected3d.face}
        style={StyleSheet.absoluteFill}
      />
      <Image source={KAI_FACE} style={styles.fill} contentFit="cover" />
    </View>
  );
}

/** Lean and size per mood: she leans in to listen and tilts her head while she thinks. */
const LEAN: Record<KaiMood, { angle: number; zoom: number }> = {
  idle: { angle: 0, zoom: 0 },
  talk: { angle: 0, zoom: 0 },
  listen: { angle: 2.5, zoom: 0.03 },
  think: { angle: -3, zoom: 0 },
  happy: { angle: 0, zoom: 0 },
};

/**
 * The figure, `width` square: the loops (her face turning, blinking and talking) moved as a whole by breathing, a slow
 * sway, a lean for the mood and a hop when she's happy. It pivots at the bottom of the picture, around her waist.
 */
function KaiBody({ mood, width, style }: { mood: KaiMood; width: number; style?: StyleProp<ViewStyle> }) {
  const reduceMotion = useReducedMotion();
  const breath = useSharedValue(0);
  const sway = useSharedValue(0);
  const swing = useSharedValue(1);
  const lean = useSharedValue(0);
  const zoom = useSharedValue(0);
  const hop = useSharedValue(0);
  const talking = mood === 'talk';

  // Breathing, always.
  useEffect(() => {
    if (reduceMotion) {
      return;
    }
    breath.set(withRepeat(withTiming(1, { duration: 1900, easing: Easing.inOut(Easing.sin) }), -1, true));
    return () => cancelAnimation(breath);
  }, [breath, reduceMotion]);

  // A slow sway, livelier while she talks.
  useEffect(() => {
    if (reduceMotion) {
      return;
    }
    const half = talking ? 1300 : 3400;
    sway.set(withRepeat(withTiming(1, { duration: half, easing: Easing.inOut(Easing.sin) }), -1, true));
    swing.set(withTiming(talking ? 1.4 : 1, { duration: 400 }));
    return () => cancelAnimation(sway);
  }, [reduceMotion, sway, swing, talking]);

  // The lean for the mood, and the happy bounce.
  useEffect(() => {
    if (reduceMotion) {
      lean.set(0);
      zoom.set(0);
      hop.set(0);
      return;
    }
    lean.set(withSpring(LEAN[mood].angle, { damping: 14, stiffness: 90 }));
    zoom.set(withSpring(LEAN[mood].zoom, { damping: 16, stiffness: 90 }));
    if (mood === 'happy') {
      hop.set(
        withRepeat(
          withSequence(
            withTiming(1, { duration: 260, easing: Easing.out(Easing.quad) }),
            withTiming(0, { duration: 320, easing: Easing.in(Easing.quad) }),
            withDelay(900, withTiming(0, { duration: 1 })),
          ),
          -1,
        ),
      );
    } else {
      hop.set(withTiming(0, { duration: 200 }));
    }
    return () => cancelAnimation(hop);
  }, [hop, lean, mood, reduceMotion, zoom]);

  const motion = useAnimatedStyle(() => ({
    transform: [
      { translateY: -hop.get() * width * 0.03 },
      { rotate: `${(sway.get() * 2 - 1) * swing.get() + lean.get()}deg` },
      { scale: 1 + breath.get() * 0.01 + zoom.get() },
    ],
  }));

  return (
    <Animated.View style={[{ width, height: width }, styles.pivot, style, motion]}>
      <KaiLoops talking={talking} still={reduceMotion} />
    </Animated.View>
  );
}

/**
 * The idle and talking loops, one over the other: both play all the time, and the talking one fades in and out, so
 * switching never waits for a loop to load. They share her outline, so the one on top covers the one below.
 */
function KaiLoops({ talking, still }: { talking: boolean; still: boolean }) {
  const talk = useSharedValue(talking ? 1 : 0);
  useEffect(() => {
    talk.set(withTiming(talking ? 1 : 0, { duration: 160 }));
  }, [talk, talking]);
  const talkStyle = useAnimatedStyle(() => ({ opacity: talk.get() }));

  if (still) {
    return <Image source={KAI_STILL} style={styles.fill} contentFit="contain" />;
  }
  return (
    <>
      <Image
        source={KAI_LOOPS.idle}
        placeholder={KAI_STILL}
        placeholderContentFit="contain"
        style={StyleSheet.absoluteFill}
        contentFit="contain"
      />
      <Animated.View style={[StyleSheet.absoluteFill, talkStyle]}>
        <Image source={KAI_LOOPS.talk} style={styles.fill} contentFit="contain" />
      </Animated.View>
    </>
  );
}

/** A soft ring pulsing out from the round window: violet while Luna talks, green while she listens. */
function Ring({ size, top, color }: { size: number; top: number; color: string }) {
  const reduceMotion = useReducedMotion();
  const pulse = useSharedValue(0);
  useEffect(() => {
    if (reduceMotion) {
      return;
    }
    pulse.set(withRepeat(withTiming(1, { duration: 1300, easing: Easing.out(Easing.quad) }), -1, false));
    return () => cancelAnimation(pulse);
  }, [pulse, reduceMotion]);
  const style = useAnimatedStyle(() => ({
    opacity: reduceMotion ? 0.5 : 0.55 * (1 - pulse.get()),
    transform: [{ scale: reduceMotion ? 1.06 : 1 + pulse.get() * 0.16 }],
  }));
  return (
    <Animated.View
      style={[styles.ring, { width: size, height: size, borderRadius: size / 2, top, borderColor: color }, style]}
    />
  );
}

/** Three dots in a little bubble beside Luna's head while she thinks. */
function ThinkingDots({ size, top }: { size: number; top: number }) {
  const { colors } = useTheme();
  const dot = Math.max(5, Math.round(size * 0.04));
  return (
    <View
      style={[
        styles.thought,
        {
          right: -size * 0.02,
          top,
          gap: dot * 0.6,
          paddingHorizontal: dot * 1.2,
          paddingVertical: dot * 0.9,
          borderRadius: dot * 3,
          backgroundColor: colors.glassStrong,
          borderColor: colors.glassBorder,
          boxShadow: colors.cardShadow,
        },
      ]}>
      {[0, 1, 2].map((i) => (
        <Dot key={i} size={dot} delay={i * 180} color={colors.accent} />
      ))}
    </View>
  );
}

function Dot({ size, delay, color }: { size: number; delay: number; color: string }) {
  const reduceMotion = useReducedMotion();
  const lift = useSharedValue(0);
  useEffect(() => {
    if (reduceMotion) {
      return;
    }
    lift.set(withDelay(delay, withRepeat(withTiming(1, { duration: 420, easing: Easing.inOut(Easing.quad) }), -1, true)));
    return () => cancelAnimation(lift);
  }, [delay, lift, reduceMotion]);
  const style = useAnimatedStyle(() => ({
    opacity: 0.45 + lift.get() * 0.55,
    transform: [{ translateY: -lift.get() * size * 0.5 }],
  }));
  return <Animated.View style={[{ width: size, height: size, borderRadius: size / 2, backgroundColor: color }, style]} />;
}

const styles = StyleSheet.create({
  fill: { width: '100%', height: '100%' },
  glow: { position: 'absolute' },
  layer: { position: 'absolute', left: 0, pointerEvents: 'none' },
  window: { position: 'absolute', left: 0, top: 0, overflow: 'hidden' },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  rim: { backgroundColor: 'transparent' },
  pivot: { transformOrigin: '50% 100%' },
  face: { overflow: 'hidden' },
  ring: { position: 'absolute', left: 0, borderWidth: 3, pointerEvents: 'none' },
  thought: { position: 'absolute', flexDirection: 'row', alignItems: 'center', borderWidth: 1, pointerEvents: 'none' },
});
