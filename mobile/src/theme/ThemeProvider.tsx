import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { useColorScheme } from 'react-native';

import { usePrefs } from '@/stores/prefs';

import { darkColors, lightColors, type ColorTokens } from './colors';

export type Theme = {
  scheme: 'light' | 'dark';
  colors: ColorTokens;
};

const ThemeContext = createContext<Theme>({ scheme: 'light', colors: lightColors });

export function ThemeProvider({ children }: { children: ReactNode }) {
  const system = useColorScheme();
  const preference = usePrefs((s) => s.appearance);
  const scheme = preference === 'system' ? (system === 'dark' ? 'dark' : 'light') : preference;
  const theme = useMemo<Theme>(
    () => ({ scheme, colors: scheme === 'dark' ? darkColors : lightColors }),
    [scheme],
  );
  return <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>;
}

export function useTheme(): Theme {
  return useContext(ThemeContext);
}
