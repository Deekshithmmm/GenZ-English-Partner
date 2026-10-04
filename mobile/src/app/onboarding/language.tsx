import { router } from 'expo-router';
import { useState } from 'react';

import type { ExplanationLanguage } from '@/api/types';
import { AppText } from '@/components/AppText';
import type { Choice } from '@/components/ChoiceList';
import { PreferenceStep, useSavedPreferences } from '@/features/onboarding/PreferenceStep';
import { LANGUAGES } from '@/i18n/languages';
import { usePrefs } from '@/stores/prefs';
import { useAllLanguageFonts } from '@/theme/fonts';

export default function LanguageStep() {
  const fontsReady = useAllLanguageFonts();
  const saved = useSavedPreferences();
  const draft = usePrefs((s) => s.draft.language);
  const updateDraft = usePrefs((s) => s.updateDraft);
  const [error, setError] = useState<string | null>(null);
  const value = draft ?? saved?.explanationLanguage ?? null;

  const choices: Choice<ExplanationLanguage>[] = LANGUAGES.map((language) => {
    // Until the script fonts load, the system font (which covers every Indian script) stands in.
    const font = fontsReady ? language.fontFamily : null;
    return {
      value: language.code,
      title: language.english,
      description: language.sample,
      descriptionFontFamily: font,
      leading:
        language.code === 'ENGLISH' ? undefined : (
          <AppText variant="subheading" fontFamily={font}>
            {language.native}
          </AppText>
        ),
      testID: `language-${language.code.toLowerCase()}`,
    };
  });

  return (
    <PreferenceStep
      step={1}
      title="Which language should Luna explain things in?"
      lede="Luna always talks with you in English. When something's confusing, Luna can explain it in your language."
      label="Explanation language"
      choices={choices}
      value={value}
      onChange={(language) => {
        setError(null);
        updateDraft({ language });
      }}
      onContinue={() => {
        if (!value) {
          setError('Pick a language to continue.');
          return;
        }
        updateDraft({ language: value });
        router.push('/onboarding/goal');
      }}
      error={error}
    />
  );
}
