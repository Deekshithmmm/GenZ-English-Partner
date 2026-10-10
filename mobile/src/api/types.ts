// Mirrors the backend DTOs (backend/src/main/java/com/genzenglish/partner/**/…Dtos.java).

export type ExplanationLanguage =
  | 'ENGLISH'
  | 'HINDI'
  | 'KANNADA'
  | 'TAMIL'
  | 'TELUGU'
  | 'MALAYALAM'
  | 'MARATHI'
  | 'BENGALI';

export type LearningGoal =
  | 'JOB_INTERVIEWS'
  | 'COLLEGE_PRESENTATIONS'
  | 'EVERYDAY_CONFIDENCE'
  | 'CASUAL_SOCIAL';

export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1';
export type DailyMinutes = 5 | 10 | 20;
export type TutorVoice = 'KAI_WARM' | 'KAI_BRIGHT';

export type LevelInfo = { code: CefrLevel; label: string; description: string };

export type Profile = {
  explanationLanguage: ExplanationLanguage;
  goal: LearningGoal | null;
  dailyMinutes: DailyMinutes | null;
  level: LevelInfo | null;
  levelSource: 'PLACEMENT' | 'SELF_SELECTED' | null;
  tutorVoice: TutorVoice;
  speakingSpeed: number;
  keepAudio: boolean;
  timeZone: string;
};

export type OnboardingStep = 'PREFERENCES' | 'PLACEMENT' | 'DONE';

export type Me = {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
  hasPassword: boolean;
  hasGoogle: boolean;
  profile: Profile;
  onboardingStep: OnboardingStep;
};

export type AuthResponse = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  me: Me;
};

export type AuthProviders = { email: boolean; google: 'google' | 'mock' | 'disabled' };

export type Speech = {
  mode: 'AUDIO' | 'DEVICE';
  audioUrl: string | null;
  voice: TutorVoice;
  rate: number;
  languageTag: string;
};

export type KaiLine = { text: string; speech: Speech };

export type PlacementStart = {
  sessionId: string;
  questionNumber: number;
  totalQuestions: number;
  kai: KaiLine;
};

export type PlacementAnswer = {
  questionNumber: number;
  transcript: string;
  wordsPerMinute: number | null;
  fillerCount: number;
  done: boolean;
  nextQuestionNumber: number | null;
  kai: KaiLine;
};

export type PathUnit = {
  id: string;
  title: string;
  summary: string;
  skills: string[];
  microLessons: string[];
  roleplay: { slug: string; title: string };
  status: 'CURRENT' | 'UP_NEXT' | 'LATER';
};

export type LearningPath = { goal: LearningGoal; level: CefrLevel; units: PathUnit[] };

export type PlacementResult = {
  level: LevelInfo;
  scores: { fluency: number; grammar: number; vocabulary: number };
  strengths: string[];
  focusAreas: string[];
  summary: string;
  path: LearningPath;
};

export type PlanKind = 'WARM_UP' | 'MICRO_LESSON' | 'ROLEPLAY' | 'WORD_REVIEW';

export type PlanItem = {
  id: string;
  kind: PlanKind;
  title: string;
  subtitle: string;
  minutes: number;
  action: string;
  done: boolean;
};

export type Today = {
  date: string;
  dailyMinutes: number;
  level: LevelInfo;
  focus: { id: string; title: string; summary: string };
  plan: PlanItem[];
  /** `week`: whether the learner practised on each day of this week, Monday first. */
  streak: { current: number; longest: number; freezeAvailable: boolean; week: boolean[] };
  xp: { today: number; total: number };
};

export type PracticeKind = 'WARM_UP' | 'ROLEPLAY' | 'FREE_TALK' | 'INTERVIEW';
export type RoleplayDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type PracticeCatalog = {
  topics: { slug: string; title: string }[];
  roleplays: { slug: string; title: string; goal: string; difficulty: RoleplayDifficulty; targetTurns: number }[];
};

/** One or two things the learner could say next. */
export type PracticeHint = { ideas: string[] };

/** First event of a streamed answer: what the learner said, as the server heard it. */
export type TurnTranscriptEvent = {
  turnNumber: number;
  transcript: string;
  wordsPerMinute?: number | null;
  fillerCount: number;
};

/**
 * One slip in an answer: the learner's own words and a more natural way to say them. `whyNative` is the reason in the
 * learner's explanation language (absent when that's English).
 */
export type Correction = { original: string; better: string; why?: string | null; whyNative?: string | null };

/** A stronger word to try; `instead` quotes the learner's words it would replace. */
export type NewWord = { word: string; meaning: string; instead?: string | null };

/** Luna's notes on one answer. Older answers may lack the newer lists, so treat them as possibly missing. */
export type TurnFeedback = {
  corrections: Correction[];
  tip?: string | null;
  /** The whole answer said more naturally; absent when it already sounded natural. */
  betterPhrasing?: string | null;
  newWords?: NewWord[];
  fillerWords?: { word: string; count: number }[];
  /** Words that didn't come through clearly (spoken answers only), with a 0..100 score. */
  mispronounced?: { word: string; score: number }[];
};

export type PracticeStart = {
  sessionId: string;
  kind: PracticeKind;
  /** The roleplay or free-talk topic slug; absent for a warm-up. */
  scenario?: string | null;
  title: string;
  /** The warm-up or free-talk topic, the learner's goal in a roleplay, or the job in a mock interview. */
  brief: string;
  /** Who Luna plays in a roleplay or interview, written to follow "Luna plays". */
  kaiRole?: string | null;
  turnNumber: number;
  /** Answers before Luna wraps up; for a chat that runs on time, only an upper bound. */
  targetTurns: number;
  /** How long a chat that runs on time lasts (free talk: ten minutes); absent when it ends after `targetTurns`. */
  timeLimitSeconds?: number | null;
  kai: KaiLine;
  /** What counts as success in a roleplay or interview; empty for other chats. */
  criteria: string[];
};

