import NetInfo from '@react-native-community/netinfo';
import { focusManager, onlineManager, QueryClient } from '@tanstack/react-query';
import { AppState, Platform, type AppStateStatus } from 'react-native';

import { ApiError } from './errors';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 10 * 60_000,
      // The API client already retries transient failures; only retry here for offline gaps.
      retry: (failureCount, error) => error instanceof ApiError && error.isNetwork && failureCount < 2,
    },
    mutations: {
      retry: false,
    },
  },
});

/** Pause queries while offline and refetch when the connection or the app comes back. */
export function wireQueryLifecycle(): () => void {
  onlineManager.setEventListener((setOnline) =>
    NetInfo.addEventListener((state) => setOnline(state.isConnected !== false)),
  );
  if (Platform.OS === 'web') {
    return () => undefined;
  }
  const subscription = AppState.addEventListener('change', (status: AppStateStatus) =>
    focusManager.setFocused(status === 'active'),
  );
  return () => subscription.remove();
}

export const queryKeys = {
  me: ['me'] as const,
  today: ['today'] as const,
  path: ['path'] as const,
  providers: ['auth', 'providers'] as const,
  placementResult: (sessionId: string) => ['placement', sessionId, 'result'] as const,
  practiceResult: (sessionId: string) => ['practice', sessionId, 'result'] as const,
  practiceCatalog: ['practice', 'catalog'] as const,
  grammarPath: ['grammar', 'path'] as const,
  grammarLesson: (lessonId: string) => ['grammar', 'lesson', lessonId] as const,
};
