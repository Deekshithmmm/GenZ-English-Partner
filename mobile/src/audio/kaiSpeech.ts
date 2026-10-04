import { createAudioPlayer } from 'expo-audio';
import * as Speech from 'expo-speech';
import { Platform } from 'react-native';
import { create } from 'zustand';

import { absoluteUrl } from '@/api/config';
import { speechApi } from '@/api/endpoints';
import type { KaiLine, TutorVoice } from '@/api/types';

/** Luna's voice settings, as the server sends them with each line. */
type VoiceSettings = KaiLine['speech'];

/** A piece of text to read out in one language, e.g. a Kannada line and then its English. */
export type SpokenPart = { text: string; languageTag: string };

/**
 * Luna's two voices on the phone's speech engine: both a young woman's voice, "warm" calm and a little slower,
 * "bright" more upbeat. Pitch stays natural: lifting it makes speech engines sound thin and harder to follow.
 */
const STYLE: Record<TutorVoice, { pitch: number; rate: number }> = {
  KAI_WARM: { pitch: 1.0, rate: 0.88 },
  KAI_BRIGHT: { pitch: 1.04, rate: 0.96 },
};

/**
 * Speech engines don't say which voices are female, so names are matched against well-known female voices on Edge,
 * Chrome, Windows, Android and iOS, in English and Indian languages (and male ones are avoided).
 */
const FEMALE =
  /female|woman|neerja|heera|isha|veena|kajal|aditi|raveena|swara|kalpana|lekha|ananya|sapna|pallavi|shruti|sobhana|aarohi|tanishaa|samantha|karen|moira|tessa|fiona|victoria|serena|zira|hazel|susan|libby|sonia|jenny|aria\b|ava\b|emma|michelle|nancy|catherine|natasha|clara|salli|kimberly|joanna|ivy|kendra|google us english|en-in-x-(?:ahp|cxx|ena)|en-us-x-(?:tpf|sfg|iob|iog|tpc)|en-gb-x-(?:gba|gbc|gbg)|-x-[a-z]{2}f-/i;
const MALE =
  /\bmale\b|\bman\b|ravi|prabhat|rishi|hemant|madhur|gagan|valluvar|mohan|midhun|manohar|bashkar|david|mark|george|guy|daniel|alex\b|fred|ryan|thomas|en-in-x-(?:end|ene)|en-us-x-(?:iol|iom|tpd)|en-gb-x-(?:rjs|gbb|gbd)|-x-[a-z]{2}m-/i;
/** Neural and network voices are far clearer than the old built-in ones. */
const CLEAR = /natural|neural|online|enhanced|premium|wavenet|network|google/i;
const MUFFLED = /compact|espeak/i;

/** How well a voice suits Luna reading `wanted` (a BCP 47 tag); -1 when it's another language. */
function score(voice: Speech.Voice, wanted: string): number {
  const language = voice.language?.toLowerCase().replace('_', '-') ?? '';
  const want = wanted.toLowerCase();
  if (!language.startsWith(want.split('-')[0])) {
    return -1;
  }
  const label = `${voice.name} ${voice.identifier}`;
  let total = language === want ? 25 : 10;
  if (FEMALE.test(label)) {
    total += 50;
  } else if (MALE.test(label)) {
    total -= 60;
  }
  if (CLEAR.test(label) || voice.quality === Speech.VoiceQuality.Enhanced) {
    total += 40;
  }
  if (MUFFLED.test(label)) {
    total -= 30;
  }
  return Math.max(0, total + 100);
}

let voiceList: Speech.Voice[] = [];
const chosen = new Map<string, Speech.Voice | null>();
let watching = false;

/**
 * Browsers list their voices in stages: Chrome gives its old built-in voices first and adds the clearer Google voices
 * a moment later. Each time the list changes, Luna chooses again.
 */
function watchVoices() {
  if (watching || Platform.OS !== 'web') {
    return;
  }
  watching = true;
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    return;
  }
  window.speechSynthesis.addEventListener('voiceschanged', () => {
    voiceList = [];
    chosen.clear();
  });
}

/** The phone's voices. Browsers fill the list a moment after the page loads, and some never do, so don't wait long. */
async function voices(): Promise<Speech.Voice[]> {
  watchVoices();
  if (voiceList.length > 0) {
    return voiceList;
  }
  try {
    voiceList = await Promise.race([
      Speech.getAvailableVoicesAsync(),
      new Promise<Speech.Voice[]>((resolve) => setTimeout(() => resolve([]), 1500)),
    ]);
  } catch {
    voiceList = [];
  }
  if (voiceList.length > 0) {
    chosen.clear();
  }
  return voiceList;
}

