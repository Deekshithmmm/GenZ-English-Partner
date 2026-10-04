import { useQuery } from '@tanstack/react-query';
import { LinearGradient } from 'expo-linear-gradient';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useEffect, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { practiceApi } from '@/api/endpoints';
import { queryKeys } from '@/api/queryClient';
import type {
  CriterionResult,
  CriterionStatus,
  PracticeResult,
  PracticeSummary,
  Scorecard,
  SessionScores,
} from '@/api/types';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Highlight } from '@/components/Highlight';
import { Icon, type IconName } from '@/components/Icon';
import { Icon3D, type Icon3DName } from '@/components/Icon3D';
import { Kai3D } from '@/components/Kai3D';
import { Loading } from '@/components/Loading';
import { Notice } from '@/components/Notice';
import { ScoreMeter } from '@/components/ScoreMeter';
import { Screen } from '@/components/Screen';
import { SpeechBubble } from '@/components/SpeechBubble';
import { StatTile } from '@/components/StatTile';
import { Corrections } from '@/features/conversation/Corrections';
import { formatSpeakingTime } from '@/features/practice/format';
import { useSession } from '@/stores/session';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

export default function PracticeSummaryScreen() {
  const { sessionId } = useLocalSearchParams<{ sessionId?: string }>();
  // The chat leaves the result in the cache; completing again is idempotent, so a reload can fetch it.
  const result = useQuery({
    queryKey: queryKeys.practiceResult(sessionId ?? ''),
    queryFn: () => practiceApi.complete(sessionId ?? ''),
    enabled: Boolean(sessionId),
    staleTime: Infinity,
  });

  if (!sessionId) {
    return <Redirect href="/today" />;
  }

  const done = () => (router.canGoBack() ? router.back() : router.replace('/today'));

  if (!result.data) {
    return (
      <Screen
        footer={
          result.isError ? (
            <Notice
              tone="error"
              message={result.error.message}
              actionLabel="Try again"
              onAction={() => void result.refetch()}
            />
          ) : null
        }>
        <Loading label="Adding up your practice…" />
      </Screen>
    );
  }

  return (
    <Screen footer={<Button label="Back to today" icon="sunny-outline" onPress={done} testID="summary-done" />}>
      <SummaryBody result={result.data} />
    </Screen>
  );
}

function SummaryBody({ result }: { result: PracticeResult }) {
  const { colors } = useTheme();
  const language = useSession((s) => s.me?.profile.explanationLanguage);
  const { summary, progress } = result;
  const { stats } = summary;
  const streak = `${progress.streak}-day streak`;
  const focus = summary.focus ?? [];

  return (
    <View style={styles.body}>
      <LinearGradient
        colors={colors.heroGradient}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={[styles.hero, { boxShadow: colors.raisedShadow }]}
        accessible
        accessibilityRole="header"
        accessibilityLabel={`${summary.title} done. You earned ${summary.xp} XP.${progress.streak > 0 ? ` ${streak}.` : ''}`}>
        <AppText variant="label" style={{ color: colors.onHeroMuted }}>
          {summary.title} · done
        </AppText>
        <Highlight delayMs={250}>
          <AppText style={[styles.xp, { color: colors.onHighlight }]}>+{summary.xp} XP</AppText>
        </Highlight>
        {progress.streak > 0 ? (
          <View style={styles.streak}>
            <Flame />
            <AppText variant="bodyStrong" style={{ color: colors.onHero }}>
              {streak}
            </AppText>
            <AppText style={{ color: colors.onHeroMuted }}>· {progress.xpToday} XP today</AppText>
          </View>
        ) : null}
        <Kai3D mood="happy" size={180} glow={false} style={styles.heroKai} />
      </LinearGradient>

      <SpeechBubble tone="primary">
        <AppText variant="label" tone="muted">
          Luna&apos;s note
        </AppText>
        <AppText variant="bodyStrong">{summary.note}</AppText>
      </SpeechBubble>

      {summary.scorecard ? (
        <Section title={summary.kind === 'INTERVIEW' ? 'How the interview went' : 'How the scene went'}>
          <ScorecardCard scorecard={summary.scorecard} onTryAgain={tryAgain(summary)} />
        </Section>
      ) : null}

      {summary.scores ? (
        <Section title="Your scores">
          <ScoresCard scores={summary.scores} />
        </Section>
      ) : null}

      {focus.length > 0 ? (
        <Section title="Work on next">
          <FocusList items={focus} />
        </Section>
      ) : null}

      <Section title="Your session">
        <View style={styles.tiles}>
          <StatTile
            icon="mic-outline"
            icon3d="mic"
            value={formatSpeakingTime(stats.speakingSeconds)}
            label="Speaking time"
            detail={stats.voiceAnswers === 0 ? 'All typed this time' : undefined}
          />
          <StatTile icon="chatbubbles-outline" icon3d="speech" value={String(stats.answers)} label="Answers" />
        </View>
        <View style={styles.tiles}>
          <StatTile
            icon="speedometer-outline"
            icon3d="speaker"
            value={stats.averageWpm != null ? String(stats.averageWpm) : '–'}
            label="Words a minute"
            detail={stats.averageWpm != null ? 'Around 120–150 is easy to follow' : 'Only spoken answers are timed'}
          />
          <StatTile
            icon="ellipsis-horizontal"
            icon3d="thought"
            value={String(stats.fillerCount)}
            label="Filler words"
            detail={stats.topFiller ? `Most often: "${stats.topFiller}"` : 'None heard'}
          />
        </View>
      </Section>

      <Section title="Try these next time">
        {summary.corrections.length > 0 ? (
          <Corrections corrections={summary.corrections} title="From this session" language={language} />
        ) : (
          <Notice tone="success" message="Nothing worth fixing this time. Clear and natural!" />
        )}
      </Section>

      <Section title="How you earned it">
        <XpReceipt items={summary.xpItems} total={summary.xp} />
      </Section>
    </View>
  );
}

