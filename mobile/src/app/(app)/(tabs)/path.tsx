import { useQuery } from '@tanstack/react-query';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { Pressable, StyleSheet, useWindowDimensions, View, type ScrollView } from 'react-native';

import { grammarApi } from '@/api/endpoints';
import { queryKeys } from '@/api/queryClient';
import type { GrammarPath, PathNode, PathWeek } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Icon } from '@/components/Icon';
import { Icon3D, icon3d } from '@/components/Icon3D';
import { KaiFace } from '@/components/Kai3D';
import { Loading } from '@/components/Loading';
import { Notice } from '@/components/Notice';
import { Pressable3D } from '@/components/Pressable3D';
import { SCREEN_TOP_PADDING, Screen } from '@/components/Screen';
import { nodeCenterY, PathMap } from '@/features/grammar/PathMap';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

/** Where a week's section and its road sit, so the page can scroll to a lesson. */
type WeekPosition = { section?: number; map?: number };

/**
 * The grammar path: week after week of short lessons, a "Learn" lesson then its fluency booster, opened one at a time.
 * The page opens scrolled to the learner's next lesson, and Luna's button brings them back to it.
 */
export default function PathScreen() {
  const path = useQuery({ queryKey: queryKeys.grammarPath, queryFn: grammarApi.path });
  const [refreshing, setRefreshing] = useState(false);
  // A lesson card stays open only until the next lesson changes (e.g. after finishing one), so coming back to the
  // path shows the newly opened lesson instead of the old card.
  const [selection, setSelection] = useState<{ id: string; under: string | null } | null>(null);
  const currentId = path.data?.currentNodeId ?? null;
  const selected = selection?.under === currentId ? selection.id : null;
  const setSelected = (id: string | null) => setSelection(id ? { id, under: currentId } : null);
  const scroll = useRef<ScrollView>(null);
  const positions = useRef(new Map<number, WeekPosition>());
  // The lesson the page last scrolled to by itself, so it only jumps when the next lesson changes.
  const scrolledTo = useRef<string | null>(null);
  const { height } = useWindowDimensions();

  const refresh = async () => {
    setRefreshing(true);
    await path.refetch();
    setRefreshing(false);
  };

  function scrollTo(data: GrammarPath, nodeId: string, animated: boolean): boolean {
    const week = data.weeks.find((w) => w.nodes.some((n) => n.id === nodeId));
    const at = week ? positions.current.get(week.number) : undefined;
    if (!week || at?.section == null || at.map == null) {
      return false;
    }
    const index = week.nodes.findIndex((n) => n.id === nodeId);
    const y = SCREEN_TOP_PADDING + at.section + at.map + nodeCenterY(index);
    scroll.current?.scrollTo({ y: Math.max(0, y - height * 0.35), animated });
    return true;
  }

  /** Scrolls to the next lesson once its week has been laid out, and again whenever the next lesson changes. */
  function followCurrent() {
    const data = path.data;
    const target = data?.currentNodeId;
    if (data && target && scrolledTo.current !== target && scrollTo(data, target, scrolledTo.current != null)) {
      scrolledTo.current = target;
    }
  }
  const followCurrentOnChange = useEffectEvent(followCurrent);
  useEffect(() => {
    followCurrentOnChange();
  }, [path.data?.currentNodeId]);

  const onPosition = (week: number, patch: WeekPosition) => {
    positions.current.set(week, { ...positions.current.get(week), ...patch });
    followCurrent();
  };

  const data = path.data;
  const current = data?.currentNodeId ? findNode(data, data.currentNodeId) : null;
  const currentWeek = data && current ? (data.weeks.find((w) => w.nodes.includes(current))?.number ?? 1) : 1;
  // Only the weeks around the next lesson are drawn out at first, so a 46-week path stays quick; the learner can open
  // or close any week, and those choices win.
  const [opened, setOpened] = useState<Map<number, boolean>>(() => new Map());
  const openAtFirst = (week: number) => week >= currentWeek - 1 && week <= currentWeek + 2;
  const isOpen = (week: number) => opened.get(week) ?? openAtFirst(week);
  const toggleWeek = (week: number) => {
    if (isOpen(week)) {
      // Its road is about to go, so don't scroll by where it used to be.
      positions.current.set(week, { ...positions.current.get(week), map: undefined });
    }
    setOpened((now) => new Map(now).set(week, !(now.get(week) ?? openAtFirst(week))));
  };

  /** Opens a lesson's card, scrolling the lesson up so the card underneath it has room. */
  function select(id: string | null) {
    setSelected(id);
    if (!id || !data) {
      return;
    }
    const week = data.weeks.find((w) => w.nodes.some((n) => n.id === id));
    if (week && !isOpen(week.number)) {
      // Open the week first, then scroll once its road has been laid out.
      setOpened((now) => new Map(now).set(week.number, true));
      setTimeout(() => scrollTo(data, id, true), 150);
    } else {
      scrollTo(data, id, true);
    }
  }

  return (
    <Screen
      edges={['top']}
      scrollRef={scroll}
      onRefresh={data ? refresh : undefined}
      refreshing={refreshing}
      // Luna's button steps aside while a card is open, so it never covers the card.
      overlay={current && !selected ? <KaiButton next={current} onPress={() => select(current.id)} /> : null}>
      <View style={styles.header}>
        <AppText variant="title" accessibilityRole="header">
          Grammar path
        </AppText>
        <AppText tone="muted">
          A new topic every week, one lesson at a time. Each topic comes back later so you revise it, then master it.
        </AppText>
      </View>
      {data ? (
        <>
          <Overall done={data.done} total={data.total} />
          {data.weeks.map((week, index) => (
            <WeekSection
              key={week.number}
              week={week}
              previous={index > 0 ? data.weeks[index - 1] : null}
              current={current}
              open={isOpen(week.number)}
              onToggle={() => toggleWeek(week.number)}
              selectedId={selected}
              onSelect={select}
              onPosition={onPosition}
            />
          ))}
          {/* Room for the last lesson's card and Luna's button. */}
          <View style={styles.end} />
        </>
      ) : path.isError ? (
        <Notice tone="error" message={path.error.message} actionLabel="Try again" onAction={() => void path.refetch()} />
      ) : (
        <Loading label="Getting your path…" />
      )}
    </Screen>
  );
}

