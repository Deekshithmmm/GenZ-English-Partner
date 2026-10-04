import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

import { AppText } from './AppText';
import { Button } from './Button';
import { Icon } from './Icon';

type Props = {
  tone?: 'error' | 'success' | 'info';
  message: string;
  actionLabel?: string;
  onAction?: () => void;
};

/** An inline message. Errors say what went wrong and offer the fix as a button. */
export function Notice({ tone = 'info', message, actionLabel, onAction }: Props) {
  const { colors } = useTheme();
  const palette = {
    error: { bg: colors.errorBackground, fg: colors.error, icon: 'alert-circle' as const },
    success: { bg: colors.successBackground, fg: colors.success, icon: 'checkmark-circle' as const },
    info: { bg: colors.glass, fg: colors.text, icon: 'information-circle' as const },
  }[tone];
  return (
    <View
      style={[styles.box, { backgroundColor: palette.bg, borderColor: colors.glassBorder }]}
      accessibilityRole={tone === 'error' ? 'alert' : undefined}
      accessibilityLiveRegion="polite">
      <View style={styles.row}>
        <Icon name={palette.icon} color={palette.fg} />
        <AppText
          variant="small"
          fontFamily={fonts.bodyBold}
          style={[styles.message, { color: tone === 'info' ? colors.text : palette.fg }]}>
          {message}
        </AppText>
      </View>
      {actionLabel && onAction ? <Button label={actionLabel} onPress={onAction} variant="ghost" /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { borderRadius: radius.lg, padding: space.md, gap: space.xs, borderWidth: 1 },
  row: { flexDirection: 'row', gap: space.sm, alignItems: 'flex-start' },
  message: { flex: 1 },
});