/** Back into the same scene, or a new interview (the pasted text isn't kept). Free chats have nothing to retry. */
function tryAgain(summary: PracticeSummary): (() => void) | undefined {
  const scenario = summary.scenario;
  if (summary.kind === 'ROLEPLAY' && scenario) {
    return () => router.replace({ pathname: '/practice/roleplay/[scenario]', params: { scenario } });
  }
  if (summary.kind === 'INTERVIEW') {
    return () => router.replace('/practice/interview');
  }
  return undefined;
}

const VERDICT: Record<Scorecard['verdict'], { title: string; icon: Icon3DName }> = {
  REACHED: { title: 'Goal reached', icon: 'trophy' },
  ALMOST: { title: 'Almost there', icon: 'target' },
  NOT_YET: { title: 'Not there yet', icon: 'rocket' },
};

const STATUS: Record<CriterionStatus, { icon: IconName; label: string }> = {
  MET: { icon: 'checkmark-circle', label: 'Done' },
  PARTLY: { icon: 'contrast-outline', label: 'Partly' },
  MISSED: { icon: 'ellipse-outline', label: 'Not yet' },
};

/** The scene's success criteria, each with the learner's own words as proof, under a one-line verdict. */
function ScorecardCard({ scorecard, onTryAgain }: { scorecard: Scorecard; onTryAgain?: () => void }) {
  const { colors } = useTheme();
  const verdict = VERDICT[scorecard.verdict];
  const reached = scorecard.verdict === 'REACHED';
  const partly = scorecard.criteria.filter((c) => c.status === 'PARTLY').length;
  const detail = reached
    ? `You did all ${scorecard.total} things the scene needed.`
    : `${scorecard.met} of ${scorecard.total} done${partly > 0 ? `, ${partly} partly` : ''}. ${
        scorecard.verdict === 'ALMOST' ? "One more go and you'll have it." : 'Try it again with the goals in mind.'
      }`;
  return (
    <Card padding={0} style={styles.scorecard}>
      <View
        style={[styles.verdict, { backgroundColor: reached ? colors.successBackground : colors.primarySoft }]}
        accessible
        accessibilityLabel={`${verdict.title}. ${detail}`}>
        <Icon3D name={verdict.icon} size={40} />
        <View style={styles.flex}>
          <AppText variant="subheading">{verdict.title}</AppText>
          <AppText variant="small">{detail}</AppText>
        </View>
      </View>
      {scorecard.criteria.map((result) => (
        <CriterionRow key={result.criterion} result={result} />
      ))}
      {onTryAgain && !reached ? (
        <View style={[styles.tryAgain, { borderTopColor: colors.hairline }]}>
          <Button label="Try it again" icon="refresh" variant="ghost" onPress={onTryAgain} testID="summary-retry" />
        </View>
      ) : null}
    </Card>
  );
}

function CriterionRow({ result }: { result: CriterionResult }) {
  const { colors } = useTheme();
  const status = STATUS[result.status];
  const color = { MET: colors.success, PARTLY: colors.text, MISSED: colors.textMuted }[result.status];
  // Met criteria quote the learner; anything else may say what was missing instead.
  const evidence = result.evidence
    ? result.status === 'MET'
      ? `“${result.evidence.replace(/^["“]|["”]$/g, '')}”`
      : result.evidence
    : null;
  return (
    <View
      style={[styles.criterion, { borderTopColor: colors.hairline }]}
      accessible
      accessibilityLabel={`${status.label}: ${result.criterion}.${evidence ? ` ${evidence}` : ''}`}>
      <Icon name={status.icon} size={22} color={color} />
      <View style={styles.flex}>
        <AppText variant="bodyStrong">{result.criterion}</AppText>
        {evidence ? (
          <AppText variant="small" tone="muted">
            {evidence}
          </AppText>
        ) : null}
      </View>
      <AppText variant="label" tone={result.status === 'MET' ? 'success' : 'muted'}>
        {status.label}
      </AppText>
    </View>
  );
}