function findNode(data: GrammarPath, id: string): PathNode | null {
  for (const week of data.weeks) {
    const node = week.nodes.find((n) => n.id === id);
    if (node) {
      return node;
    }
  }
  return null;
}

function openLesson(node: PathNode) {
  router.push({ pathname: '/lesson/[id]', params: { id: node.id } });
}

function WeekSection({
  week,
  previous,
  current,
  open,
  onToggle,
  selectedId,
  onSelect,
  onPosition,
}: {
  week: PathWeek;
  previous: PathWeek | null;
  current: PathNode | null;
  open: boolean;
  onToggle: () => void;
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onPosition: (week: number, patch: WeekPosition) => void;
}) {
  const holdsSelected = open && week.nodes.some((n) => n.id === selectedId);
  return (
    // The week with an open lesson card sits above the next week, so the card can overlap its banner.
    <View
      style={[open ? styles.week : styles.closedWeek, holdsSelected && styles.raised]}
      onLayout={(e) => onPosition(week.number, { section: e.nativeEvent.layout.y })}>
      <WeekBanner week={week} previous={previous} open={open} onToggle={onToggle} />
      {open ? (
        <View onLayout={(e) => onPosition(week.number, { map: e.nativeEvent.layout.y })}>
          <PathMap
            nodes={week.nodes}
            selectedId={holdsSelected ? selectedId : null}
            onSelect={onSelect}
            onStart={openLesson}
            current={current}
          />
        </View>
      ) : null}
    </View>
  );
}

function Overall({ done, total }: { done: number; total: number }) {
  const { colors } = useTheme();
  return (
    <View
      style={[styles.overall, { backgroundColor: colors.glass, borderColor: colors.glassBorder, boxShadow: colors.cardShadow }]}
      accessible
      accessibilityLabel={`${done} of ${total} lessons done`}>
      <Icon3D name="trophy" size={36} />
      <View style={styles.flex}>
        <AppText variant="bodyStrong">
          {done} of {total} lessons done
        </AppText>
        <View style={[styles.track, { backgroundColor: colors.surfaceMuted }]}>
          <LinearGradient
            colors={colors.mango3d.face}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 0 }}
            style={[styles.fill, { width: `${Math.max(3, (done / Math.max(1, total)) * 100)}%` }]}
          />
        </View>
      </View>
    </View>
  );
}

/**
 * The week's heading, naming the week's new topic: violet for the week the learner is in, quieter for the rest.
 * Tapping it opens or closes the week's lessons.
 */
