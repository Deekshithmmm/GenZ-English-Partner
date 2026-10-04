import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import type { Me } from '@/api/types';

const REFRESH_TOKEN_KEY = 'genz.refreshToken';
const CACHED_ME_KEY = 'genz.me';

/**
 * The refresh token lives in the OS keystore (localStorage on web). The access token is kept in memory only.
 * A copy of the learner's profile is cached so the app can open offline.
 */
export async function readRefreshToken(): Promise<string | null> {
  if (Platform.OS === 'web') {
    return safeLocalStorage()?.getItem(REFRESH_TOKEN_KEY) ?? null;
  }
  return SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
}

export async function writeRefreshToken(token: string): Promise<void> {
  if (Platform.OS === 'web') {
    safeLocalStorage()?.setItem(REFRESH_TOKEN_KEY, token);
    return;
  }
  await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token);
}

export async function readCachedMe(): Promise<Me | null> {
  try {
    const raw = await AsyncStorage.getItem(CACHED_ME_KEY);
    return raw ? (JSON.parse(raw) as Me) : null;
  } catch {
    return null;
  }
}

export async function writeCachedMe(me: Me): Promise<void> {
  await AsyncStorage.setItem(CACHED_ME_KEY, JSON.stringify(me)).catch(() => undefined);
}

export async function clearStoredSession(): Promise<void> {
  if (Platform.OS === 'web') {
    safeLocalStorage()?.removeItem(REFRESH_TOKEN_KEY);
  } else {
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY).catch(() => undefined);
  }
  await AsyncStorage.removeItem(CACHED_ME_KEY).catch(() => undefined);
}

function safeLocalStorage(): Storage | null {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null;
  } catch {
    return null;
  }
}
