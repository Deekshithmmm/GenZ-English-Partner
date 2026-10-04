import { useLocalSearchParams } from 'expo-router';

import { PracticeChat } from '@/features/practice/PracticeChat';

/** Free talk with Luna, about a topic from the Talk tab or (with no topic) anything at all. */
export default function FreeTalkScreen() {
  const { topic } = useLocalSearchParams<{ topic?: string }>();
  // Keyed so picking another topic starts a fresh chat instead of reusing this one.
  return <PracticeChat key={topic ?? 'anything'} kind="FREE_TALK" scenario={topic} />;
}
