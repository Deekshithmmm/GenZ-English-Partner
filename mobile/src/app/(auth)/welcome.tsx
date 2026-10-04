import { router } from 'expo-router';
import { StyleSheet, useWindowDimensions, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Highlight } from '@/components/Highlight';
import { Icon3D, type Icon3DName } from '@/components/Icon3D';
import { Kai3D } from '@/components/Kai3D';
import { Screen } from '@/components/Screen';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

const PERKS: { icon: Icon3DName; label: string }[] = [
  { icon: 'mic', label: 'Speak, not type' },
  { icon: 'bulb', label: 'Kind corrections' },
  { icon: 'fire', label: 'Daily streaks' },
];

export default function Welcome() {
  const { colors } = useTheme();
  const { height } = useWindowDimensions();
  const kaiSize = Math.round(Math.min(300, Math.max(190, height * 0.32)));
  return (
    <Screen
      footer={
        <>
          <Button label="Create my account" onPress={() => router.push('/sign-up')} testID="welcome-sign-up" />
          <Button
            label="I already have an account"
            variant="secondary"
            onPress={() => router.push('/sign-in')}
            testID="welcome-sign-in"
          />
        </>
      }>
      <View style={styles.brand}>
        <Icon3D name="sparkles" size={22} />
        <AppText variant="label" tone="muted">
          GenZ English Partner
        </AppText>
      </View>

      {/* Luna stands behind the card, so her shoulders disappear into it. */}
      <View style={styles.stage}>
        <Kai3D mood="idle" size={kaiSize} />
      </View>
      <View
        style={[
          styles.card,
          { backgroundColor: colors.surface, borderColor: colors.glassBorder, boxShadow: colors.raisedShadow },
        ]}>
        <AppText variant="label" tone="muted">
          Meet Luna, your AI speaking partner
        </AppText>
        <View>
          <AppText variant="display" accessibilityRole="header" style={styles.headline}>
            You know English.
          </AppText>
          <Highlight delayMs={400}>
            <AppText variant="display" style={[styles.headline, { color: colors.onHighlight }]}>
              Now say it.
            </AppText>
          </Highlight>
        </View>
        <AppText tone="muted">
          A few minutes a day of real conversation, with gentle notes on the side. No judging, just more confidence
          every time you speak.
        </AppText>
      </View>

      <View style={styles.perks}>
        {PERKS.map((perk) => (
          <View
            key={perk.label}
            style={[styles.perk, { backgroundColor: colors.glass, borderColor: colors.glassBorder }]}>
            <Icon3D name={perk.icon} size={26} />
            <AppText variant="label">{perk.label}</AppText>
          </View>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  brand: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  stage: { alignItems: 'center', marginTop: space.sm, marginBottom: -space.xxxl - space.lg },
  card: {
    borderRadius: radius.lg + 8,
    borderWidth: 1,
    padding: space.xl,
    gap: space.md,
  },
  headline: { fontSize: 40, lineHeight: 46 },
  perks: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm, marginTop: space.lg, justifyContent: 'center' },
  perk: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingLeft: space.sm,
    paddingRight: space.md,
    paddingVertical: space.xs,
  },
});
