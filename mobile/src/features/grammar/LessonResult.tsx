import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState, type ReactNode } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle } from 'react-native-svg';

import type { GrammarLesson, LessonReport } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Icon } from '@/components/Icon';
import { Icon3D } from '@/components/Icon3D';
import { Kai3D } from '@/components/Kai3D';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { SpeechBubble } from '@/components/SpeechBubble';
import { StatTile } from '@/components/StatTile';
import { formatSpeakingTime } from '@/features/practice/format';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

type Props = {
  lesson: GrammarLesson;
  report: LessonReport;
  onNext: (() => void) | null;
  onRetry: () => void;
  onBack: () => void;
};

/** The end of a lesson: a celebration first, then the report with the score, Luna's note and what to improve. */
export function LessonResult(props: Props) {
  const [view, setView] = useState<'celebrate' | 'report'>('celebrate');
  return view === 'celebrate' ? (
    <Celebration {...props} onContinue={() => setView('report')} />
  ) : (
    <Report {...props} />
  );
}

function Celebration({ report, onContinue }: Props & { onContinue: () => void }) {
  const { colors } = useTheme();
  const { height } = useWindowDimensions();
  const kaiSize = Math.round(Math.min(330, Math.max(200, height * 0.34)));
  const title = !report.passed ? 'So close!' : report.stars === 3 ? 'Perfect lesson!' : 'Lesson complete!';
  const detail = report.passed
    ? `${report.correct} of ${report.total} right`
    : `${report.correct} of ${report.total} right. Get half right to pass, so have another go.`;
  return (
    <Screen scroll={false} footer={<Button label="See your report" icon="document-text-outline" onPress={onContinue} testID="result-report" />}>
      <LinearGradient
        colors={colors.heroGradient}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={[styles.celebrate, { boxShadow: colors.raisedShadow, paddingBottom: Math.round(kaiSize * 0.83) }]}
        accessible
        accessibilityRole="header"
        accessibilityLabel={`${title} ${detail}.${report.passed ? ` ${report.stars} of 3 stars. ${report.xp} XP.` : ''}`}>
        <Confetti />
        {/* Centred in the space above Luna. */}
        <View style={styles.centered}>
          {report.passed ? (
            <View style={styles.bigStars}>
              {[1, 2, 3].map((n) => (
                <PopIn key={n} delayMs={350 + n * 220}>
                  <Icon3D name="star" size={n === 2 ? 76 : 60} style={{ opacity: n <= report.stars ? 1 : 0.25 }} />
                </PopIn>
              ))}
            </View>
          ) : (
            <PopIn delayMs={300}>
              <Icon3D name="target" size={72} />
            </PopIn>
          )}
          <AppText style={[styles.bigTitle, { color: colors.onHero }]}>{title}</AppText>
          <AppText variant="bodyStrong" style={[styles.center, { color: colors.onHeroMuted }]}>
            {detail}
          </AppText>
          {report.passed ? (
            <View style={styles.chips}>
              <Chip icon={<Icon3D name="gem" size={20} />} label={`+${report.xp} XP`} />
              {report.progress.streak > 0 ? (
                <Chip icon={<Icon3D name="fire" size={20} />} label={`${report.progress.streak}-day streak`} />
              ) : null}
            </View>
          ) : null}
        </View>
        {/* Anchored to the bottom edge, which hides where her torso is cut off. */}
        <Kai3D
          mood={report.passed ? 'happy' : 'idle'}
          size={kaiSize}
          glow={false}
          style={[styles.celebrateKai, { bottom: -Math.round(kaiSize * 0.17) }]}
        />
      </LinearGradient>
    </Screen>
  );
}

