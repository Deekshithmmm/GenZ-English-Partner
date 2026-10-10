import Constants from 'expo-constants';
import { Platform } from 'react-native';

const API_PORT = 8080;

/**
 * Where the backend lives. EXPO_PUBLIC_API_URL wins; otherwise, in development we reuse the host the JS bundle was
 * served from (so a phone on the same Wi-Fi reaches your laptop), with emulator/simulator fallbacks.
 */
function resolveApiUrl(): string {
  const explicit = process.env.EXPO_PUBLIC_API_URL;
  if (explicit) {
    return explicit.replace(/\/+$/, '');
  }
  if (Platform.OS === 'web') {
    if (typeof window === 'undefined') {
      return `http://localhost:${API_PORT}`;
    }
    // An https page can't call plain http, so over https the API is reached through the page's own address (a phone
    // uses https for the mic; tools/phone.mjs passes /api on to the backend).
    if (window.location.protocol === 'https:') {
      return window.location.origin;
    }
    return `http://${window.location.hostname}:${API_PORT}`;
  }
  const devHost = Constants.expoConfig?.hostUri?.split(':')[0];
  if (devHost && devHost !== 'localhost' && devHost !== '127.0.0.1') {
    return `http://${devHost}:${API_PORT}`;
  }
  return Platform.OS === 'android' ? `http://10.0.2.2:${API_PORT}` : `http://localhost:${API_PORT}`;
}

export const API_URL = resolveApiUrl();

export const GOOGLE_WEB_CLIENT_ID = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ?? '';
export const GOOGLE_IOS_CLIENT_ID = process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID ?? '';

export function absoluteUrl(pathOrUrl: string): string {
  return /^https?:\/\//.test(pathOrUrl) ? pathOrUrl : `${API_URL}${pathOrUrl}`;
}
