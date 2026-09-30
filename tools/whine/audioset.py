"""AudioSet clips of a few classes, as 16 kHz mono WAV, to check VoiceFeatures against.

    tools/.venv/Scripts/python tools/whine/audioset.py [--per 40]
Writes tools/whine/data/<class>/<ytid>_<start>.wav (git-ignored: YouTube audio, for checking only).
AudioSet is 10 s segments of YouTube videos with human labels; many videos are gone, those are skipped.
"""

import argparse
import csv
import io
import os
import subprocess
import sys
import urllib.request

import imageio_ffmpeg

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
CSV = "http://storage.googleapis.com/us_audioset/youtube_corpus/v1/csv/"

# class -> (labels a clip must have, labels it must not have)
CLASSES = {
    "whimper": ({"Whimper"}, {"Music", "Dog", "Animal", "Baby cry, infant cry"}),
    "child_speech": ({"Child speech, kid speaking"},
                     {"Whimper", "Crying, sobbing", "Screaming", "Shout", "Yell", "Children shouting", "Music",
                      "Baby cry, infant cry", "Singing"}),
    "crying": ({"Crying, sobbing"}, {"Baby cry, infant cry", "Music", "Singing"}),
}


def fetch(name):
    with urllib.request.urlopen(CSV + name) as r:
        return r.read().decode("utf-8")


def segments():
    names = {row["mid"]: row["display_name"] for row in csv.DictReader(io.StringIO(fetch("class_labels_indices.csv")))}
    out = []
    for f in ("eval_segments.csv", "balanced_train_segments.csv"):
        for line in fetch(f).splitlines():
            if line.startswith("#"):
                continue
            ytid, start, end, labels = [p.strip() for p in line.split(",", 3)]
            out.append((ytid, float(start), float(end), {names.get(m, m) for m in labels.strip('"').split(",")}))
    return out


def grab(ytid, start, end, dst):
    """The segment's audio, cut and resampled by ffmpeg; False when the video is gone."""
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    tmp = dst + ".part"
    cmd = [sys.executable, "-m", "yt_dlp", "-q", "--no-warnings", "-f", "bestaudio/best",
           "--download-sections", f"*{start}-{end}", "--ffmpeg-location", ffmpeg,
           "-o", tmp + ".%(ext)s", f"https://www.youtube.com/watch?v={ytid}"]
    if subprocess.run(cmd, capture_output=True, timeout=180).returncode != 0:
        return False
    got = [f for f in os.listdir(os.path.dirname(dst)) if f.startswith(os.path.basename(tmp))]
    if not got:
        return False
    src = os.path.join(os.path.dirname(dst), got[0])
    ok = subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", src, "-ac", "1", "-ar", "16000",
                         "-sample_fmt", "s16", dst], capture_output=True).returncode == 0
    os.remove(src)
    return ok


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--per", type=int, default=40, help="clips per class")
    a = p.parse_args()
    segs = segments()
    for cls, (need, avoid) in CLASSES.items():
        folder = os.path.join(DATA, cls)
        os.makedirs(folder, exist_ok=True)
        have = len([f for f in os.listdir(folder) if f.endswith(".wav")])
        picks = [s for s in segs if need <= s[3] and not (avoid & s[3])]
        print(f"{cls}: {len(picks)} segments in AudioSet, {have} here")
        for ytid, start, end, _ in picks:
            if have >= a.per:
                break
            dst = os.path.join(folder, f"{ytid}_{int(start)}.wav")
            if os.path.exists(dst):
                continue
            try:
                ok = grab(ytid, start, end, dst)
            except subprocess.TimeoutExpired:
                ok = False
            if ok:
                have += 1
                print(f"  {cls} {have}/{a.per}: {ytid}", flush=True)


if __name__ == "__main__":
    main()