/** Luna's voice for a language, or null when the phone has none for it. */
async function voiceFor(languageTag: string): Promise<Speech.Voice | null> {
  const key = languageTag.toLowerCase();
  if (chosen.has(key)) {
    return chosen.get(key) ?? null;
  }
  const list = await voices();
  let best: Speech.Voice | null = null;
  let bestScore = -1;
  for (const voice of list) {
    const s = score(voice, languageTag);
    if (s > bestScore) {
      best = voice;
      bestScore = s;
    }
  }
  if (list.length > 0) {
    chosen.set(key, best);
  }
  return best;
}

/**
 * Whether Luna can read `languageTag` aloud. English always works (every engine has a default voice). Other languages
 * work with a server voice that speaks them (ElevenLabs), or a voice installed on the phone, e.g. Kannada on
 * Android's Google voice data or Microsoft Edge. Null when the phone hasn't listed its voices (yet), so a slow browser
 * doesn't look like a missing voice.
 */
export async function canSpeak(languageTag: string): Promise<boolean | null> {
  if (isEnglish(languageTag) || (await serverSpeaks(languageTag))) {
    return true;
  }
  if ((await voices()).length === 0) {
    return null;
  }
  return (await voiceFor(languageTag)) != null;
}

/** Whether Luna is talking right now, so her face can move her mouth. */
export const useKaiSpeaking = create<{ speaking: boolean }>(() => ({ speaking: false }));
const setSpeaking = (speaking: boolean) => useKaiSpeaking.setState({ speaking });

/** Bumped by every new line and by stopKai, so older lines stop instead of talking over newer ones. */
let generation = 0;

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Pauses at Luna's normal speed, as in everyday talk: a breath after a comma, a short beat after a sentence, and a
 * little more between two parts (say Kannada, then English). Her server voice pauses inside a sentence itself.
 */
const COMMA_PAUSE_MS = 120;
const SENTENCE_PAUSE_MS = 200;
const PART_PAUSE_MS = 350;
/** Browsers cut off an utterance after about 15 seconds, so no piece is longer than this. */
const MAX_PIECE = 200;
/** A server clip shorter than this takes the next sentence along too. */
const SHORT_CLIP = 60;

/** A piece of a line that Luna says in one go, and how long she pauses after it. */
export type SpeechPiece = { text: string; pauseMs: number };

