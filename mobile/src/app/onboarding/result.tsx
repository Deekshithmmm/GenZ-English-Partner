import { useQuery } from '@tanstack/react-query';
import { Redirect, useLocalSearchParams } from 'expo-router';
import { useEffect, useState, type ReactNode } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';

import { placementApi } from '@/api/endpoints';
import { queryKeys } from '@/api/queryClient';
import type { PlacementResult } from '@/api/types';
import { applyProfile } from '@/auth/session';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Highlight } from '@/components/Highlight';
import { Icon } from '@/components/Icon';
import { Loading } from '@/components/Loading';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { ScoreMeter } from '@/components/ScoreMeter';
import { SpeechBubble } from '@/components/SpeechBubble';
import { useSession } from '@/stores/session';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

export default function PlacementResultScreen() {
  const { sessionId } = useLocalSearchParams<{ sessionId?: string }>();
  const me = useSession((s) => s.me);
  const [starting, setStarting] = useState(false);
  // The chat leaves the result in the cache; completing again is idempotent, so a reload can fetch it.
  const result = useQuery({
    queryKey: queryKeys.placementResult(sessionId ?? ''),
    queryFn: () => placementApi.complete(sessionId ?? ''),
    enabled: Boolean(sessionId),
    staleTime: Infinity,
  });

  // Android back would land on the intro and invite a second placement; the way on is forward.
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => subscription.remove();
  }, []);

  if (!sessionId) {
    return <Redirect href="/onboarding" />;
  }

  const start = async () => {
    if (!result.data || !me) {
      return;
    }
    setStarting(true);
    // The server saved the level when the chat completed. Updating it here flips the route guards into the app.
    await applyProfile({ ...me.profile, level: result.data.level, levelSource: 'PLACEMENT' });
  };

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
        <Loading label="Getting your results…" />
      </Screen>
    );
  }

  return (
    <Screen
      footer={<Button label="Start my first day" onPress={start} loading={starting} testID="result-start" />}>
      <ResultBody result={result.data} />
    </Screen>
  );
}

function ResultBody({ result }: { result: PlacementResult }) {
  const { colors } = useTheme();
  const units = result.path.units.slice(0, 3);
  return (
    <View style={styles.body}>
      <View
        style={styles.hero}
        accessible
        accessibilityRole="header"
        accessibilityLabel={`Your speaking level is ${result.level.code}, ${result.level.label}. ${result.level.description}`}>
        <AppText variant="label" tone="muted">
          Your speaking level
        </AppText>
        <View style={styles.levelRow}>
          <Highlight delayMs={250}>
            <AppText style={[styles.levelCode, { color: colors.onHighlight }]}>{result.level.code}</AppText>
          </Highlight>
          <AppText variant="heading">{result.level.label}</AppText>
        </View>
        <AppText>{result.level.description}</AppText>
      </View>

      <SpeechBubble tone="primary">
        <AppText variant="label" tone="muted">
          Luna&apos;s note
        </AppText>
        <AppText variant="bodyStrong">{result.summary}</AppText>
      </SpeechBubble>

      <Section title="How you did">
        <ScoreMeter label="Fluency" score={result.scores.fluency} hint="How smoothly you keep talking." />
        <ScoreMeter label="Grammar" score={result.scores.grammar} hint="How accurately you build sentences." />
        <ScoreMeter label="Vocabulary" score={result.scores.vocabulary} hint="How varied and precise your words are." />
      </Section>

      {result.strengths.length > 0 ? (
        <Section title="What you did well">
          {result.strengths.map((strength) => (
            <View key={strength} style={styles.listRow}>
              <Icon name="checkmark-circle" color={colors.success} />
              <AppText style={styles.listText}>{strength}</AppText>
            </View>
          ))}
        </Section>
      ) : null}

      <Section title="What Luna will help you with">
        {result.focusAreas.map((area, index) => (
          <View key={area} style={styles.listRow}>
            <View style={[styles.number, { borderColor: colors.text }]}>
              <AppText variant="label">{index + 1}</AppText>
            </View>
            <AppText style={styles.listText}>{area}</AppText>
          </View>
        ))}
      </Section>

      <Section title="Your path starts here">
        {units.map((unit, index) => (
          <View
            key={unit.id}
            style={[
              styles.unit,
              index === 0
                ? {
                    backgroundColor: colors.surface,
                    borderColor: colors.hairline,
                    borderWidth: StyleSheet.hairlineWidth,
                    borderLeftColor: colors.primary,
                    borderLeftWidth: 4,
                    boxShadow: colors.cardShadow,
                  }
                : { borderColor: colors.hairline, borderWidth: 1 },
            ]}>
            <AppText variant="label" tone="muted">
              {index === 0 ? 'First up' : index === 1 ? 'Then' : 'After that'}
            </AppText>
            <AppText variant="bodyStrong">{unit.title}</AppText>
            <AppText variant="small" tone="muted">
              {unit.summary}
            </AppText>
          </View>
        ))}
      </Section>
    </View>
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
  hero: { gap: space.sm },
  // Centred rather than baseline: the highlighter wraps the code in a View, which breaks baseline alignment on web.
  levelRow: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  // The one hero figure on this screen.
  levelCode: { fontFamily: fonts.headingHeavy, fontSize: 64, lineHeight: 72 },
  section: { gap: space.md },
  listRow: { flexDirection: 'row', gap: space.md, alignItems: 'flex-start' },
  listText: { flex: 1 },
  number: {
    width: 24,
    height: 24,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  unit: { borderRadius: radius.md, padding: space.lg, gap: 2 },
});
