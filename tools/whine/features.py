"""How a voice sounded: app/.../speech/VoiceFeatures.kt, line for line, to check it on recordings.

    from features import measure, read_wav
    measure(read_wav("clip.wav"))  -> dict of the same measures the app sends Qwen
"""

import wave

import numpy as np

RATE = 16000
FRAME = 640          # 40 ms
HOP = 160            # 10 ms
F0_MIN = 120.0
F0_MAX = 800.0
YIN_THRESHOLD = 0.15


def read_wav(path):
    """16 kHz mono 16-bit PCM as int16."""
    with wave.open(str(path)) as w:
        assert w.getframerate() == RATE and w.getnchannels() == 1 and w.getsampwidth() == 2, path
        return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16)


def _yin(x, o):
    max_lag = int(RATE / F0_MIN)
    min_lag = int(RATE / F0_MAX)
    w = FRAME - max_lag
    seg = x[o:o + FRAME]
    base = seg[:w]
    d = np.zeros(max_lag + 1, dtype=np.float32)
    for lag in range(1, max_lag + 1):
        v = base - seg[lag:lag + w]
        d[lag] = np.dot(v, v)
    run = np.cumsum(d[1:])
    lags = np.arange(1, max_lag + 1)
    cm = np.ones_like(d)
    cm[1:] = np.where(run > 0, d[1:] * lags / np.where(run > 0, run, 1), 1.0)
    lag = min_lag
    while lag < max_lag:
        if cm[lag] < YIN_THRESHOLD:
            while lag + 1 < max_lag and cm[lag + 1] < cm[lag]:
                lag += 1
            a, b, c = cm[lag - 1], cm[lag], cm[lag + 1]
            den = a - 2 * b + c
            shift = 0.5 * (a - c) / den if abs(den) > 1e-9 else 0.0
            return RATE / (lag + shift)
        lag += 1
    return 0.0


def _smooth(f0):
    c = f0.copy()
    for i in range(1, len(f0) - 1):
        if c[i] > 0 and c[i - 1] == 0 and c[i + 1] == 0:
            f0[i] = 0
        elif c[i] > 0 and c[i - 1] > 0 and c[i + 1] > 0:
            f0[i] = sorted((c[i - 1], c[i], c[i + 1]))[1]


def _peaks(db, gate):
    count, last, valley = 0, -100, float("inf")
    for i in range(1, len(db) - 1):
        valley = min(valley, db[i])
        if db[i] > gate and db[i] >= db[i - 1] and db[i] > db[i + 1] and db[i] - valley > 3 and i - last >= 10:
            count += 1
            last = i
            valley = db[i]
    return count


def _pct(a, q):
    s = np.sort(a)
    return float(s[int((len(s) - 1) * q)]) if len(s) else 0.0


def measure(pcm):
    x = pcm.astype(np.float32) / 32768.0
    n = len(x)
    frames = 0 if n < FRAME else (n - FRAME) // HOP + 1
    db = np.array([10 * np.log10(np.mean(x[i * HOP:i * HOP + FRAME] ** 2) + 1e-10) for i in range(frames)], dtype=np.float32)
    f0 = np.zeros(frames, dtype=np.float32)
    floor = _pct(db, 0.1)
    gate = max(floor + 12, -50)
    for i in range(frames):
        if db[i] > gate:
            f0[i] = _yin(x, i * HOP)
    _smooth(f0)
    voiced = np.nonzero(f0 > 0)[0]
    seconds = n / RATE
    if len(voiced) < 10:
        return {"seconds": seconds, "voiced_share": len(voiced) * HOP / RATE / max(seconds, 0.01)}
    hz = f0[voiced]
    st = 12 * np.log2(hz / 100)
    runs, start, last = [], -1, -10
    for i in voiced:
        if i - last > 3:
            if start >= 0:
                runs.append((start, last))
            start = i
        last = i
    runs.append((start, last))
    run_s = np.array([(b - a + 1) * HOP / RATE for a, b in runs])
    voiced_s = len(voiced) * HOP / RATE
    steps = []
    for a, b in runs:
        for i in range(a + 1, b + 1):
            if f0[i] > 0 and f0[i - 1] > 0:
                d = abs(12 * np.log2(f0[i] / f0[i - 1]))
                if d < 3:
                    steps.append(d)
    return {
        "seconds": seconds,
        "voiced_share": voiced_s / max(seconds, 0.01),
        "pitch_median_hz": _pct(hz, 0.5),
        "pitch_high_hz": _pct(hz, 0.9),
        "pitch_spread_st": float(np.std(st)),
        "pitch_range_st": _pct(st, 0.9) - _pct(st, 0.1),
        "glide_st_per_s": float(np.mean(steps) * RATE / HOP) if steps else 0.0,
        "longest_vowel_s": float(run_s.max()),
        "long_vowel_share": float(run_s[run_s > 0.5].sum() / max(voiced_s, 0.01)),
        "loudness_db": float(np.mean(db[voiced])),
        "loudness_spread_db": float(np.std(db[voiced])),
        "syllables_per_s": _peaks(db, gate) / max(voiced_s, 0.01),
    }