/** Ends a sentence: . ! ? or the Hindi and Bengali full stops, maybe inside a closing quote or bracket. */
const SENTENCE_END = /[.!?।॥]+["”’')]*\s+/g;
/** Ends a clause: a comma, semicolon or colon, or a dash between spaces. */
const CLAUSE_END = /[,;:]["”’')]*\s+|\s+[–—-]\s+/g;
/** A full stop that doesn't end a sentence. */
const ABBREVIATION = /\b(?:mr|mrs|ms|dr|vs|e\.g|i\.e)\.$/i;
/** Something to say: a letter or digit in Latin or an Indian script. */
const SAYABLE = /[0-9A-Za-z\u00C0-\u024F\u0900-\u0DFF]/;

/**
 * Rewrites marks a speech engine would read out ("underscore", "slash", "right arrow") or run straight past, the way
 * a teacher says them: a gap is "blank", "a/an" is "a or an", and "go → went" or "I am (I'm)" get a short pause.
 */
export function speakable(text: string, languageTag: string): string {
  const isEnglish = languageTag.toLowerCase().startsWith('en');
  return (
    text
      .replace(/_{2,}/g, isEnglish ? ' blank ' : ', ')
      .replace(/\s*(?:…|\.{3})/g, ',')
      .replace(/\s*(?:→|⇒|->)\s*/g, ', ')
      .replace(/\s+\/\s+/g, isEnglish ? ' or ' : ', ')
      .replace(/([A-Za-z'’])\/(?=[A-Za-z'’])/g, '$1 or ')
      .replace(/\s*\(\s*/g, ', ')
      .replace(/\s*\)/g, ',')
      .replace(/[\u2600-\u27BF\uFE0F]|[\uD83C-\uD83E][\uDC00-\uDFFF]/g, '')
      // Tidy up after the rules above: no doubled commas, and no comma before other punctuation or at the start.
      .replace(/,(?:\s*,)+/g, ',')
      .replace(/,(\s*["”’']?\s*)([.!?;:।])/g, '$1$2')
      .replace(/^\s*,\s*/, '')
      .replace(/\s+/g, ' ')
      .trim()
  );
}

/**
 * Splits text into the pieces Luna says one at a time, with a short pause after each comma, semicolon, colon or dash
 * and a longer one after each sentence. Speech engines run straight through punctuation, so the pauses are made here.
 * By sentence is for Luna's server voice, which makes the pauses inside a clip itself: short sentences ("Ready?
 * Let's begin!") share a clip, since every clip has a fixed cost to make.
 */
export function speechPieces(text: string, languageTag: string, by: 'clause' | 'sentence' = 'clause'): SpeechPiece[] {
  const sentences: string[] = [];
  for (const sentence of splitAfter(speakable(text, languageTag), SENTENCE_END)) {
    const last = sentences.length - 1;
    if (last >= 0 && ABBREVIATION.test(sentences[last])) {
      sentences[last] = `${sentences[last]} ${sentence}`;
    } else {
      sentences.push(sentence);
    }
  }
  const pieces: SpeechPiece[] = [];
  for (const sentence of sentences) {
    if (by === 'sentence' && sentence.length <= MAX_PIECE && SAYABLE.test(sentence)) {
      const previous = pieces[pieces.length - 1];
      if (previous && previous.text.length < SHORT_CLIP && previous.text.length + sentence.length < MAX_PIECE) {
        previous.text = `${previous.text} ${sentence}`;
      } else {
        pieces.push({ text: sentence, pauseMs: SENTENCE_PAUSE_MS });
      }
      continue;
    }
    const clauses = splitAfter(sentence, CLAUSE_END);
    clauses.forEach((clause, i) => {
      const pauseMs = i === clauses.length - 1 ? SENTENCE_PAUSE_MS : COMMA_PAUSE_MS;
      for (const short of splitLong(clause)) {
        const previous = pieces[pieces.length - 1];
        if (SAYABLE.test(short)) {
          pieces.push({ text: short, pauseMs });
        } else if (previous) {
          // A stray mark (a quote, a lone "+") rides along with what came before.
          previous.text = `${previous.text} ${short}`;
          previous.pauseMs = pauseMs;
        }
      }
    });
  }
  return pieces;
}

/** Breaks a very long piece at a space, so the browser never cuts it off. */
function splitLong(text: string): string[] {
  const out: string[] = [];
  let rest = text;
  while (rest.length > MAX_PIECE) {
    const cut = rest.lastIndexOf(' ', MAX_PIECE);
    const at = cut > 0 ? cut : MAX_PIECE;
    out.push(rest.slice(0, at).trim());
    rest = rest.slice(at).trim();
  }
  if (rest) {
    out.push(rest);
  }
  return out;
}

/** Splits after each match of `separator` (punctuation then a space), keeping the punctuation: "7.30" stays whole. */
function splitAfter(text: string, separator: RegExp): string[] {
  const parts: string[] = [];
  let start = 0;
  separator.lastIndex = 0;
  for (let match = separator.exec(text); match; match = separator.exec(text)) {
    parts.push(text.slice(start, match.index + match[0].length).trim());
    start = match.index + match[0].length;
  }
  parts.push(text.slice(start).trim());
  return parts.filter(Boolean);
}

/** How fast Luna speaks with these settings (1 is the engine's normal speed). */
function rateOf(settings: VoiceSettings, slow: boolean): number {
  const style = STYLE[settings.voice] ?? STYLE.KAI_WARM;
  return Math.max(0.5, settings.rate * style.rate * (slow ? 0.75 : 1));
}

/** Pieces being read in the browser. Chrome can drop an utterance nothing refers to, and then never says it ended. */
const reading = new Set<SpeechSynthesisUtterance>();

/** Reads one short piece and resolves when it's done (or when it should have been, if the engine never says). */
function sayPiece(text: string, languageTag: string, voice: Speech.Voice | null, settings: VoiceSettings, slow: boolean) {
  return new Promise<void>((resolve) => {
    const style = STYLE[settings.voice] ?? STYLE.KAI_WARM;
    const rate = rateOf(settings, slow);
    const language = voice?.language ?? languageTag;
    // Indian scripts take longer per character; this is only a fallback, so it errs on the long side.
    const perChar = languageTag.toLowerCase().startsWith('en') ? 85 : 130;
    let utterance: SpeechSynthesisUtterance | null = null;
    const finish = () => {
      clearTimeout(timer);
      if (utterance) {
        reading.delete(utterance);
      }
      resolve();
    };
    const timer = setTimeout(finish, (text.length * perChar) / rate + 2500);

    const synth = Platform.OS === 'web' && typeof window !== 'undefined' ? window.speechSynthesis : undefined;
    if (synth) {
      utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = language;
      const match = voice ? synth.getVoices().find((v) => v.voiceURI === voice.identifier) : undefined;
      if (match) {
        utterance.voice = match;
      }
      utterance.pitch = style.pitch;
      utterance.rate = rate;
      utterance.volume = 1;
      utterance.onend = finish;
      utterance.onerror = finish;
      reading.add(utterance);
      synth.speak(utterance);
      return;
    }
    Speech.speak(text, {
      language,
      voice: voice?.identifier,
      pitch: style.pitch,
      rate,
      volume: 1,
      onDone: finish,
      onStopped: finish,
      onError: finish,
    });
  });
}

// --- Luna's server voice: a soft neural voice, made a sentence at a time (see tts/server.py) ---------------------------

/** The server keeps a clip for ten minutes; ask again a little before that. */
const CLIP_TTL_MS = 9 * 60_000;
/**
 * How long to use the phone's voice for a language after the server couldn't voice a sentence in it, though it says
 * it speaks it (a busy or failing voice service, usually gone in moments), or couldn't be reached at all.
 */
const SERVER_HICCUP_MS = 15_000;
const SERVER_DOWN_MS = 30_000;
/** How long the server's list of languages is trusted (it changes only when its voice is set up differently). */
const LANGUAGES_TTL_MS = 10 * 60_000;

type Clip = { at: number; early: boolean; settled: boolean; url: Promise<string | null> };
const clips = new Map<string, Clip>();
/** Per language (primary subtag): when to try the server voice again after it had none or failed. */
const serverOffUntil = new Map<string, number>();
let serverLanguages: { at: number; list: Promise<string[]> } | null = null;

const isEnglish = (languageTag: string) => languageTag.toLowerCase().startsWith('en');
const primaryOf = (languageTag: string) => languageTag.toLowerCase().split('-')[0];

/**
 * Whether Luna's server voice speaks this language: English with the free voice, every language (Kannada too) with
 * an ElevenLabs voice. The server says which, once in a while.
 */
async function serverSpeaks(languageTag: string): Promise<boolean> {
  if (!serverLanguages || Date.now() - serverLanguages.at > LANGUAGES_TTL_MS) {
    const entry = {
      at: Date.now(),
      list: speechApi.languages().then(
        (r) => r.languages,
        () => {
          entry.at = 0; // ask again next time
          return [] as string[];
        },
      ),
    };
    serverLanguages = entry;
  }
  const list = await serverLanguages.list;
  const language = primaryOf(languageTag);
  return (list.includes('*') || list.includes(language)) && Date.now() >= (serverOffUntil.get(language) ?? 0);
}

/**
 * The address of a clip of Luna saying `text`, or null when the phone's voice should say it instead: the server may
 * not speak the language, or have no voice set up. Each clip is asked for once; asking for one that was fetched early
 * but isn't ready asks again as wanted now, which moves it up the server's queue.
 */
function serverClip(text: string, languageTag: string, settings: VoiceSettings, early: boolean): Promise<string | null> {
  const key = `${settings.voice}|${settings.rate}|${languageTag}|${text}`;
  const known = clips.get(key);
  if (known && Date.now() - known.at < CLIP_TTL_MS && (early || !known.early || known.settled)) {
    return known.url;
  }
  if (clips.size > 400) {
    for (const [old, clip] of clips) {
      if (Date.now() - clip.at >= CLIP_TTL_MS) {
        clips.delete(old);
      }
    }
  }
  const language = primaryOf(languageTag);
  const clip: Clip = { at: Date.now(), early, settled: false, url: Promise.resolve(null) };
  clip.url = (async () => {
    if (!(await serverSpeaks(languageTag))) {
      clips.delete(key);
      return null;
    }
    try {
      const speech = await speechApi.voice(text, languageTag, early);
      clip.settled = true;
      if (speech.mode === 'AUDIO' && speech.audioUrl) {
        return absoluteUrl(speech.audioUrl);
      }
      serverOffUntil.set(language, Date.now() + SERVER_HICCUP_MS);
    } catch {
      serverOffUntil.set(language, Date.now() + SERVER_DOWN_MS);
    }
    clips.delete(key);
    return null;
  })();
  clips.set(key, clip);
  return clip.url;
}

/** Sentences to fetch ahead, in the order Luna will say them. */
const ahead: { text: string; languageTag: string; settings: VoiceSettings }[] = [];
let fetchingAhead = false;

/**
 * Asks the server for Luna's voice for these parts ahead of time, so she starts at once when she says them. One
 * sentence at a time and in order: the server may make one clip at a time, so the first lines are ready first and
 * whatever Luna is about to say only waits for the one being made. Parts the phone will voice are skipped.
 */
export function prefetchKaiParts(parts: SpokenPart[], settings: VoiceSettings): void {
  for (const part of parts) {
    for (const piece of speechPieces(part.text, part.languageTag, 'sentence')) {
      ahead.push({ text: piece.text, languageTag: part.languageTag, settings });
    }
  }
  void fetchAhead();
}

async function fetchAhead() {
  if (fetchingAhead) {
    return;
  }
  fetchingAhead = true;
  try {
    for (let next = ahead.shift(); next; next = ahead.shift()) {
      await serverClip(next.text, next.languageTag, next.settings, true);
    }
  } finally {
    fetchingAhead = false;
  }
}

/** The clip playing now, and how to end it early. */
let playing: { finish: () => void } | null = null;

/**
 * Plays a clip and resolves when it ends, is stopped, can't play, or (if it never says it ended) shortly after it
 * should have.
 */
function playClip(url: string, rate: number): Promise<void> {
  playing?.finish();
  return new Promise((resolve) => {
    let timer = setTimeout(() => entry.finish(), 30_000);
    /** Once the clip's length is known, the fallback is its length plus a little. */
    const expect = (seconds: number) => {
      clearTimeout(timer);
      timer = setTimeout(() => entry.finish(), (seconds * 1000) / rate + 3000);
    };
    let release = () => {};
    const entry = {
      finish: () => {
        if (playing !== entry) {
          return;
        }
        playing = null;
        clearTimeout(timer);
        release();
        resolve();
      },
    };
    playing = entry;

    // In the browser the audio element is used directly, so a clip stopped while starting, or blocked until the
    // learner taps something, ends quietly instead of throwing.
    if (Platform.OS === 'web' && typeof Audio !== 'undefined') {
      const audio = new Audio(url);
      audio.playbackRate = rate;
      audio.onloadedmetadata = () => expect(audio.duration);
      audio.onended = () => entry.finish();
      audio.onerror = () => entry.finish();
      release = () => {
        audio.onended = audio.onerror = audio.onloadedmetadata = null;
        audio.pause();
        audio.removeAttribute('src');
      };
      audio.play().catch(() => entry.finish());
      return;
    }

    const clip = createAudioPlayer(url);
    let timed = false;
    const subscription = clip.addListener('playbackStatusUpdate', (status) => {
      if (status.didJustFinish) {
        entry.finish();
      } else if (!timed && status.isLoaded && status.duration > 0) {
        timed = true;
        expect(status.duration);
      }
    });
    release = () => {
      subscription.remove();
      clip.remove();
    };
    if (rate !== 1) {
      clip.setPlaybackRate(rate, 'high');
    }
    clip.play();
  });
}

/**
 * Says one part in Luna's server voice, sentence by sentence: the next sentence is made while this one plays. Returns
 * how many sentences were said (fewer than all when the server stops voicing, so the phone takes over) or -1 when
 * Luna was interrupted.
 */
async function speakOnServer(pieces: SpeechPiece[], part: SpokenPart, settings: VoiceSettings, slow: boolean, mine: number) {
  const pace = rateOf(settings, slow);
  let next = serverClip(pieces[0].text, part.languageTag, settings, false);
  for (let i = 0; i < pieces.length; i++) {
    const url = await next;
    if (generation !== mine) {
      return -1;
    }
    if (!url) {
      return i;
    }
    if (i + 1 < pieces.length) {
      next = serverClip(pieces[i + 1].text, part.languageTag, settings, false);
    }
    setSpeaking(true);
    await playClip(url, slow ? 0.75 : 1);
    if (generation !== mine) {
      return -1;
    }
    if (i + 1 < pieces.length) {
      await delay(pieces[i].pauseMs / pace);
    }
  }
  return pieces.length;
}

/** Says pieces on the phone's speech engine, pausing after each but the last. False when Luna was interrupted. */
async function speakOnDevice(
  pieces: SpeechPiece[],
  part: SpokenPart,
  voice: Speech.Voice | null,
  settings: VoiceSettings,
  slow: boolean,
  mine: number,
) {
  for (let i = 0; i < pieces.length; i++) {
    if (generation !== mine) {
      return false;
    }
    setSpeaking(true);
    await sayPiece(pieces[i].text, part.languageTag, voice, settings, slow);
    if (i + 1 < pieces.length) {
      // A slower voice gets longer pauses, so the rhythm stays the same.
      await delay(pieces[i].pauseMs / rateOf(settings, slow));
    }
  }
  return generation === mine;
}

/**
 * Reads parts in order, each in its own language's voice: English in Luna's server voice when there is one, anything
 * else (or everything, without a server voice) on the phone's speech engine with pauses at commas and full stops. A
 * part in a language the phone can't speak is skipped (its English follows). Resolves when Luna is done or
 * interrupted.
 */
export async function speakKaiParts(
  parts: SpokenPart[],
  settings: VoiceSettings,
  options: { slow?: boolean; onDone?: () => void } = {},
): Promise<void> {
  stopKai();
  const mine = generation;
  const slow = options.slow ?? false;
  try {
    // Chrome drops an utterance queued straight after cancelling the last one.
    if (Platform.OS === 'web') {
      await delay(60);
    }
    // A part is said when the server voice or the phone speaks its language; a Kannada part with neither is skipped,
    // and its English follows.
    const spoken = [];
    for (const part of parts) {
      const onServer = await serverSpeaks(part.languageTag);
      const voice = await voiceFor(part.languageTag);
      const onPhone = voice != null || isEnglish(part.languageTag);
      if (onServer || onPhone) {
        spoken.push({ part, voice, onServer, onPhone });
      }
    }
    // The part after the first (say the English after a Kannada line) is made while the first is read.
    for (const { part, onServer } of spoken.slice(1)) {
      const [first] = speechPieces(part.text, part.languageTag, 'sentence');
      if (first && onServer) {
        void serverClip(first.text, part.languageTag, settings, false);
      }
    }
    for (let p = 0; p < spoken.length; p++) {
      const { part, voice, onServer, onPhone } = spoken[p];
      if (p > 0) {
        await delay(PART_PAUSE_MS / rateOf(settings, slow));
      }
      if (generation !== mine) {
        return;
      }
      const sentences = speechPieces(part.text, part.languageTag, 'sentence');
      const said = onServer ? await speakOnServer(sentences, part, settings, slow, mine) : 0;
      if (said < 0) {
        return;
      }
      if (said < sentences.length && onPhone) {
        const rest = sentences.slice(said).map((piece) => piece.text).join(' ');
        if (!(await speakOnDevice(speechPieces(rest, part.languageTag), part, voice, settings, slow, mine))) {
          return;
        }
      }
    }
  } finally {
    if (generation === mine) {
      setSpeaking(false);
    }
    options.onDone?.();
  }
}

/**
 * Voices a line from Luna. Plays server audio when the line comes with some, otherwise reads it in Luna's voice
 * (speakKaiParts). `slow` is the 0.75x replay.
 */
export async function speakKai(line: KaiLine, options: { slow?: boolean; onDone?: () => void } = {}): Promise<void> {
  if (line.speech.mode === 'AUDIO' && line.speech.audioUrl) {
    stopKai();
    const mine = generation;
    try {
      setSpeaking(true);
      await playClip(absoluteUrl(line.speech.audioUrl), line.speech.rate * (options.slow ? 0.75 : 1));
    } finally {
      if (generation === mine) {
        setSpeaking(false);
      }
      options.onDone?.();
    }
    return;
  }
  await speakKaiParts([{ text: line.text, languageTag: line.speech.languageTag }], line.speech, options);
}

/** Reads out text Luna wrote but never said (like the smoother version of an answer), in Luna's voice settings. */
export function speakAsKai(text: string, like: VoiceSettings, options: { slow?: boolean } = {}): Promise<void> {
  return speakKaiParts([{ text, languageTag: like.languageTag }], like, options);
}

/**
 * Speaks a line and resolves when Luna has finished, or after `maxMs` in case audio never reports back.
 */
export function speakKaiAndWait(line: KaiLine, maxMs = 30_000): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, maxMs);
    const done = () => {
      clearTimeout(timer);
      resolve();
    };
    speakKai(line, { onDone: done }).catch(done);
  });
}

export function stopKai(): void {
  generation++;
  Speech.stop().catch(() => undefined);
  playing?.finish();
  setSpeaking(false);
}
