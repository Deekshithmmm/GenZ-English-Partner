import { useQuery } from '@tanstack/react-query';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { todayApi } from '@/api/endpoints';
import { queryKeys } from '@/api/queryClient';
import type { PlanItem, Today } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon3D } from '@/components/Icon3D';
import { Kai3D } from '@/components/Kai3D';
import { Loading } from '@/components/Loading';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { WeekStrip } from '@/components/WeekStrip';
import { formatLongDate, mondayFirstIndex } from '@/features/today/dates';
import { planHref } from '@/features/today/planActions';
import { PlanRow, type PlanState } from '@/features/today/PlanRow';
import { useSession } from '@/stores/session';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

export default function TodayScreen() {
  const me = useSession((s) => s.me);
  const today = useQuery({ queryKey: queryKeys.today, queryFn: todayApi.today });
  const [refreshing, setRefreshing] = useState(false);
  const firstName = me?.displayName.trim().split(/\s+/)[0] ?? '';
  const greeting = firstName ? `Hi ${firstName}` : 'Hi there';

  const refresh = async () => {
    setRefreshing(true);
    await today.refetch();
    setRefreshing(false);
  };

  if (!today.data) {
    return (
      <Screen edges={['top']}>
        <View style={styles.header}>
          <AppText variant="title" accessibilityRole="header">
            {greeting}
          </AppText>
        </View>
        {today.isError ? (
          <Notice tone="error" message={today.error.message} actionLabel="Try again" onAction={() => void today.refetch()} />
        ) : (
          <Loading label="Getting today's plan…" />
        )}
      </Screen>
    );
  }

  const data = today.data;
  const next = data.plan.find((item) => !item.done && planHref(item.action));
  const nextHref = next ? planHref(next.action) : null;
  // Once today's plan is done, the big button is for talking freely with Luna.
  const start =
    next && nextHref
      ? { label: `Start: ${next.title}`, href: nextHref }
      : { label: 'Start speaking', href: '/practice/free-talk' as const };

  return (
    <Screen
      edges={['top']}
      onRefresh={refresh}
      refreshing={refreshing}
      footer={<Button label={start.label} icon="mic" onPress={() => router.push(start.href)} testID="today-start" />}>
      <Hero date={formatLongDate(data.date)} greeting={greeting} line={kaiSays(data, next)} level={data.level} />

      <ProgressCard data={data} />

      <View style={styles.planHeader}>
        <AppText variant="heading" accessibilityRole="header">
          Today&apos;s plan
        </AppText>
        <AppText tone="muted">{planProgress(data)}</AppText>
      </View>
      <View style={styles.plan}>
        {data.plan.map((item, index) => (
          <PlanRow key={item.id} item={item} state={stateOf(item, next)} last={index === data.plan.length - 1} />
        ))}
      </View>

      <Card tone="accent" style={styles.unit}>
        <View style={styles.unitLabel}>
          <Icon3D name="target" size={22} />
          <AppText variant="label" tone="muted">
            This week&apos;s speaking focus
          </AppText>
        </View>
        <AppText variant="subheading">{data.focus.title}</AppText>
        <AppText tone="muted">{data.focus.summary}</AppText>
      </Card>
    </Screen>
  );
}

/** Luna's line for the day: what to do next, or a nudge to keep talking once the plan is done. */
function kaiSays(data: Today, next: PlanItem | undefined): string {
  const doneCount = data.plan.filter((item) => item.done).length;
  if (!next) {
    return "That's today's plan done. Want to keep talking? Pick any topic in Talk.";
  }
  const step = {
    WARM_UP: 'a quick warm-up chat',
    MICRO_LESSON: `the "${next.title}" lesson`,
    ROLEPLAY: `the "${next.title}" roleplay`,
    WORD_REVIEW: 'a quick word review',
  }[next.kind];
  if (doneCount === 0) {
    return data.streak.current > 0
      ? `Day ${data.streak.current + 1} of your streak starts here. Let's begin with ${step}.`
      : `Ready for your ${data.dailyMinutes} minutes? Let's begin with ${step}.`;
  }
  return `Nice work! Next up: ${step}.`;
}

function planProgress(data: Today): string {
  const practice = data.plan.filter((item) => planHref(item.action));
  const done = practice.filter((item) => item.done).length;
  return `${done} of ${practice.length} done · ${data.dailyMinutes} min`;
}

