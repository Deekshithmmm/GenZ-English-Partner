import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { IconButton } from '@/components/IconButton';
import { Notice } from '@/components/Notice';
import { Screen } from '@/components/Screen';
import { SpeechBubble } from '@/components/SpeechBubble';
import { TextField } from '@/components/TextField';
import { PracticeChat } from '@/features/practice/PracticeChat';
import { space } from '@/theme/layout';

/** The same limits the server checks, so the learner finds out here rather than after Luna starts planning. */
const MIN_CONTEXT = 80;
const MAX_CONTEXT = 6000;

/**
 * Interview mode: the learner pastes their résumé or a job description, then Luna runs a mock HR round planned from it.
 * The pasted text stays on this screen (and goes to the server once, to plan); if the server can't use it, the learner
 * comes back here with their text still in the box.
 */
export default function InterviewScreen() {
  const [pasted, setPasted] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [started, setStarted] = useState<string | null>(null);

  if (started !== null) {
    return (
      <PracticeChat key={started} kind="INTERVIEW" context={started} onEditContext={() => setStarted(null)} />
    );
  }

  function start() {
    const text = pasted.trim();
    if (text.length < MIN_CONTEXT) {
      setError('Paste a few more lines: your recent studies, projects or internships, or the job requirements.');
      return;
    }
    setError(null);
    setStarted(text);
  }

  return (
    <Screen
      footer={<Button label="Start the interview" icon="mic" onPress={start} testID="interview-start" />}>
      <View style={styles.top}>
        {router.canGoBack() ? (
          <IconButton icon="arrow-back" label="Go back" onPress={() => router.back()} />
        ) : null}
      </View>
      <View style={styles.body}>
        <AppText variant="title" accessibilityRole="header">
          Mock interview
        </AppText>
        <SpeechBubble>
          <AppText variant="bodyStrong">Paste your résumé or the job description.</AppText>
          <AppText variant="small" tone="muted">
            I&apos;ll play the HR interviewer and ask six questions about it, like a real first round.
          </AppText>
        </SpeechBubble>
        <View style={styles.field}>
          <TextField
            label="Résumé or job description"
            value={pasted}
            onChangeText={(text) => {
              setPasted(text);
              if (error) {
                setError(null);
              }
            }}
            multiline
            numberOfLines={10}
            maxLength={MAX_CONTEXT}
            placeholder="For example: Final-year B.Tech student. Built a college attendance app in React. Two-month internship as a data analyst…"
            error={error}
            testID="interview-context"
          />
          <AppText variant="small" tone="muted" style={styles.count}>
            {pasted.length.toLocaleString('en-IN')} / {MAX_CONTEXT.toLocaleString('en-IN')}
          </AppText>
        </View>
        <Notice message="Luna keeps the questions it plans from this, not the text you paste." />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { flexDirection: 'row', marginLeft: -space.md, marginTop: -space.md, minHeight: space.lg },
  body: { gap: space.xl },
  field: { gap: space.xs },
  count: { alignSelf: 'flex-end' },
});
