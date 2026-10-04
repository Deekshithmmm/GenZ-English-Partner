import { api, apiStream } from './client';
import type { ServerEvent } from './sse';
import type {
  AuthProviders,
  AuthResponse,
  CefrLevel,
  ExerciseAnswer,
  GrammarLesson,
  GrammarPath,
  LessonReport,
  SpeakResult,
  DailyMinutes,
  ExplanationLanguage,
  LearningGoal,
  LearningPath,
  Me,
  PlacementAnswer,
  PlacementResult,
  PlacementStart,
  PracticeAnswer,
  PracticeCatalog,
  PracticeHint,
  PracticeResult,
  PracticeStart,
  Profile,
  Speech,
  StartPractice,
  Today,
} from './types';

export const authApi = {
  providers: () => api<AuthProviders>('/api/v1/auth/providers', { auth: false }),
  register: (body: { email: string; password: string; displayName: string }) =>
    api<AuthResponse>('/api/v1/auth/register', { method: 'POST', json: body, auth: false }),
  login: (body: { email: string; password: string }) =>
    api<AuthResponse>('/api/v1/auth/login', { method: 'POST', json: body, auth: false }),
  google: (idToken: string) =>
    api<AuthResponse>('/api/v1/auth/google', { method: 'POST', json: { idToken }, auth: false }),
  logout: (refreshToken: string) =>
    api<void>('/api/v1/auth/logout', { method: 'POST', json: { refreshToken }, auth: false, retries: 1 }),
};

export const meApi = {
  get: () => api<Me>('/api/v1/me'),
  deleteAccount: () => api<void>('/api/v1/me', { method: 'DELETE' }),
};

export const profileApi = {
  setPreferences: (body: {
    explanationLanguage: ExplanationLanguage;
    goal: LearningGoal;
    dailyMinutes: DailyMinutes;
    /** IANA zone, so "today" and streaks follow the learner's clock. The server keeps its default if omitted. */
    timeZone?: string;
  }) => api<Profile>('/api/v1/profile/preferences', { method: 'PUT', json: body }),
  setLevel: (level: CefrLevel) => api<Profile>('/api/v1/profile/level', { method: 'PUT', json: { level } }),
};

// Placement answers are idempotent per question number on the server, so retrying uploads is safe.
export const placementApi = {
  start: () => api<PlacementStart>('/api/v1/placement', { method: 'POST' }),
  answerVoice: (sessionId: string, form: FormData) =>
    api<PlacementAnswer>(`/api/v1/placement/${sessionId}/answers/voice`, {
      method: 'POST',
      form,
      retries: 2,
      timeoutMs: 45_000,
    }),
  answerText: (sessionId: string, questionNumber: number, text: string) =>
    api<PlacementAnswer>(`/api/v1/placement/${sessionId}/answers/text`, {
      method: 'POST',
      json: { questionNumber, text },
      retries: 2,
    }),
  complete: (sessionId: string) =>
    api<PlacementResult>(`/api/v1/placement/${sessionId}/complete`, {
      method: 'POST',
      retries: 2,
      timeoutMs: 45_000,
    }),
};

export const todayApi = {
  today: () => api<Today>('/api/v1/today'),
  path: () => api<LearningPath>('/api/v1/path'),
};

// Like placement, answers are idempotent per turn number, so retrying uploads is safe. The stream variants send the
// same answer as server-sent events (transcript, reply, kai, feedback, done) so Luna can start talking sooner.
export const practiceApi = {
  catalog: () => api<PracticeCatalog>('/api/v1/practice/catalog'),
  // Planning a mock interview reads the whole résumé, so it gets longer than the default.
  start: (body: StartPractice) =>
    api<PracticeStart>('/api/v1/practice', {
      method: 'POST',
      json: body,
      timeoutMs: body.kind === 'INTERVIEW' ? 40_000 : undefined,
    }),
  hint: (sessionId: string) =>
    api<PracticeHint>(`/api/v1/practice/${sessionId}/hint`, { method: 'POST', retries: 1, timeoutMs: 20_000 }),
  streamVoice: (sessionId: string, form: FormData, onEvent: (event: ServerEvent) => void) =>
    apiStream(`/api/v1/practice/${sessionId}/turns/voice/stream`, { method: 'POST', form }, onEvent),
  streamText: (sessionId: string, turnNumber: number, text: string, onEvent: (event: ServerEvent) => void) =>
    apiStream(`/api/v1/practice/${sessionId}/turns/text/stream`, { method: 'POST', json: { turnNumber, text } }, onEvent),
  answerVoice: (sessionId: string, form: FormData) =>
    api<PracticeAnswer>(`/api/v1/practice/${sessionId}/turns/voice`, {
      method: 'POST',
      form,
      retries: 2,
      timeoutMs: 45_000,
    }),
  answerText: (sessionId: string, turnNumber: number, text: string) =>
    api<PracticeAnswer>(`/api/v1/practice/${sessionId}/turns/text`, {
      method: 'POST',
      json: { turnNumber, text },
      retries: 2,
      timeoutMs: 30_000,
    }),
  complete: (sessionId: string) =>
    api<PracticeResult>(`/api/v1/practice/${sessionId}/complete`, {
      method: 'POST',
      retries: 2,
      timeoutMs: 45_000,
    }),
};

// Luna's voice, a sentence at a time: a clip to play, or DEVICE when the server has no voice for it. Safe to repeat
// (the server makes each clip once). Fetches made `early` wait behind speech that's about to play, so they get longer.
export const speechApi = {
  /** The languages the server voice speaks: primary subtags ("en", "kn"), or "*" for any. */
  languages: () => api<{ languages: string[] }>('/api/v1/speech/languages', { auth: false }),
  voice: (text: string, languageTag: string, early: boolean) =>
    api<Speech>('/api/v1/speech', {
      method: 'POST',
      json: { text, languageTag, early },
      retries: 1,
      timeoutMs: early ? 90_000 : 30_000,
    }),
};

// The grammar path: lessons open one by one. Completing is idempotent per attemptId, so it's safe to retry.
export const grammarApi = {
  path: () => api<GrammarPath>('/api/v1/grammar'),
  lesson: (lessonId: string) => api<GrammarLesson>(`/api/v1/grammar/lessons/${lessonId}`),
  speakText: (lessonId: string, index: number, text: string) =>
    api<SpeakResult>(`/api/v1/grammar/lessons/${lessonId}/speaking/${index}/text`, {
      method: 'POST',
      json: { text },
      retries: 1,
      timeoutMs: 30_000,
    }),
  speakVoice: (lessonId: string, index: number, form: FormData) =>
    api<SpeakResult>(`/api/v1/grammar/lessons/${lessonId}/speaking/${index}/voice`, {
      method: 'POST',
      form,
      retries: 2,
      timeoutMs: 45_000,
    }),
  complete: (
    lessonId: string,
    body: { answers: (ExerciseAnswer | null)[]; spoken: (string | null)[]; seconds: number; attemptId: string },
  ) =>
    api<LessonReport>(`/api/v1/grammar/lessons/${lessonId}/complete`, {
      method: 'POST',
      json: body,
      retries: 2,
      timeoutMs: 30_000,
    }),
};
