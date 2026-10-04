import { router } from 'expo-router';
import { useState } from 'react';

import { PreferenceStep, useSavedPreferences } from '@/features/onboarding/PreferenceStep';
import { GOAL_CHOICES } from '@/features/onboarding/options';
import { usePrefs } from '@/stores/prefs';

export default function GoalStep() {
  const saved = useSavedPreferences();
  const draft = usePrefs((s) => s.draft.goal);
  const updateDraft = usePrefs((s) => s.updateDraft);
  const [error, setError] = useState<string | null>(null);
  const value = draft ?? saved?.goal ?? null;

  return (
    <PreferenceStep
      step={2}
      title="What are you practising for?"
      lede="Luna builds your lessons and roleplays around this."
      label="Your goal"
      choices={GOAL_CHOICES}
      value={value}
      onChange={(goal) => {
        setError(null);
        updateDraft({ goal });
      }}
      onContinue={() => {
        if (!value) {
          setError('Pick a goal to continue.');
          return;
        }
        updateDraft({ goal: value });
        router.push('/onboarding/time');
      }}
      error={error}
    />
  );
}
