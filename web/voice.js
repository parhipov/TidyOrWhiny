// How the child sounded, not what they said, and the recording that it is measured from.
// measure() is app/.../speech/VoiceFeatures.kt line for line (and tools/whine/features.py):
// change one, change the others.

export const RATE = 16000;
const FRAME = 640;          // 40 ms: two periods of a 50 Hz floor, plenty for a child's voice
const HOP = 160;            // 10 ms
const F0_MIN = 120;         // a child's voice, whines and squeals included
const F0_MAX = 800;
const YIN_THRESHOLD = 0.15;

export const MAX_MS = 30000;
export const LEVELS = 36;

/** Measures 16 kHz mono samples in -1..1. Only the numbers the server's prompt takes leave the page. */
export function measure(x) {
  const n = x.length;
  const frames = n < FRAME ? 0 : Math.floor((n - FRAME) / HOP) + 1;
  const db = new Float32Array(frames);
  const f0 = new Float32Array(frames);          // 0 = unvoiced
  for (let i = 0; i < frames; i++) {
    const o = i * HOP;
    let e = 0;
    for (let k = 0; k < FRAME; k++) e += x[o + k] * x[o + k];
    db[i] = 10 * Math.log10(e / FRAME + 1e-10);
  }
  // a voice must stand out of the room: 12 dB over the quiet floor and not near silence
  const floor = percentile(db, 0.1);
  const gate = Math.max(floor + 12, -50);
  for (let i = 0; i < frames; i++) if (db[i] > gate) f0[i] = yin(x, i * HOP);
  smooth(f0);

  const voiced = [];
  for (let i = 0; i < frames; i++) if (f0[i] > 0) voiced.push(i);
  const seconds = n / RATE;
  if (voiced.length < 10) {
    return { seconds, voicedShare: voiced.length * HOP / RATE / Math.max(seconds, 0.01),
      pitchMedianHz: 0, pitchHighHz: 0, pitchSpreadSt: 0, pitchRangeSt: 0, glideStPerS: 0,
      longestVowelS: 0, longVowelShare: 0, loudnessDb: 0, loudnessSpreadDb: 0, syllablesPerS: 0 };
  }

  const st = voiced.map(i => 12 * Math.log2(f0[i] / 100));   // semitones re 100 Hz
  const hz = voiced.map(i => f0[i]);
  const vdb = voiced.map(i => db[i]);

  // voiced stretches: runs of voiced frames, gaps of up to 30 ms bridged
  const runs = [];
  let start = -1, last = -10;
  for (const i of voiced) {
    if (i - last > 3) { if (start >= 0) runs.push([start, last]); start = i; }
    last = i;
  }
  runs.push([start, last]);
  const runS = runs.map(([a, b]) => (b - a + 1) * HOP / RATE);
  const voicedS = voiced.length * HOP / RATE;

  // glide: mean |d pitch / dt| inside stretches, frame to frame, jumps over 3 st (octave errors) left out
  let glide = 0, steps = 0;
  for (const [a, b] of runs) for (let i = a + 1; i <= b; i++) {
    if (f0[i] > 0 && f0[i - 1] > 0) {
      const d = Math.abs(12 * Math.log2(f0[i] / f0[i - 1]));
      if (d < 3) { glide += d; steps++; }
    }
  }

  return {
    seconds,
    voicedShare: voicedS / Math.max(seconds, 0.01),
    pitchMedianHz: percentile(hz, 0.5),
    pitchHighHz: percentile(hz, 0.9),
    pitchSpreadSt: std(st),
    pitchRangeSt: percentile(st, 0.9) - percentile(st, 0.1),
    glideStPerS: steps > 0 ? glide / steps * RATE / HOP : 0,
    longestVowelS: Math.max(...runS),
    longVowelShare: runS.filter(s => s > 0.5).reduce((a, b) => a + b, 0) / Math.max(voicedS, 0.01),
    loudnessDb: vdb.reduce((a, b) => a + b, 0) / vdb.length,
    loudnessSpreadDb: std(vdb),
    syllablesPerS: peaks(db, gate) / Math.max(voicedS, 0.01),
  };
}

