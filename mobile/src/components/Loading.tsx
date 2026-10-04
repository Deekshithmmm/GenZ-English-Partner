import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { space } from '@/theme/layout';

import { AppText } from './AppText';

/** A spinner with words, so it's clear what we're waiting for. */
export function Loading({ label }: { label: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.row} accessibilityLiveRegion="polite">
      <ActivityIndicator color={colors.text} />
      <AppText tone="muted">{label}</AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: space.md, paddingVertical: space.lg },
});