function Report({ lesson, report, onNext, onRetry, onBack }: Props) {
  const { colors } = useTheme();
  const footer = (
    <>
      {report.passed && onNext ? (
        <Button label="Next lesson" icon="arrow-forward" onPress={onNext} testID="result-next" />
      ) : !report.passed ? (
        <Button label="Try again" icon="refresh" onPress={onRetry} testID="result-retry" />
      ) : null}
      <Button
        label="Back to the path"
        variant={report.passed && !onNext ? 'primary' : 'ghost'}
        icon="map-outline"
        onPress={onBack}
        testID="result-path"
      />
    </>
  );
  return (
    <Screen footer={footer}>
      <View style={styles.body}>
        <View style={styles.header}>
          <AppText variant="label" tone="muted">
            Your report
          </AppText>
          <AppText variant="title" accessibilityRole="header">
            {lesson.title}
          </AppText>
        </View>

        <View
          style={[styles.scoreCard, { backgroundColor: colors.glass, borderColor: colors.glassBorder, boxShadow: colors.cardShadow }]}
          accessible
          accessibilityLabel={`Score ${report.score} percent. ${report.correct} of ${report.total} right.${report.passed ? ` ${report.stars} of 3 stars.` : ' Not passed yet.'}`}>
          <ScoreRing score={report.score} passed={report.passed} />
          <View style={styles.flex}>
            <AppText variant="subheading">
              {report.correct} of {report.total} right
            </AppText>
            {report.passed ? (
              <View style={styles.stars}>
                {[1, 2, 3].map((n) => (
                  <Icon3D key={n} name="star" size={24} style={{ opacity: n <= report.stars ? 1 : 0.25 }} />
                ))}
              </View>
            ) : (
              <AppText variant="small" tone="muted">
                Half right passes the lesson.
              </AppText>
            )}
            {report.passed && !report.firstTime ? (
              <AppText variant="small" tone="muted">
                Practice round: your best stars are kept.
              </AppText>
            ) : null}
          </View>
        </View>

        <View style={styles.tiles}>
          <StatTile icon="chatbubbles-outline" icon3d="speech" value={String(report.wordsUsed)} label="Words used" />
          <StatTile icon="time-outline" icon3d="hourglass" value={formatSpeakingTime(report.seconds)} label="Time" />
          <StatTile icon="star-outline" icon3d="gem" value={`+${report.xp}`} label="XP" />
        </View>

        <SpeechBubble tone="primary">
          <AppText variant="label" tone="muted">
            Luna&apos;s note
          </AppText>
          <AppText variant="bodyStrong">{report.note}</AppText>
        </SpeechBubble>

        <View style={styles.section}>
          <AppText variant="subheading" accessibilityRole="header">
            Improve these
          </AppText>
          {report.improvements.length > 0 ? (
            report.improvements.map((item) => <Improvement key={item.tip + item.wrong} {...item} />)
          ) : (
            <Notice tone="success" message="Nothing to fix this time. Clean lesson!" />
          )}
        </View>
      </View>
    </Screen>
  );
}

function Improvement({ tip, wrong, right }: { tip: string; wrong: string; right: string }) {
  const { colors } = useTheme();
  return (
    <View
      style={[styles.improvement, { backgroundColor: colors.glass, borderColor: colors.glassBorder }]}
      accessible
      accessibilityLabel={`${tip} You said: ${wrong}. Better: ${right}.`}>
      <AppText variant="bodyStrong">{tip}</AppText>
      <View style={styles.line}>
        <Icon name="close-circle" size={18} color={colors.error} />
        <AppText variant="small" tone="error" style={[styles.flex, styles.struck]}>
          {wrong}
        </AppText>
      </View>
      <View style={styles.line}>
        <Icon name="checkmark-circle" size={18} color={colors.success} />
        <AppText variant="small" style={[styles.flex, styles.bold]}>
          {right}
        </AppText>
      </View>
    </View>
  );
}

const RING = 108;
const STROKE = 11;

/** The score as a ring that fills up: violet when passed, mango when not yet. */
function ScoreRing({ score, passed }: { score: number; passed: boolean }) {
  const { colors } = useTheme();
  const r = (RING - STROKE) / 2;
  const circumference = 2 * Math.PI * r;
  return (
    <View style={styles.ring}>
      <Svg width={RING} height={RING}>
        <Circle cx={RING / 2} cy={RING / 2} r={r} stroke={colors.surfaceMuted} strokeWidth={STROKE} fill="none" />
        <Circle
          cx={RING / 2}
          cy={RING / 2}
          r={r}
          stroke={passed ? colors.accent : colors.primary}
          strokeWidth={STROKE}
          strokeLinecap="round"
          fill="none"
          strokeDasharray={`${(circumference * Math.max(score, 1)) / 100} ${circumference}`}
          transform={`rotate(-90 ${RING / 2} ${RING / 2})`}
        />
      </Svg>
      <View style={styles.ringLabel}>
        <AppText style={styles.ringScore}>{score}%</AppText>
      </View>
    </View>
  );
}