function stateOf(item: PlanItem, next: PlanItem | undefined): PlanState {
  if (!planHref(item.action)) {
    return 'soon';
  }
  if (item.done) {
    return 'done';
  }
  return item.id === next?.id ? 'next' : 'open';
}

/**
 * The day's welcome: a violet card with the date, a greeting and Luna's line for the day, and Luna herself rising from
 * the bottom edge.
 */
function Hero({
  date,
  greeting,
  line,
  level,
}: {
  date: string;
  greeting: string;
  line: string;
  level: Today['level'];
}) {
  const { colors } = useTheme();
  return (
    <LinearGradient
      colors={colors.heroGradient}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={[styles.hero, { boxShadow: colors.raisedShadow }]}>
      <View style={styles.heroText}>
        <AppText variant="label" style={{ color: colors.onHeroMuted }}>
          {date}
        </AppText>
        <AppText variant="title" accessibilityRole="header" style={{ color: colors.onHero }}>
          {greeting}
        </AppText>
        <AppText variant="bodyStrong" style={{ color: colors.onHero }}>
          {line}
        </AppText>
        <View
          style={styles.levelChip}
          accessible
          accessibilityLabel={`Level ${level.code}, ${level.label}`}>
          <Icon3D name="medal" size={18} />
          <AppText variant="label" style={{ color: colors.onHero }}>
            {level.code} · {level.label}
          </AppText>
        </View>
      </View>
      <Kai3D mood="idle" size={170} glow={false} style={styles.heroKai} />
    </LinearGradient>
  );
}

/** Streak and XP side by side, with this week underneath. */
function ProgressCard({ data }: { data: Today }) {
  const { colors } = useTheme();
  const { streak, xp } = data;
  return (
    <Card style={styles.progress}>
      <View style={styles.progressRow}>
        <View
          style={styles.stat}
          accessible
          accessibilityLabel={`${streak.current} day streak. ${streak.current === 0 ? 'Practise today to start one.' : `Best: ${streak.longest}.`}`}>
          <View style={[styles.statIcon, { backgroundColor: colors.celebrateSoft }]}>
            <Icon3D name="fire" size={30} />
          </View>
          <View style={styles.statText}>
            <AppText variant="number">{streak.current}</AppText>
            <AppText variant="label">day streak</AppText>
            <AppText variant="small" tone="muted">
              {streak.current === 0 ? 'Start one today' : `Best: ${streak.longest}`}
            </AppText>
          </View>
        </View>
        <View style={[styles.divider, { backgroundColor: colors.hairline }]} />
        <View
          style={styles.stat}
          accessible
          accessibilityLabel={`${xp.today} XP today. ${xp.total} XP in total.`}>
          <View style={[styles.statIcon, { backgroundColor: colors.primarySoft }]}>
            <Icon3D name="star" size={30} />
          </View>
          <View style={styles.statText}>
            <AppText variant="number">{xp.today}</AppText>
            <AppText variant="label">XP today</AppText>
            <AppText variant="small" tone="muted">
              {xp.total} in total
            </AppText>
          </View>
        </View>
      </View>
      <View style={[styles.weekDivider, { backgroundColor: colors.hairline }]} />
      <WeekStrip week={streak.week ?? []} todayIndex={mondayFirstIndex(data.date)} />
    </Card>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md, marginBottom: space.lg },
  hero: {
    borderRadius: radius.lg + 8,
    padding: space.xl,
    paddingRight: 128,
    minHeight: 196,
    marginBottom: space.xl,
    overflow: 'hidden',
  },
  heroText: { gap: space.xs },
  heroKai: { position: 'absolute', right: -24, bottom: -38 },
  levelChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    alignSelf: 'flex-start',
    marginTop: space.sm,
    paddingHorizontal: space.md,
    paddingVertical: 4,
    borderRadius: radius.pill,
    backgroundColor: 'rgba(255, 255, 255, 0.18)',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.35)',
  },
  progress: { gap: space.md, marginBottom: space.xxl },
  progressRow: { flexDirection: 'row', alignItems: 'center' },
  stat: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: space.md },
  statIcon: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  statText: { flex: 1 },
  divider: { width: StyleSheet.hairlineWidth, alignSelf: 'stretch', marginHorizontal: space.md },
  weekDivider: { height: StyleSheet.hairlineWidth },
  planHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    marginBottom: space.md,
  },
  plan: { marginBottom: space.xl },
  unit: { marginBottom: space.lg },
  unitLabel: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
});
