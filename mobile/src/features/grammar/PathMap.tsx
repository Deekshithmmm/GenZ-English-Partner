import { useEffect, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import type { PathNode } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Icon3D } from '@/components/Icon3D';
import { Pressable3D } from '@/components/Pressable3D';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';
import { fonts } from '@/theme/typography';

import { KIND_LABEL, kindColorOf, kindIcon } from './labels';
import { NodePopover } from './NodePopover';

const NODE = 72;
const CURRENT_NODE = 84;
/** Vertical distance between two lessons on the road. */
export const PATH_ROW = 128;
/** The road winds: each lesson sits a little left or right of the middle, as a fraction of the width. */
const SWING = [0, 0.2, 0.28, 0.2, 0, -0.2, -0.28, -0.2];
const LABEL_GAP = 14;
const LABEL_WIDTH = 150;

/** Where the centre of the lesson at `index` (within its week) sits below the top of the map. */
export function nodeCenterY(index: number): number {
  return index * PATH_ROW + CURRENT_NODE / 2;
}

type Props = {
  /** One week's lessons. Their numbers place them on the road, so it winds on from the week before. */
  nodes: PathNode[];
  /** The lesson whose card is open, if it's in this week. */
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onStart: (node: PathNode) => void;
  /** The learner's next lesson anywhere on the path, for "finish lesson N first" on locked ones. */
  current: PathNode | null;
};

/**
 * One week of the grammar path as a winding road of 3D stepping stones, each labelled at the side. Finished lessons
 * show their stars, the next one glows violet under a START tag, and the ones after it are locked. Tapping a lesson
 * opens its card underneath, with the button that starts it.
 */
export function PathMap({ nodes, selectedId, onSelect, onStart, current }: Props) {
  const { colors } = useTheme();
  const [width, setWidth] = useState(0);
  const height = nodes.length * PATH_ROW + space.md;
  const centers = nodes.map((node, i) => ({ x: width / 2 + SWING[(node.number - 1) % SWING.length] * width, y: nodeCenterY(i) }));
  const reached = nodes.reduce((last, node, i) => (node.status === 'LOCKED' ? last : i), -1);

  const road = (upTo: number) =>
    centers
      .slice(0, upTo + 1)
      .map((p, i) => {
        if (i === 0) {
          return `M ${p.x} ${p.y}`;
        }
        const prev = centers[i - 1];
        return `C ${prev.x} ${prev.y + PATH_ROW * 0.55} ${p.x} ${p.y - PATH_ROW * 0.55} ${p.x} ${p.y}`;
      })
      .join(' ');

  const selectedIndex = nodes.findIndex((n) => n.id === selectedId);

  return (
    <View style={{ height }} onLayout={(e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width)}>
      {width > 0 ? (
        <>
          <Svg width={width} height={height} style={StyleSheet.absoluteFill}>
            <Path
              d={road(nodes.length - 1)}
              stroke={colors.border}
              strokeOpacity={0.45}
              strokeWidth={6}
              strokeDasharray="0.1 14"
              strokeLinecap="round"
              fill="none"
            />
            {reached > 0 ? (
              <Path
                d={road(reached)}
                stroke={colors.accent}
                strokeWidth={6}
                strokeDasharray="0.1 14"
                strokeLinecap="round"
                fill="none"
              />
            ) : null}
          </Svg>
          {nodes.map((node, i) => (
            <Stop
              key={node.id}
              node={node}
              x={centers[i].x}
              y={centers[i].y}
              width={width}
              selected={node.id === selectedId}
              onPress={() => onSelect(node.id === selectedId ? null : node.id)}
            />
          ))}
          {selectedIndex >= 0 ? (
            <NodePopover
              node={nodes[selectedIndex]}
              x={centers[selectedIndex].x}
              top={centers[selectedIndex].y + sizeOf(nodes[selectedIndex]) / 2 + space.md}
              width={width}
              current={current}
              onStart={() => onStart(nodes[selectedIndex])}
              onClose={() => onSelect(null)}
            />
          ) : null}
        </>
      ) : null}
    </View>
  );
}

function sizeOf(node: PathNode): number {
  return node.status === 'CURRENT' ? CURRENT_NODE : NODE;
}

