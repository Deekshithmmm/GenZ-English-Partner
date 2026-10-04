import { Redirect, useLocalSearchParams } from 'expo-router';

import { LessonPlayer } from '@/features/grammar/LessonPlayer';

export default function LessonScreen() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  if (!id) {
    return <Redirect href="/path" />;
  }
  // Keyed so moving on to the next lesson starts it fresh.
  return <LessonPlayer key={id} lessonId={id} />;
}
