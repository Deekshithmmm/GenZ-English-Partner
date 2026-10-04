import { create } from 'zustand';

import type { Me } from '@/api/types';

/**
 * - loading: reading the stored session at launch
 * - signedIn / signedOut: as you'd expect
 * - unreachable: we have a stored session but no cached profile and the server can't be reached yet
 */
export type SessionStatus = 'loading' | 'signedOut' | 'signedIn' | 'unreachable';

type SessionState = {
  status: SessionStatus;
  me: Me | null;
  accessToken: string | null;
  accessTokenExpiresAt: number | null;
  refreshToken: string | null;
};

export const useSession = create<SessionState>(() => ({
  status: 'loading',
  me: null,
  accessToken: null,
  accessTokenExpiresAt: null,
  refreshToken: null,
}));

export function isOnboarded(me: Me | null): boolean {
  return me?.onboardingStep === 'DONE';
}
