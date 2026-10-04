import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { DailyMinutes, ExplanationLanguage, LearningGoal } from '@/api/types';

export type Appearance = 'system' | 'light' | 'dark';
/** hold = press and hold to talk; toggle = tap to start, tap to stop (easier with switch access or tremors). */
export type MicMode = 'hold' | 'toggle';

type OnboardingDraft = {
  language: ExplanationLanguage | null;
  goal: LearningGoal | null;
  dailyMinutes: DailyMinutes | null;
};

type PrefsState = {
  appearance: Appearance;
  micMode: MicMode;
  draft: OnboardingDraft;
  setAppearance: (appearance: Appearance) => void;
  setMicMode: (mode: MicMode) => void;
  updateDraft: (patch: Partial<OnboardingDraft>) => void;
  clearDraft: () => void;
};

const EMPTY_DRAFT: OnboardingDraft = { language: null, goal: null, dailyMinutes: null };

/** Device-level preferences that don't need the server. */
export const usePrefs = create<PrefsState>()(
  persist(
    (set) => ({
      appearance: 'system',
      micMode: 'hold',
      draft: EMPTY_DRAFT,
      setAppearance: (appearance) => set({ appearance }),
      setMicMode: (micMode) => set({ micMode }),
      updateDraft: (patch) => set((s) => ({ draft: { ...s.draft, ...patch } })),
      clearDraft: () => set({ draft: EMPTY_DRAFT }),
    }),
    {
      name: 'genz.prefs',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ appearance, micMode, draft }) => ({ appearance, micMode, draft }),
    },
  ),
);
