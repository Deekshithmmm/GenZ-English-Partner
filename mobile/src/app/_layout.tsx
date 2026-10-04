import { QueryClientProvider } from '@tanstack/react-query';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider as NavigationThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { queryClient, wireQueryLifecycle } from '@/api/queryClient';
import { hydrateSession } from '@/auth/session';
import { isOnboarded, useSession } from '@/stores/session';
import { useBaseFonts } from '@/theme/fonts';
import { ThemeProvider, useTheme } from '@/theme/ThemeProvider';

SplashScreen.preventAutoHideAsync().catch(() => undefined);

export default function RootLayout() {
  const fontsReady = useBaseFonts();
  const status = useSession((s) => s.status);

  useEffect(() => {
    hydrateSession().catch(() => useSession.setState({ status: 'signedOut' }));
    return wireQueryLifecycle();
  }, []);

  const ready = fontsReady && status !== 'loading';
  useEffect(() => {
    if (ready) {
      SplashScreen.hideAsync().catch(() => undefined);
    }
  }, [ready]);

  if (!ready) {
    return null;
  }

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <AppStack />
        </ThemeProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}

function AppStack() {
  const { colors, scheme } = useTheme();
  const status = useSession((s) => s.status);
  const me = useSession((s) => s.me);
  const signedIn = status === 'signedIn' && me != null;
  const onboarded = isOnboarded(me);

  const navigationTheme = useMemo(() => {
    const base = scheme === 'dark' ? DarkTheme : DefaultTheme;
    return {
      ...base,
      colors: { ...base.colors, background: colors.background, card: colors.surface, text: colors.text, border: colors.border, primary: colors.text },
    };
  }, [colors, scheme]);

  return (
    <NavigationThemeProvider value={navigationTheme}>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
        <Stack.Screen name="index" />
        <Stack.Protected guard={!signedIn}>
          <Stack.Screen name="(auth)" />
        </Stack.Protected>
        <Stack.Protected guard={signedIn && !onboarded}>
          <Stack.Screen name="onboarding" />
        </Stack.Protected>
        <Stack.Protected guard={signedIn && onboarded}>
          <Stack.Screen name="(app)" />
        </Stack.Protected>
      </Stack>
    </NavigationThemeProvider>
  );
}
