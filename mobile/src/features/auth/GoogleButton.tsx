import { useMutation, useQuery } from '@tanstack/react-query';
import { StyleSheet, View } from 'react-native';

import { applyAuth } from '@/api/client';
import { authApi } from '@/api/endpoints';
import { queryKeys } from '@/api/queryClient';
import { getGoogleIdToken, GoogleSignInCancelled } from '@/auth/google';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { useTheme } from '@/theme/ThemeProvider';
import { space } from '@/theme/layout';

type Props = { onError: (message: string | null) => void };

/** "Continue with Google", shown only when the server has Google sign-in turned on. */
export function GoogleButton({ onError }: Props) {
  const { colors } = useTheme();
  const providers = useQuery({ queryKey: queryKeys.providers, queryFn: authApi.providers, staleTime: Infinity });
  const google = providers.data?.google;

  const signIn = useMutation({
    mutationFn: async () => {
      const idToken = await getGoogleIdToken(google ?? 'disabled');
      return authApi.google(idToken);
    },
    onMutate: () => onError(null),
    onSuccess: applyAuth,
    onError: (error) => {
      if (!(error instanceof GoogleSignInCancelled)) {
        onError(error.message);
      }
    },
  });

  if (!google || google === 'disabled') {
    return null;
  }
  return (
    <View style={styles.wrap}>
      <View style={styles.dividerRow}>
        <View style={[styles.line, { backgroundColor: colors.border }]} />
        <AppText variant="label" tone="muted">
          or
        </AppText>
        <View style={[styles.line, { backgroundColor: colors.border }]} />
      </View>
      <Button
        label="Continue with Google"
        icon="logo-google"
        variant="secondary"
        loading={signIn.isPending}
        onPress={() => signIn.mutate()}
        testID="google-sign-in"
      />
      {google === 'mock' ? (
        <AppText variant="small" tone="muted">
          Developer mode: signs in as a test Google account.
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: space.md },
  dividerRow: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  line: { flex: 1, height: 1 },
});
