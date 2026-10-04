import { Text, type TextProps } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { MAX_FONT_SCALE, textStyles, type TextVariant } from '@/theme/typography';

export type TextTone = 'default' | 'muted' | 'success' | 'error' | 'celebrate' | 'onPrimary';

type Props = TextProps & {
  variant?: TextVariant;
  tone?: TextTone;
  /** Overrides the family, e.g. a Baloo sister font for native-language explanations. */
  fontFamily?: string | null;
};

export function AppText({ variant = 'body', tone = 'default', fontFamily, style, ...rest }: Props) {
  const { colors } = useTheme();
  const color = {
    default: colors.text,
    muted: colors.textMuted,
    success: colors.success,
    error: colors.error,
    celebrate: colors.celebrate,
    onPrimary: colors.onPrimary,
  }[tone];
  return (
    <Text
      maxFontSizeMultiplier={MAX_FONT_SCALE}
      style={[textStyles[variant], { color }, fontFamily ? { fontFamily } : null, style]}
      {...rest}
    />
  );
}
