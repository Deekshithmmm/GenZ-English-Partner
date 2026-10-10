import {
  AudioQuality,
  getRecordingPermissionsAsync,
  IOSOutputFormat,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
  useAudioRecorderState,
  type RecordingOptions,
} from 'expo-audio';
import { useCallback, useRef, useState } from 'react';
import { Platform } from 'react-native';

/**
 * Speech-grade audio: 16 kHz mono AAC at 32 kbps is about 4 KB per second, so a 30-second answer uploads quickly
 * on patchy 4G while staying clear enough for speech recognition.
 */
const SPEECH_PRESET: RecordingOptions = {
  extension: '.m4a',
  sampleRate: 16_000,
  numberOfChannels: 1,
  bitRate: 32_000,
  isMeteringEnabled: true,
  android: { outputFormat: 'mpeg4', audioEncoder: 'aac' },
  ios: { outputFormat: IOSOutputFormat.MPEG4AAC, audioQuality: AudioQuality.MEDIUM },
  // No web bitsPerSecond: browsers treat it as an audio+video budget and give audio a tenth (Chrome clamps that to
  // 5 kbps). Without it, expo-audio passes bitRate above as the audio bitrate.
  web: { mimeType: 'audio/webm' },
};

export const MAX_RECORDING_MS = 120_000;

export type RecordedClip = {
  uri: string;
  durationMs: number;
  mimeType: string;
  fileName: string;
};

export type RecorderPhase = 'idle' | 'starting' | 'recording';

export class MicPermissionDenied extends Error {
  constructor() {
    super('Allow microphone access in your phone settings so Luna can hear you.');
  }
}

/** Port of the https link from tools/phone.mjs. */
const PHONE_HTTPS_PORT = 8443;

/**
 * Browsers only open the mic on secure pages (https or localhost); a phone opening the laptop's address over the
 * Wi-Fi gets a plain http page.
 */
export class MicNeedsSecurePage extends Error {
  constructor() {
    const secure = typeof window === 'undefined' ? null : `https://${window.location.hostname}:${PHONE_HTTPS_PORT}`;
    super(
      secure
        ? `This browser only lets Luna hear you on a secure page. Open ${secure} instead, or type your answer.`
        : 'This browser only lets Luna hear you on a secure (https) page. Type your answer instead.',
    );
  }
}

export function useVoiceRecorder() {
  const recorder = useAudioRecorder(SPEECH_PRESET);
  const recorderState = useAudioRecorderState(recorder, 100);
  const [phase, setPhase] = useState<RecorderPhase>('idle');
  const startedAt = useRef(0);
  const stopRequested = useRef(false);

  const start = useCallback(async () => {
    stopRequested.current = false;
    setPhase('starting');
    try {
      if (Platform.OS === 'web' && typeof window !== 'undefined' && !window.isSecureContext) {
        throw new MicNeedsSecurePage();
      }
      // Asking opens the microphone just to close it again (on the web, a whole extra start), so only ask when it
      // isn't already allowed: the recording then starts sooner and the learner's first words aren't lost.
      const known = await getRecordingPermissionsAsync();
      const permission = known.granted ? known : await requestRecordingPermissionsAsync();
      if (!permission.granted) {
        throw new MicPermissionDenied();
      }
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      if (stopRequested.current) {
        // The learner let go before the mic was ready.
        setPhase('idle');
        return false;
      }
      recorder.record({ forDuration: MAX_RECORDING_MS / 1000 });
      startedAt.current = Date.now();
      setPhase('recording');
      return true;
    } catch (error) {
      setPhase('idle');
      throw error;
    }
  }, [recorder]);

  /** Stops and returns the clip, or null if nothing usable was recorded. */
  const stop = useCallback(async (): Promise<RecordedClip | null> => {
    stopRequested.current = true;
    if (phase !== 'recording' && !recorder.isRecording) {
      setPhase('idle');
      return null;
    }
    // Recording stops itself at the limit even if the learner keeps holding, so the audio is never longer than that.
    const durationMs = Math.min(Date.now() - startedAt.current, MAX_RECORDING_MS);
    await recorder.stop();
    setPhase('idle');
    await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true }).catch(() => undefined);
    const uri = recorder.uri;
    if (!uri) {
      return null;
    }
    const web = Platform.OS === 'web';
    return {
      uri,
      durationMs,
      mimeType: web ? 'audio/webm' : 'audio/mp4',
      fileName: web ? 'answer.webm' : 'answer.m4a',
    };
  }, [phase, recorder]);

  return {
    phase,
    start,
    stop,
    durationMs: recorderState.isRecording ? recorderState.durationMillis : 0,
    /** 0..1 loudness for the listening animation. */
    level: meteringToLevel(recorderState.metering),
  };
}

function meteringToLevel(db: number | undefined): number {
  if (db == null || Number.isNaN(db)) {
    return 0;
  }
  // Metering is roughly -60 dB (silence) to 0 dB (loud).
  return Math.max(0, Math.min(1, (db + 60) / 60));
}