export type StartPractice = {
  kind: PracticeKind;
  scenario?: string;
  /** Interview mode: the pasted résumé or job description. Used to plan the questions, never stored. */
  context?: string;
};

export type PracticeAnswer = {
  turnNumber: number;
  transcript: string;
  wordsPerMinute?: number | null;
  fillerCount: number;
  feedback: TurnFeedback;
  done: boolean;
  nextTurnNumber?: number | null;
  kai: KaiLine;
};

export type PracticeStats = {
  answers: number;
  voiceAnswers: number;
  speakingSeconds: number;
  wordsSpoken: number;
  averageWpm?: number | null;
  fillerCount: number;
  topFiller?: string | null;
};

export type PracticeSummary = {
  sessionId: string;
  kind: PracticeKind;
  scenario?: string | null;
  title: string;
  stats: PracticeStats;
  xp: number;
  xpItems: { label: string; xp: number }[];
  /** Luna's note: one thing that went well, one thing to try next time. */
  note: string;
  corrections: Correction[];
  /** Absent for sessions finished before scoring existed. */
  scores?: SessionScores | null;
  /** The top things to work on next, most useful first. */
  focus?: string[];
  /** Roleplays and interviews only. */
  scorecard?: Scorecard | null;
};

/** 0..100 against a confident, fluent speaker. Pronunciation is absent when nothing was spoken out loud. */
export type SessionScores = { fluency: number; grammar: number; pronunciation?: number | null; vocabulary: number };

export type CriterionStatus = 'MET' | 'PARTLY' | 'MISSED';

/** `evidence` quotes the learner, or says what was missing. */
export type CriterionResult = { criterion: string; status: CriterionStatus; evidence?: string | null };

export type Scorecard = {
  verdict: 'REACHED' | 'ALMOST' | 'NOT_YET';
  met: number;
  total: number;
  criteria: CriterionResult[];
};

export type PracticeResult = {
  summary: PracticeSummary;
  progress: { streak: number; xpToday: number; xpTotal: number };
};

// ---- Grammar path ----

/**
 * DONE: finished at least once. CURRENT: the next lesson. OPEN: before the learner's starting unit, there for review.
 * LOCKED: after the current lesson.
 */
export type NodeStatus = 'DONE' | 'CURRENT' | 'OPEN' | 'LOCKED';

/**
 * LEARN: the rule and exercises. BOOST: a fluency booster, mostly speaking. REVIEW: the topic again two weeks later,
 * with new questions. MASTER: a last, harder round five weeks later, with more speaking.
 */
export type LessonKind = 'LEARN' | 'BOOST' | 'REVIEW' | 'MASTER';

export type PathNode = {
  id: string;
  kind: LessonKind;
  /** Position on the whole path, from 1. */
  number: number;
  /** With the kind, e.g. "Learn: Introducing yourself". */
  title: string;
  /** The topic alone, for places that show the kind separately. */
  topicTitle: string;
  /** The topic's one-line summary. */
  subtitle: string;
  /** A 3D icon key. */
  icon: string;
  status: NodeStatus;
  /** Best result so far, 0 to 3. */
  stars: number;
};

export type PathWeek = { number: number; title: string; level: CefrLevel; nodes: PathNode[] };

export type GrammarPath = { weeks: PathWeek[]; currentNodeId: string | null; done: number; total: number };

/** A line from Luna, in English and (for learners who chose another language) in theirs. */
export type TutorLine = { text: string; nativeText?: string | null };

export type ChartRow = { label: string; items: string[] };

export type GrammarExample = { wrong?: string | null; right: string; note?: string | null };

/**
 * CHOICE: pick one of `options` (`answer` is the right index). BUILD: tap `tiles` into the order of `words`.
 */
export type LessonExercise = {
  type: 'CHOICE' | 'BUILD';
  instruction: string;
  prompt: string;
  options?: string[] | null;
  answer?: number | null;
  tiles?: string[] | null;
  words?: string[] | null;
  explain: string;
};

/** FIX: say a slip the right way. ANSWER: answer a question out loud. OPEN: a longer answer. */
export type SpeakItem = { type: 'FIX' | 'ANSWER' | 'OPEN'; instruction: string; prompt: string; sample: string; tip: string };

export type GrammarLesson = {
  id: string;
  kind: LessonKind;
  number: number;
  week: number;
  title: string;
  topicTitle: string;
  summary: string;
  why: string;
  icon: string;
  intro: TutorLine[];
  chart: ChartRow[];
  rules: string[];
  examples: GrammarExample[];
  exercises: LessonExercise[];
  speaking: SpeakItem[];
  phrases: { practice: TutorLine; right: TutorLine; almost: TutorLine; done: TutorLine };
  status: NodeStatus;
  stars: number;
};

export type SpeakResult = { transcript: string; correct: boolean; expected: string; feedback: TurnFeedback };

export type ExerciseAnswer = { choice?: number; words?: string[] };

export type LessonReport = {
  nodeId: string;
  /** At least half right, so the lesson counts as done. */
  passed: boolean;
  /** 0 to 100. */
  score: number;
  correct: number;
  total: number;
  stars: number;
  xp: number;
  firstTime: boolean;
  wordsUsed: number;
  seconds: number;
  note: string;
  improvements: { tip: string; wrong: string; right: string }[];
  nextNodeId: string | null;
  progress: { streak: number; xpToday: number; xpTotal: number };
};
