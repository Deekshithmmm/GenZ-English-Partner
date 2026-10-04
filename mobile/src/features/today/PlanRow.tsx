import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import type { PlanItem, PlanKind } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Icon } from '@/components/Icon';
import { Icon3D, type Icon3DName } from '@/components/Icon3D';
import { roleplayIcon3d } from '@/features/practice/catalogIcons';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { planHref } from './planActions';

const ICONS: Record<PlanKind, Icon3DName> = {
  WARM_UP: 'sun',
  MICRO_LESSON: 'book',
  ROLEPLAY: 'group',
  WORD_REVIEW: 'cards',
};

function iconFor(item: PlanItem): Icon3DName {
  const [kind, argument] = item.action.split(':');
  return kind === 'roleplay' ? roleplayIcon3d(argument) : ICONS[item.kind];
}

/** done: finished today. next: the step the Start button opens. open: can be done now. soon: not in the app yet. */
export type PlanState = 'done' | 'next' | 'open' | 'soon';

type Props = { item: PlanItem; state: PlanState; last: boolean };

/**
 * One step of today's plan on a timeline: a glass node with the step's 3D icon (a tick once done) and the step beside
 * it. The next step sits on a mango wash so the eye lands there first.
 */
export function PlanRow({ item, state, last }: Props) {
  const { colors } = useTheme();
  const href = planHref(item.action);
  const meta = state === 'done' ? 'Done' : state === 'soon' ? 'Soon' : `${item.minutes} min`;
  const label = [item.title, item.subtitle, `${item.minutes} minutes`, state === 'done' ? 'Done' : null, state === 'soon' ? 'Coming soon' : null]
    .filter(Boolean)
    .join('. ');

  const body = (
    <View
      style={[
        styles.body,
        { backgroundColor: state === 'next' ? colors.primarySoft : colors.glass, borderColor: colors.glassBorder },
        state === 'next' && { boxShadow: colors.cardShadow },
      ]}>
      <View style={styles.text}>
        {state === 'next' ? (
          <AppText variant="label" tone="muted">
            Up next
          </AppText>
        ) : null}
        <AppText variant={state === 'next' ? 'subheading' : 'bodyStrong'} tone={state === 'soon' ? 'muted' : 'default'}>
          {item.title}
        </AppText>
        <AppText variant="small" tone="muted">
          {item.subtitle}
        </AppText>
      </View>
      <View style={styles.meta}>
        {state === 'soon' ? (
          <View style={[styles.soonPill, { borderColor: colors.border }]}>
            <AppText variant="label" tone="muted">
              {meta}
            </AppText>
          </View>
        ) : (
          <AppText variant="label" tone={state === 'done' ? 'success' : state === 'next' ? 'default' : 'muted'}>
            {meta}
          </AppText>
        )}
        {href && state !== 'soon' ? <Icon name="chevron-forward" size={18} color={colors.textMuted} /> : null}
      </View>
    </View>
  );

  return (
    <View style={styles.row}>
      <View style={styles.rail}>
        <View
          style={[
            styles.node,
            {
              backgroundColor: state === 'done' ? colors.successBackground : colors.glassStrong,
              borderColor: state === 'next' ? colors.primary : colors.glassBorder,
              boxShadow: state === 'next' ? colors.raisedShadow : colors.cardShadow,
              opacity: state === 'soon' ? 0.6 : 1,
            },
          ]}>
          {state === 'done' ? (
            <Icon3D name="check" size={26} />
          ) : (
            <Icon3D name={iconFor(item)} size={state === 'next' ? 28 : 24} />
          )}
        </View>
        {last ? null : <View style={[styles.line, { backgroundColor: colors.hairline }]} />}
      </View>
      {href ? (
        <Pressable
          onPress={() => router.push(href)}
          accessibilityRole="button"
          accessibilityLabel={label}
          testID={`plan-${item.id}`}
          style={({ pressed }) => [styles.press, pressed && { opacity: 0.75, transform: [{ scale: 0.99 }] }]}>
          {body}
        </Pressable>
      ) : (
        <View style={styles.press} accessible accessibilityLabel={label} testID={`plan-${item.id}`}>
          {body}
        </View>
      )}
    </View>
  );
}

const NODE = 44;

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: space.md },
  rail: { width: NODE, alignItems: 'center' },
  node: {
    width: NODE,
    height: NODE,
    borderRadius: NODE / 2,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: space.sm,
  },
  line: { width: 2, flex: 1, minHeight: space.md, borderRadius: 1 },
  press: { flex: 1, paddingBottom: space.md },
  body: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    minHeight: 60,
    borderRadius: radius.lg,
    borderWidth: 1,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
  },
  text: { flex: 1, gap: 2 },
  meta: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  soonPill: { borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: space.sm, paddingVertical: 1 },
});