function WeekBanner({
  week,
  previous,
  open,
  onToggle,
}: {
  week: PathWeek;
  previous: PathWeek | null;
  open: boolean;
  onToggle: () => void;
}) {
  const { colors } = useTheme();
  const done = week.nodes.filter((n) => n.status === 'DONE').length;
  const isCurrent = week.nodes.some((n) => n.status === 'CURRENT');
  const locked = week.nodes.every((n) => n.status === 'LOCKED');
  const allDone = done === week.nodes.length;
  const fresh = week.nodes[0];
  const reviews = week.nodes.length - 2;
  const ink = isCurrent ? colors.onHero : colors.text;
  const muted = isCurrent ? colors.onHeroMuted : colors.textMuted;
  const status = locked
    ? previous
      ? `Opens after week ${previous.number}`
      : 'Opens soon'
    : allDone
      ? 'All done'
      : `${done} of ${week.nodes.length} done`;
  const plan = `${week.nodes.length} lessons${reviews > 0 ? `, ${reviews} to revise` : ''}`;
  const content = (
    <>
      <View style={[styles.weekIcon, { backgroundColor: isCurrent ? 'rgba(255,255,255,0.18)' : colors.glassStrong }]}>
        <Icon3D name={allDone ? 'trophy' : icon3d(fresh?.icon, 'books')} size={34} />
      </View>
      <View style={styles.flex}>
        <AppText variant="label" style={{ color: muted }} numberOfLines={1}>
          WEEK {week.number} · {week.level} · {week.title.toUpperCase()}
        </AppText>
        <AppText variant="subheading" style={{ color: ink }}>
          {fresh?.topicTitle ?? week.title}
        </AppText>
        <AppText variant="small" style={{ color: muted }}>
          {status} · {plan}
        </AppText>
      </View>
      <Icon name={open ? 'chevron-up' : 'chevron-down'} size={20} color={ink} />
    </>
  );
  return (
    <Pressable
      onPress={onToggle}
      accessibilityRole="button"
      accessibilityState={{ expanded: open }}
      accessibilityLabel={`Week ${week.number}, level ${week.level}, ${week.title}: ${fresh?.topicTitle ?? ''}. ${status}. ${plan}.`}
      accessibilityHint={open ? 'Hides this week’s lessons' : 'Shows this week’s lessons'}
      testID={`week-${week.number}`}
      style={({ pressed }) => [{ transform: [{ scale: pressed ? 0.99 : 1 }] }]}>
      {isCurrent ? (
        <LinearGradient
          colors={colors.heroGradient}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={[styles.banner, { boxShadow: colors.raisedShadow }]}>
          {content}
        </LinearGradient>
      ) : (
        <View
          style={[
            styles.banner,
            { backgroundColor: colors.glass, borderColor: colors.glassBorder, borderWidth: 1, opacity: locked ? 0.8 : 1 },
          ]}>
          {content}
        </View>
      )}
    </Pressable>
  );
}

/** Luna floats at the corner of the path; tapping her goes back to the next lesson and opens its card. */
function KaiButton({ next, onPress }: { next: PathNode; onPress: () => void }) {
  const { colors } = useTheme();
  return (
    <View style={styles.fab}>
      <Pressable3D
        material={colors.violet3d}
        onPress={onPress}
        radius={36}
        depth={5}
        accessibilityLabel={`Go to your next lesson: ${next.title}`}
        testID="path-kai"
        faceStyle={styles.fabFace}>
        <KaiFace size={60} />
      </Pressable3D>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { gap: space.xs, marginBottom: space.lg },
  flex: { flex: 1, gap: 2 },
  overall: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    borderRadius: radius.lg + 4,
    borderWidth: 1,
    padding: space.lg,
    marginBottom: space.xl,
  },
  track: { height: 10, borderRadius: radius.pill, overflow: 'hidden', marginTop: space.xs },
  fill: { height: 10, borderRadius: radius.pill },
  week: { gap: space.xxl, marginBottom: space.xl },
  closedWeek: { marginBottom: space.md },
  raised: { zIndex: 1 },
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    borderRadius: radius.lg + 6,
    padding: space.lg,
  },
  weekIcon: { width: 54, height: 54, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  end: { height: 160 },
  fab: { position: 'absolute', right: space.lg, bottom: space.lg },
  fabFace: { width: 72, height: 72, alignItems: 'center', justifyContent: 'center' },
});
