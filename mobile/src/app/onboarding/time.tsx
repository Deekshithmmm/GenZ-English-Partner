import { useMutation } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';

import { profileApi } from '@/api/endpoints';
import type { DailyMinutes } from '@/api/types';
import { applyProfile } from '@/auth/session';
import { PreferenceStep, useSavedPreferences } from '@/features/onboarding/PreferenceStep';
import { deviceTimeZone, MINUTE_CHOICES } from '@/features/onboarding/options';
import { usePrefs } from '@/stores/prefs';

export default function TimeStep() {
  const saved = useSavedPreferences();
  const draft = usePrefs((s) => s.draft);
  const updateDraft = usePrefs((s) => s.updateDraft);
  const clearDraft = usePrefs((s) => s.clearDraft);
  const [error, setError] = useState<string | null>(null);
  const value = draft.dailyMinutes ?? saved?.dailyMinutes ?? null;
  const language = draft.language ?? saved?.explanationLanguage ?? null;
  const goal = draft.goal ?? saved?.goal ?? null;

  const save = useMutation({
    mutationFn: profileApi.setPreferences,
    onSuccess: async (profile) => {
      clearDraft();
      // Moves the learner's onboarding step to PLACEMENT, so a relaunch resumes at the chat.
      await applyProfile(profile);
      router.push('/onboarding/meet-kai');
    },
    onError: (e) => setError(e.message),
  });

  const submit = () => {
    setError(null);
    if (!language) {
      router.replace('/onboarding/language');
      return;
    }
    if (!goal) {
      router.replace('/onboarding/goal');
      return;
    }
    if (!value) {
      setError('Pick how much time you have to continue.');
      return;
    }
    save.mutate({ explanationLanguage: language, goal, dailyMinutes: value, timeZone: deviceTimeZone() });
  };

  return (
    <PreferenceStep<DailyMinutes>
      step={3}
      title="How much time can you give each day?"
      lede="A few minutes every day beats one long session a week."
      label="Daily practice time"
      choices={MINUTE_CHOICES}
      value={value}
      onChange={(dailyMinutes) => {
        setError(null);
        updateDraft({ dailyMinutes });
      }}
      onContinue={submit}
      loading={save.isPending}
      error={error}
    />
  );
}
