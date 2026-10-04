import { Image } from 'expo-image';
import type { ReactNode, Ref } from 'react';
import { KeyboardAvoidingView, Platform, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useTheme } from '@/theme/ThemeProvider';
import { MAX_CONTENT_WIDTH, space } from '@/theme/layout';

/** Soft aurora backdrops (tools/backdrop/make_backdrops.py): a gradient with blurred colour glows. */
const BACKDROPS = {
  light: require('../../assets/backdrop/light.webp'),
  dark: require('../../assets/backdrop/dark.webp'),
} as const;

type Props = {
  children: ReactNode;
  /** Pinned to the bottom, inside the thumb zone: the screen's one primary action or the mic. */
  footer?: ReactNode;
  scroll?: boolean;
  /** Bottom safe-area is handled by the tab bar on tab screens. */
  edges?: ('top' | 'bottom')[];
  /** Adds pull-to-refresh to scrolling screens. */
  onRefresh?: () => void;
  refreshing?: boolean;
  /** For scrolling to something on the page. Children's layout y plus SCREEN_TOP_PADDING is their scroll offset. */
  scrollRef?: Ref<ScrollView>;
  /** Floats over the page (position it absolutely), e.g. a button that stays put while the page scrolls. */
  overlay?: ReactNode;
};

/** Space above the first child of a scrolling screen. */
export const SCREEN_TOP_PADDING = space.xl;

export function Screen({
  children,
  footer,
  scroll = true,
  edges = ['top', 'bottom'],
  onRefresh,
  refreshing = false,
  scrollRef,
  overlay,
}: Props) {
  const { colors, scheme } = useTheme();
  const body = scroll ? (
    <ScrollView
      ref={scrollRef}
      contentContainerStyle={styles.scrollContent}
      keyboardShouldPersistTaps="handled"
      showsVerticalScrollIndicator={false}
      refreshControl={
        onRefresh ? (
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.text}
            colors={[colors.text]}
            progressBackgroundColor={colors.surface}
          />
        ) : undefined
      }>
      <View style={styles.column}>{children}</View>
    </ScrollView>
  ) : (
    <View style={[styles.column, styles.fill]}>{children}</View>
  );

  return (
    <SafeAreaView edges={edges} style={[styles.fill, { backgroundColor: colors.background }]}>
      <Image source={BACKDROPS[scheme]} style={styles.backdrop} contentFit="cover" />
      <KeyboardAvoidingView style={styles.fill} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        {body}
        {footer ? <View style={[styles.column, styles.footer]}>{footer}</View> : null}
      </KeyboardAvoidingView>
      {overlay}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  backdrop: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, pointerEvents: 'none' },
  scrollContent: { flexGrow: 1, paddingVertical: SCREEN_TOP_PADDING },
  column: {
    width: '100%',
    maxWidth: MAX_CONTENT_WIDTH,
    alignSelf: 'center',
    paddingHorizontal: space.xl,
  },
  footer: { paddingTop: space.md, paddingBottom: space.lg, gap: space.sm },
});
