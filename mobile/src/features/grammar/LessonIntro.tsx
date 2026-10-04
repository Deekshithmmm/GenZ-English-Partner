import { useEffect, useEffectEvent, useRef, useState, type ReactNode } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import type { ExplanationLanguage, GrammarExample, GrammarLesson, Speech } from '@/api/types';
import type { SpokenPart } from '@/audio/kaiSpeech';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Icon } from '@/components/Icon';
import { Icon3D } from '@/components/Icon3D';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { languageInfo } from '@/i18n/languages';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

import { ChartCard } from './ChartCard';
import { KaiStage } from './KaiStage';
import { introSpeech, useMissingVoice } from './lessonVoice';
import { TutorBubble } from './TutorBubble';

type Props = {
  lesson: GrammarLesson;
  header: ReactNode;
  voice: Speech;
  language: ExplanationLanguage | null;
  say: (parts: SpokenPart[]) => Promise<void>;
  onDone: () => void;
};

/** Shown once per app session: the learner only needs telling once that their language can't be read aloud. */
let missingVoiceShown = false;

/**
 * Luna explains the topic as a short chat, one line at a time, in the learner's language with the English under it.
 * The cheat sheet comes in with the rule (the second line) and, in a Learn lesson, the rules and examples with the
 * third. The learner moves on with Continue, so they can read at their own pace.
 */
export function LessonIntro({ lesson, header, voice, language, say, onDone }: Props) {
  const [shown, setShown] = useState(1);
  const scroll = useRef<ScrollView>(null);
  const native = language != null && language !== 'ENGLISH';
  const lines = lesson.intro;
  const last = shown >= lines.length;
  const missingVoice = useMissingVoice(language);
  const [showMissing] = useState(() => !missingVoiceShown);
  useEffect(() => {
    if (missingVoice && showMissing) {
      missingVoiceShown = true;
    }
  }, [missingVoice, showMissing]);

  // Each new line is read as it appears, with the cheat sheet or "How it works" under it the first time they show.
  const speakLatest = useEffectEvent(() => {
    const parts = introSpeech(lesson, shown - 1, language);
    if (parts.length > 0) {
      void say(parts);
    }
  });
  useEffect(() => {
    speakLatest();
  }, [shown]);

  return (
    <Screen
      scroll={false}
      footer={
        <Button
          label={last ? (lesson.exercises.length > 0 ? "Let's practise" : 'Start speaking') : 'Continue'}
          icon={last ? 'play' : 'arrow-down'}
          onPress={() => (last ? onDone() : setShown((n) => n + 1))}
          testID="intro-next"
        />
      }>
      {header}
      <KaiStage />
      {missingVoice && showMissing ? (
        <View style={styles.notice}>
          <Notice
            message={`This device has no ${languageInfo(language).english} voice, so Luna reads the English aloud. Add one in your phone's text-to-speech settings, or use Microsoft Edge on a computer.`}
          />
        </View>
      ) : null}
      <ScrollView
        ref={scroll}
        style={styles.fill}
        contentContainerStyle={styles.chat}
        showsVerticalScrollIndicator={false}
        onContentSizeChange={() => scroll.current?.scrollToEnd({ animated: true })}>
        {lines.slice(0, shown).map((line, index) => (
          <View key={index} style={styles.turn}>
            <TutorBubble line={line} native={native} language={language} voice={voice} />
            {index === 1 && lesson.chart.length > 0 ? <ChartCard chart={lesson.chart} /> : null}
            {index === 2 && lesson.kind === 'LEARN' ? <RulesCard rules={lesson.rules} examples={lesson.examples} /> : null}
          </View>
        ))}
      </ScrollView>
    </Screen>
  );
}

/** The rule in a few short points, then examples: the slip crossed out and the right way ticked. */
function RulesCard({ rules, examples }: { rules: string[]; examples: GrammarExample[] }) {
  const { colors } = useTheme();
  return (
    <View
      style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.glassBorder, boxShadow: colors.cardShadow }]}>
      <View style={styles.cardTitle}>
        <Icon3D name="memo" size={20} />
        <AppText variant="label" tone="muted">
          How it works
        </AppText>
      </View>
      {rules.map((rule) => (
        <View key={rule} style={styles.rule}>
          <View style={[styles.dot, { backgroundColor: colors.accent }]} />
          <AppText variant="small" style={styles.fill}>
            {rule}
          </AppText>
        </View>
      ))}
      {examples.length > 0 ? (
        <View style={[styles.examples, { borderTopColor: colors.hairline }]}>
          {examples.map((example) => (
            <View key={example.right} style={styles.example}>
              {example.wrong ? (
                <View style={styles.exampleLine} accessible accessibilityLabel={`Not: ${example.wrong}`}>
                  <Icon name="close-circle" size={18} color={colors.error} />
                  <AppText variant="small" tone="error" style={[styles.fill, styles.struck]}>
                    {example.wrong}
                  </AppText>
                </View>
              ) : null}
              <View style={styles.exampleLine} accessible accessibilityLabel={`Say: ${example.right}`}>
                <Icon name="checkmark-circle" size={18} color={colors.success} />
                <AppText variant="small" style={[styles.fill, styles.right]}>
                  {example.right}
                </AppText>
              </View>
              {example.note ? (
                <AppText variant="small" tone="muted" style={styles.note}>
                  {example.note}
                </AppText>
              ) : null}
            </View>
          ))}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  notice: { marginBottom: space.md },
  chat: { gap: space.lg, paddingBottom: space.lg },
  turn: { gap: space.md },
  card: { borderRadius: radius.lg + 2, borderWidth: 1, padding: space.lg, gap: space.sm },
  cardTitle: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  rule: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm },
  dot: { width: 7, height: 7, borderRadius: 4, marginTop: 7 },
  examples: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: space.sm, gap: space.md },
  example: { gap: 2 },
  exampleLine: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm },
  struck: { textDecorationLine: 'line-through' },
  right: { fontFamily: fonts.bodyBold },
  note: { marginLeft: 26 },
});
