import { useEffect, useEffectEvent, useRef, useState, type ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { grammarApi } from '@/api/endpoints';
import { ApiError, toMessage } from '@/api/errors';
import type { ExplanationLanguage, GrammarLesson, SpeakItem, SpeakResult, Speech, TurnFeedback } from '@/api/types';
import type { SpokenPart } from '@/audio/kaiSpeech';
import { audioForm } from '@/audio/upload';
import type { RecordedClip } from '@/audio/useVoiceRecorder';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Icon } from '@/components/Icon';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { LearnerMessage } from '@/components/Transcript';
import { AnswerComposer, type AnswerMode } from '@/features/conversation/AnswerComposer';
import { TurnNotes } from '@/features/conversation/TurnNotes';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { KaiStage } from './KaiStage';
import { english, partsFor } from './lessonVoice';
import { TutorBubble } from './TutorBubble';

type Entry =
  | { id: string; kind: 'prompt'; item: SpeakItem }
  | { id: string; kind: 'learner'; text: string; pending: boolean; feedback?: TurnFeedback }
  | { id: string; kind: 'verdict'; correct: boolean; expected: string; item: SpeakItem };

/** ready: waiting for an answer. retry: the last answer wasn't quite right. complete: every question is done. */
type Status = 'ready' | 'sending' | 'retry' | 'complete';
type Problem = { message: string; actionLabel?: string; onAction?: () => void };

type Props = {
  lesson: GrammarLesson;
  header: ReactNode;
  voice: Speech;
  language: ExplanationLanguage | null;
  say: (parts: SpokenPart[]) => Promise<void>;
  hush: () => void;
  /** Gets lines Luna will say soon ready in her voice. */
  prefetch: (parts: SpokenPart[]) => void;
  /** What the learner said for a question (the latest try counts). */
  onSpoken: (index: number, transcript: string) => void;
  /** A question is finished with, right or skipped. */
  onItemDone: () => void;
  onDone: () => void;
};

let entryCounter = 0;
const nextId = () => `speak-${++entryCounter}`;

/** What Luna reads out for a question: its card. A sentence with a slip is read after the instruction to fix it. */
export function promptSpeech(item: SpeakItem): string {
  return item.type === 'FIX' ? `${item.instruction}: ${item.prompt}` : item.prompt;
}

/**
 * The speaking part of a lesson as a chat with Luna: she asks one question at a time, the learner answers out loud
 * (or types), and Luna says whether it was right with a good way to say it. A miss can be tried again or skipped.
 */
