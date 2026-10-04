import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';

import { profileApi } from '@/api/endpoints';
import type { CefrLevel } from '@/api/types';
import { applyProfile } from '@/auth/session';
import { PreferenceStep, ONBOARDING_STEPS } from '@/features/onboarding/PreferenceStep';
import { LEVEL_CHOICES } from '@/features/onboarding/options';

/** For learners who'd rather not do the spoken placement. Saving finishes onboarding. */
export default function ChooseLevel() {
  const [level, setLevel] = useState<CefrLevel | null>(null);
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: profileApi.setLevel,
    // Completes onboarding, which flips the route guards and opens today's plan.
    onSuccess: (profile) => applyProfile(profile),
    onError: (e) => setError(e.message),
  });

  return (
    <PreferenceStep
      step={ONBOARDING_STEPS}
      title="Pick your speaking level"
      lede="Choose the one that sounds most like you when you speak English. Not sure? Go back and take the two-minute chat."
      label="Speaking level"
      choices={LEVEL_CHOICES}
      value={level}
      onChange={(next) => {
        setError(null);
        setLevel(next);
      }}
      continueLabel="Use this level"
      onContinue={() => {
        if (!level) {
          setError('Pick a level to continue.');
          return;
        }
        save.mutate(level);
      }}
      loading={save.isPending}
      error={error}
    />
  );
}
