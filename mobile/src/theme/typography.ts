import type { TextStyle } from 'react-native';

/**
 * Baloo 2 for headings and numbers; Atkinson Hyperlegible for English UI and transcripts.
 * Custom fonts carry their weight in the family name, so never combine them with fontWeight.
 */
export const fonts = {
  headingSemiBold: 'Baloo2_600SemiBold',
  heading: 'Baloo2_700Bold',
  headingHeavy: 'Baloo2_800ExtraBold',
  body: 'AtkinsonHyperlegible_400Regular',
  bodyBold: 'AtkinsonHyperlegible_700Bold',
} as const;

export type TextVariant =
  | 'display'
  | 'title'
  | 'heading'
  | 'subheading'
  | 'body'
  | 'bodyStrong'
  | 'small'
  | 'label'
  | 'number';

export const textStyles: Record<TextVariant, TextStyle> = {
  display: { fontFamily: fonts.headingHeavy, fontSize: 44, lineHeight: 52 },
  title: { fontFamily: fonts.heading, fontSize: 28, lineHeight: 36 },
  heading: { fontFamily: fonts.heading, fontSize: 22, lineHeight: 30 },
  subheading: { fontFamily: fonts.headingSemiBold, fontSize: 18, lineHeight: 26 },
  body: { fontFamily: fonts.body, fontSize: 16, lineHeight: 24 },
  bodyStrong: { fontFamily: fonts.bodyBold, fontSize: 16, lineHeight: 24 },
  small: { fontFamily: fonts.body, fontSize: 14, lineHeight: 20 },
  label: { fontFamily: fonts.bodyBold, fontSize: 13, lineHeight: 18, letterSpacing: 0.3 },
  number: { fontFamily: fonts.heading, fontSize: 22, lineHeight: 28 },
};

/** Caps text growth from system font scaling before layouts break, while still honouring large text. */
export const MAX_FONT_SCALE = 1.6;