function Chip({ icon, label }: { icon: ReactNode; label: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.chip}>
      {icon}
      <AppText variant="bodyStrong" style={{ color: colors.onHero }}>
        {label}
      </AppText>
    </View>
  );
}

/** Springs in once after a delay. Still when reduced motion is on. */
function PopIn({ delayMs, children }: { delayMs: number; children: ReactNode }) {
  const reduceMotion = useReducedMotion();
  const scale = useSharedValue(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (!reduceMotion) {
      scale.set(withDelay(delayMs, withSequence(withSpring(1.25, { damping: 7 }), withTiming(1, { duration: 180 }))));
    }
  }, [delayMs, reduceMotion, scale]);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: scale.get() }] }));
  return <Animated.View style={style}>{children}</Animated.View>;
}

const CONFETTI = [
  { left: '8%', top: '10%', color: '#FFB21A', size: 12, rotate: '20deg' },
  { left: '22%', top: '4%', color: '#FF7AB8', size: 9, rotate: '-30deg' },
  { left: '78%', top: '8%', color: '#5FE6AD', size: 11, rotate: '45deg' },
  { left: '90%', top: '22%', color: '#FFE07A', size: 8, rotate: '10deg' },
  { left: '12%', top: '38%', color: '#9A82FF', size: 10, rotate: '-15deg' },
  { left: '86%', top: '46%', color: '#FF7AB8', size: 12, rotate: '35deg' },
  { left: '6%', top: '66%', color: '#5FE6AD', size: 9, rotate: '60deg' },
  { left: '92%', top: '72%', color: '#FFB21A', size: 10, rotate: '-40deg' },
] as const;

/** A few bits of paper scattered around Luna, drifting down once. */
function Confetti() {
  const reduceMotion = useReducedMotion();
  const drop = useSharedValue(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (!reduceMotion) {
      drop.set(withTiming(1, { duration: 1400 }));
    }
  }, [drop, reduceMotion]);
  const style = useAnimatedStyle(() => ({ opacity: Math.min(1, drop.get() * 2), transform: [{ translateY: (drop.get() - 1) * 40 }] }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.confetti, style]}>
      {CONFETTI.map((bit, i) => (
        <View
          key={i}
          style={{
            position: 'absolute',
            left: bit.left,
            top: bit.top,
            width: bit.size,
            height: bit.size * 1.6,
            borderRadius: 3,
            backgroundColor: bit.color,
            transform: [{ rotate: bit.rotate }],
          }}
        />
      ))}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  celebrate: {
    flex: 1,
    borderRadius: radius.lg + 12,
    alignItems: 'center',
    padding: space.xl,
    overflow: 'hidden',
    marginBottom: space.sm,
  },
  // Auto margins centre the words, but on a short screen they start at the top instead of being cut off.
  centered: { marginVertical: 'auto', alignItems: 'center', gap: space.sm },
  celebrateKai: { position: 'absolute', alignSelf: 'center' },
  confetti: { pointerEvents: 'none' },
  bigTitle: {
    fontFamily: fonts.headingHeavy,
    fontSize: 38,
    lineHeight: 46,
    textAlign: 'center',
    textShadowColor: 'rgba(30, 14, 110, 0.55)',
    textShadowOffset: { width: 0, height: 3 },
    textShadowRadius: 0,
  },
  center: { textAlign: 'center' },
  bigStars: { flexDirection: 'row', alignItems: 'flex-end', gap: space.sm, marginTop: space.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: space.sm, marginTop: space.sm },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    borderRadius: radius.pill,
    paddingHorizontal: space.md,
    paddingVertical: 6,
    backgroundColor: 'rgba(255,255,255,0.18)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.35)',
  },
  body: { gap: space.xl },
  header: { gap: 2 },
  flex: { flex: 1 },
  scoreCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.lg,
    borderRadius: radius.lg + 4,
    borderWidth: 1,
    padding: space.lg,
  },
  stars: { flexDirection: 'row', gap: 4, marginTop: 2 },
  ring: { width: RING, height: RING },
  ringLabel: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  ringScore: { fontFamily: fonts.headingHeavy, fontSize: 28, lineHeight: 34 },
  tiles: { flexDirection: 'row', gap: space.sm },
  section: { gap: space.md },
  improvement: { borderRadius: radius.lg, borderWidth: 1, padding: space.lg, gap: space.xs },
  line: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm },
  struck: { textDecorationLine: 'line-through' },
  bold: { fontFamily: fonts.bodyBold },
});
