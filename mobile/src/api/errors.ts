/** An error the UI can show as-is: `message` says what went wrong and how to fix it. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string>;

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }

  /** No response at all: offline, DNS, dropped connection or timeout. */
  get isNetwork(): boolean {
    return this.status === 0;
  }

  /** Worth retrying automatically: the network or the server hiccupped, not the request. */
  get isTransient(): boolean {
    return this.status === 0 || this.status === 502 || this.status === 503 || this.status === 504;
  }
}

export const NETWORK_MESSAGE =
  "We couldn't reach the server. Check your internet connection and try again.";
export const TIMEOUT_MESSAGE =
  'The connection is slow right now. Try again, or move somewhere with better signal.';
export const UNKNOWN_MESSAGE = 'Something went wrong on our side. Try again in a moment.';

export function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return UNKNOWN_MESSAGE;
}
