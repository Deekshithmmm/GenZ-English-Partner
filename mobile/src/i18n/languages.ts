import type { ExplanationLanguage } from '@/api/types';

type FontModule = number;

export type LanguageInfo = {
  code: ExplanationLanguage;
  english: string;
  native: string;
  /** A short line in the language, shown on the picker so learners recognise their script. */
  sample: string;
  /** BCP 47 tag for reading lines in this language aloud. */
  speechTag: string;
  /** Baloo sister family covering this script. Loaded on demand to keep startup light on low-RAM phones. */
  fontFamily: string | null;
  loadFont: (() => FontModule) | null;
};

export const LANGUAGES: LanguageInfo[] = [
  {
    code: 'ENGLISH',
    english: 'English',
    native: 'English',
    sample: 'Explain it in simple English',
    speechTag: 'en-IN',
    fontFamily: null,
    loadFont: null,
  },
  {
    code: 'HINDI',
    english: 'Hindi',
    native: 'हिन्दी',
    sample: 'हिन्दी में समझाइए',
    speechTag: 'hi-IN',
    fontFamily: 'Baloo2_500Medium',
    loadFont: () => require('@expo-google-fonts/baloo-2/500Medium/Baloo2_500Medium.ttf'),
  },
  {
    code: 'KANNADA',
    english: 'Kannada',
    native: 'ಕನ್ನಡ',
    sample: 'ಕನ್ನಡದಲ್ಲಿ ವಿವರಿಸಿ',
    speechTag: 'kn-IN',
    fontFamily: 'BalooTamma2_500Medium',
    loadFont: () => require('@expo-google-fonts/baloo-tamma-2/500Medium/BalooTamma2_500Medium.ttf'),
  },
  {
    code: 'TAMIL',
    english: 'Tamil',
    native: 'தமிழ்',
    sample: 'தமிழில் விளக்குங்கள்',
    speechTag: 'ta-IN',
    fontFamily: 'BalooThambi2_500Medium',
    loadFont: () => require('@expo-google-fonts/baloo-thambi-2/500Medium/BalooThambi2_500Medium.ttf'),
  },
  {
    code: 'TELUGU',
    english: 'Telugu',
    native: 'తెలుగు',
    sample: 'తెలుగులో వివరించండి',
    speechTag: 'te-IN',
    fontFamily: 'BalooTammudu2_500Medium',
    loadFont: () => require('@expo-google-fonts/baloo-tammudu-2/500Medium/BalooTammudu2_500Medium.ttf'),
  },
  {
    code: 'MALAYALAM',
    english: 'Malayalam',
    native: 'മലയാളം',
    sample: 'മലയാളത്തിൽ വിശദീകരിക്കൂ',
    speechTag: 'ml-IN',
    fontFamily: 'BalooChettan2_500Medium',
    loadFont: () => require('@expo-google-fonts/baloo-chettan-2/500Medium/BalooChettan2_500Medium.ttf'),
  },
  {
    code: 'MARATHI',
    english: 'Marathi',
    native: 'मराठी',
    sample: 'मराठीत समजावून सांगा',
    speechTag: 'mr-IN',
    fontFamily: 'Baloo2_500Medium',
    loadFont: () => require('@expo-google-fonts/baloo-2/500Medium/Baloo2_500Medium.ttf'),
  },
  {
    code: 'BENGALI',
    english: 'Bengali',
    native: 'বাংলা',
    sample: 'বাংলায় বুঝিয়ে বলুন',
    speechTag: 'bn-IN',
    fontFamily: 'BalooDa2_500Medium',
    loadFont: () => require('@expo-google-fonts/baloo-da-2/500Medium/BalooDa2_500Medium.ttf'),
  },
];

export function languageInfo(code: ExplanationLanguage | null | undefined): LanguageInfo {
  return LANGUAGES.find((l) => l.code === code) ?? LANGUAGES[0];
}
