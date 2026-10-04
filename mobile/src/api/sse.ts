export type ServerEvent = { event: string; data: unknown };

/**
 * Incremental parser for a text/event-stream body: feed it text as it arrives and it calls `onEvent` once per complete
 * event, with `data` parsed as JSON (or left as text if it isn't JSON).
 */
export function createEventParser(onEvent: (event: ServerEvent) => void) {
  let buffer = '';
  let name = 'message';
  let data: string[] = [];

  function dispatch() {
    if (data.length > 0) {
      const raw = data.join('\n');
      let parsed: unknown = raw;
      try {
        parsed = JSON.parse(raw);
      } catch {
        // Plain-text data stays as it is.
      }
      onEvent({ event: name, data: parsed });
    }
    name = 'message';
    data = [];
  }

  function line(text: string) {
    if (text === '') {
      dispatch();
      return;
    }
    if (text.startsWith(':')) {
      return; // A comment, e.g. a keep-alive.
    }
    const colon = text.indexOf(':');
    const field = colon < 0 ? text : text.slice(0, colon);
    let value = colon < 0 ? '' : text.slice(colon + 1);
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }
    if (field === 'event') {
      name = value;
    } else if (field === 'data') {
      data.push(value);
    }
  }

  return {
    push(chunk: string) {
      buffer += chunk;
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';
      lines.forEach(line);
    },
    /** The stream ended: anything left over is a final event. */
    end() {
      if (buffer) {
        line(buffer);
        buffer = '';
      }
      dispatch();
    },
  };
}
