import { useMutation } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { deleteAccount, signOut } from '@/auth/session';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { Icon } from '@/components/Icon';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { Segmented } from '@/components/Segmented';
import { goalTitle } from '@/features/onboarding/options';
import { APPEARANCE_OPTIONS, MIC_OPTIONS, micModeHint } from '@/features/settings/options';
import { languageInfo } from '@/i18n/languages';
import { usePrefs } from '@/stores/prefs';
import { useSession } from '@/stores/session';
import { useLanguageFont } from '@/theme/fonts';
import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

export default function MeScreen() {
  const { colors } = useTheme();
  const me = useSession((s) => s.me);
  const appearance = usePrefs((s) => s.appearance);
  const setAppearance = usePrefs((s) => s.setAppearance);
  const micMode = usePrefs((s) => s.micMode);
  const setMicMode = usePrefs((s) => s.setMicMode);
  const languageFont = useLanguageFont(me?.profile.explanationLanguage);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const signingOut = useMutation({ mutationFn: signOut });
  const deleting = useMutation({ mutationFn: deleteAccount });

  if (!me) {
    return null;
  }
  const { profile } = me;
  const language = languageInfo(profile.explanationLanguage);

  return (
    <Screen edges={['top']}>
      <View style={styles.header}>
        <View
          style={[styles.initials, { backgroundColor: colors.primary, borderColor: colors.onPrimary }]}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants">
          <AppText variant="heading" tone="onPrimary">
            {initials(me.displayName)}
          </AppText>
        </View>
        <View style={styles.headerText}>
          <AppText variant="title" accessibilityRole="header">
            {me.displayName}
          </AppText>
          <AppText tone="muted">{me.email}</AppText>
        </View>
      </View>

      {profile.level ? (
        <Card tone="primary" style={styles.levelCard}>
          <AppText variant="label" tone="muted">
            Your speaking level
          </AppText>
          <View style={styles.levelRow}>
            <AppText variant="title">{profile.level.code}</AppText>
            <AppText variant="subheading">{profile.level.label}</AppText>
          </View>
          <AppText>{profile.level.description}</AppText>
          <AppText variant="small" tone="muted">
            {profile.levelSource === 'PLACEMENT' ? 'From your placement chat with Luna.' : 'You picked this level.'}
          </AppText>
        </Card>
      ) : null}

      <Section title="Learning">
        <Card padding={0} style={styles.infoCard}>
          <InfoRow label="Practising for" value={goalTitle(profile.goal)} />
          <InfoRow label="Daily time" value={`${profile.dailyMinutes ?? 10} minutes`} />
          <InfoRow
            label="Explanations in"
            value={language.code === 'ENGLISH' ? 'English' : language.native}
            valueFont={language.code === 'ENGLISH' ? null : languageFont}
            accessibilityValue={language.english}
            last
          />
        </Card>
      </Section>

      <Section title="App">
        <Card style={styles.settings}>
          <View style={styles.setting}>
            <AppText variant="bodyStrong">Appearance</AppText>
            <Segmented label="Appearance" options={APPEARANCE_OPTIONS} value={appearance} onChange={setAppearance} />
          </View>
          <View style={styles.setting}>
            <AppText variant="bodyStrong">Mic</AppText>
            <Segmented label="Mic mode" options={MIC_OPTIONS} value={micMode} onChange={setMicMode} />
            <AppText variant="small" tone="muted">
              {micModeHint(micMode)}
            </AppText>
          </View>
        </Card>
      </Section>

      <Section title="Privacy">
        <Card tone="muted" style={styles.privacy}>
          <Icon name="shield-checkmark-outline" color={colors.text} />
          <AppText style={styles.privacyText}>
            Your voice recordings are deleted as soon as they&apos;re scored. We keep the text of your chats so you
            can see your progress, and deleting your account removes all of it.
          </AppText>
        </Card>
      </Section>

      <View style={styles.actions}>
        {signingOut.isError ? <Notice tone="error" message={signingOut.error.message} /> : null}
        <Button
          label="Sign out"
          variant="secondary"
          onPress={() => signingOut.mutate()}
          loading={signingOut.isPending}
          testID="sign-out"
        />
        {confirmingDelete ? (
          <View
            style={[styles.confirm, { borderColor: colors.error, backgroundColor: colors.errorBackground }]}
            accessibilityRole="alert">
            <AppText variant="bodyStrong">Delete your account?</AppText>
            <AppText>
              This permanently removes your profile, chats and progress. It can&apos;t be undone.
            </AppText>
            {deleting.isError ? <Notice tone="error" message={deleting.error.message} /> : null}
            <Button
              label="Yes, delete everything"
              variant="danger"
              onPress={() => deleting.mutate()}
              loading={deleting.isPending}
              testID="delete-confirm"
            />
            <Button label="Keep my account" variant="ghost" onPress={() => setConfirmingDelete(false)} />
          </View>
        ) : (
          <Button
            label="Delete my account"
            variant="danger"
            onPress={() => setConfirmingDelete(true)}
            testID="delete-account"
          />
        )}
      </View>
    </Screen>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <AppText variant="subheading" accessibilityRole="header">
        {title}
      </AppText>
      {children}
    </View>
  );
}

/** "Meera Iyer" -> "MI"; one name gives one letter. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return parts
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

type InfoRowProps = {
  label: string;
  value: string;
  valueFont?: string | null;
  /** What a screen reader says instead of a native-script value. */
  accessibilityValue?: string;
  last?: boolean;
};

function InfoRow({ label, value, valueFont, accessibilityValue, last = false }: InfoRowProps) {
  const { colors } = useTheme();
  return (
    <View
      style={[
        styles.infoRow,
        { borderBottomColor: colors.hairline, borderBottomWidth: last ? 0 : StyleSheet.hairlineWidth },
      ]}
      accessible
      accessibilityLabel={`${label}: ${accessibilityValue ?? value}`}>
      <AppText tone="muted">{label}</AppText>
      <AppText variant="bodyStrong" fontFamily={valueFont}>
        {value}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', gap: space.lg, marginBottom: space.xl },
  headerText: { flex: 1, gap: 2 },
  initials: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  levelCard: { marginBottom: space.xxl, gap: space.xs },
  levelRow: { flexDirection: 'row', alignItems: 'baseline', gap: space.sm },
  infoCard: { paddingHorizontal: space.lg, gap: 0 },
  settings: { gap: space.lg },
  privacy: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md },
  privacyText: { flex: 1 },
  section: { gap: space.md, marginBottom: space.xxl },
  setting: { gap: space.sm },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: space.md,
    minHeight: 52,
  },
  actions: { gap: space.md, marginBottom: space.xl },
  confirm: { borderWidth: 1.5, borderRadius: radius.md, padding: space.lg, gap: space.sm },
});
