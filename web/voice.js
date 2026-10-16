// The recording, and nothing else: the words and how the voice sounded are made out on the
// server (Whisper, tools/whine/features.py). Browsers' own speech-to-text failed in Safari and
// then in Chrome, and it fought this recording for the microphone.

export const RATE = 16000;
export const MAX_MS = 30000;
export const LEVELS = 36;

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

/** 16-bit mono WAV of 16 kHz samples, as base64. */
function wavBase64(x) {
  const buf = new ArrayBuffer(44 + x.length * 2);
  const v = new DataView(buf);
  const str = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
  str(0, "RIFF"); v.setUint32(4, 36 + x.length * 2, true); str(8, "WAVE");
  str(12, "fmt "); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, RATE, true); v.setUint32(28, RATE * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  str(36, "data"); v.setUint32(40, x.length * 2, true);
  for (let i = 0; i < x.length; i++) v.setInt16(44 + i * 2, Math.max(-32768, Math.min(32767, Math.round(x[i] * 32768))), true);
  const bytes = new Uint8Array(buf);
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(bin);
}

/**
 * One recording from the microphone, up to MAX_MS.
 * on: { level(v 0..1), tick(ms), done(base64 WAV), fail() -- no permission or no microphone }
 */
export class Recording {
  constructor(on) {
    this.on = on;
    this.chunks = [];
    this.live = false;
  }

  async start() {
    try {
      // the raw voice: echo cancelling, noise suppression and gain control would bend what is measured
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false },
      });
    } catch (e) {
      this.on.fail();
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
  }

  stop() {
    if (!this.live) return;
    this.live = false;
    clearInterval(this.timer);
    const rate = this.ctx.sampleRate;
    this.release();
    const wav = wavBase64(to16k(this.chunks, rate));
    this.chunks = [];
    this.on.done(wav);
  }

  /** Lets go of the microphone; the recording is dropped if it is still running. */
  cancel() {
    this.live = false;
    clearInterval(this.timer);
    this.release();
  }

  release() {
    if (this.proc) { this.proc.onaudioprocess = null; try { this.proc.disconnect(); } catch (_) {} }
    if (this.stream) this.stream.getTracks().forEach(t => t.stop());
    if (this.ctx) this.ctx.close().catch(() => {});
    this.proc = this.stream = null;
  }
}