export function SpeakingStep({
  lesson,
  header,
  voice,
  language,
  say,
  hush,
  prefetch,
  onSpoken,
  onItemDone,
  onDone,
}: Props) {
  const { colors } = useTheme();
  const items = lesson.speaking;
  const native = language != null && language !== 'ENGLISH';
  const [index, setIndex] = useState(0);
  const [entries, setEntries] = useState<Entry[]>(() => [{ id: nextId(), kind: 'prompt', item: items[0] }]);
  const [status, setStatus] = useState<Status>('ready');
  const [problem, setProblem] = useState<Problem | null>(null);
  const [mode, setMode] = useState<AnswerMode>('voice');
  const [recording, setRecording] = useState(false);
  const scroll = useRef<ScrollView>(null);

  const greet = useEffectEvent(() => {
    void say([...partsFor(lesson.phrases.practice, language), english(promptSpeech(items[0]))]);
  });
  useEffect(() => {
    greet();
  }, []);

  // The next question is made in Luna's voice while the learner answers this one.
  const getNextReady = useEffectEvent(() => {
    if (index + 1 < items.length) {
      prefetch([english(promptSpeech(items[index + 1]))]);
    }
  });
  useEffect(() => {
    getNextReady();
  }, [index]);

  /** @param lead what Luna says first, e.g. her praise for the last answer */
  function advance(lead: SpokenPart[]) {
    onItemDone();
    const next = index + 1;
    if (next >= items.length) {
      setStatus('complete');
      void say([...lead, ...partsFor(lesson.phrases.done, language)]);
      return;
    }
    setIndex(next);
    setStatus('ready');
    setProblem(null);
    setEntries((current) => [...current, { id: nextId(), kind: 'prompt', item: items[next] }]);
    void say([...lead, english(promptSpeech(items[next]))]);
  }

  async function submit(pendingText: string, send: () => Promise<SpeakResult>, resend?: () => void): Promise<boolean> {
    const item = items[index];
    const learnerId = nextId();
    const before = status;
    setEntries((current) => [...current, { id: learnerId, kind: 'learner', text: pendingText, pending: true }]);
    setStatus('sending');
    setProblem(null);
    hush();
    try {
      const result = await send();
      onSpoken(index, result.transcript);
      setEntries((current) => [
        ...current.map((entry) =>
          entry.id === learnerId && entry.kind === 'learner'
            ? { ...entry, text: result.transcript, pending: false, feedback: result.feedback }
            : entry,
        ),
        { id: nextId(), kind: 'verdict', correct: result.correct, expected: result.expected, item },
      ]);
      if (result.correct) {
        advance(partsFor(lesson.phrases.right, language));
      } else {
        setStatus('retry');
        void say([...partsFor(lesson.phrases.almost, language), english(`Try saying: ${result.expected}`)]);
      }
      return true;
    } catch (error) {
      setEntries((current) => current.filter((entry) => entry.id !== learnerId));
      setStatus(before);
      setProblem(problemFor(error, resend));
      return false;
    }
  }

  function submitVoice(clip: RecordedClip) {
    const at = index;
    void submit(
      'Sending your answer…',
      async () => grammarApi.speakVoice(lesson.id, at, await audioForm(clip, { durationMs: Math.round(clip.durationMs) })),
      () => submitVoice(clip),
    );
  }

  function submitText(text: string): Promise<boolean> {
    const at = index;
    return submit(text, () => grammarApi.speakText(lesson.id, at, text));
  }

  function problemFor(error: unknown, resend?: () => void): Problem {
    const message = toMessage(error);
    if (error instanceof ApiError) {
      if (error.code === 'stt_failed') {
        return { message, actionLabel: 'Type instead', onAction: () => setMode('text') };
      }
      if (error.status === 400 || error.status === 422) {
        // Nothing heard or too short: the fix is to answer again, not to resend.
        return { message };
      }
    }
    return resend ? { message, actionLabel: 'Send again', onAction: resend } : { message };
  }

  const remaining = items.length - index;
  const footer =
    status === 'complete' ? (
      <Button label="Finish the lesson" icon="flag" onPress={onDone} testID="speaking-finish" />
    ) : (
      <>
        {problem ? (
          <Notice tone="error" message={problem.message} actionLabel={problem.actionLabel} onAction={problem.onAction} />
        ) : null}
        <AnswerComposer
          mode={mode}
          onModeChange={setMode}
          busy={status === 'sending'}
          busyLabel="Luna is listening…"
          onVoice={submitVoice}
          onText={submitText}
          onRecordingChange={(on) => {
            setRecording(on);
            if (on) {
              hush();
            }
          }}
          secondaryAction={
            status === 'sending' ? undefined : (
              <Button
                label={status === 'retry' ? 'Next' : 'Skip'}
                icon={status === 'retry' ? 'arrow-forward' : 'play-skip-forward-outline'}
                variant="ghost"
                onPress={() => advance([])}
                testID="speaking-next"
              />
            )
          }
        />
      </>
    );

  return (
    <Screen scroll={false} footer={footer}>
      {header}
      <KaiStage listening={recording} thinking={status === 'sending'} compact={mode === 'text'} />
      <View style={styles.remainingRow}>
        <View
          style={[styles.remaining, { backgroundColor: colors.glassStrong, borderColor: colors.glassBorder }]}
          accessibilityLiveRegion="polite"
          testID="speaking-remaining">
          <Icon name="mic" size={14} color={colors.accent} />
          <AppText variant="label">
            {status === 'complete'
              ? 'All questions done'
              : `${remaining} ${remaining === 1 ? 'question' : 'questions'} remaining`}
          </AppText>
        </View>
      </View>
      <ScrollView
        ref={scroll}
        style={styles.fill}
        contentContainerStyle={styles.chat}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
        onContentSizeChange={() => scroll.current?.scrollToEnd({ animated: true })}>
        <TutorBubble line={lesson.phrases.practice} native={native} language={language} voice={voice} />
        {entries.map((entry) => {
          switch (entry.kind) {
            case 'prompt':
              return <PromptBubble key={entry.id} item={entry.item} voice={voice} />;
            case 'learner':
              return (
                <View key={entry.id} style={styles.turn}>
                  <LearnerMessage
                    text={entry.text}
                    pending={entry.pending}
                    marks={entry.feedback?.corrections.map((c) => c.original)}
                  />
                  {entry.pending ? null : (
                    // Luna's verdict follows, so an answer with no notes needs no "sounds natural" of its own.
                    <TurnNotes feedback={entry.feedback ?? null} language={language} voice={voice} hideWhenClear />
                  )}
                </View>
              );
            case 'verdict':
              return <Verdict key={entry.id} entry={entry} lesson={lesson} native={native} language={language} voice={voice} />;
          }
        })}
        {status === 'complete' ? (
          <TutorBubble line={lesson.phrases.done} native={native} language={language} voice={voice} />
        ) : null}
      </ScrollView>
    </Screen>
  );
}

