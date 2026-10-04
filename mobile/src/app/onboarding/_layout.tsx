import { Stack } from 'expo-router';

import { useTheme } from '@/theme/ThemeProvider';

export default function OnboardingLayout() {
  const { colors } = useTheme();
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      {/* A stray edge swipe while holding the mic shouldn't leave the chat; the result is a one-way door. */}
      <Stack.Screen name="placement" options={{ gestureEnabled: false }} />
      <Stack.Screen name="result" options={{ gestureEnabled: false }} />
    </Stack>
  );
}
