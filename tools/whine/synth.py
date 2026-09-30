"""Synthetic voices with known answers, shared by the Kotlin test: python tools/whine/synth.py"""

import numpy as np

from features import RATE, measure


def voice(f0_start, f0_end, seconds, loud=0.3):
    """A buzzy voice: 5 harmonics, pitch sliding linearly from f0_start to f0_end."""
    t = np.arange(int(seconds * RATE)) / RATE
    f0 = np.linspace(f0_start, f0_end, len(t))
    phase = 2 * np.pi * np.cumsum(f0) / RATE
    x = sum(np.sin(k * phase) / k for k in range(1, 6))
    ramp = np.minimum(1, np.minimum(t, t[-1] - t) / 0.02)
    return loud * x / 2.3 * ramp


def silence(seconds):
    return np.zeros(int(seconds * RATE))


def calm():
    """Short steady syllables at 260 Hz, 0.18 s each with 0.08 s gaps: quick, level talk."""
    parts = []
    for i in range(12):
        f = 260 + 10 * np.sin(i)
        parts += [voice(f, f + 5, 0.18), silence(0.08)]
    return np.concatenate([silence(0.3)] + parts + [silence(0.3)])


def whine():
    """Two drawn-out vowels at 420-520 Hz, 1.0 s each, gliding up then down."""
    return np.concatenate([silence(0.3), voice(420, 520, 1.0), silence(0.25), voice(520, 430, 1.0), silence(0.3)])


def pcm(x):
    rng = np.random.default_rng(0)
    return np.clip((x + rng.normal(0, 0.002, len(x))) * 32767, -32768, 32767).astype(np.int16)


if __name__ == "__main__":
    for name, f in (("calm", calm), ("whine", whine)):
        m = measure(pcm(f()))
        print(name, {k: round(v, 2) for k, v in m.items()})