/** Luna asks one question: what to do, the sentence or question, and a hint behind a tap. */
function PromptBubble({ item, voice }: { item: SpeakItem; voice: Speech }) {
  const { colors } = useTheme();
  const [hint, setHint] = useState(false);
  const fix = item.type === 'FIX';
  return (
    <TutorBubble line={{ text: item.instruction }} native={false} voice={voice} speak={promptSpeech(item)}>
      <View
        style={[styles.prompt, { backgroundColor: fix ? colors.errorBackground : colors.accentSoft }]}
        accessible
        accessibilityLabel={fix ? `Sentence with a slip: ${item.prompt}` : item.prompt}>
        {fix ? <Icon name="close-circle" size={18} color={colors.error} /> : null}
        <AppText variant="bodyStrong" style={styles.fill}>
          {fix ? `“${item.prompt}”` : item.prompt}
        </AppText>
      </View>
      {hint ? (
        <AppText variant="small" tone="muted" accessibilityLiveRegion="polite">
          Tip: {item.tip}
        </AppText>
      ) : (
        <Pressable
          onPress={() => setHint(true)}
          accessibilityRole="button"
          hitSlop={8}
          style={({ pressed }) => [styles.hintButton, pressed && { opacity: 0.6 }]}>
          <Icon name="bulb-outline" size={14} color={colors.accent} />
          <AppText variant="label" style={{ color: colors.accent }}>
            Show a hint
          </AppText>
        </Pressable>
      )}
    </TutorBubble>
  );
}

/** Luna's answer to an answer: praise and a model answer, or "almost" with the way to say it. */
function Verdict({
  entry,
  lesson,
  native,
  language,
  voice,
}: {
  entry: Extract<Entry, { kind: 'verdict' }>;
  lesson: GrammarLesson;
  native: boolean;
  language: ExplanationLanguage | null;
  voice: Speech;
}) {
  const { colors } = useTheme();
  const line = entry.correct ? lesson.phrases.right : lesson.phrases.almost;
  return (
    <View testID={entry.correct ? 'verdict-right' : 'verdict-almost'}>
      <TutorBubble line={line} native={native} language={language} voice={voice} speak={entry.expected}>
        <View style={[styles.model, { backgroundColor: entry.correct ? colors.successBackground : colors.primarySoft }]}>
          <AppText variant="label" tone="muted">
            {entry.correct ? (entry.item.type === 'FIX' ? 'The right way' : 'Another good answer') : 'Try saying'}
          </AppText>
          <AppText variant="bodyStrong">“{entry.expected}”</AppText>
        </View>
        {entry.correct ? null : (
          <AppText variant="small" tone="muted">
            {entry.item.tip} Have another go, or tap Next.
          </AppText>
        )}
      </TutorBubble>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  chat: { gap: space.lg, paddingBottom: space.lg },
  turn: { gap: space.sm },
  remainingRow: { alignItems: 'center', marginBottom: space.md },
  remaining: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: space.md,
    paddingVertical: 4,
  },
  prompt: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm, borderRadius: radius.md, padding: space.md },
  hintButton: { flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', paddingVertical: 2 },
  model: { borderRadius: radius.md, padding: space.md, gap: 2 },
});
