import { clearStoredSession, writeCachedMe, writeRefreshToken } from '@/auth/tokenStorage';
import { useSession } from '@/stores/session';

import { API_URL } from './config';
import { ApiError, NETWORK_MESSAGE, TIMEOUT_MESSAGE, UNKNOWN_MESSAGE } from './errors';
import { createEventParser, type ServerEvent } from './sse';
import type { AuthResponse, Me } from './types';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export type RequestOptions = {
  method?: Method;
  json?: unknown;
  form?: FormData;
  /** Send the access token (default true). */
  auth?: boolean;
  timeoutMs?: number;
  /** Automatic retries on network/5xx errors. Defaults to 2 for GET/PUT/DELETE and 0 otherwise. */
  retries?: number;
};

const DEFAULT_TIMEOUT_MS = 20_000;
/** A stream gives up if nothing arrives for this long (the first event can wait on speech-to-text). */
const STREAM_IDLE_MS = 45_000;
const REFRESH_EARLY_MS = 30_000;

/**
 * Calls the backend. Handles auth (refreshing the access token once, with a single in-flight refresh shared by
 * concurrent calls), timeouts, and backoff retries for transient failures on patchy connections.
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const idempotent = method === 'GET' || method === 'PUT' || method === 'DELETE';
  const maxRetries = options.retries ?? (idempotent ? 2 : 0);

  for (let attempt = 0; ; attempt++) {
    try {
      return await (options.auth === false
        ? send<T>(method, path, options, null)
        : sendAuthed<T>(method, path, options));
    } catch (error) {
      if (error instanceof ApiError && error.isTransient && attempt < maxRetries) {
        await sleep(backoffMs(attempt));
        continue;
      }
      throw error;
    }
  }
}

/**
 * Calls an endpoint that answers with server-sent events and hands each one to `onEvent` as it arrives. Auth works
 * like `api`. Errors before the stream starts throw the same ApiErrors as `api`; a connection that drops mid-stream
 * throws a network ApiError, so the caller can fall back to a plain (idempotent) request. Never retried here, since
 * events may already have been handled.
 */
export async function apiStream(
  path: string,
  options: Omit<RequestOptions, 'retries'>,
  onEvent: (event: ServerEvent) => void,
): Promise<void> {
  const method = options.method ?? 'POST';
  try {
    return await sendStream(method, path, options, await freshAccessToken(), onEvent);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401 && (await refreshSession())) {
      return sendStream(method, path, options, useSession.getState().accessToken, onEvent);
    }
    throw error;
  }
}

async function sendAuthed<T>(method: Method, path: string, options: RequestOptions): Promise<T> {
  const token = await freshAccessToken();
  try {
    return await send<T>(method, path, options, token);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401 && (await refreshSession())) {
      return send<T>(method, path, options, useSession.getState().accessToken);
    }
    throw error;
  }
}

async function freshAccessToken(): Promise<string | null> {
  const { accessToken, accessTokenExpiresAt, refreshToken } = useSession.getState();
  const expiringSoon = !accessTokenExpiresAt || accessTokenExpiresAt - Date.now() < REFRESH_EARLY_MS;
  if ((!accessToken || expiringSoon) && refreshToken) {
    await refreshSession();
    return useSession.getState().accessToken;
  }
  return accessToken;
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Swaps the refresh token for a new pair. Returns false (and signs out) when the server says the session is over;
 * rethrows network errors so an offline learner stays signed in.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = useSession.getState().refreshToken;
  if (!refreshToken) {
    return false;
  }
  try {
    const auth = await send<AuthResponse>('POST', '/api/v1/auth/refresh', { json: { refreshToken } }, null);
    await applyAuth(auth);
    return true;
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 400)) {
      await endSession();
      return false;
    }
    throw error;
  }
}

/** Stores a fresh sign-in: access token in memory, refresh token in the keystore. */
export async function applyAuth(auth: AuthResponse): Promise<void> {
  await writeRefreshToken(auth.refreshToken);
  await writeCachedMe(auth.me);
  useSession.setState({
    status: 'signedIn',
    me: auth.me,
    accessToken: auth.accessToken,
    accessTokenExpiresAt: Date.parse(auth.accessTokenExpiresAt),
    refreshToken: auth.refreshToken,
  });
}

export async function applyMe(me: Me): Promise<void> {
  await writeCachedMe(me);
  useSession.setState({ me });
}

export async function endSession(): Promise<void> {
  await clearStoredSession();
  useSession.setState({
    status: 'signedOut',
    me: null,
    accessToken: null,
    accessTokenExpiresAt: null,
    refreshToken: null,
  });
}

async function send<T>(method: Method, path: string, options: RequestOptions, token: string | null): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      ...requestInit(options, token, 'application/json'),
      signal: controller.signal,
    });
  } catch {
    throw connectionError(controller);
  } finally {
    clearTimeout(timer);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text().catch(() => '');
  const data = parseJson(text);
  if (!response.ok) {
    throw problemError(response.status, data);
  }
  return data as T;
}

async function sendStream(
  method: Method,
  path: string,
  options: RequestOptions,
  token: string | null,
  onEvent: (event: ServerEvent) => void,
): Promise<void> {
  const controller = new AbortController();
  const idleMs = options.timeoutMs ?? STREAM_IDLE_MS;
  let timer = setTimeout(() => controller.abort(), idleMs);
  const stillAlive = () => {
    clearTimeout(timer);
    timer = setTimeout(() => controller.abort(), idleMs);
  };
  try {
    let response: Response;
    try {
      response = await fetch(`${API_URL}${path}`, {
        method,
        ...requestInit(options, token, 'text/event-stream'),
        signal: controller.signal,
      });
    } catch {
      throw connectionError(controller);
    }
    if (!response.ok) {
      throw problemError(response.status, parseJson(await response.text().catch(() => '')));
    }

    const parser = createEventParser(onEvent);
    try {
      const reader = response.body?.getReader();
      if (reader) {
        const decoder = new TextDecoder();
        for (;;) {
          const { value, done } = await reader.read();
          if (done) {
            break;
          }
          stillAlive();
          parser.push(decoder.decode(value, { stream: true }));
        }
        parser.push(decoder.decode());
      } else {
        // No streaming body on this platform: the events still arrive, just all at once.
        parser.push(await response.text());
      }
    } catch {
      throw connectionError(controller);
    }
    parser.end();
  } finally {
    clearTimeout(timer);
  }
}

function requestInit(options: RequestOptions, token: string | null, accept: string): RequestInit {
  const headers: Record<string, string> = { Accept: accept };
  let body: BodyInit | undefined;
  if (options.form) {
    body = options.form;
  } else if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.json);
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { headers, body };
}

function connectionError(controller: AbortController): ApiError {
  const timedOut = controller.signal.aborted;
  return new ApiError(0, timedOut ? 'timeout' : 'network', timedOut ? TIMEOUT_MESSAGE : NETWORK_MESSAGE);
}

function problemError(status: number, data: unknown): ApiError {
  const problem = (data ?? {}) as { code?: string; detail?: string; fields?: Record<string, string> };
  return new ApiError(status, problem.code ?? `http_${status}`, problem.detail ?? UNKNOWN_MESSAGE, problem.fields ?? {});
}

function parseJson(text: string): unknown {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function backoffMs(attempt: number): number {
  const base = 600 * 3 ** attempt;
  return base + Math.random() * base * 0.3;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
