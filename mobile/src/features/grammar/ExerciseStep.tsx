import * as Haptics from 'expo-haptics';
import { Fragment, useEffect, useEffectEvent, useState, type ReactNode } from 'react';
import { Platform, StyleSheet, View } from 'react-native';

import type { ExerciseAnswer, GrammarLesson, LessonExercise } from '@/api/types';
import type { SpokenPart } from '@/audio/kaiSpeech';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Icon } from '@/components/Icon';
import { Icon3D } from '@/components/Icon3D';
import { Pressable3D } from '@/components/Pressable3D';
import { Screen } from '@/components/Screen';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

import { ChartCard } from './ChartCard';
import { english } from './lessonVoice';

const PRAISE = ['Nice!', 'Correct!', 'Spot on!', 'Well done!'];

type Props = {
  lesson: GrammarLesson;
  header: ReactNode;
  /** Luna reads each question as it comes up, and her feedback after CHECK. */
  say: (parts: SpokenPart[]) => Promise<void>;
  /** Gets lines Luna will say soon ready in her voice. */
  prefetch: (parts: SpokenPart[]) => void;
  /** Called once per exercise, when the learner checks their answer. */
  onAnswer: (index: number, answer: ExerciseAnswer) => void;
  onDone: () => void;
};

/**
 * The lesson's exercises, one at a time: pick the right option, or tap word tiles into the right sentence. Luna reads
 * each question out, CHECK marks the answer straight away with a feedback bar she reads too (the server marks it again
 * at the end), and the cheat sheet is one tap away throughout.
 */
export function ExerciseStep({ lesson, header, say, prefetch, onAnswer, onDone }: Props) {
  const [index, setIndex] = useState(0);
  const [choice, setChoice] = useState<number | null>(null);
  // Tiles by position, since a sentence can use the same word twice.
  const [picked, setPicked] = useState<number[]>([]);
  const [result, setResult] = useState<boolean | null>(null);
  const [sheet, setSheet] = useState(false);
  const exercise = lesson.exercises[index];
  const tiles = exercise.tiles ?? [];
  const ready = exercise.type === 'CHOICE' ? choice != null : picked.length > 0;
  const checked = result != null;

  // Each question is read as it comes up; the next one is made in Luna's voice meanwhile.
  const announce = useEffectEvent(() => {
    void say([english(exerciseSpeech(exercise))]);
    const following = lesson.exercises[index + 1];
    if (following) {
      prefetch([english(exerciseSpeech(following))]);
    }
  });
  useEffect(() => {
    announce();
  }, [index]);

  function check() {
    const answer: ExerciseAnswer =
      exercise.type === 'CHOICE' ? { choice: choice ?? undefined } : { words: picked.map((i) => tiles[i]) };
    const right = isRight(exercise, answer);
    setResult(right);
    void say([english(feedbackSpeech(exercise, right, PRAISE[index % PRAISE.length]))]);
    onAnswer(index, answer);
    if (Platform.OS !== 'web') {
      Haptics.notificationAsync(
        right ? Haptics.NotificationFeedbackType.Success : Haptics.NotificationFeedbackType.Error,
      ).catch(() => undefined);
    }
  }

  function next() {
    if (index + 1 >= lesson.exercises.length) {
      onDone();
      return;
    }
    setIndex(index + 1);
    setChoice(null);
    setPicked([]);
    setResult(null);
    setSheet(false);
  }

  const footer = checked ? (
    <>
      <FeedbackBar exercise={exercise} right={result} praise={PRAISE[index % PRAISE.length]} />
      <Button label="Continue" icon="arrow-forward" onPress={next} testID="exercise-continue" />
    </>
  ) : (
    <Button label="Check" onPress={check} disabled={!ready} testID="exercise-check" />
  );

  return (
    <Screen footer={footer}>
      {header}
      <View style={styles.body}>
        <View style={styles.top}>
          <AppText variant="label" tone="muted" style={styles.flex}>
            Question {index + 1} of {lesson.exercises.length} · {exercise.instruction}
          </AppText>
          <Button
            label={sheet ? 'Hide cheat sheet' : 'Cheat sheet'}
            icon="bulb-outline"
            variant="ghost"
            onPress={() => setSheet((s) => !s)}
            testID="exercise-sheet"
          />
        </View>
        {sheet ? <ChartCard chart={lesson.chart} /> : null}
        {exercise.type === 'CHOICE' ? (
          <ChoiceExercise exercise={exercise} choice={choice} result={result} onChoose={setChoice} />
        ) : (
          <BuildExercise
            exercise={exercise}
            picked={picked}
            result={result}
            onPick={(i) => setPicked((p) => [...p, i])}
            onUnpick={(i) => setPicked((p) => p.filter((x) => x !== i))}
          />
        )}
      </View>
    </Screen>
  );
}

