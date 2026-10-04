import type { Appearance, MicMode } from '@/stores/prefs';

export const MIC_OPTIONS: { value: MicMode; label: string }[] = [
  { value: 'hold', label: 'Hold to talk' },
  { value: 'toggle', label: 'Tap to talk' },
];

export const APPEARANCE_OPTIONS: { value: Appearance; label: string }[] = [
  { value: 'system', label: 'Auto' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
];

export function micModeHint(mode: MicMode): string {
  return mode === 'hold'
    ? 'Press and hold the mic while you talk, then let go to send.'
    : 'Tap the mic once to start, and again when you’re done.';
}
