import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { ChoiceList, type Choice } from '@/components/ChoiceList';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { StepHeader } from '@/components/StepHeader';
import { useSession } from '@/stores/session';
import { space } from '@/theme/layout';

export const ONBOARDING_STEPS = 4;

type Props<T extends string | number> = {
  step: number;
  title: string;
  lede: string;
  /** Spoken name of the option group. */
  label: string;
  choices: Choice<T>[];
  value: T | null;
  onChange: (value: T) => void;
  onContinue: () => void;
  continueLabel?: string;
  loading?: boolean;
  error?: string | null;
};

/** One onboarding question with a single-select list and a Continue button in the thumb zone. */
export function PreferenceStep<T extends string | number>({
  step,
  title,
  lede,
  label,
  choices,
  value,
  onChange,
  onContinue,
  continueLabel = 'Continue',
  loading = false,
  error,
}: Props<T>) {
  return (
    <Screen
      footer={
        <>
          {error ? <Notice tone="error" message={error} /> : null}
          <Button label={continueLabel} onPress={onContinue} loading={loading} testID="onboarding-continue" />
        </>
      }>
      <StepHeader step={step} total={ONBOARDING_STEPS} canGoBack={step > 1} />
      <View style={styles.header}>
        <AppText variant="title" accessibilityRole="header">
          {title}
        </AppText>
        <AppText tone="muted">{lede}</AppText>
      </View>
      <ChoiceList label={label} choices={choices} value={value} onChange={onChange} />
    </Screen>
  );
}

/** Preferences already saved on the server (when the learner comes back to an earlier step). */
export function useSavedPreferences() {
  const me = useSession((s) => s.me);
  return me && me.onboardingStep !== 'PREFERENCES' ? me.profile : null;
}

const styles = StyleSheet.create({
  header: { gap: space.sm, marginBottom: space.xl },
});
