import { StyleSheet, useWindowDimensions, View } from 'react-native';

import { useKaiSpeaking } from '@/audio/kaiSpeech';
import { Kai3D, type KaiMood } from '@/components/Kai3D';
import { space } from '@/theme/layout';

type Props = {
  /** The learner is recording. */
  listening?: boolean;
  /** Luna is checking an answer. */
  thinking?: boolean;
  /** Smaller, e.g. while the keyboard is up. */
  compact?: boolean;
};

/** Luna in her round window above a lesson chat, talking while her voice plays and listening while the mic is on. */
export function KaiStage({ listening = false, thinking = false, compact = false }: Props) {
  const speaking = useKaiSpeaking((s) => s.speaking);
  const { height } = useWindowDimensions();
  const mood: KaiMood = listening ? 'listen' : thinking ? 'think' : speaking ? 'talk' : 'idle';
  const size = compact ? 76 : Math.round(Math.min(140, Math.max(92, height * 0.15)));
  return (
    <View style={styles.stage}>
      <Kai3D mood={mood} size={size} frame="portal" />
    </View>
  );
}

const styles = StyleSheet.create({
  stage: { alignItems: 'center', marginTop: -space.sm, marginBottom: space.md },
});
