import { useRef, type ReactNode } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import type { ExplanationLanguage, KaiLine, TurnFeedback } from '@/api/types';
import { speakKai } from '@/audio/kaiSpeech';
import { KaiDraftMessage, KaiMessage, LearnerMessage } from '@/components/Transcript';
import { space } from '@/theme/layout';

import { TurnNotes } from './TurnNotes';

export type LearnerLine = { id: string; speaker: 'learner'; text: string; pending?: boolean; feedback?: TurnFeedback };

export type ChatLine =
  | { id: string; speaker: 'kai'; line: KaiLine }
  /** Luna's reply while it's still streaming in. */
  | { id: string; speaker: 'kai-draft'; text: string }
  | LearnerLine;

type Props = {
  lines: ChatLine[];
  /** Shows Luna's notes under each answer (practice chats; placement has none). */
  notes?: boolean;
  /** The learner's explanation language, for "Explain in my language" in Luna's notes. */
  language?: ExplanationLanguage | null;
  /** Rendered above the first line, e.g. the roleplay's goal. */
  header?: ReactNode;
  /** Rendered after the last line, e.g. a "Luna is getting ready" placeholder. */
  children?: ReactNode;
};

/** The conversation so far, newest at the bottom; keeps the latest line in view as the chat grows. */
export function ChatLog({ lines, notes = false, language, header, children }: Props) {
  const scroll = useRef<ScrollView>(null);
  // Luna's voice settings, for reading notes out loud.
  const voice = lines.find((line) => line.speaker === 'kai')?.line.speech;
  return (
    <ScrollView
      ref={scroll}
      style={styles.fill}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      showsVerticalScrollIndicator={false}
      onContentSizeChange={() => scroll.current?.scrollToEnd({ animated: true })}>
      {header}
      {lines.map((entry) => {
        switch (entry.speaker) {
          case 'kai':
            return (
              <KaiMessage
                key={entry.id}
                line={entry.line}
                onReplay={() => void speakKai(entry.line).catch(() => undefined)}
                onReplaySlow={() => void speakKai(entry.line, { slow: true }).catch(() => undefined)}
              />
            );
          case 'kai-draft':
            return <KaiDraftMessage key={entry.id} text={entry.text} />;
          case 'learner':
            return (
              <View key={entry.id} style={styles.learnerTurn}>
                <LearnerMessage
                  text={entry.text}
                  pending={entry.pending}
                  marks={entry.feedback?.corrections.map((c) => c.original)}
                />
                {!notes || entry.pending ? null : (
                  <TurnNotes feedback={entry.feedback ?? null} language={language} voice={voice} />
                )}
              </View>
            );
        }
      })}
      {children ? <View>{children}</View> : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: space.xl, paddingBottom: space.lg },
  learnerTurn: { gap: space.md },
});
