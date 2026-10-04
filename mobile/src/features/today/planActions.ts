import type { Href } from 'expo-router';

/**
 * Where a plan item's action opens (the server sends e.g. "warmup", "lesson:learn.past-simple", "roleplay:hr-interview",
 * "path", "words"). Null while that kind of practice isn't in the app yet, so the row shows as coming soon instead of a
 * dead button.
 */
export function planHref(action: string): Href | null {
  const [kind, argument] = action.split(':');
  switch (kind) {
    case 'warmup':
      return '/practice/warm-up';
    case 'roleplay':
      return argument ? { pathname: '/practice/roleplay/[scenario]', params: { scenario: argument } } : null;
    case 'lesson':
      return argument ? { pathname: '/lesson/[id]', params: { id: argument } } : null;
    case 'path':
      return '/path';
    default:
      return null;
  }
}
