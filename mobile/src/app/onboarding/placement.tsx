import { router } from 'expo-router';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { placementApi } from '@/api/endpoints';
import { ApiError, toMessage } from '@/api/errors';
import { queryClient, queryKeys } from '@/api/queryClient';
import type { KaiLine, PlacementAnswer } from '@/api/types';
import { speakKai, speakKaiAndWait, stopKai } from '@/audio/kaiSpeech';
import { audioForm } from '@/audio/upload';
import type { RecordedClip } from '@/audio/useVoiceRecorder';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { StepHeader } from '@/components/StepHeader';
import { AnswerComposer, type AnswerMode } from '@/features/conversation/AnswerComposer';
import { ChatLog, type ChatLine } from '@/features/conversation/ChatLog';
import { useTheme } from '@/theme/ThemeProvider';
import { space } from '@/theme/layout';

type Status = 'starting' | 'start-failed' | 'ready' | 'sending' | 'finishing' | 'finish-failed';
type Problem = { message: string; actionLabel?: string; onAction?: () => void };

/** Mirrors PlacementScript.MIN_ANSWERS_TO_ASSESS on the server. */
const MIN_ANSWERS_TO_FINISH = 2;

let lineCounter = 0;
const nextLineId = () => `line-${++lineCounter}`;

function say(line: KaiLine) {
  speakKai(line).catch(() => undefined);
}

/**
 * The two-minute spoken placement: Luna asks four questions, reacts to each answer, then works out a level.
 * Answers are idempotent per question on the server, so resending after a dropped connection is safe.
 */
