import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { practiceApi } from '@/api/endpoints';
import { queryKeys } from '@/api/queryClient';
import type { PracticeCatalog, RoleplayDifficulty } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { Icon3D } from '@/components/Icon3D';
import { Loading } from '@/components/Loading';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { SpeechBubble } from '@/components/SpeechBubble';
import { roleplayIcon3d, topicIcon3d } from '@/features/practice/catalogIcons';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space, TOUCH_TARGET } from '@/theme/layout';

/** The free-talk "no topic" option is the big button, so it isn't repeated in the topic list. */
const OPEN_TOPIC = 'anything';

const DIFFICULTY: Record<RoleplayDifficulty, { label: string; bars: number }> = {
  EASY: { label: 'Easy', bars: 1 },
  MEDIUM: { label: 'Medium', bars: 2 },
  HARD: { label: 'Hard', bars: 3 },
};

/** Talk with Luna about anything, pick a topic, or step into a roleplay. */
export default function TalkScreen() {
  const catalog = useQuery({ queryKey: queryKeys.practiceCatalog, queryFn: practiceApi.catalog, staleTime: Infinity });

  return (
    <Screen
      edges={['top']}
      footer={
        <Button
          label="Start speaking"
          icon="mic"
          onPress={() => router.push('/practice/free-talk')}
          accessibilityHint="Starts a free chat with Luna about anything"
          testID="talk-start"
        />
      }>
      <View style={styles.header}>
        <AppText variant="title" accessibilityRole="header">
          Talk with Luna
        </AppText>
        <SpeechBubble>
          <AppText variant="bodyStrong">What do you feel like talking about today?</AppText>
          <AppText variant="small" tone="muted">
            We can talk for up to 10 minutes. I&apos;ll keep the chat going and note your slips on the side.
          </AppText>
        </SpeechBubble>
      </View>
      {catalog.data ? (
        <Catalog catalog={catalog.data} />
      ) : catalog.isError ? (
        <Notice
          tone="error"
          message={catalog.error.message}
          actionLabel="Try again"
          onAction={() => void catalog.refetch()}
        />
      ) : (
        <Loading label="Getting topics…" />
      )}
    </Screen>
  );
}

function Catalog({ catalog }: { catalog: PracticeCatalog }) {
  const { colors } = useTheme();
  const topics = catalog.topics.filter((topic) => topic.slug !== OPEN_TOPIC);
  return (
    <View style={styles.body}>
      <View style={styles.section}>
        <AppText variant="subheading" accessibilityRole="header">
          Pick a topic
        </AppText>
        <View style={styles.topics}>
          {topics.map((topic) => (
            <Pressable
              key={topic.slug}
              onPress={() => router.push({ pathname: '/practice/free-talk', params: { topic: topic.slug } })}
              accessibilityRole="button"
              accessibilityLabel={`Talk about ${topic.title}`}
              testID={`topic-${topic.slug}`}
              style={({ pressed }) => [
                styles.topic,
                {
                  borderColor: colors.glassBorder,
                  backgroundColor: pressed ? colors.primarySoft : colors.glassStrong,
                  boxShadow: pressed ? undefined : colors.cardShadow,
                  transform: [{ scale: pressed ? 0.97 : 1 }],
                },
              ]}>
              <Icon3D name={topicIcon3d(topic.slug)} size={24} />
              <AppText variant="bodyStrong">{topic.title}</AppText>
            </Pressable>
          ))}
        </View>
      </View>

      <View style={styles.section}>
        <AppText variant="subheading" accessibilityRole="header">
          Interview mode
        </AppText>
        <Pressable
          onPress={() => router.push('/practice/interview')}
          accessibilityRole="button"
          accessibilityLabel="Mock interview. Paste your résumé or a job description, and Luna runs an HR round built on it."
          testID="talk-interview"
          style={({ pressed }) => [
            styles.interview,
            { backgroundColor: pressed ? colors.surfaceMuted : colors.primarySoft },
          ]}>
          <View style={[styles.rowIcon, { backgroundColor: colors.glassStrong }]}>
            <Icon3D name="career" size={32} />
          </View>
          <View style={styles.rowText}>
            <AppText variant="bodyStrong">Mock interview</AppText>
            <AppText variant="small">
              Paste your résumé or a job description. Luna runs an HR round built on it.
            </AppText>
          </View>
          <Icon name="chevron-forward" size={18} color={colors.text} />
        </Pressable>
      </View>

      <View style={styles.section}>
        <View style={styles.sectionTitle}>
          <AppText variant="subheading" accessibilityRole="header">
            Roleplays
          </AppText>
          <AppText tone="muted">Real situations with a goal. Luna stays in character.</AppText>
        </View>
        <Card padding={0} style={styles.list}>
          {catalog.roleplays.map((roleplay, index) => {
            const difficulty = DIFFICULTY[roleplay.difficulty];
            return (
              <Pressable
                key={roleplay.slug}
                onPress={() =>
                  router.push({ pathname: '/practice/roleplay/[scenario]', params: { scenario: roleplay.slug } })
                }
                accessibilityRole="button"
                accessibilityLabel={`${roleplay.title}. ${difficulty.label}. ${roleplay.goal}`}
                testID={`roleplay-${roleplay.slug}`}
                style={({ pressed }) => [
                  styles.row,
                  index > 0 && { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.hairline },
                  pressed && { backgroundColor: colors.accentSoft },
                ]}>
                <View style={[styles.rowIcon, { backgroundColor: colors.accentSoft }]}>
                  <Icon3D name={roleplayIcon3d(roleplay.slug)} size={32} />
                </View>
                <View style={styles.rowText}>
                  <AppText variant="bodyStrong">{roleplay.title}</AppText>
                  <AppText variant="small" tone="muted" numberOfLines={2}>
                    {roleplay.goal}
                  </AppText>
                  <View style={styles.difficulty}>
                    <DifficultyBars filled={difficulty.bars} />
                    <AppText variant="label" tone="muted">
                      {difficulty.label}
                    </AppText>
                  </View>
                </View>
                <Icon name="chevron-forward" size={18} color={colors.textMuted} />
              </Pressable>
            );
          })}
        </Card>
      </View>
    </View>
  );
}

/** Three rising bars, so difficulty reads at a glance; the label next to it says the same in words. */
function DifficultyBars({ filled }: { filled: number }) {
  const { colors } = useTheme();
  return (
    <View style={styles.bars}>
      {[0, 1, 2].map((bar) => (
        <View
          key={bar}
          style={[
            styles.bar,
            { height: 6 + bar * 4, backgroundColor: bar < filled ? colors.text : colors.hairline },
          ]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { gap: space.lg, marginBottom: space.xxl },
  body: { gap: space.xxl },
  section: { gap: space.md },
  sectionTitle: { gap: 2 },
  topics: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm },
  topic: {
    minHeight: TOUCH_TARGET,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingHorizontal: space.lg,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radius.pill,
  },
  interview: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    minHeight: 72,
    borderRadius: radius.lg,
  },
  list: { overflow: 'hidden', gap: 0 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    minHeight: 72,
  },
  rowIcon: { width: 52, height: 52, borderRadius: radius.md + 2, alignItems: 'center', justifyContent: 'center' },
  rowText: { flex: 1, gap: 2 },
  difficulty: { flexDirection: 'row', alignItems: 'center', gap: space.sm, marginTop: 2 },
  bars: { flexDirection: 'row', alignItems: 'flex-end', gap: 2, height: 14 },
  bar: { width: 4, borderRadius: 2 },
});
