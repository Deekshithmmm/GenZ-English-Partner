import { useState, type Ref } from 'react';
import { Pressable, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space, TOUCH_TARGET } from '@/theme/layout';
import { fonts, MAX_FONT_SCALE } from '@/theme/typography';

import { AppText } from './AppText';
import { Icon } from './Icon';

type Props = Omit<TextInputProps, 'style'> & {
  label: string;
  error?: string | null;
  /** Adds a show/hide toggle. */
  secure?: boolean;
  ref?: Ref<TextInput>;
};

export function TextField({ label, error, secure = false, multiline = false, onFocus, onBlur, ref, ...inputProps }: Props) {
  const { colors } = useTheme();
  const [focused, setFocused] = useState(false);
  const [hidden, setHidden] = useState(secure);
  const borderColor = error ? colors.error : focused ? colors.text : colors.border;
  const labelId = `field-${label.replace(/\W+/g, '-').toLowerCase()}`;

  return (
    <View style={styles.wrapper}>
      <AppText variant="label" nativeID={labelId}>
        {label}
      </AppText>
      <View
        style={[
          styles.box,
          {
            borderColor,
            backgroundColor: colors.glassStrong,
            borderWidth: focused || error ? 2 : 1.5,
            boxShadow: focused ? `0px 0px 0px 4px ${colors.accentSoft}` : colors.cardShadow,
          },
        ]}>
        <TextInput
          {...inputProps}
          ref={ref}
          accessibilityLabel={label}
          accessibilityLabelledBy={labelId}
          placeholderTextColor={colors.textMuted}
          selectionColor={colors.primary}
          secureTextEntry={hidden}
          multiline={multiline}
          maxFontSizeMultiplier={MAX_FONT_SCALE}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
          style={[styles.input, multiline && styles.multiline, { color: colors.text }]}
        />
        {secure ? (
          <Pressable
            onPress={() => setHidden((h) => !h)}
            accessibilityRole="button"
            accessibilityLabel={hidden ? 'Show password' : 'Hide password'}
            style={styles.toggle}>
            <Icon name={hidden ? 'eye-outline' : 'eye-off-outline'} color={colors.textMuted} />
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <View style={styles.errorRow} accessibilityLiveRegion="polite">
          <Icon name="alert-circle" size={16} color={colors.error} />
          <AppText variant="small" tone="error" style={styles.errorText}>
            {error}
          </AppText>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { gap: space.xs },
  box: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radius.lg,
    minHeight: 52,
  },
  input: {
    flex: 1,
    fontFamily: fonts.body,
    fontSize: 16,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    minHeight: 52,
  },
  multiline: { minHeight: 96, textAlignVertical: 'top' },
  toggle: {
    width: TOUCH_TARGET,
    height: TOUCH_TARGET,
    alignItems: 'center',
    justifyContent: 'center',
  },
  errorRow: { flexDirection: 'row', gap: space.xs, alignItems: 'flex-start' },
  errorText: { flex: 1 },
});
