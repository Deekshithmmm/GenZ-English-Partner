import { Redirect } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { retryHydration, signOut } from '@/auth/session';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { isOnboarded, useSession } from '@/stores/session';
import { space } from '@/theme/layout';

/** Sends the learner to the right place for their session and onboarding state. */
export default function Index() {
  const status = useSession((s) => s.status);
  const me = useSession((s) => s.me);

  if (status === 'unreachable') {
    return <Unreachable />;
  }
  if (status !== 'signedIn' || !me) {
    return <Redirect href="/welcome" />;
  }
  if (!isOnboarded(me)) {
    return <Redirect href="/onboarding" />;
  }
  return <Redirect href="/today" />;
}

function Unreachable() {
  const [retrying, setRetrying] = useState(false);
  return (
    <Screen
      footer={
        <>
          <Button
            label="Try again"
            loading={retrying}
            onPress={async () => {
              setRetrying(true);
              await retryHydration();
              setRetrying(false);
            }}
          />
          <Button label="Sign out" variant="ghost" onPress={() => signOut()} />
        </>
      }>
      <View style={styles.body}>
        <AppText variant="title" accessibilityRole="header">
          We can&apos;t reach the server
        </AppText>
        <AppText tone="muted">
          Check that you&apos;re online, then try again. Your account and progress are safe.
        </AppText>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: space.md, paddingTop: space.xxxl },
});
