import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, Platform, Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

import type { MicMode } from '@/stores/prefs';
import { useTheme } from '@/theme/ThemeProvider';
import { MIC_SIZE } from '@/theme/layout';

import { Icon } from './Icon';

type Props = {
  mode: MicMode;
  recording: boolean;
  /** Uploading or waiting for Luna: the mic is visible but can't be pressed. */
  busy?: boolean;
  disabled?: boolean;
  /** 0..1 input loudness while recording. */
  level?: number;
  onStart: () => void;
  onStop: () => void;
  testID?: string;
};

/** How far the orb's face sits above its edge; it sinks while held. */
const DEPTH = 7;

/**
 * The one bold element on a speaking screen: a glossy 3D mango orb. Hold to talk, release to send; or tap to start and
 * tap to stop. Screen-reader users always get tap-to-toggle, since holding is awkward with a screen reader.
 */
export function MicButton({ mode, recording, busy = false, disabled = false, level = 0, onStart, onStop, testID }: Props) {
  const { colors } = useTheme();
  const reduceMotion = useReducedMotion();
  const effectiveMode = useEffectiveMicMode(mode);
  const inactive = disabled || busy;

  const pulse = useSharedValue(0);
  const loudness = useSharedValue(0);

  useEffect(() => {
    if (recording && !reduceMotion) {
      pulse.set(0);
      pulse.set(withRepeat(withTiming(1, { duration: 1200, easing: Easing.out(Easing.quad) }), -1, false));
    } else {
      cancelAnimation(pulse);
      pulse.set(0);
    }
  }, [pulse, recording, reduceMotion]);

  useEffect(() => {
    loudness.set(reduceMotion ? 0 : withTiming(recording ? level : 0, { duration: 120 }));
  }, [level, loudness, recording, reduceMotion]);

  const ringStyle = useAnimatedStyle(() => ({
    opacity: 0.5 * (1 - pulse.get()),
    transform: [{ translateY: -DEPTH / 2 }, { scale: 1 + pulse.get() * 0.5 }],
  }));
  const buttonStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 + loudness.get() * 0.12 }],
  }));

  const begin = () => {
    haptic('start');
    onStart();
  };
  const end = () => {
    haptic('stop');
    onStop();
  };

  const label = recording
    ? effectiveMode === 'hold'
      ? 'Recording. Let go to send.'
      : 'Stop recording and send'
    : effectiveMode === 'hold'
      ? 'Hold to talk'
      : 'Start recording';

  return (
    <View style={styles.container}>
      <Animated.View style={[styles.ring, { backgroundColor: colors.primary }, ringStyle]} />
      {recording && reduceMotion ? <View style={[styles.staticRing, { borderColor: colors.primary }]} /> : null}
      <Animated.View style={buttonStyle}>
        <Pressable
          testID={testID}
          accessibilityRole="button"
          accessibilityLabel={label}
          accessibilityState={{ disabled: inactive, busy, selected: recording }}
          disabled={inactive}
          onPressIn={effectiveMode === 'hold' ? begin : undefined}
          // Always report the release: letting go before the mic is ready must cancel the start.
          onPressOut={effectiveMode === 'hold' ? end : undefined}
          onPress={effectiveMode === 'toggle' ? () => (recording ? end() : begin()) : undefined}
          style={[styles.button, { opacity: disabled ? 0.5 : 1 }]}>
          {({ pressed }) => {
            const sunk = pressed || recording;
            return (
              <>
                <View
                  style={[
                    styles.edge,
                    { backgroundColor: colors.mango3d.edge, boxShadow: sunk ? colors.cardShadow : colors.raisedShadow },
                  ]}
                />
                <LinearGradient
                  colors={colors.mango3d.face}
                  style={[styles.face, { transform: [{ translateY: sunk ? DEPTH - 2 : 0 }] }]}>
                  <LinearGradient
                    colors={['rgba(255,255,255,0.75)', 'rgba(255,255,255,0)']}
                    style={styles.gloss}
                  />
                  {busy ? (
                    <ActivityIndicator size="large" color={colors.onPrimary} />
                  ) : (
                    <Icon
                      name={recording && effectiveMode === 'toggle' ? 'stop' : 'mic'}
                      size={40}
                      color={colors.onPrimary}
                    />
                  )}
                </LinearGradient>
              </>
            );
          }}
        </Pressable>
      </Animated.View>
    </View>
  );
}

/**
 * The mode the mic actually uses: the learner's choice, except that screen-reader users always get tap-to-toggle.
 * Instructions shown next to the mic must use this, not the raw preference.
 */
export function useEffectiveMicMode(preferred: MicMode): MicMode {
  return useScreenReader() ? 'toggle' : preferred;
}

function useScreenReader(): boolean {
  const [enabled, setEnabled] = useState(false);
  useEffect(() => {
    // Browsers can't tell whether a screen reader is running, and react-native-web always answers "yes", which would
    // silently override the learner's choice. On the web we go by the setting (offered before the placement chat).
    if (Platform.OS === 'web') {
      return;
    }
    AccessibilityInfo.isScreenReaderEnabled()
      .then(setEnabled)
      .catch(() => undefined);
    const subscription = AccessibilityInfo.addEventListener('screenReaderChanged', setEnabled);
    return () => subscription.remove();
  }, []);
  return enabled;
}

function haptic(kind: 'start' | 'stop') {
  if (Platform.OS === 'web') {
    return;
  }
  const run = kind === 'start' ? Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium) : Haptics.selectionAsync();
  run.catch(() => undefined);
}

const styles = StyleSheet.create({
  container: {
    width: MIC_SIZE * 1.6,
    height: MIC_SIZE * 1.6,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'center',
  },
  ring: {
    position: 'absolute',
    width: MIC_SIZE,
    height: MIC_SIZE,
    borderRadius: MIC_SIZE / 2,
    pointerEvents: 'none',
  },
  staticRing: {
    position: 'absolute',
    pointerEvents: 'none',
    width: MIC_SIZE + 20,
    height: MIC_SIZE + 20,
    borderRadius: (MIC_SIZE + 20) / 2,
    borderWidth: 4,
    transform: [{ translateY: -DEPTH / 2 }],
  },
  button: {
    width: MIC_SIZE,
    height: MIC_SIZE + DEPTH,
  },
  edge: {
    position: 'absolute',
    top: DEPTH,
    width: MIC_SIZE,
    height: MIC_SIZE,
    borderRadius: MIC_SIZE / 2,
  },
  face: {
    width: MIC_SIZE,
    height: MIC_SIZE,
    borderRadius: MIC_SIZE / 2,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  gloss: {
    position: 'absolute',
    top: MIC_SIZE * 0.06,
    left: MIC_SIZE * 0.18,
    width: MIC_SIZE * 0.64,
    height: MIC_SIZE * 0.42,
    borderRadius: MIC_SIZE,
    pointerEvents: 'none',
  },
});
