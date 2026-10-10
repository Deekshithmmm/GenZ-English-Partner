import { useEffect, useEffectEvent, useRef, useState, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { stopKai } from '@/audio/kaiSpeech';
import {
  MAX_RECORDING_MS,
  MicNeedsSecurePage,
  MicPermissionDenied,
  useVoiceRecorder,
  type RecordedClip,
} from '@/audio/useVoiceRecorder';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { MicButton, useEffectiveMicMode } from '@/components/MicButton';
import { Notice } from '@/components/Notice';
import { TextField } from '@/components/TextField';
import { usePrefs } from '@/stores/prefs';
import { space } from '@/theme/layout';

export type AnswerMode = 'voice' | 'text';

/** `fix` is the one-tap way out: switch the mic to tap-to-talk, or type instead. */
type Problem = { message: string; fix: 'tap' | 'type' | null };

/** Anything shorter is almost always an accidental tap (the server rejects under half a second). */
const MIN_CLIP_MS = 800;
/** A quick tap in hold mode: show how the mic works instead of failing silently. */
const QUICK_TAP_MS = 600;
const COUNTDOWN_FROM_MS = 20_000;
export const MAX_TEXT_ANSWER = 1500;

type Props = {
  mode: AnswerMode;
  onModeChange: (mode: AnswerMode) => void;
  /** Waiting on the server: the mic shows a spinner and nothing can be sent. */
  busy: boolean;
  /** What the learner sees while busy, e.g. "Luna is listening…". */
  busyLabel?: string;
  /** Nothing to answer yet. */
  disabled?: boolean;
  onVoice: (clip: RecordedClip) => void;
  /** Resolves true once the answer was accepted, which clears the field. */
  onText: (text: string) => Promise<boolean>;
  /** Shown at the right of the bottom row, e.g. "Finish now". */
  secondaryAction?: ReactNode;
  /** Told when the mic starts and stops listening, so Luna can look like she's listening. */
  onRecordingChange?: (recording: boolean) => void;
};

/**
 * How the learner answers Luna: the mic by default, typing as a fallback for noisy places or a failing mic.
 * Starting to talk interrupts Luna, so learners never have to wait for Luna to finish.
 */
export function AnswerComposer({
  mode,
  onModeChange,
  busy,
  busyLabel = 'Sending your answer…',
  disabled = false,
  onVoice,
  onText,
  secondaryAction,
  onRecordingChange,
}: Props) {
  const preferredMicMode = usePrefs((s) => s.micMode);
  const setMicMode = usePrefs((s) => s.setMicMode);
  const micMode = useEffectiveMicMode(preferredMicMode);
  const recorder = useVoiceRecorder();
  const [problem, setProblem] = useState<Problem | null>(null);
  const [draft, setDraft] = useState('');
  const pressedAt = useRef(0);
  /** The mic couldn't start this time; its problem is already showing and letting go mustn't replace it. */
  const startFailed = useRef(false);
  const recording = recorder.phase === 'recording';
  const reportRecording = useEffectEvent((on: boolean) => onRecordingChange?.(on));
  useEffect(() => {
    reportRecording(recording);
  }, [recording]);

  async function startRecording() {
    if (recorder.phase !== 'idle') {
      return;
    }
    setProblem(null);
    stopKai();
    pressedAt.current = Date.now();
    startFailed.current = false;
    try {
      await recorder.start();
    } catch (error) {
      startFailed.current = true;
      setProblem({
        message:
          error instanceof MicPermissionDenied || error instanceof MicNeedsSecurePage
            ? error.message
            : "The mic didn't start. Try again, or type your answer instead.",
        fix: 'type',
      });
    }
  }

  async function finishRecording() {
    const clip = await recorder.stop().catch(() => null);
    if (!clip) {
      if (startFailed.current) {
        return;
      }
      if (micMode === 'hold' && Date.now() - pressedAt.current < QUICK_TAP_MS) {
        // Holding is hard for some people (and impossible with most screen readers): offer tapping instead.
        setProblem({ message: 'Keep holding the mic while you talk, then let go to send.', fix: 'tap' });
      } else {
        // Let go while the mic was still starting (the first time, the browser asks for permission): nothing was
        // recorded, so say so instead of quietly doing nothing.
        setProblem({
          message: 'The mic wasn\'t ready yet, so nothing was recorded. Hold it again and start talking when you see "Listening…".',
          fix: null,
        });
      }
      return;
    }
    if (clip.durationMs < MIN_CLIP_MS) {
      setProblem({
        message:
          micMode === 'hold'
            ? 'That was too quick to hear. Hold the mic the whole time you talk.'
            : 'That was too quick to hear. Tap the mic, talk, then tap again.',
        fix: micMode === 'hold' ? 'tap' : 'type',
      });
      return;
    }
    onVoice(clip);
  }

  // The recorder stops itself at the limit; send what was said instead of leaving the mic looking live.
  const sendAtLimit = useEffectEvent(() => {
    void finishRecording();
  });
  useEffect(() => {
    if (!recording) {
      return;
    }
    const timer = setTimeout(sendAtLimit, MAX_RECORDING_MS);
    return () => clearTimeout(timer);
  }, [recording]);

  async function sendText() {
    const text = draft.trim();
    if (!text) {
      setProblem({ message: 'Type your answer first.', fix: null });
      return;
    }
    setProblem(null);
    if (await onText(text)) {
      setDraft('');
    }
  }

  if (mode === 'text') {
    return (
      <View style={styles.wrap}>
        {problem ? <Notice tone="error" message={problem.message} /> : null}
        <TextField
          label="Type your answer"
          value={draft}
          onChangeText={setDraft}
          multiline
          maxLength={MAX_TEXT_ANSWER}
          editable={!busy && !disabled}
          placeholder="Write it the way you'd say it"
          testID="answer-text"
        />
        <Button label="Send" onPress={sendText} loading={busy} disabled={disabled} testID="answer-send" />
        <View style={styles.bottomRow}>
          <Button label="Speak instead" icon="mic-outline" variant="ghost" onPress={() => onModeChange('voice')} />
          {secondaryAction}
        </View>
      </View>
    );
  }

  const remainingMs = MAX_RECORDING_MS - recorder.durationMs;
  const hint = busy
    ? busyLabel
    : recording
      ? `Listening… ${clock(recorder.durationMs)}${remainingMs <= COUNTDOWN_FROM_MS ? ` · ${Math.ceil(remainingMs / 1000)}s left` : ''}`
      : micMode === 'hold'
        ? 'Hold the mic and talk. Let go to send.'
        : 'Tap the mic, talk, then tap again to send.';

  return (
    <View style={styles.wrap}>
      {problem ? (
        <Notice
          tone="error"
          message={problem.message}
          actionLabel={problem.fix === 'tap' ? 'Use tap to talk' : problem.fix === 'type' ? 'Type instead' : undefined}
          onAction={() => {
            setProblem(null);
            if (problem.fix === 'tap') {
              setMicMode('toggle');
            } else {
              onModeChange('text');
            }
          }}
        />
      ) : null}
      <AppText variant="small" tone="muted" style={styles.hint} accessibilityLiveRegion="polite">
        {hint}
      </AppText>
      <MicButton
        mode={micMode}
        recording={recording}
        // Not busy while the mic warms up: the button must stay pressable so letting go cancels the start.
        busy={busy}
        disabled={disabled}
        level={recorder.level}
        onStart={startRecording}
        onStop={finishRecording}
        testID="mic"
      />
      <View style={styles.bottomRow}>
        <Button
          label="Type instead"
          icon="create-outline"
          variant="ghost"
          disabled={recording}
          onPress={() => onModeChange('text')}
          testID="type-instead"
        />
        {secondaryAction}
      </View>
    </View>
  );
}

function clock(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

const styles = StyleSheet.create({
  wrap: { gap: space.sm },
  hint: { textAlign: 'center' },
  bottomRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
});
