"""Luna's voice: a small web service that turns text into soft, natural speech with Kokoro-82M, an open neural
text-to-speech model (Apache-2.0) that runs on an ordinary CPU. The backend calls it when TTS_PROVIDER=kokoro; the app
never talks to it directly.

    pip install -r requirements.txt
    python server.py                    # listens on http://localhost:8090

or `docker compose up -d tts`. The first start downloads the model (about 350 MB) into MODEL_DIR. On a laptop CPU a
sentence takes about as long to make as to say (longer on battery), which is why the app fetches ahead.

    POST /speak   {"text": "Hi! I'm Luna.", "voice": "af_heart", "speed": 0.9, "priority": "now"}  ->  audio/mpeg
    GET  /health  ->  {"ready": true, "voices": [...]}

Every clip is kept in CACHE_DIR, so a line is only ever made once. Clips are made one at a time (a CPU makes two no
faster one after the other), and speech that's about to play ("now") goes ahead of speech fetched early ("later").

Kokoro reads through espeak-ng phonemes; espeak-ng (GPL-3.0) is loaded as a separate library by espeakng-loader.
"""
import hashlib
import heapq
import itertools
import json
import logging
import os
import threading
import time
import urllib.request
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import lameenc
import numpy as np
import onnxruntime as ort
from kokoro_onnx import Kokoro

HERE = Path(__file__).parent
PORT = int(os.environ.get("PORT", "8090"))
MODEL_DIR = Path(os.environ.get("MODEL_DIR", HERE / "models"))
CACHE_DIR = Path(os.environ.get("CACHE_DIR", HERE / "cache"))
# Eight threads was fastest on a 10-core laptop; more than the cores slows it down.
THREADS = int(os.environ.get("THREADS", str(min(8, os.cpu_count() or 4))))
# Kokoro v1.0 exported with how long each sound lasts (the "timestamped" export), so pauses can go exactly after each
# punctuation mark; and its voices.
MODEL = ("kokoro-v1.0-timestamped.onnx",
         "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX-timestamped/resolve/main/onnx/model.onnx")
VOICES = ("voices-v1.0.bin",
          "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin")
# English voices only: Luna reads other languages with the phone's own voice. af_ is American, bf_ British.
ACCENTS = {"af": "en-us", "bf": "en-gb"}
MAX_TEXT = 400
# Change this whenever something below changes how a clip sounds, so clips cached before aren't reused.
SOUND_VERSION = 4

# The shortest pauses, in seconds, as in everyday talk: a breath after a comma, semicolon, colon or dash, and a short
# beat after a sentence inside a clip. Where Kokoro already pauses this long, nothing is added.
CLAUSE_PAUSE = 0.12
SENTENCE_PAUSE = 0.3
CLAUSE_MARKS, SENTENCE_MARKS = ",;:—", ".!?…"
# Softer: the treble above about 6 kHz turned down a little, which takes the edge off "s" sounds on phone speakers.
SOFTEN_DB = 3.0
# Every clip equally loud: the RMS of its voiced parts.
TARGET_RMS = 0.1

log = logging.getLogger("kai-voice")


def download(name, url):
    path = MODEL_DIR / name
    if path.exists():
        return path
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    log.info("downloading %s", name)
    partial = path.with_suffix(path.suffix + ".part")
    urllib.request.urlretrieve(url, partial)
    partial.replace(path)
    return path


