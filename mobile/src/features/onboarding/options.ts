import type { CefrLevel, DailyMinutes, LearningGoal } from '@/api/types';
import type { Choice } from '@/components/ChoiceList';

// Titles mirror the backend enums (LearningGoal, CefrLevel) so the app and server describe things the same way.

export const GOAL_CHOICES: Choice<LearningGoal>[] = [
  {
    value: 'JOB_INTERVIEWS',
    title: 'Job interviews',
    description: 'Your intro, HR rounds, group discussions and your first weeks at work.',
    testID: 'goal-job-interviews',
  },
  {
    value: 'COLLEGE_PRESENTATIONS',
    title: 'College and presentations',
    description: 'Presentations, vivas, seminars and pitching your ideas.',
    testID: 'goal-college',
  },
  {
    value: 'EVERYDAY_CONFIDENCE',
    title: 'Everyday confidence',
    description: 'Small talk, phone calls and asking for what you need.',
    testID: 'goal-everyday',
  },
  {
    value: 'CASUAL_SOCIAL',
    title: 'Casual and social',
    description: 'Making friends, keeping chats going and slang that lands.',
    testID: 'goal-social',
  },
];

// Matches how the server splits each day's plan (TodayService.plan).
export const MINUTE_CHOICES: Choice<DailyMinutes>[] = [
  {
    value: 5,
    title: '5 minutes a day',
    description: 'A quick warm-up and a short roleplay. Easy to keep up.',
    testID: 'minutes-5',
  },
  {
    value: 10,
    title: '10 minutes a day',
    description: 'The full daily plan: warm-up, a lesson, a roleplay and your words.',
    testID: 'minutes-10',
  },
  {
    value: 20,
    title: '20 minutes a day',
    description: 'Longer roleplays and more speaking time. For a big interview or exam soon.',
    testID: 'minutes-20',
  },
];

export const LEVELS: { code: CefrLevel; label: string; description: string }[] = [
  { code: 'A1', label: 'Starter', description: 'You can say simple things about yourself with short, familiar phrases.' },
  {
    code: 'A2',
    label: 'Basic',
    description: 'You can handle everyday topics in short sentences when people speak slowly.',
  },
  {
    code: 'B1',
    label: 'Independent',
    description: 'You can talk about your life, plans and opinions, even if you pause to find words.',
  },
  { code: 'B2', label: 'Confident', description: 'You can hold a real discussion and explain your view with reasons.' },
  {
    code: 'C1',
    label: 'Advanced',
    description: 'You speak fluently and flexibly, and can adjust your tone for work or friends.',
  },
];

export const LEVEL_CHOICES: Choice<CefrLevel>[] = LEVELS.map((level) => ({
  value: level.code,
  title: `${level.code} · ${level.label}`,
  description: level.description,
  testID: `level-${level.code}`,
}));

export function goalTitle(goal: LearningGoal | null | undefined): string {
  return GOAL_CHOICES.find((choice) => choice.value === goal)?.title ?? 'Everyday confidence';
}

/** The device's IANA time zone, so "today" and streaks follow the learner's clock. */
export function deviceTimeZone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
  } catch {
    return undefined;
  }
}