/** YIN pitch of one frame, or 0 when it is not clearly periodic. */
function yin(x, o) {
  const maxLag = Math.trunc(RATE / F0_MIN);
  const minLag = Math.trunc(RATE / F0_MAX);
  const w = FRAME - maxLag;
  const d = new Float32Array(maxLag + 1);
  for (let lag = 1; lag <= maxLag; lag++) {
    let s = 0;
    for (let k = 0; k < w; k++) { const v = x[o + k] - x[o + k + lag]; s += v * v; }
    d[lag] = s;
  }
  let run = 0;
  for (let lag = 1; lag <= maxLag; lag++) {       // cumulative mean normalised difference
    run += d[lag];
    d[lag] = run > 0 ? d[lag] * lag / run : 1;
  }
  let lag = minLag;
  while (lag < maxLag) {
    if (d[lag] < YIN_THRESHOLD) {
      while (lag + 1 < maxLag && d[lag + 1] < d[lag]) lag++;
      // parabolic interpolation around the dip
      const a = d[lag - 1], b = d[lag], c = d[lag + 1];
      const den = a - 2 * b + c;
      const shift = Math.abs(den) > 1e-9 ? 0.5 * (a - c) / den : 0;
      return RATE / (lag + shift);
    }
    lag++;
  }
  return 0;
}

/** Median of three over voiced frames: drops single-frame octave jumps and lone voiced blips. */
function smooth(f0) {
  const c = f0.slice();
  for (let i = 1; i < f0.length - 1; i++) {
    if (c[i] > 0 && c[i - 1] === 0 && c[i + 1] === 0) f0[i] = 0;
    else if (c[i] > 0 && c[i - 1] > 0 && c[i + 1] > 0) f0[i] = [c[i - 1], c[i], c[i + 1]].sort((p, q) => p - q)[1];
  }
}

/** Syllable nuclei: loudness peaks over the gate at least 100 ms apart, 3 dB above their valleys. */
function peaks(db, gate) {
  let count = 0, lastPeak = -100, valley = Infinity;
  for (let i = 1; i < db.length - 1; i++) {
    valley = Math.min(valley, db[i]);
    if (db[i] > gate && db[i] >= db[i - 1] && db[i] > db[i + 1] && db[i] - valley > 3 && i - lastPeak >= 10) {
      count++; lastPeak = i; valley = db[i];
    }
  }
  return count;
}

function percentile(a, q) {
  if (!a.length) return 0;
  const s = Array.from(a).sort((p, r) => p - r);
  return s[Math.trunc((s.length - 1) * q)];
}

function std(a) {
  const m = a.reduce((p, r) => p + r, 0) / a.length;
  return Math.sqrt(a.reduce((p, r) => p + (r - m) * (r - m), 0) / a.length);
}

/** Box-filter resampling to 16 kHz: each output sample is the mean of the input it covers. */
function to16k(chunks, rate) {
  let n = 0;
  for (const c of chunks) n += c.length;
  const x = new Float32Array(n);
  let o = 0;
  for (const c of chunks) { x.set(c, o); o += c.length; }
  if (rate === RATE) return x;
  const r = rate / RATE;
  const out = new Float32Array(Math.floor(n / r));
  for (let i = 0; i < out.length; i++) {
    const a = Math.floor(i * r), b = Math.max(a + 1, Math.floor((i + 1) * r));
    let s = 0;
    for (let k = a; k < b; k++) s += x[k];
    out[i] = s / (b - a);
  }
  return out;
}

export const speechSupported = () => !!(window.SpeechRecognition || window.webkitSpeechRecognition);

/**
 * One recording: the microphone's samples for measuring and, where the browser has it, its
 * speech-to-text alongside. Like SpeechCapture.kt, the recogniser is started again whenever it
 * stops on its own (Chrome on Android ends a session at the first pause) until the recording ends.
 *
 * on: { level(v 0..1), partial(text), tick(ms), done({transcript, voice, speech}), fail(code) }
 * fail codes: "mic" (no permission or no microphone).
 */
export class Recording {
  constructor(lang, on) {
    this.lang = lang;
    this.on = on;
    this.chunks = [];
    this.heard = "";        // finished sessions' words
    this.session = "";      // this session's final words
    this.interim = "";
    this.live = false;
    this.speech = speechSupported();
  }

