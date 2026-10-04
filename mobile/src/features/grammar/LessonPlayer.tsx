import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { grammarApi } from '@/api/endpoints';
import { ApiError, toMessage } from '@/api/errors';
import { queryClient, queryKeys } from '@/api/queryClient';
import type { ExerciseAnswer, GrammarLesson, LessonReport } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Kai3D } from '@/components/Kai3D';
import { Loading } from '@/components/Loading';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { StepHeader } from '@/components/StepHeader';
import { useSession } from '@/stores/session';
import { useTheme } from '@/theme/ThemeProvider';
import { space } from '@/theme/layout';

import { exerciseSpeech, ExerciseStep } from './ExerciseStep';
import { KIND_KICKER, KIND_LABEL, kindIcon } from './labels';
import { LessonIntro } from './LessonIntro';
import { LessonResult } from './LessonResult';
import { LessonSplash } from './LessonSplash';
import { english, introSpeech, partsFor, useKaiLines, useLessonVoice } from './lessonVoice';
import { promptSpeech, SpeakingStep } from './SpeakingStep';

/** The server keeps a lesson's time under ten hours. */
const MAX_SECONDS = 36_000;

type Phase = 'splash' | 'intro' | 'exercises' | 'practice-splash' | 'speaking' | 'finishing';

/** Leaves the lesson for the path (or wherever the learner came from, when there is history). */
function leave() {
  if (router.canGoBack()) {
    router.back();
  } else {
    router.replace('/path');
  }
}

/**
 * One lesson on the grammar path. A Learn lesson goes: title card, Luna's explanation with the cheat sheet, exercises,
 * then a short speaking practice; a fluency booster skips the exercises and speaks more. At the end the answers are
 * sent for marking and the learner gets a celebration and a report.
 */
export function LessonPlayer({ lessonId }: { lessonId: string }) {
  const lesson = useQuery({ queryKey: queryKeys.grammarLesson(lessonId), queryFn: () => grammarApi.lesson(lessonId) });
  // A new attempt starts the lesson over from a clean slate.
  const [attempt, setAttempt] = useState(0);

  if (!lesson.data) {
    const locked = lesson.error instanceof ApiError && (lesson.error.code === 'lesson_locked' || lesson.error.status === 404);
    return (
      <Screen
        footer={
          lesson.isError ? (
            <Notice
              tone="error"
              message={lesson.error.message}
              actionLabel={locked ? 'Back to the path' : 'Try again'}
              onAction={locked ? () => router.replace('/path') : () => void lesson.refetch()}
            />
          ) : null
        }>
        <StepHeader step={0} total={1} label="Lesson" backIcon="close" backLabel="Leave the lesson" onBack={leave} />
        {lesson.isError ? null : <Loading label="Getting your lesson ready…" />}
      </Screen>
    );
  }
  return <Lesson key={attempt} lesson={lesson.data} onRetry={() => setAttempt((n) => n + 1)} />;
}

