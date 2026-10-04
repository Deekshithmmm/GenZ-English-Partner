import { applyMe, endSession, refreshSession } from '@/api/client';
import { authApi, meApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { queryClient } from '@/api/queryClient';
import type { OnboardingStep, Profile } from '@/api/types';
import { useSession } from '@/stores/session';

import { signOutOfGoogle } from './google';
import { readCachedMe, readRefreshToken } from './tokenStorage';

/**
 * Restores the session at launch. With a cached profile the app opens immediately (even offline) and refreshes
 * the token in the background; without one we wait for the server.
 */
export async function hydrateSession(): Promise<void> {
  const [refreshToken, cachedMe] = await Promise.all([readRefreshToken(), readCachedMe()]);
  if (!refreshToken) {
    useSession.setState({ status: 'signedOut' });
    return;
  }
  useSession.setState({ refreshToken });
  if (cachedMe) {
    useSession.setState({ status: 'signedIn', me: cachedMe });
    refreshSession().catch(() => undefined);
    return;
  }
  await retryHydration();
}

/** Used by the "can't reach the server" screen. */
export async function retryHydration(): Promise<void> {
  try {
    const ok = await refreshSession();
    if (!ok) {
      useSession.setState({ status: 'signedOut' });
    }
  } catch {
    useSession.setState({ status: 'unreachable' });
  }
}

export async function reloadMe(): Promise<void> {
  const me = await meApi.get();
  await applyMe(me);
}

/**
 * Applies a profile the server just returned, without another round trip for /me. Moving to the next onboarding
 * step flips the route guards in the root layout, which is what moves the learner on.
 */
export async function applyProfile(profile: Profile): Promise<void> {
  const me = useSession.getState().me;
  if (!me) {
    return;
  }
  await applyMe({ ...me, profile, onboardingStep: onboardingStepFor(profile) });
}

/** Mirrors MeResponse.of on the server: preferences first, then a level. */
function onboardingStepFor(profile: Profile): OnboardingStep {
  if (profile.goal == null || profile.dailyMinutes == null) {
    return 'PREFERENCES';
  }
  return profile.level == null ? 'PLACEMENT' : 'DONE';
}

export async function signOut(): Promise<void> {
  const refreshToken = useSession.getState().refreshToken;
  if (refreshToken) {
    // Best effort: sign out locally even if the server can't be reached.
    await authApi.logout(refreshToken).catch((error: unknown) => {
      if (!(error instanceof ApiError)) {
        throw error;
      }
    });
  }
  await signOutOfGoogle();
  queryClient.clear();
  await endSession();
}

export async function deleteAccount(): Promise<void> {
  await meApi.deleteAccount();
  await signOutOfGoogle();
  queryClient.clear();
  await endSession();
}