  async start() {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false },
      });
    } catch (e) {
      this.on.fail("mic");
      return;
    }
    const Ctx = window.AudioContext || window.webkitAudioContext;
    this.ctx = new Ctx();
    await this.ctx.resume().catch(() => {});
    const src = this.ctx.createMediaStreamSource(this.stream);
    // ScriptProcessor is old, but it runs everywhere without a module file; 4096 is ~85 ms at 48 kHz
    this.proc = this.ctx.createScriptProcessor(4096, 1, 1);
    this.proc.onaudioprocess = e => {
      if (!this.live) return;
      const x = e.inputBuffer.getChannelData(0);
      this.chunks.push(new Float32Array(x));
      let s = 0;
      for (let i = 0; i < x.length; i++) s += x[i] * x[i];
      const db = 10 * Math.log10(s / x.length + 1e-10);
      this.on.level(Math.min(1, Math.max(0.06, (db + 60) / 50)));     // about -60..-10 dBFS
    };
    const mute = this.ctx.createGain();
    mute.gain.value = 0;
    src.connect(this.proc);
    this.proc.connect(mute);
    mute.connect(this.ctx.destination);

    this.live = true;
    this.t0 = performance.now();
    this.timer = setInterval(() => {
      const ms = performance.now() - this.t0;
      this.on.tick(ms);
      if (ms >= MAX_MS) this.stop();
    }, 100);
    if (this.speech) this.listen();
  }

  listen() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    const rec = new SR();
    rec.lang = this.lang === "ru" ? "ru-RU" : "en-US";
    rec.continuous = true;
    rec.interimResults = true;
    rec.maxAlternatives = 1;
    this.session = ""; this.interim = "";
    rec.onresult = e => {
      let fin = "", mid = "";
      for (let i = 0; i < e.results.length; i++) {
        const t = e.results[i][0].transcript;
        if (e.results[i].isFinal) fin += t; else mid += t;
      }
      this.session = fin; this.interim = mid;
      this.on.partial(this.text(true));
    };
    rec.onerror = e => {
      // no-speech and aborted are a pause or our own stop; anything else and the words are lost
      if (!["no-speech", "aborted"].includes(e.error)) this.speech = false;
    };
    rec.onend = () => {
      this.heard = join(this.heard, this.session || this.interim);
      this.session = ""; this.interim = "";
      if (this.live && this.speech) {
        try { rec.start(); return; } catch (_) { this.speech = false; }
      }
      if (this.ending) this.ending();
    };
    this.rec = rec;
    try { rec.start(); } catch (_) { this.speech = false; }
  }

  text(withInterim) {
    return join(join(this.heard, this.session), withInterim ? this.interim : "").trim();
  }

  async stop() {
    if (!this.live) return;
    this.live = false;
    clearInterval(this.timer);
    // the recogniser hands over its last words on "end"; some never answer a stop, so 3 s at most
    if (this.rec) {
      await new Promise(done => {
        this.ending = done;
        try { this.rec.stop(); } catch (_) { done(); }
        setTimeout(done, 3000);
      });
      this.rec.onend = null;
    }
    this.release();
    const rate = this.ctx ? this.ctx.sampleRate : RATE;
    this.on.measuring();
    await new Promise(r => setTimeout(r, 30));   // let the page show "listening to the recording" first
    const voice = measure(to16k(this.chunks, rate));
    this.chunks = [];
    this.on.done({ transcript: this.text(false), voice, speech: this.speech });
  }

  /** Lets go of the microphone; the recording is dropped if it is still running. */
  cancel() {
    this.live = false;
    clearInterval(this.timer);
    if (this.rec) { this.rec.onend = null; try { this.rec.abort(); } catch (_) {} }
    this.release();
  }

  release() {
    if (this.proc) { this.proc.onaudioprocess = null; try { this.proc.disconnect(); } catch (_) {} }
    if (this.stream) this.stream.getTracks().forEach(t => t.stop());
    if (this.ctx) this.ctx.close().catch(() => {});
    this.proc = this.stream = null;
  }
}

function join(a, b) {
  a = (a || "").trim(); b = (b || "").trim();
  return a && b ? a + " " + b : a || b;
}