def add_pauses(samples, rate, timings):
    """Makes sure every punctuation mark inside the clip gets at least a natural pause. Where Kokoro already left some
    silence at the mark, any more goes in the middle of it; where it ran straight on, the silence goes between the two
    words, faded in and out so it doesn't click."""
    words = [t for t in timings if t.phoneme.strip() and t.phoneme not in CLAUSE_MARKS + SENTENCE_MARKS]
    if not words:
        return samples
    speech_ends = max(t.end for t in words)
    hop = rate // 100
    frames = len(samples) // hop
    rms = np.sqrt(np.mean(samples[: frames * hop].reshape(frames, hop) ** 2, axis=1))
    quiet = rms < max(0.004, 0.08 * np.percentile(rms, 90))
    fade = int(rate * 0.006)
    parts, cut = [], 0
    for t in timings:
        pause = SENTENCE_PAUSE if t.phoneme in SENTENCE_MARKS else CLAUSE_PAUSE if t.phoneme in CLAUSE_MARKS else 0
        if not pause or t.start >= speech_ends:  # the mark that ends the clip: the app pauses after it
            continue
        at = min(frames - 1, int(t.end * rate) // hop)
        # The quiet stretch at the mark, if there is one within 80 ms.
        near = [f for f in range(max(0, at - 8), min(frames, at + 9)) if quiet[f]]
        if near:
            middle = min(near, key=lambda f: abs(f - at))
            start = end = middle
            while start > 0 and quiet[start - 1]:
                start -= 1
            while end + 1 < frames and quiet[end + 1]:
                end += 1
            have = (end - start + 1) / 100
            splice = ((start + end + 1) // 2) * hop
        else:
            have, splice = 0.0, at * hop
        if pause <= have or splice <= cut:
            continue
        before = samples[cut:splice].copy()
        if not near and len(before) > fade:
            before[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)
        parts += [before, np.zeros(int(rate * (pause - have)), np.float32)]
        cut = splice
        if not near:
            after = min(len(samples), cut + fade)
            samples = samples.copy()
            samples[cut:after] *= np.linspace(0, 1, after - cut, dtype=np.float32)
    parts.append(samples[cut:])
    return np.concatenate(parts)


def soften(samples, rate):
    """Turns the treble down by SOFTEN_DB, easing in from 5 to 8 kHz."""
    spectrum = np.fft.rfft(samples)
    t = np.clip((np.fft.rfftfreq(len(samples), 1 / rate) - 5000) / 3000, 0, 1)
    gain = 10 ** (-SOFTEN_DB * t * t * (3 - 2 * t) / 20)
    return np.fft.irfft(spectrum * gain, n=len(samples)).astype(np.float32)


def level(samples, rate):
    """Brings the voiced parts to TARGET_RMS, keeps peaks under full scale and fades the ends in and out so a clip
    never starts or stops with a click."""
    hop = rate // 100
    frames = len(samples) // hop
    if frames:
        rms = np.sqrt(np.mean(samples[: frames * hop].reshape(frames, hop) ** 2, axis=1))
        voiced = rms[rms > 0.1 * rms.max()]
        loudness = float(np.sqrt(np.mean(voiced ** 2))) if voiced.size else 0.0
        if loudness > 0:
            samples = samples * (TARGET_RMS / loudness)
    peak = float(np.abs(samples).max()) if samples.size else 0.0
    if peak > 0.97:
        samples = samples * (0.97 / peak)
    fade = min(len(samples) // 2, int(rate * 0.008))
    if fade:
        ramp = np.linspace(0, 1, fade, dtype=np.float32)
        samples[:fade] *= ramp
        samples[-fade:] *= ramp[::-1]
    return samples


def mp3(samples, rate):
    encoder = lameenc.Encoder()
    encoder.set_bit_rate(56)
    encoder.set_in_sample_rate(rate)
    encoder.set_channels(1)
    encoder.set_quality(2)
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2").tobytes()
    return bytes(encoder.encode(pcm) + encoder.flush())


class Job:
    """One clip to make. Everyone asking for the same clip waits on the same job."""

    def __init__(self, key, text, voice, speed):
        self.key, self.text, self.voice, self.speed = key, text, voice, speed
        self.priority = None
        self.done = threading.Event()
        self.audio = None
        self.error = None


class Voice:
    def __init__(self):
        options = ort.SessionOptions()
        options.intra_op_num_threads = THREADS
        options.inter_op_num_threads = 1
        session = ort.InferenceSession(str(download(*MODEL)), options, providers=["CPUExecutionProvider"])
        self.kokoro = Kokoro.from_session(session, str(download(*VOICES)))
        # kokoro-onnx looks for an output called "duration"; this export calls it "durations" (the second output,
        # which is where kokoro-onnx reads it from).
        self.kokoro.has_timings = len(session.get_outputs()) > 1
        self.voices = sorted(v for v in self.kokoro.get_voices() if v[:2] in ACCENTS)
        self.queue = []
        self.jobs = {}
        self.order = itertools.count()
        self.wake = threading.Condition()
        self.kokoro.create("Hello.", voice="af_heart", speed=1.0, lang="en-us")  # the first run is slow; get it done
        threading.Thread(target=self.work, daemon=True).start()

    @staticmethod
    def path(key):
        return CACHE_DIR / key[:2] / f"{key}.mp3"

    def speak(self, text, voice, speed, soon):
        """The clip for this text: from the cache, or made (waiting for its turn) and cached."""
        key = hashlib.sha256(json.dumps([SOUND_VERSION, MODEL, voice, round(speed, 2), text]).encode()).hexdigest()
        path = self.path(key)
        if path.exists():
            return path.read_bytes()
        priority = 0 if soon else 1
        with self.wake:
            job = self.jobs.setdefault(key, Job(key, text, voice, speed))
            # Queue it (again) when it's new or now wanted sooner; the worker skips copies of finished jobs.
            if job.priority is None or priority < job.priority:
                job.priority = priority
                heapq.heappush(self.queue, (priority, next(self.order), job))
                self.wake.notify()
        if not job.done.wait(timeout=300):
            raise TimeoutError("the voice is busy")
        if job.error:
            raise RuntimeError(job.error)
        return job.audio

    def work(self):
        while True:
            with self.wake:
                while not self.queue:
                    self.wake.wait()
                _, _, job = heapq.heappop(self.queue)
            if job.done.is_set():
                continue
            try:
                job.audio = self.make(job)
                path = self.path(job.key)
                path.parent.mkdir(parents=True, exist_ok=True)
                partial = path.with_suffix(".part")
                partial.write_bytes(job.audio)
                partial.replace(path)
            except Exception as e:  # noqa: BLE001 - the request that asked reports it
                log.exception("couldn't make %r", job.text)
                job.error = str(e)
            finally:
                with self.wake:
                    self.jobs.pop(job.key, None)
                job.done.set()

    def make(self, job):
        started = time.monotonic()
        # The pauses are added here, so kokoro-onnx adds none of its own.
        samples, rate, timings = self.kokoro.create_timed(job.text, voice=job.voice, speed=job.speed,
                                                          lang=ACCENTS[job.voice[:2]], sentence_pause=0, clause_pause=0)
        samples = add_pauses(np.asarray(samples, np.float32), rate, timings)
        samples = level(soften(samples, rate), rate)
        audio = mp3(samples, rate)
        log.info("%s %.1f s of speech in %.1f s: %s", "now" if job.priority == 0 else "early", len(samples) / rate,
                 time.monotonic() - started, job.text[:70])
        return audio


VOICE = None


class Handler(BaseHTTPRequestHandler):
    server_version = "KaiVoice/1"

    def do_GET(self):
        if self.path != "/health":
            return self.reply(HTTPStatus.NOT_FOUND, {"error": "not found"})
        return self.reply(HTTPStatus.OK, {"ready": True, "voices": VOICE.voices})

    def do_POST(self):
        if self.path != "/speak":
            return self.reply(HTTPStatus.NOT_FOUND, {"error": "not found"})
        try:
            raw = self.read_body()
            if raw is None:
                return self.reply(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"error": "too long"})
            body = json.loads(raw or b"{}")
            text = " ".join(str(body.get("text", "")).split())
            voice = str(body.get("voice", "af_heart"))
            speed = min(1.5, max(0.5, float(body.get("speed", 1.0))))
            soon = body.get("priority", "now") != "later"
        except (ValueError, TypeError):
            return self.reply(HTTPStatus.BAD_REQUEST, {"error": "expected JSON with text, voice and speed"})
        if not text or len(text) > MAX_TEXT:
            return self.reply(HTTPStatus.BAD_REQUEST, {"error": f"text must be 1 to {MAX_TEXT} characters"})
        if voice not in VOICE.voices:
            return self.reply(HTTPStatus.BAD_REQUEST, {"error": f"unknown voice {voice}"})
        try:
            audio = VOICE.speak(text, voice, speed, soon)
        except Exception as e:  # noqa: BLE001
            return self.reply(HTTPStatus.SERVICE_UNAVAILABLE, {"error": str(e)})
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "audio/mpeg")
        self.send_header("Content-Length", str(len(audio)))
        self.end_headers()
        self.wfile.write(audio)

    def read_body(self, limit=16_384):
        """The request body, sent with a length or in chunks (Java's HTTP client streams in chunks); None if too long."""
        if "chunked" not in self.headers.get("Transfer-Encoding", "").lower():
            length = int(self.headers.get("Content-Length") or 0)
            return None if length > limit else self.rfile.read(length)
        body = b""
        while True:
            size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
            if size == 0:
                self.rfile.readline()  # the blank line after the last chunk
                return body
            body += self.rfile.read(size)
            self.rfile.readline()  # the line break after each chunk
            if len(body) > limit:
                return None

    def reply(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass  # each clip made is logged by Voice.make


def main():
    global VOICE
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    # It warns about every line with punctuation ("words count mismatch"), which is harmless.
    logging.getLogger("phonemizer").setLevel(logging.ERROR)
    VOICE = Voice()
    log.info("ready on port %d with %d threads; voices: %s", PORT, THREADS, ", ".join(VOICE.voices))
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
