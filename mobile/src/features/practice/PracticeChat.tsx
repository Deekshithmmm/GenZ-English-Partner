import { router } from 'expo-router';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, useWindowDimensions, View } from 'react-native';

import { practiceApi } from '@/api/endpoints';
import { ApiError, toMessage } from '@/api/errors';
import { queryClient, queryKeys } from '@/api/queryClient';
import type { KaiLine, PracticeAnswer, PracticeKind, PracticeStart, StartPractice } from '@/api/types';
import { speakKaiAndWait, stopKai, useKaiSpeaking } from '@/audio/kaiSpeech';
import { audioForm } from '@/audio/upload';
import type { RecordedClip } from '@/audio/useVoiceRecorder';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { Icon3D, type Icon3DName } from '@/components/Icon3D';
import { Kai3D, type KaiMood } from '@/components/Kai3D';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { StepHeader } from '@/components/StepHeader';
import { AnswerComposer, type AnswerMode } from '@/features/conversation/AnswerComposer';
import { ChatLog, type ChatLine, type LearnerLine } from '@/features/conversation/ChatLog';
import { useSession } from '@/stores/session';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { roleplayIcon3d, topicIcon3d } from './catalogIcons';
import { sendTurn, type TurnProgress } from './sendTurn';

type Status = 'starting' | 'start-failed' | 'ready' | 'sending' | 'finishing' | 'finish-failed';
type Problem = { message: string; actionLabel?: string; onAction?: () => void };
type Hint = { state: 'idle' } | { state: 'loading' } | { state: 'shown'; ideas: string[] } | { state: 'failed'; message: string };

const DEFAULT_TITLE: Record<PracticeKind, string> = {
  WARM_UP: 'Warm-up chat',
  ROLEPLAY: 'Roleplay',
  FREE_TALK: 'Talk with Luna',
  INTERVIEW: 'Mock interview',
};
const BRIEF_LABEL: Record<PracticeKind, string> = {
  WARM_UP: "Today's topic",
  ROLEPLAY: 'Your goal',
  FREE_TALK: 'Topic',
  INTERVIEW: 'The job',
};
const GETTING_READY: Record<PracticeKind, string> = {
  WARM_UP: 'Luna is getting ready…',
  ROLEPLAY: 'Luna is getting into character…',
  FREE_TALK: 'Luna is getting ready…',
  INTERVIEW: 'Luna is reading what you pasted and planning the questions…',
};

function briefIcon(session: PracticeStart): Icon3DName {
  switch (session.kind) {
    case 'WARM_UP':
      return 'sun';
    case 'ROLEPLAY':
      return roleplayIcon3d(session.scenario);
    case 'FREE_TALK':
      return topicIcon3d(session.scenario);
    case 'INTERVIEW':
      return 'career';
  }
}

let lineCounter = 0;
const nextLineId = () => `practice-line-${++lineCounter}`;

/** In a chat that runs on time, the server makes the first answer this close to the end the last one. */
const LAST_ANSWER_SECONDS = 30;

function clockText(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/** Seconds since `startedAt`, ticking once a second while `running`. */
function useElapsedSeconds(startedAt: number | null, running: boolean): number {
  const [now, setNow] = useState(0);
  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [running]);
  return startedAt == null || now < startedAt ? 0 : Math.floor((now - startedAt) / 1000);
}

type Props = {
  kind: PracticeKind;
  /** The roleplay slug (e.g. "hr-interview") or free-talk topic slug. */
  scenario?: string;
  /** Interview mode: the résumé or job description the learner pasted. */
  context?: string;
  /** Interview mode: back to the pasted text, for when the server can't plan from it. */
  onEditContext?: () => void;
};

/**
 * A practice chat with Luna: warm-up, roleplay, free talk or mock interview. Answers stream back, so Luna's words appear
 * as they're written and Luna starts talking before the feedback is ready; Luna's notes then appear under the learner's
 * words. Luna wraps up after a few answers (free talk: when its ten minutes are nearly up) and the session ends on a
 * summary with scores and XP. Answers are idempotent per turn on the server, so resending after a dropped connection is
 * safe.
 */
