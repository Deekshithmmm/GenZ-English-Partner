import { Stack } from 'expo-router';

import { useTheme } from '@/theme/ThemeProvider';

/** The signed-in app: tabs at the root, with full-screen flows (like a practice chat) pushed over them. */
export default function AppLayout() {
  const { colors } = useTheme();
  return <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }} />;
}
