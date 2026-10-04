import { useMutation } from '@tanstack/react-query';
import { Link } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import { applyAuth } from '@/api/client';
import { authApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { KaiAvatar } from '@/components/KaiAvatar';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { TextField } from '@/components/TextField';
import { GoogleButton } from '@/features/auth/GoogleButton';
import { emailError, nameError, newPasswordError } from '@/features/auth/validation';
import { space, TOUCH_TARGET } from '@/theme/layout';

type Errors = { displayName?: string | null; email?: string | null; password?: string | null };

export default function SignUp() {
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const emailRef = useRef<TextInput>(null);
  const passwordRef = useRef<TextInput>(null);

  const register = useMutation({
    mutationFn: authApi.register,
    onSuccess: applyAuth,
    onError: (error) => {
      if (error instanceof ApiError && Object.keys(error.fields).length > 0) {
        setErrors(error.fields);
      } else {
        setFormError(error.message);
      }
    },
  });

  const submit = () => {
    const next: Errors = {
      displayName: nameError(displayName),
      email: emailError(email),
      password: newPasswordError(password),
    };
    setErrors(next);
    setFormError(null);
    if (next.displayName || next.email || next.password) {
      return;
    }
    register.mutate({ displayName: displayName.trim(), email: email.trim(), password });
  };

  return (
    <Screen
      footer={
        <>
          <Button label="Create account" onPress={submit} loading={register.isPending} testID="sign-up-submit" />
          <View style={styles.switchRow}>
            <AppText variant="small" tone="muted">
              Already have an account?
            </AppText>
            <Link href="/sign-in" replace style={styles.link}>
              <AppText variant="bodyStrong">Sign in</AppText>
            </Link>
          </View>
        </>
      }>
      <View style={styles.header}>
        <KaiAvatar size={48} />
        <AppText variant="title" accessibilityRole="header">
          Create your account
        </AppText>
        <AppText tone="muted">Two minutes to set up, then you&apos;re talking.</AppText>
      </View>

      <View style={styles.form}>
        <TextField
          label="What should Luna call you?"
          value={displayName}
          onChangeText={setDisplayName}
          error={errors.displayName}
          autoComplete="given-name"
          textContentType="givenName"
          returnKeyType="next"
          onSubmitEditing={() => emailRef.current?.focus()}
          testID="sign-up-name"
        />
        <TextField
          ref={emailRef}
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
          testID="sign-up-email"
        />
        <TextField
          ref={passwordRef}
          label="Password"
          value={password}
          onChangeText={setPassword}
          error={errors.password}
          secure
          autoComplete="new-password"
          textContentType="newPassword"
          returnKeyType="go"
          onSubmitEditing={submit}
          placeholder="At least 8 characters"
          testID="sign-up-password"
        />
        {formError ? <Notice tone="error" message={formError} /> : null}
        <GoogleButton onError={setFormError} />
        <AppText variant="small" tone="muted">
          Your voice recordings are deleted after scoring. We keep transcripts so you can see your progress, and you
          can delete everything any time.
        </AppText>
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