function Lesson({ lesson, onRetry }: { lesson: GrammarLesson; onRetry: () => void }) {
  const { colors } = useTheme();
  const language = useSession((s) => s.me?.profile.explanationLanguage ?? null);
  const voice = useLessonVoice();
  const { say, hush, prefetch } = useKaiLines(voice);
  const [phase, setPhase] = useState<Phase>('splash');

  // Luna's voice for the intro (with its cards), the first question and the speaking part is made while the splash is
  // up, in the order she'll say it, so she starts talking at once.
  const getVoiceReady = useEffectEvent(() => {
    prefetch([
      ...lesson.intro.flatMap((_, index) => introSpeech(lesson, index, language)),
      ...lesson.exercises.slice(0, 1).map((exercise) => english(exerciseSpeech(exercise))),
      ...partsFor(lesson.phrases.practice, language),
      ...lesson.speaking.slice(0, 1).map((item) => english(promptSpeech(item))),
      ...[lesson.phrases.right, lesson.phrases.almost, lesson.phrases.done].flatMap((line) => partsFor(line, language)),
    ]);
  });
  useEffect(() => {
    getVoiceReady();
  }, []);
  const [progress, setProgress] = useState(0);
  const [report, setReport] = useState<LessonReport | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const answers = useRef<(ExerciseAnswer | null)[]>(lesson.exercises.map(() => null));
  const spoken = useRef<(string | null)[]>(lesson.speaking.map(() => null));
  const startedAt = useRef(Date.now());
  const attemptId = useRef(newAttemptId());
  const total = lesson.exercises.length + lesson.speaking.length;

  const quit = () => {
    hush();
    leave();
  };

  function afterIntro() {
    if (lesson.exercises.length > 0) {
      setPhase('exercises');
    } else if (lesson.speaking.length > 0) {
      setPhase('practice-splash');
    } else {
      void finish();
    }
  }

  function afterExercises() {
    if (lesson.speaking.length > 0) {
      setPhase('practice-splash');
    } else {
      void finish();
    }
  }

  async function finish() {
    setPhase('finishing');
    setProblem(null);
    try {
      const seconds = Math.min(MAX_SECONDS, Math.round((Date.now() - startedAt.current) / 1000));
      const result = await grammarApi.complete(lesson.id, {
        answers: answers.current,
        spoken: spoken.current,
        seconds,
        attemptId: attemptId.current,
      });
      // The path, today's plan, streak and XP may all have moved on; lesson statuses too, but not the one on screen.
      void queryClient.invalidateQueries({ queryKey: queryKeys.grammarPath });
      void queryClient.invalidateQueries({ queryKey: queryKeys.today });
      void queryClient.invalidateQueries({ queryKey: ['grammar', 'lesson'], refetchType: 'none' });
      setReport(result);
    } catch (error) {
      setProblem(toMessage(error));
    }
  }

  const header = (
    <StepHeader
      step={progress}
      total={total}
      label={`${KIND_LABEL[lesson.kind]} · ${lesson.topicTitle}`}
      backIcon="close"
      backLabel="Leave the lesson"
      onBack={quit}
    />
  );

  if (report) {
    const next = report.nextNodeId && report.nextNodeId !== lesson.id ? report.nextNodeId : null;
    return (
      <LessonResult
        lesson={lesson}
        report={report}
        onNext={
          next
            ? () => {
                hush();
                router.replace({ pathname: '/lesson/[id]', params: { id: next } });
              }
            : null
        }
        onRetry={onRetry}
        onBack={() => {
          hush();
          // Back to the Path tab under this lesson, wherever the lesson was opened from.
          router.dismissTo('/path');
        }}
      />
    );
  }

  switch (phase) {
    case 'splash':
      return (
        <LessonSplash
          kicker={KIND_KICKER[lesson.kind]}
          title={lesson.topicTitle}
          detail={`Week ${lesson.week} · Lesson ${lesson.number}. ${lesson.summary}`}
          icon={kindIcon(lesson.kind, lesson.icon)}
          buttonLabel="Let's go"
          onDone={() => {
            startedAt.current = Date.now();
            setPhase('intro');
          }}
        />
      );
    case 'intro':
      return (
        <LessonIntro lesson={lesson} header={header} voice={voice} language={language} say={say} onDone={afterIntro} />
      );
    case 'exercises':
      return (
        <ExerciseStep
          lesson={lesson}
          header={header}
          say={say}
          prefetch={prefetch}
          onAnswer={(index, answer) => {
            answers.current[index] = answer;
            setProgress((n) => n + 1);
          }}
          onDone={afterExercises}
        />
      );
    case 'practice-splash':
      return (
        <LessonSplash
          kicker="PRACTICE"
          title="Now say it out loud"
          detail={`${lesson.speaking.length} short ${lesson.speaking.length === 1 ? 'question' : 'questions'} with Luna. Speak, or type if you can't talk right now.`}
          icon="mic"
          buttonLabel="Start speaking"
          onDone={() => setPhase('speaking')}
        />
      );
    case 'speaking':
      return (
        <SpeakingStep
          lesson={lesson}
          header={header}
          voice={voice}
          language={language}
          say={say}
          hush={hush}
          prefetch={prefetch}
          onSpoken={(index, transcript) => {
            spoken.current[index] = transcript;
          }}
          onItemDone={() => setProgress((n) => n + 1)}
          onDone={() => void finish()}
        />
      );
    case 'finishing':
      return (
        <Screen
          scroll={false}
          footer={
            problem ? (
              <Notice tone="error" message={problem} actionLabel="Try again" onAction={() => void finish()} />
            ) : null
          }>
          {header}
          <View style={styles.finishing} accessibilityLiveRegion="polite">
            <Kai3D mood="think" size={180} frame="portal" />
            {problem ? null : (
              <View style={styles.waiting}>
                <ActivityIndicator color={colors.text} />
                <AppText tone="muted">Luna is marking your lesson…</AppText>
              </View>
            )}
          </View>
        </Screen>
      );
  }
}

/** Ties retries of one completion together, so the server counts it once. */
function newAttemptId(): string {
  const crypto = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  if (crypto?.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

const styles = StyleSheet.create({
  finishing: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.lg },
  waiting: { flexDirection: 'row', alignItems: 'center', gap: space.md },
});
