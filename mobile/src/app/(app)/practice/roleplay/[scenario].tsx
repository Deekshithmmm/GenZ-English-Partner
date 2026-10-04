import { Redirect, useLocalSearchParams } from 'expo-router';

import { PracticeChat } from '@/features/practice/PracticeChat';

export default function RoleplayScreen() {
  const { scenario } = useLocalSearchParams<{ scenario?: string }>();
  if (!scenario) {
    return <Redirect href="/today" />;
  }
  // Keyed so opening a different roleplay starts a fresh chat instead of reusing this one.
  return <PracticeChat key={scenario} kind="ROLEPLAY" scenario={scenario} />;
}