function Stop({
  node,
  x,
  y,
  width,
  selected,
  onPress,
}: {
  node: PathNode;
  x: number;
  y: number;
  width: number;
  selected: boolean;
  onPress: () => void;
}) {
  const { colors } = useTheme();
  const current = node.status === 'CURRENT';
  const locked = node.status === 'LOCKED';
  const size = sizeOf(node);
  const material = {
    DONE: colors.mango3d,
    CURRENT: colors.violet3d,
    OPEN: colors.plain3d,
    LOCKED: colors.locked3d,
  }[node.status];
  const statusText = {
    DONE: `Done, ${node.stars} of 3 stars`,
    CURRENT: 'Your next lesson',
    OPEN: 'Open for review',
    LOCKED: 'Locked',
  }[node.status];
  const icon = locked ? 'lock' : kindIcon(node.kind, node.icon);
  const kindColor = locked ? colors.textMuted : kindColorOf(node.kind, colors);

  // The label goes on whichever side of the stone has more room.
  const onRight = x <= width / 2;
  const labelSpace = onRight ? width - (x + size / 2 + LABEL_GAP) : x - size / 2 - LABEL_GAP;
  const labelWidth = Math.max(0, Math.min(LABEL_WIDTH, labelSpace));
  const labelLeft = onRight ? x + size / 2 + LABEL_GAP : x - size / 2 - LABEL_GAP - labelWidth;

  return (
    <>
      {current ? <Glow size={size} x={x} y={y} /> : null}
      {current ? (
        <View
          style={[
            styles.tag,
            { left: x - 40, top: y - size / 2 - 30, backgroundColor: colors.glassStrong, borderColor: colors.glassBorder },
          ]}>
          <AppText variant="label" style={{ color: colors.accent }}>
            START
          </AppText>
        </View>
      ) : null}
      <Pressable3D
        material={material}
        onPress={onPress}
        radius={size / 2}
        depth={current ? 7 : 6}
        style={[styles.stop, { left: x - size / 2, top: y - size / 2 }]}
        accessibilityLabel={`Lesson ${node.number}, ${KIND_LABEL[node.kind]}: ${node.topicTitle}. ${statusText}.`}
        accessibilityHint={selected ? 'Closes the lesson card' : 'Opens the lesson card'}
        accessibilityState={{ expanded: selected }}
        testID={`node-${node.id}`}
        faceStyle={[
          styles.node,
          { width: size, height: size },
          selected && { borderWidth: 3, borderColor: colors.glassBorder },
        ]}>
        <Icon3D name={icon} size={current ? 46 : 38} style={locked ? styles.lockedIcon : null} />
      </Pressable3D>
      {labelWidth >= 72 ? (
        <View
          style={[styles.label, { left: labelLeft, width: labelWidth, top: y - 30, alignItems: onRight ? 'flex-start' : 'flex-end' }]}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants">
          <AppText variant="label" style={{ color: kindColor }}>
            {KIND_LABEL[node.kind]}
          </AppText>
          <AppText
            variant="small"
            numberOfLines={2}
            tone={locked ? 'muted' : 'default'}
            style={[styles.title, { textAlign: onRight ? 'left' : 'right' }]}>
            {node.topicTitle}
          </AppText>
          {node.status === 'DONE' ? (
            <View style={styles.stars}>
              {[1, 2, 3].map((n) => (
                <Icon3D key={n} name="star" size={15} style={{ opacity: n <= node.stars ? 1 : 0.25 }} />
              ))}
            </View>
          ) : null}
        </View>
      ) : null}
    </>
  );
}

/** A slow glow pulsing behind the next lesson. Still when reduced motion is on. */
function Glow({ size, x, y }: { size: number; x: number; y: number }) {
  const { colors } = useTheme();
  const reduceMotion = useReducedMotion();
  const pulse = useSharedValue(0);
  useEffect(() => {
    if (reduceMotion) {
      return;
    }
    pulse.set(withRepeat(withTiming(1, { duration: 1600, easing: Easing.inOut(Easing.quad) }), -1, true));
    return () => cancelAnimation(pulse);
  }, [pulse, reduceMotion]);
  const style = useAnimatedStyle(() => ({
    opacity: 0.35 + pulse.get() * 0.35,
    transform: [{ scale: 1.12 + pulse.get() * 0.12 }],
  }));
  return (
    <Animated.View
      style={[
        styles.glow,
        { width: size, height: size, borderRadius: size / 2, backgroundColor: colors.accent, left: x - size / 2, top: y - size / 2 },
        style,
      ]}
    />
  );
}

const styles = StyleSheet.create({
  stop: { position: 'absolute' },
  node: { alignItems: 'center', justifyContent: 'center' },
  lockedIcon: { opacity: 0.85 },
  glow: { position: 'absolute', pointerEvents: 'none' },
  tag: {
    position: 'absolute',
    width: 80,
    alignItems: 'center',
    zIndex: 2,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingVertical: 2,
    pointerEvents: 'none',
  },
  label: { position: 'absolute', gap: 1, pointerEvents: 'none' },
  title: { fontFamily: fonts.bodyBold },
  stars: { flexDirection: 'row', gap: 2, marginTop: 2 },
});
