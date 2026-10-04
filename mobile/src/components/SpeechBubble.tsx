import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { KaiAvatar } from './KaiAvatar';

type Props = {
  children: ReactNode;
  avatarSize?: number;
  /** surface: white paper on the page. primary: a mango wash, for Luna's notes. */
  tone?: 'surface' | 'primary';
};

/** Luna saying something outside the chat: her face with a glass bubble pointing at it. */
export function SpeechBubble({ children, avatarSize = 44, tone = 'surface' }: Props) {
  const { colors } = useTheme();
  const background = tone === 'primary' ? colors.primarySoft : colors.glassStrong;
  return (
    <View style={styles.row}>
      <KaiAvatar size={avatarSize} />
      <View style={styles.bubbleWrap}>
        <View
          style={[
            styles.tail,
            { top: Math.max(space.md, avatarSize / 2 - TAIL / 2), backgroundColor: background },
          ]}
        />
        <View
          style={[
            styles.bubble,
            { backgroundColor: background, borderColor: colors.glassBorder },
            tone === 'surface' && { boxShadow: colors.cardShadow },
          ]}>
          {children}
        </View>
      </View>
    </View>
  );
}

const TAIL = 14;

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md },
  bubbleWrap: { flex: 1 },
  tail: {
    position: 'absolute',
    left: -TAIL / 2 + 1,
    width: TAIL,
    height: TAIL,
    borderRadius: 3,
    transform: [{ rotate: '45deg' }],
  },
  bubble: {
    borderWidth: 1,
    borderRadius: radius.lg,
    borderTopLeftRadius: radius.sm,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    gap: space.xs,
  },
});
