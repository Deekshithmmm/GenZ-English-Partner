import { isLoaded, loadAsync, useFonts } from 'expo-font';
import { useEffect, useState } from 'react';

import type { ExplanationLanguage } from '@/api/types';
import { LANGUAGES, languageInfo } from '@/i18n/languages';

/** Only the weights the UI uses, imported per file so unused weights stay out of the bundle. */
export function useBaseFonts(): boolean {
  const [loaded, error] = useFonts({
    Baloo2_600SemiBold: require('@expo-google-fonts/baloo-2/600SemiBold/Baloo2_600SemiBold.ttf'),
    Baloo2_700Bold: require('@expo-google-fonts/baloo-2/700Bold/Baloo2_700Bold.ttf'),
    Baloo2_800ExtraBold: require('@expo-google-fonts/baloo-2/800ExtraBold/Baloo2_800ExtraBold.ttf'),
    AtkinsonHyperlegible_400Regular: require('@expo-google-fonts/atkinson-hyperlegible/400Regular/AtkinsonHyperlegible_400Regular.ttf'),
    AtkinsonHyperlegible_700Bold: require('@expo-google-fonts/atkinson-hyperlegible/700Bold/AtkinsonHyperlegible_700Bold.ttf'),
  });
  // If fonts fail to load, carry on with system fonts rather than blocking the app.
  return loaded || error != null;
}

async function loadLanguageFont(code: ExplanationLanguage): Promise<void> {
  const info = languageInfo(code);
  if (!info.fontFamily || !info.loadFont || isLoaded(info.fontFamily)) {
    return;
  }
  await loadAsync({ [info.fontFamily]: info.loadFont() });
}

/**
 * Loads the native-script font for explanations on demand. Returns the family once ready, or null while loading
 * (text falls back to the system font, which also covers every Indian script).
 */
export function useLanguageFont(code: ExplanationLanguage | null | undefined): string | null {
  const info = languageInfo(code);
  const [ready, setReady] = useState(() => (info.fontFamily ? isLoaded(info.fontFamily) : true));

  useEffect(() => {
    let cancelled = false;
    if (!code || !info.fontFamily) {
      return;
    }
    loadLanguageFont(code)
      .then(() => !cancelled && setReady(true))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [code, info.fontFamily]);

  return ready ? info.fontFamily : null;
}

/** For the language picker, which shows every script at once. */
export function useAllLanguageFonts(): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let cancelled = false;
    Promise.all(LANGUAGES.map((l) => loadLanguageFont(l.code)))
      .then(() => !cancelled && setReady(true))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);
  return ready;
}
