import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon, type IconName } from '@/components/Icon';
import { Screen } from '@/components/Screen';
import { Segmented } from '@/components/Segmented';
import { SpeechBubble } from '@/components/SpeechBubble';
import { StepHeader } from '@/components/StepHeader';
import { ONBOARDING_STEPS } from '@/features/onboarding/PreferenceStep';
import { MIC_OPTIONS, micModeHint } from '@/features/settings/options';
import { usePrefs } from '@/stores/prefs';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

export default function MeetKai() {
  const micMode = usePrefs((s) => s.micMode);
  const setMicMode = usePrefs((s) => s.setMicMode);

  return (
    <Screen
      footer={
        <>
          <Button
            label="Start the chat"
            icon="mic"
            onPress={() => router.push('/onboarding/placement')}
            testID="placement-start"
          />
          <Button
            label="I'd rather pick my level"
            variant="ghost"
            onPress={() => router.push('/onboarding/choose-level')}
            testID="placement-skip"
          />
        </>
      }>
      <StepHeader step={ONBOARDING_STEPS} total={ONBOARDING_STEPS} />
      <View style={styles.header}>
        <AppText variant="title" accessibilityRole="header">
          Last step: a quick chat with Luna
        </AppText>
      </View>

      <View style={styles.kai}>
        <SpeechBubble avatarSize={56}>
          <AppText variant="bodyStrong">
            Hi, I&apos;m Luna! I&apos;ll ask you four short questions to find your speaking level.
          </AppText>
          <AppText tone="muted">
            Answer out loud, the way you&apos;d talk to a friend. It takes about two minutes, and there are no wrong
            answers.
          </AppText>
        </SpeechBubble>
      </View>

      <Card style={styles.tips}>
        <Tip icon="volume-mute-outline" text="Find a quiet spot if you can." />
        <Tip icon="chatbubbles-outline" text="Say as much as you can. Longer answers help Luna place you right." />
        <Tip icon="create-outline" text="Can't talk right now? You can type your answers instead." />
      </Card>

      <Card style={styles.micSetting}>
        <AppText variant="bodyStrong">How do you want to use the mic?</AppText>
        <Segmented label="Mic mode" options={MIC_OPTIONS} value={micMode} onChange={setMicMode} />
        <AppText variant="small" tone="muted">
          {micModeHint(micMode)}
        </AppText>
      </Card>

      <AppText variant="small" tone="muted">
        Your recordings are deleted as soon as they&apos;re scored.
      </AppText>
    </Screen>
  );
}

function Tip({ icon, text }: { icon: IconName; text: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.tip}>
      <View style={[styles.tipIcon, { backgroundColor: colors.primarySoft }]}>
        <Icon name={icon} size={20} color={colors.text} />
      </View>
      <AppText style={styles.tipText}>{text}</AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { gap: space.sm, marginBottom: space.lg },
  kai: { marginBottom: space.xl },
  tips: { gap: space.md, marginBottom: space.lg },
  tip: { flexDirection: 'row', gap: space.md, alignItems: 'center' },
  tipIcon: { width: 40, height: 40, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  tipText: { flex: 1 },
  micSetting: { gap: space.sm, marginBottom: space.xl },
});
