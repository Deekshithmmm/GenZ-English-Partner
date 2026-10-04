import { File } from 'expo-file-system';
import { Platform } from 'react-native';

import type { RecordedClip } from './useVoiceRecorder';

/** Builds a multipart body with the recording plus plain fields. */
export async function audioForm(clip: RecordedClip, fields: Record<string, string | number>): Promise<FormData> {
  const form = new FormData();
  for (const [key, value] of Object.entries(fields)) {
    form.append(key, String(value));
  }
  if (Platform.OS === 'web') {
    const blob = await (await fetch(clip.uri)).blob();
    form.append('audio', blob, clip.fileName);
  } else {
    // On Android and iOS the global fetch is expo/fetch, which can't send React Native's { uri, name, type } file
    // parts. An expo-file-system File is a Blob that it streams from disk.
    form.append('audio', new File(clip.uri));
  }
  return form;
}
