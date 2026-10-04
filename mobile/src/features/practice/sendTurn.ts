import { ApiError } from '@/api/errors';
import type { ServerEvent } from '@/api/sse';
import type { KaiLine, PracticeAnswer, TurnFeedback, TurnTranscriptEvent } from '@/api/types';

/** What happens while an answer is being worked out, in the order the server sends it. */
export type TurnProgress = {
  onTranscript: (event: TurnTranscriptEvent) => void;
  /** Luna's words so far; each call replaces the last. */
  onReply: (textSoFar: string) => void;
  /** Luna's finished line, ready to play. Feedback may still be on its way. */
  onKai: (line: KaiLine) => void;
  onFeedback: (feedback: TurnFeedback) => void;
};

/**
 * Sends one answer as a stream, reporting progress as it comes in, and resolves with the stored answer. If the
 * connection drops part-way, it asks again with the plain request: answers are idempotent per turn, so the server
 * returns the stored answer (or finishes working it out) instead of recording it twice.
 */
export async function sendTurn(
  stream: (onEvent: (event: ServerEvent) => void) => Promise<void>,
  plain: () => Promise<PracticeAnswer>,
  progress: TurnProgress,
): Promise<PracticeAnswer> {
  const outcome: { answer?: PracticeAnswer; problem?: ApiError } = {};
  try {
    await stream(({ event, data }) => {
      switch (event) {
        case 'transcript':
          progress.onTranscript(data as TurnTranscriptEvent);
          break;
        case 'reply':
          progress.onReply((data as { text: string }).text);
          break;
        case 'kai':
          progress.onKai(data as KaiLine);
          break;
        case 'feedback':
          progress.onFeedback(data as TurnFeedback);
          break;
        case 'done':
          outcome.answer = data as PracticeAnswer;
          break;
        case 'problem': {
          // Nothing was saved, so this is worth sending again.
          const problem = data as { code: string; message: string };
          outcome.problem = new ApiError(503, problem.code, problem.message);
          break;
        }
      }
    });
  } catch (error) {
    if (error instanceof ApiError && error.isNetwork) {
      return plain();
    }
    throw error;
  }
  if (outcome.answer) {
    return outcome.answer;
  }
  if (outcome.problem) {
    throw outcome.problem;
  }
  return plain();
}
