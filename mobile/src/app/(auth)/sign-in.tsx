import { useMutation } from '@tanstack/react-query';
import { Link } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import { applyAuth } from '@/api/client';
import { authApi } from '@/api/endpoints';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { KaiAvatar } from '@/components/KaiAvatar';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { TextField } from '@/components/TextField';
import { GoogleButton } from '@/features/auth/GoogleButton';
import { emailError } from '@/features/auth/validation';
import { space, TOUCH_TARGET } from '@/theme/layout';

export default function SignIn() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string | null; password?: string | null }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const passwordRef = useRef<TextInput>(null);

  const login = useMutation({
    mutationFn: authApi.login,
    onSuccess: applyAuth,
    onError: (error) => setFormError(error.message),
  });

  const submit = () => {
    const next = { email: emailError(email), password: password ? null : 'Enter your password.' };
    setErrors(next);
    setFormError(null);
    if (next.email || next.password) {
      return;
    }
    login.mutate({ email: email.trim(), password });
  };

  return (
    <Screen
      footer={
        <>
          <Button label="Sign in" onPress={submit} loading={login.isPending} testID="sign-in-submit" />
          <View style={styles.switchRow}>
            <AppText variant="small" tone="muted">
              New here?
            </AppText>
            <Link href="/sign-up" replace style={styles.link}>
              <AppText variant="bodyStrong">Create an account</AppText>
            </Link>
          </View>
        </>
      }>
      <View style={styles.header}>
        <KaiAvatar size={48} />
        <AppText variant="title" accessibilityRole="header">
          Welcome back
        </AppText>
        <AppText tone="muted">Luna saved your spot.</AppText>
      </View>
      <View style={styles.form}>
        <TextField
          label="Email"
          value={email}
          onChangeText={setEmail}
          error={errors.email}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          textContentType="emailAddress"
          returnKeyType="next"
          onSubmitEditing={() => passwordRef.current?.focus()}
          testID="sign-in-email"
        />
        <TextField
          ref={passwordRef}
          label="Password"
          value={password}
          onChangeText={setPassword}
          error={errors.password}
          secure
          autoComplete="current-password"
          textContentType="password"
          returnKeyType="go"
          onSubmitEditing={submit}
          testID="sign-in-password"
        />
        {formError ? <Notice tone="error" message={formError} /> : null}
        <GoogleButton onError={setFormError} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { gap: space.sm, marginBottom: space.xl },
  form: { gap: space.lg },
  switchRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.sm },
  link: { minHeight: TOUCH_TARGET, paddingHorizontal: space.sm, justifyContent: 'center', display: 'flex', alignItems: 'center' },
});
