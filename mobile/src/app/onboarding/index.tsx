import { Redirect } from 'expo-router';

import { useSession } from '@/stores/session';

/** Resumes onboarding where the learner left off: preferences first, then the placement chat. */
export default function OnboardingIndex() {
  const step = useSession((s) => s.me?.onboardingStep);
  return <Redirect href={step === 'PLACEMENT' ? '/onboarding/meet-kai' : '/onboarding/language'} />;
}
