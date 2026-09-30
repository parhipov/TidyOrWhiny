"""VoiceFeatures over the downloaded clips: each measure's median and quartiles per class.

    tools/.venv/Scripts/python tools/whine/evaluate.py [--csv out.csv]
A measure that tells whimper from child speech has quartile boxes that barely overlap.
"""

import argparse
import csv
import glob
import os

import numpy as np

from features import measure, read_wav

HERE = os.path.dirname(os.path.abspath(__file__))
KEYS = ["pitch_median_hz", "pitch_high_hz", "pitch_spread_st", "pitch_range_st", "glide_st_per_s",
        "longest_vowel_s", "long_vowel_share", "loudness_spread_db", "syllables_per_s", "voiced_share"]


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--csv", default="")
    a = p.parse_args()
    rows = []
    for folder in sorted(glob.glob(os.path.join(HERE, "data", "*"))):
        cls = os.path.basename(folder)
        for f in sorted(glob.glob(os.path.join(folder, "*.wav"))):
            m = measure(read_wav(f))
            if "pitch_median_hz" in m:
                rows.append({"class": cls, "clip": os.path.basename(f), **m})
    classes = sorted({r["class"] for r in rows})
    print(f"{'measure':20s}" + "".join(f"{c:>26s}" for c in classes))
    print(f"{'clips':20s}" + "".join(f"{sum(r['class'] == c for r in rows):>26d}" for c in classes))
    for k in KEYS:
        cells = []
        for c in classes:
            v = np.array([r[k] for r in rows if r["class"] == c])
            q1, med, q3 = np.percentile(v, [25, 50, 75])
            cells.append(f"{med:8.2f} [{q1:6.2f}..{q3:6.2f}]")
        print(f"{k:20s}" + "".join(f"{s:>26s}" for s in cells))
    if a.csv:
        with open(a.csv, "w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
            w.writeheader()
            w.writerows(rows)


if __name__ == "__main__":
    main()
