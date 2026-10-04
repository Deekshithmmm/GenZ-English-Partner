import { StyleSheet, View } from 'react-native';

import type { PathNode } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Icon3D } from '@/components/Icon3D';
import { IconButton } from '@/components/IconButton';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { KIND_LABEL, kindColorOf } from './labels';

const WIDTH = 300;
const ARROW = 16;

type Props = {
  node: PathNode;
  /** The lesson's centre, so the card's arrow points at it. */
  x: number;
  top: number;
  /** The map's width; the card stays inside it. */
  width: number;
  current: PathNode | null;
  onStart: () => void;
  onClose: () => void;
};

/**
 * The card that opens under a lesson on the path: what it is, how it went last time and the button that starts it.
 * A locked lesson says which one to finish first instead.
 */
export function NodePopover({ node, x, top, width, current, onStart, onClose }: Props) {
  const { colors } = useTheme();
  const cardWidth = Math.min(WIDTH, width);
  const left = Math.max(0, Math.min(width - cardWidth, x - cardWidth / 2));
  const arrowLeft = Math.max(radius.lg, Math.min(cardWidth - radius.lg - ARROW, x - left - ARROW / 2));
  const locked = node.status === 'LOCKED';

  return (
    <View style={[styles.wrap, { left, top, width: cardWidth }]} accessibilityLiveRegion="polite" testID="node-card">
      {/* Opaque, so the lesson labels underneath don't show through. */}
      <View
        style={[styles.arrow, { left: arrowLeft, backgroundColor: colors.surface, borderColor: colors.glassBorder }]}
      />
      <View
        style={[
          styles.card,
          { backgroundColor: colors.surface, borderColor: colors.glassBorder, boxShadow: colors.raisedShadow },
        ]}>
        <View style={styles.top}>
          <View style={styles.flex}>
            <AppText variant="label" style={{ color: kindColorOf(node.kind, colors) }}>
              {KIND_LABEL[node.kind].toUpperCase()} · LESSON {node.number}
            </AppText>
            <AppText variant="subheading" accessibilityRole="header">
              {node.topicTitle}
            </AppText>
          </View>
          <View style={styles.close}>
            <IconButton icon="close" label="Close the lesson card" onPress={onClose} size={20} />
          </View>
        </View>
        <AppText variant="small" tone="muted">
          {
            {
              LEARN: node.subtitle,
              BOOST: `Speaking practice: ${lowerFirst(node.subtitle)}`,
              REVIEW: `Practise it again, with new questions: ${lowerFirst(node.subtitle)}`,
              MASTER: `A harder round, with more speaking: ${lowerFirst(node.subtitle)}`,
            }[node.kind]
          }
        </AppText>
        {node.status === 'DONE' ? (
          <View style={styles.stars} accessible accessibilityLabel={`Best so far: ${node.stars} of 3 stars`}>
            {[1, 2, 3].map((n) => (
              <Icon3D key={n} name="star" size={22} style={{ opacity: n <= node.stars ? 1 : 0.25 }} />
            ))}
            <AppText variant="label" tone="muted">
              Best so far
            </AppText>
          </View>
        ) : null}
        {locked ? (
          <View style={[styles.locked, { backgroundColor: colors.surfaceMuted }]}>
            <Icon3D name="lock" size={22} />
            <AppText variant="small" style={styles.flex}>
              {current
                ? `Finish lesson ${current.number}, "${current.topicTitle}", to get here.`
                : 'Finish the lessons before it first.'}
            </AppText>
          </View>
        ) : (
          <Button
            label={node.status === 'DONE' ? 'Practise again' : 'Start'}
            variant={node.status === 'CURRENT' ? 'primary' : 'secondary'}
            icon={node.status === 'DONE' ? 'refresh' : 'play'}
            onPress={onStart}
            testID="node-start"
          />
        )}
      </View>
    </View>
  );
}

function lowerFirst(text: string): string {
  return text ? text[0].toLowerCase() + text.slice(1) : text;
}

const styles = StyleSheet.create({
  wrap: { position: 'absolute', zIndex: 10, paddingTop: ARROW / 2 },
  arrow: {
    position: 'absolute',
    top: 0,
    width: ARROW,
    height: ARROW,
    borderTopWidth: 1,
    borderLeftWidth: 1,
    borderRadius: 3,
    transform: [{ rotate: '45deg' }],
    zIndex: 1,
  },
  card: { borderRadius: radius.lg + 4, borderWidth: 1, padding: space.lg, paddingTop: space.md, gap: space.sm },
  top: { flexDirection: 'row', alignItems: 'flex-start', gap: space.xs },
  close: { marginRight: -space.md, marginTop: -space.xs },
  flex: { flex: 1 },
  stars: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  locked: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    borderRadius: radius.md,
    padding: space.md,
  },
});
