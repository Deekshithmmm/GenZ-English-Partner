import { Platform } from 'react-native';

import { GOOGLE_IOS_CLIENT_ID, GOOGLE_WEB_CLIENT_ID } from '@/api/config';
import { ApiError } from '@/api/errors';
import type { AuthProviders } from '@/api/types';

/** Local development identity used when the backend runs with GOOGLE_AUTH_PROVIDER=mock. */
const MOCK_ID_TOKEN = 'mock-google:google.tester@example.com:Google Tester';

export class GoogleSignInCancelled extends Error {}

type GoogleModule = typeof import('@react-native-google-signin/google-signin');

let configured = false;

/**
 * The native module only exists in development/production builds, not Expo Go or the web build, so it's loaded
 * lazily and its absence becomes a friendly message instead of a crash.
 */
function nativeGoogle(): GoogleModule | null {
  if (Platform.OS === 'web') {
    return null;
  }
  try {
    return require('@react-native-google-signin/google-signin') as GoogleModule;
  } catch {
    return null;
  }
}

/** Returns a Google ID token for the backend to verify. */
export async function getGoogleIdToken(provider: AuthProviders['google']): Promise<string> {
  if (provider === 'mock') {
    return MOCK_ID_TOKEN;
  }
  const google = nativeGoogle();
  if (provider !== 'google' || !google || !GOOGLE_WEB_CLIENT_ID) {
    throw new ApiError(
      0,
      'google_unavailable',
      "Google sign-in isn't available in this version of the app. Use your email instead.",
    );
  }
  const { GoogleSignin, isErrorWithCode, isSuccessResponse, statusCodes } = google;
  if (!configured) {
    GoogleSignin.configure({
      webClientId: GOOGLE_WEB_CLIENT_ID,
      iosClientId: GOOGLE_IOS_CLIENT_ID || undefined,
    });
    configured = true;
  }
  try {
    await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
    const response = await GoogleSignin.signIn();
    if (!isSuccessResponse(response)) {
      throw new GoogleSignInCancelled();
    }
    const idToken = response.data.idToken;
    if (!idToken) {
      throw new ApiError(0, 'google_no_token', "Google didn't return your account details. Try again.");
    }
    return idToken;
  } catch (error) {
    if (error instanceof GoogleSignInCancelled || error instanceof ApiError) {
      throw error;
    }
    if (isErrorWithCode(error)) {
      if (error.code === statusCodes.SIGN_IN_CANCELLED) {
        throw new GoogleSignInCancelled();
      }
      if (error.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        throw new ApiError(0, 'play_services', 'Google Play services is missing or out of date on this phone.');
      }
    }
    throw new ApiError(0, 'google_failed', "Google sign-in didn't go through. Try again, or use your email.");
  }
}

export async function signOutOfGoogle(): Promise<void> {
  const google = nativeGoogle();
  if (!google || !configured) {
    return;
  }
  await google.GoogleSignin.signOut().catch(() => undefined);
}