function ScoresCard({ scores }: { scores: SessionScores }) {
  return (
    <Card style={styles.scores}>
      <ScoreMeter label="Fluency" score={scores.fluency} hint="Full, connected answers at an easy pace" />
      <ScoreMeter label="Grammar" score={scores.grammar} hint="How rarely your grammar slipped" />
      {scores.pronunciation != null ? (
        <ScoreMeter label="Pronunciation" score={scores.pronunciation} hint="How clearly your words came through" />
      ) : (
        <View style={styles.noScore}>
          <AppText variant="bodyStrong">Pronunciation</AppText>
          <AppText variant="small" tone="muted">
            Answer out loud next time to get this score.
          </AppText>
        </View>
      )}
      <ScoreMeter label="Vocabulary" score={scores.vocabulary} hint="The range and precision of your words" />
      <AppText variant="small" tone="muted">
        Scored against a confident, fluent speaker, so the numbers climb as you practise.
      </AppText>
    </Card>
  );
}

/** Numbered in mango, the one place this screen uses it as a fill: these are the next steps. */
function FocusList({ items }: { items: string[] }) {
  const { colors } = useTheme();
  return (
    <View style={styles.focus}>
      {items.map((item, index) => (
        <View key={item} style={styles.focusRow}>
          <View style={[styles.focusNumber, { backgroundColor: colors.primary }]}>
            <AppText variant="label" tone="onPrimary">
              {index + 1}
            </AppText>
          </View>
          <AppText style={styles.flex}>{item}</AppText>
        </View>
      ))}
    </View>
  );
}

/** The streak flame pops in once, the one celebration on this screen. Still when reduced motion is on. */
function Flame() {
  const reduceMotion = useReducedMotion();
  const scale = useSharedValue(reduceMotion ? 1 : 0.4);

  useEffect(() => {
    if (!reduceMotion) {
      scale.set(withDelay(500, withSequence(withSpring(1.35, { damping: 6 }), withTiming(1, { duration: 220 }))));
    }
  }, [reduceMotion, scale]);

  const style = useAnimatedStyle(() => ({ transform: [{ scale: scale.get() }] }));
  return (
    <Animated.View style={style}>
      <Icon3D name="fire" size={26} />
    </Animated.View>
  );
}

function XpReceipt({ items, total }: { items: { label: string; xp: number }[]; total: number }) {
  const { colors } = useTheme();
  return (
    <Card padding={0} style={styles.receipt}>
      {items.map((item) => (
        <View key={item.label} style={[styles.receiptRow, { borderBottomColor: colors.hairline }]}>
          <AppText style={styles.receiptLabel}>{item.label}</AppText>
          <AppText variant="bodyStrong">+{item.xp} XP</AppText>
        </View>
      ))}
      <View style={[styles.receiptRow, styles.receiptTotal, { backgroundColor: colors.primarySoft }]}>
        <AppText variant="bodyStrong" style={styles.receiptLabel}>
          Total
        </AppText>
        <AppText variant="number">+{total} XP</AppText>
      </View>
    </Card>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <AppText variant="subheading" accessibilityRole="header">
        {title}
      </AppText>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  body: { gap: space.xxl },
  hero: {
    gap: space.sm,
    padding: space.xl,
    paddingRight: 140,
    borderRadius: radius.lg + 8,
    minHeight: 190,
    overflow: 'hidden',
  },
  heroKai: { position: 'absolute', right: -26, bottom: -40 },
  // The one hero figure on this screen.
  xp: { fontFamily: fonts.headingHeavy, fontSize: 52, lineHeight: 60 },
  streak: { flexDirection: 'row', alignItems: 'center', gap: space.xs, flexWrap: 'wrap' },
  section: { gap: space.md },
  tiles: { flexDirection: 'row', gap: space.md },
  receipt: { overflow: 'hidden', gap: 0 },
  receiptRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: space.md,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  receiptTotal: { borderBottomWidth: 0 },
  receiptLabel: { flex: 1 },
  flex: { flex: 1 },
  scorecard: { overflow: 'hidden', gap: 0 },
  verdict: { flexDirection: 'row', alignItems: 'center', gap: space.md, padding: space.lg },
  criterion: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.md,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  tryAgain: { borderTopWidth: StyleSheet.hairlineWidth, paddingHorizontal: space.sm },
  scores: { gap: space.lg },
  noScore: { gap: space.xs },
  focus: { gap: space.md },
  focusRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md },
  focusNumber: {
    width: 26,
    height: 26,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: -1,
  },
});