export default function PlacementChat() {
  const { colors } = useTheme();
  const [status, setStatus] = useState<Status>('starting');
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [question, setQuestion] = useState(1);
  const [total, setTotal] = useState(4);
  const [answered, setAnswered] = useState(0);
  const [lines, setLines] = useState<ChatLine[]>([]);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [mode, setMode] = useState<AnswerMode>('voice');
  const started = useRef(false);
  // False once the learner has left, so a late response can't navigate them somewhere else.
  const onScreen = useRef(true);

  async function begin() {
    setStatus('starting');
    setProblem(null);
    try {
      const start = await placementApi.start();
      setSessionId(start.sessionId);
      setQuestion(start.questionNumber);
      setTotal(start.totalQuestions);
      setAnswered(0);
      setLines([{ id: nextLineId(), speaker: 'kai', line: start.kai }]);
      setStatus('ready');
      say(start.kai);
    } catch (error) {
      setStatus('start-failed');
      setProblem({ message: toMessage(error), actionLabel: 'Try again', onAction: () => void begin() });
    }
  }

  const startOnce = useEffectEvent(() => {
    if (!started.current) {
      started.current = true;
      void begin();
    }
  });
  useEffect(() => {
    onScreen.current = true;
    startOnce();
    return () => {
      onScreen.current = false;
      stopKai();
    };
  }, []);

  function submitVoice(clip: RecordedClip) {
    if (!sessionId) {
      return;
    }
    const id = sessionId;
    const number = question;
    void submit(
      'Sending your answer…',
      async () =>
        placementApi.answerVoice(
          id,
          await audioForm(clip, { questionNumber: number, durationMs: Math.round(clip.durationMs) }),
        ),
      () => submitVoice(clip),
    );
  }

  function submitText(text: string): Promise<boolean> {
    if (!sessionId) {
      return Promise.resolve(false);
    }
    const id = sessionId;
    const number = question;
    // A failed typed answer stays in the field, so the learner just taps Send again.
    return submit(text, () => placementApi.answerText(id, number, text));
  }

  async function submit(
    pendingText: string,
    send: () => Promise<PlacementAnswer>,
    resend?: () => void,
  ): Promise<boolean> {
    const pendingId = nextLineId();
    setLines((current) => [...current, { id: pendingId, speaker: 'learner', text: pendingText, pending: true }]);
    setStatus('sending');
    setProblem(null);
    stopKai();
    try {
      const answer = await send();
      setLines((current) => [
        ...current.map((line): ChatLine =>
          line.id === pendingId ? { id: pendingId, speaker: 'learner', text: answer.transcript } : line,
        ),
        { id: nextLineId(), speaker: 'kai', line: answer.kai },
      ]);
      setAnswered(answer.questionNumber);
      if (answer.done || answer.nextQuestionNumber == null) {
        void finish(answer.kai);
      } else {
        setQuestion(answer.nextQuestionNumber);
        setStatus('ready');
        say(answer.kai);
      }
      return true;
    } catch (error) {
      setLines((current) => current.filter((line) => line.id !== pendingId));
      setStatus('ready');
      setProblem(problemFor(error, resend));
      return false;
    }
  }

  /** @param closing Luna's last line; the result waits for Luna to finish saying it. */
  async function finish(closing?: KaiLine) {
    if (!sessionId) {
      return;
    }
    const id = sessionId;
    setStatus('finishing');
    setProblem(null);
    if (!closing) {
      stopKai();
    }
    try {
      const [result] = await Promise.all([
        placementApi.complete(id),
        closing ? speakKaiAndWait(closing) : Promise.resolve(),
      ]);
      queryClient.setQueryData(queryKeys.placementResult(id), result);
      if (onScreen.current) {
        router.replace({ pathname: '/onboarding/result', params: { sessionId: id } });
      }
    } catch (error) {
      setStatus('finish-failed');
      setProblem({ message: toMessage(error), actionLabel: 'Try again', onAction: () => void finish() });
    }
  }

  function problemFor(error: unknown, resend?: () => void): Problem {
    const message = toMessage(error);
    if (error instanceof ApiError) {
      if (error.code === 'stt_failed') {
        return { message, actionLabel: 'Type instead', onAction: () => setMode('text') };
      }
      if (error.code === 'placement_missing') {
        return { message, actionLabel: 'Start again', onAction: () => void begin() };
      }
      if (error.status === 400 || error.status === 422) {
        // Too short, nothing heard, or invalid: the fix is to answer again, not to resend.
        return { message };
      }
    }
    return resend ? { message, actionLabel: 'Send again', onAction: resend } : { message };
  }

  const finished = status === 'finishing' || status === 'finish-failed';
  const canFinishEarly = status === 'ready' && answered >= MIN_ANSWERS_TO_FINISH && answered < total;
  const notice = problem ? (
    <Notice tone="error" message={problem.message} actionLabel={problem.actionLabel} onAction={problem.onAction} />
  ) : null;

  let footer;
  if (status === 'finishing') {
    footer = (
      <View style={styles.waiting} accessibilityLiveRegion="polite">
        <ActivityIndicator color={colors.text} />
        <AppText tone="muted">Luna is working out your level…</AppText>
      </View>
    );
  } else if (status === 'start-failed' || status === 'finish-failed') {
    footer = notice;
  } else {
    footer = (
      <>
        {notice}
        <AnswerComposer
          mode={mode}
          onModeChange={setMode}
          busy={status === 'sending'}
          busyLabel="Luna is listening…"
          disabled={status === 'starting'}
          onVoice={submitVoice}
          onText={submitText}
          secondaryAction={
            canFinishEarly ? (
              <Button label="Finish now" variant="ghost" onPress={() => void finish()} testID="placement-finish" />
            ) : undefined
          }
        />
      </>
    );
  }

  return (
    <Screen scroll={false} footer={footer}>
      <StepHeader
        step={finished ? total : question}
        total={total}
        label={finished ? 'All done' : `Question ${question} of ${total}`}
        canGoBack={status !== 'finishing'}
        backLabel="Leave the chat"
      />
      <ChatLog lines={lines}>
        {status === 'starting' ? (
          <View style={styles.waiting}>
            <ActivityIndicator color={colors.text} />
            <AppText tone="muted">Luna is getting ready…</AppText>
          </View>
        ) : null}
      </ChatLog>
    </Screen>
  );
}

const styles = StyleSheet.create({
  waiting: { flexDirection: 'row', alignItems: 'center', gap: space.md, paddingVertical: space.lg },
});