/** Same rule as the server: the right option, or the right words in order (ignoring case). */
export function isRight(exercise: LessonExercise, answer: ExerciseAnswer): boolean {
  if (exercise.type === 'CHOICE') {
    return answer.choice != null && answer.choice === exercise.answer;
  }
  const words = exercise.words ?? [];
  const built = answer.words ?? [];
  return built.length === words.length && built.every((w, i) => w.toLowerCase() === words[i].toLowerCase());
}

export function rightAnswer(exercise: LessonExercise): string {
  return exercise.type === 'CHOICE'
    ? (exercise.options?.[exercise.answer ?? 0] ?? '')
    : (exercise.words ?? []).join(' ');
}

/** "Fill the gap: "___ a student."" becomes a lead ("Fill the gap") and the sentence, which is shown big. */
function splitPrompt(prompt: string): { lead: string | null; sentence: string } {
  const match = /^(.*?):\s*["“](.+)["”]\s*$/.exec(prompt.trim());
  return match ? { lead: match[1], sentence: match[2] } : { lead: null, sentence: prompt };
}

/** Ends a line with a full stop if it has no end mark, so Luna pauses after it. */
const ended = (text: string) => (/[.!?]["”’)]*$/.test(text.trim()) ? text.trim() : `${text.trim()}.`);

/** "is, are or am" */
function spokenList(items: string[]): string {
  return items.length < 2 ? items.join('') : `${items.slice(0, -1).join(', ')} or ${items[items.length - 1]}`;
}

/**
 * What Luna reads when a question comes up: the card as it's shown. A gap question ends with the choices ("Fill the
 * gap: My parents blank from Mangaluru. Choose: is, are or am."), a "which is correct" question reads each option with
 * its letter, and a sentence with a slip is read out before the instruction.
 */
export function exerciseSpeech(exercise: LessonExercise): string {
  if (exercise.type === 'BUILD') {
    return `This sentence has a slip: ${ended(exercise.prompt)} ${ended(exercise.instruction)}`;
  }
  const { lead, sentence } = splitPrompt(exercise.prompt);
  const options = exercise.options ?? [];
  if (lead) {
    return `${lead}: ${ended(sentence)} Choose: ${spokenList(options)}.`;
  }
  return [ended(sentence), ...options.map((option, i) => `${String.fromCharCode(65 + i)}: ${ended(option)}`)].join(' ');
}

/** The whole right sentence: the gap filled in, the right option, or the words in order. */
function rightSentence(exercise: LessonExercise): string {
  const answer = rightAnswer(exercise);
  if (exercise.type === 'CHOICE') {
    const { lead, sentence } = splitPrompt(exercise.prompt);
    if (lead && /_{3,}/.test(sentence)) {
      return sentence.replace(/_{3,}/, answer);
    }
  }
  return answer;
}

/** What Luna says after CHECK: praise and the right sentence, or the right answer and why. */
export function feedbackSpeech(exercise: LessonExercise, right: boolean, praise: string): string {
  const answer = ended(rightSentence(exercise));
  return right ? `${praise} ${answer}` : `Not quite. The right answer is: ${answer} ${ended(exercise.explain)}`;
}

function ChoiceExercise({
  exercise,
  choice,
  result,
  onChoose,
}: {
  exercise: LessonExercise;
  choice: number | null;
  result: boolean | null;
  onChoose: (index: number) => void;
}) {
  const { colors } = useTheme();
  const { lead, sentence } = splitPrompt(exercise.prompt);
  const parts = sentence.split(/_{3,}/);
  const checked = result != null;
  const fill = choice != null ? exercise.options?.[choice] : null;
  const fillColor = !checked ? colors.accent : result ? colors.success : colors.error;
  return (
    <>
      <View style={[styles.prompt, { backgroundColor: colors.glass, borderColor: colors.glassBorder, boxShadow: colors.cardShadow }]}>
        {lead ? (
          <AppText variant="label" tone="muted">
            {lead}
          </AppText>
        ) : null}
        <AppText variant="heading" accessibilityRole="header">
          {parts.map((part, i) => (
            <Fragment key={i}>
              {part}
              {i < parts.length - 1 ? (
                <AppText
                  variant="heading"
                  style={[styles.blank, { color: fill ? fillColor : colors.textMuted, textDecorationColor: fillColor }]}>
                  {fill ?? '______'}
                </AppText>
              ) : null}
            </Fragment>
          ))}
        </AppText>
      </View>
      <View accessibilityRole="radiogroup" accessibilityLabel="Answers" style={styles.options}>
        {(exercise.options ?? []).map((option, i) => {
          const chosen = choice === i;
          const isAnswer = i === exercise.answer;
          const material = checked
            ? isAnswer
              ? colors.tulsi3d
              : colors.plain3d
            : chosen
              ? colors.selected3d
              : colors.plain3d;
          const wrongPick = checked && chosen && !isAnswer;
          return (
            <Pressable3D
              key={i}
              material={material}
              onPress={checked ? undefined : () => onChoose(i)}
              radius={radius.lg}
              depth={chosen ? 3 : 5}
              floating={false}
              accessibilityRole="radio"
              accessibilityState={{ checked: chosen, disabled: checked }}
              accessibilityLabel={`${option}${checked && isAnswer ? ', right answer' : ''}${wrongPick ? ', your answer, not right' : ''}`}
              testID={`option-${i}`}
              style={checked && !isAnswer && !chosen ? styles.dim : undefined}
              faceStyle={[
                styles.option,
                chosen && !checked && { borderColor: colors.accent },
                wrongPick && { borderColor: colors.error },
              ]}>
              <View
                style={[
                  styles.letter,
                  { borderColor: chosen || (checked && isAnswer) ? material.ink : colors.border },
                ]}>
                <AppText variant="label" style={{ color: material.ink }}>
                  {String.fromCharCode(65 + i)}
                </AppText>
              </View>
              <AppText variant="bodyStrong" style={[styles.flex, { color: material.ink }]}>
                {option}
              </AppText>
              {checked && isAnswer ? <Icon name="checkmark-circle" size={22} color={material.ink} /> : null}
              {wrongPick ? <Icon name="close-circle" size={22} color={colors.error} /> : null}
            </Pressable3D>
          );
        })}
      </View>
    </>
  );
}

function BuildExercise({
  exercise,
  picked,
  result,
  onPick,
  onUnpick,
}: {
  exercise: LessonExercise;
  picked: number[];
  result: boolean | null;
  onPick: (index: number) => void;
  onUnpick: (index: number) => void;
}) {
  const { colors } = useTheme();
  const tiles = exercise.tiles ?? [];
  const checked = result != null;
  const answerBorder = !checked ? colors.glassBorder : result ? colors.success : colors.error;
  return (
    <>
      <View style={[styles.prompt, { backgroundColor: colors.glass, borderColor: colors.glassBorder, boxShadow: colors.cardShadow }]}>
        <AppText variant="label" tone="muted">
          This sentence has a slip
        </AppText>
        <View style={styles.slip}>
          <Icon name="close-circle" size={22} color={colors.error} />
          <AppText variant="subheading" style={styles.flex} accessibilityLabel={`Sentence with a slip: ${exercise.prompt}`}>
            {exercise.prompt}
          </AppText>
        </View>
      </View>
      <View
        style={[styles.answer, { backgroundColor: colors.surface, borderColor: answerBorder }]}
        accessibilityLabel={picked.length > 0 ? `Your sentence: ${picked.map((i) => tiles[i]).join(' ')}` : 'Your sentence is empty'}
        testID="build-answer">
        <View style={[styles.ruled, { borderBottomColor: colors.hairline }]} />
        <View style={[styles.ruled, styles.ruledSecond, { borderBottomColor: colors.hairline }]} />
        {picked.length === 0 ? (
          <AppText variant="small" tone="muted" style={styles.answerHint}>
            Tap the words below in the right order
          </AppText>
        ) : null}
        {picked.map((i) => (
          <Tile key={i} word={tiles[i]} onPress={checked ? undefined : () => onUnpick(i)} hint="Takes the word out" testID={`answer-${i}`} />
        ))}
      </View>
      <View style={styles.bank} accessibilityLabel="Words to use">
        {tiles.map((word, i) =>
          picked.includes(i) ? (
            <View key={i} style={[styles.slot, { borderColor: colors.border }]} accessibilityElementsHidden>
              <AppText variant="bodyStrong" style={styles.hidden}>
                {word}
              </AppText>
            </View>
          ) : (
            <Tile key={i} word={word} onPress={checked ? undefined : () => onPick(i)} hint="Adds the word" testID={`tile-${i}`} />
          ),
        )}
      </View>
    </>
  );
}

function Tile({ word, onPress, hint, testID }: { word: string; onPress?: () => void; hint: string; testID: string }) {
  const { colors } = useTheme();
  return (
    <Pressable3D
      material={colors.plain3d}
      onPress={onPress}
      radius={radius.md}
      depth={4}
      floating={false}
      accessibilityLabel={word}
      accessibilityHint={onPress ? hint : undefined}
      testID={testID}
      faceStyle={styles.tile}>
      <AppText variant="bodyStrong" style={{ color: colors.plain3d.ink }}>
        {word}
      </AppText>
    </Pressable3D>
  );
}

/** Slides in under a checked answer: green with praise, or red with the right answer, and the rule either way. */
function FeedbackBar({ exercise, right, praise }: { exercise: LessonExercise; right: boolean; praise: string }) {
  const { colors } = useTheme();
  return (
    <View
      style={[styles.feedback, { backgroundColor: right ? colors.successBackground : colors.errorBackground, borderColor: colors.glassBorder }]}
      accessibilityLiveRegion="assertive"
      testID={right ? 'feedback-right' : 'feedback-wrong'}>
      <Icon3D name={right ? 'check' : 'bulb'} size={34} />
      <View style={styles.flex}>
        <AppText variant="subheading" style={{ color: right ? colors.success : colors.error }}>
          {right ? praise : 'Not quite'}
        </AppText>
        {right ? null : (
          <AppText variant="bodyStrong">
            Right answer: {rightAnswer(exercise)}
          </AppText>
        )}
        <AppText variant="small">{exercise.explain}</AppText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  body: { gap: space.lg },
  flex: { flex: 1 },
  top: { flexDirection: 'row', alignItems: 'center', gap: space.sm, marginTop: -space.sm },
  prompt: { borderRadius: radius.lg + 4, borderWidth: 1, padding: space.lg, gap: space.xs },
  blank: { textDecorationLine: 'underline', fontFamily: fonts.headingHeavy },
  options: { gap: space.sm },
  option: {
    minHeight: 60,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  dim: { opacity: 0.6 },
  letter: { width: 28, height: 28, borderRadius: 14, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  slip: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm },
  // Tiles are 48 high with an 8 gap, so each ruled line sits just under a row of them.
  answer: {
    minHeight: 128,
    borderRadius: radius.lg,
    borderWidth: 2,
    padding: space.md,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignContent: 'flex-start',
    gap: space.sm,
  },
  ruled: { position: 'absolute', left: space.md, right: space.md, top: 62, borderBottomWidth: 1 },
  ruledSecond: { top: 118 },
  answerHint: { position: 'absolute', left: space.lg, top: space.lg },
  bank: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: space.sm },
  tile: { paddingHorizontal: space.lg, paddingVertical: space.sm + 2, minHeight: 44, justifyContent: 'center' },
  slot: {
    borderWidth: 2,
    borderStyle: 'dashed',
    borderRadius: radius.md,
    paddingHorizontal: space.lg - 2,
    paddingVertical: space.sm,
    minHeight: 44,
    marginBottom: 4,
    justifyContent: 'center',
  },
  hidden: { opacity: 0 },
  feedback: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.md,
    borderRadius: radius.lg,
    borderWidth: 1,
    padding: space.md,
  },
});