export function PracticeChat({ kind, scenario, context, onEditContext }: Props) {
  const { colors } = useTheme();
  const language = useSession((s) => s.me?.profile.explanationLanguage);
  const kaiSpeaking = useKaiSpeaking((s) => s.speaking);
  const [recording, setRecording] = useState(false);
  const [status, setStatus] = useState<Status>('starting');
  const [session, setSession] = useState<PracticeStart | null>(null);
  const [turn, setTurn] = useState(1);
  const [answered, setAnswered] = useState(0);
  const [lines, setLines] = useState<ChatLine[]>([]);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [hint, setHint] = useState<Hint>({ state: 'idle' });
  const [mode, setMode] = useState<AnswerMode>('voice');
  // When the chat started, for chats that run on time.
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const timeLimit = session?.timeLimitSeconds ?? null;
  const elapsed = useElapsedSeconds(startedAt, timeLimit != null && (status === 'ready' || status === 'sending'));
  const started = useRef(false);
  // False once the learner has left, so a late response can't talk or navigate them somewhere else.
  const onScreen = useRef(true);
  // Resolves when Luna finishes the line being spoken, so the summary can wait for Luna's goodbye.
  const speaking = useRef<Promise<void>>(Promise.resolve());

  function say(line: KaiLine) {
    if (onScreen.current) {
      speaking.current = speakKaiAndWait(line);
    }
  }

  async function begin() {
    setStatus('starting');
    setProblem(null);
    setHint({ state: 'idle' });
    setLines([]);
    setAnswered(0);
    try {
      const request: StartPractice = { kind };
      if (scenario) {
        request.scenario = scenario;
      }
      if (context) {
        request.context = context;
      }
      const start = await practiceApi.start(request);
      setSession(start);
      setStartedAt(Date.now());
      setTurn(start.turnNumber);
      setLines([{ id: nextLineId(), speaker: 'kai', line: start.kai }]);
      setStatus('ready');
      say(start.kai);
    } catch (error) {
      setStatus('start-failed');
      // The server couldn't use what was pasted: the fix is to change it, not to send it again.
      const editInstead = onEditContext && error instanceof ApiError && error.status === 400;
      setProblem(
        editInstead
          ? { message: toMessage(error), actionLabel: 'Change what you pasted', onAction: onEditContext }
          : { message: toMessage(error), actionLabel: 'Try again', onAction: () => void begin() },
      );
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
    if (!session) {
      return;
    }
    const id = session.sessionId;
    const number = turn;
    const form = () => audioForm(clip, { turnNumber: number, durationMs: Math.round(clip.durationMs) });
    void submit(
      'Sending your answer…',
      (progress) =>
        sendTurn(
          async (onEvent) => practiceApi.streamVoice(id, await form(), onEvent),
          async () => practiceApi.answerVoice(id, await form()),
          progress,
        ),
      () => submitVoice(clip),
    );
  }

  function submitText(text: string): Promise<boolean> {
    if (!session) {
      return Promise.resolve(false);
    }
    const id = session.sessionId;
    const number = turn;
    // A failed typed answer stays in the field, so the learner just taps Send again.
    return submit(text, (progress) =>
      sendTurn(
        (onEvent) => practiceApi.streamText(id, number, text, onEvent),
        () => practiceApi.answerText(id, number, text),
        progress,
      ),
    );
  }

  async function submit(
    pendingText: string,
    send: (progress: TurnProgress) => Promise<PracticeAnswer>,
    resend?: () => void,
  ): Promise<boolean> {
    const learnerId = nextLineId();
    const kaiId = nextLineId();
    let kaiShown = false;
    setLines((current) => [...current, { id: learnerId, speaker: 'learner', text: pendingText, pending: true }]);
    setStatus('sending');
    setProblem(null);
    setHint({ state: 'idle' });
    stopKai();

    const updateLearner = (patch: Partial<Omit<LearnerLine, 'id' | 'speaker'>>) =>
      setLines((current) =>
        current.map((line) => (line.id === learnerId && line.speaker === 'learner' ? { ...line, ...patch } : line)),
      );
    const showKai = (entry: ChatLine) =>
      setLines((current) =>
        current.some((line) => line.id === kaiId)
          ? current.map((line) => (line.id === kaiId ? entry : line))
          : [...current, entry],
      );

    try {
      const answer = await send({
        onTranscript: (event) => updateLearner({ text: event.transcript, pending: false }),
        onReply: (text) => {
          if (!kaiShown) {
            showKai({ id: kaiId, speaker: 'kai-draft', text });
          }
        },
        onKai: (line) => {
          kaiShown = true;
          showKai({ id: kaiId, speaker: 'kai', line });
          say(line);
        },
        onFeedback: (feedback) => updateLearner({ feedback }),
      });
      updateLearner({ text: answer.transcript, pending: false, feedback: answer.feedback });
      if (!kaiShown) {
        showKai({ id: kaiId, speaker: 'kai', line: answer.kai });
        say(answer.kai);
      }
      setAnswered(answer.turnNumber);
      if (answer.done || answer.nextTurnNumber == null) {
        void finish(true);
      } else {
        setTurn(answer.nextTurnNumber);
        setStatus('ready');
      }
      return true;
    } catch (error) {
      setLines((current) => current.filter((line) => line.id !== learnerId && line.id !== kaiId));
      stopKai();
      setStatus('ready');
      setProblem(problemFor(error, resend));
      return false;
    }
  }

  /** @param afterKai wait for Luna to finish the line being spoken (the goodbye) before showing the summary */
  async function finish(afterKai = false) {
    if (!session) {
      return;
    }
    const id = session.sessionId;
    setStatus('finishing');
    setProblem(null);
    setHint({ state: 'idle' });
    if (!afterKai) {
      stopKai();
    }
    try {
      const [result] = await Promise.all([practiceApi.complete(id), afterKai ? speaking.current : Promise.resolve()]);
      queryClient.setQueryData(queryKeys.practiceResult(id), result);
      // Today's plan, streak and XP all changed.
      void queryClient.invalidateQueries({ queryKey: queryKeys.today });
      if (onScreen.current) {
        router.replace({ pathname: '/practice/summary', params: { sessionId: id } });
      }
    } catch (error) {
      setStatus('finish-failed');
      setProblem({ message: toMessage(error), actionLabel: 'Try again', onAction: () => void finish() });
    }
  }

  async function askForHint() {
    if (!session) {
      return;
    }
    setHint({ state: 'loading' });
    try {
      const { ideas } = await practiceApi.hint(session.sessionId);
      setHint({ state: 'shown', ideas });
    } catch (error) {
      setHint({ state: 'failed', message: toMessage(error) });
    }
  }

  function problemFor(error: unknown, resend?: () => void): Problem {
    const message = toMessage(error);
    if (error instanceof ApiError) {
      if (error.code === 'stt_failed') {
        return { message, actionLabel: 'Type instead', onAction: () => setMode('text') };
      }
      if (error.code === 'practice_missing') {
        return { message, actionLabel: 'Start again', onAction: () => void begin() };
      }
      if (error.code === 'session_done') {
        return { message, actionLabel: 'See how you did', onAction: () => void finish() };
      }
      if (error.status === 400 || error.status === 422) {
        // Too short, nothing heard, or invalid: the fix is to answer again, not to resend.
        return { message };
      }
    }
    return resend ? { message, actionLabel: 'Send again', onAction: resend } : { message };
  }

  const mood: KaiMood =
    status === 'finishing'
      ? 'happy'
      : recording
        ? 'listen'
        : kaiSpeaking
          ? 'talk'
          : status === 'starting' || status === 'sending'
            ? 'think'
            : 'idle';
  const target = session?.targetTurns ?? 1;
  const title = session?.title ?? DEFAULT_TITLE[kind];
  const finished = status === 'finishing' || status === 'finish-failed';
  const canFinishEarly = answered >= 1 && answered < target;
  // Free talk runs on time: the header shows the clock instead of a count of answers.
  const total = timeLimit ?? target;
  const step = finished ? total : timeLimit != null ? Math.min(elapsed, timeLimit) : answered;
  let progress = `${answered} of ${target}`;
  if (finished) {
    progress = 'done';
  } else if (timeLimit != null) {
    progress = `${clockText(Math.min(elapsed, timeLimit))} of ${clockText(timeLimit)}`;
    if (timeLimit - elapsed <= LAST_ANSWER_SECONDS) {
      progress += ' · last answer';
    }
  }
  const notice = problem ? (
    <Notice tone="error" message={problem.message} actionLabel={problem.actionLabel} onAction={problem.onAction} />
  ) : null;

  let footer;
  if (status === 'finishing') {
    footer = (
      <View style={styles.waiting} accessibilityLiveRegion="polite">
        <ActivityIndicator color={colors.text} />
        <AppText tone="muted">Luna is writing up how you did…</AppText>
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
          onRecordingChange={setRecording}
          secondaryAction={
            status === 'ready' ? (
              <View style={styles.actions}>
                <Button
                  label="Hint"
                  icon="bulb-outline"
                  variant="ghost"
                  loading={hint.state === 'loading'}
                  onPress={() => void askForHint()}
                  accessibilityHint="Shows two things you could say next"
                  testID="practice-hint"
                />
                {canFinishEarly ? (
                  <Button label="Finish" variant="ghost" onPress={() => void finish()} testID="practice-finish" />
                ) : null}
              </View>
            ) : undefined
          }
        />
      </>
    );
  }

  return (
    <Screen scroll={false} footer={footer}>
      <StepHeader
        step={step}
        total={total}
        label={session ? `${title} · ${progress}` : title}
        canGoBack={status !== 'finishing'}
        backLabel="Leave the chat"
      />
      <KaiStage mood={mood} compact={mode === 'text'} />
      <ChatLog lines={lines} notes language={language} header={session ? <SceneBrief session={session} /> : null}>
        {status === 'starting' ? (
          <View style={styles.waiting}>
            <ActivityIndicator color={colors.text} />
            <AppText tone="muted" style={styles.flex}>
              {GETTING_READY[kind]}
            </AppText>
          </View>
        ) : null}
        {hint.state === 'shown' ? <HintCard ideas={hint.ideas} /> : null}
        {hint.state === 'failed' ? <Notice tone="error" message={hint.message} /> : null}
      </ChatLog>
    </Screen>
  );
}

/**
 * Luna in 3D above the conversation, reacting as it goes. Smaller while typing, so the keyboard leaves room for the
 * chat.
 */
function KaiStage({ mood, compact }: { mood: KaiMood; compact: boolean }) {
  const { height } = useWindowDimensions();
  const size = compact ? 84 : Math.round(Math.min(168, Math.max(108, height * 0.18)));
  return (
    <View style={styles.stage}>
      <Kai3D mood={mood} size={size} frame="portal" />
    </View>
  );
}

/**
 * What this chat is about, pinned above the first line: the topic or goal, who Luna plays, and for roleplays and
 * interviews, what counts as success. The scorecard at the end checks the same list.
 */
function SceneBrief({ session }: { session: PracticeStart }) {
  const { colors } = useTheme();
  const criteria = session.criteria ?? [];
  return (
    <Card tone="primary" padding={space.md} style={styles.brief}>
      <View style={styles.briefTop}>
        <View style={[styles.briefIcon, { backgroundColor: colors.glassStrong }]}>
          <Icon3D name={briefIcon(session)} size={30} />
        </View>
        <View style={styles.briefText}>
          <AppText variant="label" tone="muted">
            {BRIEF_LABEL[session.kind]}
          </AppText>
          <AppText variant="bodyStrong">{session.brief}</AppText>
          {session.kaiRole ? (
            <AppText variant="small" tone="muted">
              Luna plays {session.kaiRole}.
            </AppText>
          ) : null}
        </View>
      </View>
      {criteria.length > 0 ? (
        <View
          style={[styles.criteria, { borderTopColor: colors.hairline }]}
          accessible
          accessibilityLabel={`To reach the goal: ${criteria.join('. ')}.`}>
          <AppText variant="label" tone="muted">
            To reach the goal
          </AppText>
          {criteria.map((criterion) => (
            <View key={criterion} style={styles.criterion}>
              <Icon name="ellipse-outline" size={14} color={colors.text} />
              <AppText variant="small" style={styles.flex}>
                {criterion}
              </AppText>
            </View>
          ))}
        </View>
      ) : null}
    </Card>
  );
}

/** "What could I say next?" Shown in the chat, never spoken, and cleared once the learner answers. */
function HintCard({ ideas }: { ideas: string[] }) {
  const { colors } = useTheme();
  return (
    <Card tone="primary" style={styles.hint} accessibilityLiveRegion="polite">
      <View style={styles.hintTitle}>
        <Icon name="bulb-outline" size={18} color={colors.text} />
        <AppText variant="label" tone="muted">
          You could say
        </AppText>
      </View>
      {ideas.map((idea) => (
        <View key={idea} style={[styles.idea, { backgroundColor: colors.surface }]}>
          <AppText variant="bodyStrong">“{idea}”</AppText>
        </View>
      ))}
    </Card>
  );
}

const styles = StyleSheet.create({
  waiting: { flexDirection: 'row', alignItems: 'center', gap: space.md, paddingVertical: space.lg },
  flex: { flex: 1 },
  stage: { alignItems: 'center', marginTop: -space.sm, marginBottom: space.md },
  brief: { gap: space.md },
  briefTop: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  briefIcon: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  briefText: { flex: 1, gap: 2 },
  criteria: { gap: space.xs, borderTopWidth: StyleSheet.hairlineWidth, paddingTop: space.md },
  criterion: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm },
  actions: { flexDirection: 'row', alignItems: 'center' },
  hint: { gap: space.sm },
  idea: { borderRadius: radius.md, paddingHorizontal: space.md, paddingVertical: space.sm },
  hintTitle: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
});
