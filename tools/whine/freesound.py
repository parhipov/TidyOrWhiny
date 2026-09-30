"""Child voices from Freesound (Creative Commons), as 16 kHz mono WAV, to check VoiceFeatures against.

    tools/.venv/Scripts/python tools/whine/freesound.py [--per 30]
Writes tools/whine/data/<class>/fs<id>.wav and tools/whine/data/meta.csv (id, author, title,
licence, page: what attribution needs). The class is what the search asked for and the title
agrees with; nobody listened, so a few will be wrong. Uses the public previews, first 30 s.
"""

import argparse
import csv
import html
import os
import re
import subprocess
import time
import urllib.parse
import urllib.request

import imageio_ffmpeg

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
UA = {"User-Agent": "Mozilla/5.0 (TidyOrWhiny voice check)"}

CLASSES = {
    "whine": ["child whining", "kid whining", "whiny kid", "girl whining", "boy whining", "child whine", "kid whine"],
    "begging": ["child begging", "kid begging", "child pleading", "kid please please", "child asking please"],
    "calm": ["child talking", "kid talking", "girl talking", "boy talking", "child speaking", "child reading", "kid counting"],
    "crying": ["child crying", "kid crying", "girl crying", "boy crying"],
}
# a title with one of these is not a child's voice, or not only one
NOT = re.compile(r"baby|infant|newborn|dog|puppy|cat\b|kitten|door|machine|engine|squeak|wind|toy|robot|monster|"
                 r"alien|zombie|goat|sheep|horse|pig|bird|music|song|sing|laugh|fx|effect|synth|remix|voice ?over|adult|"
                 r"woman|man\b|men\b|crowd|playground|school|class|children\b|kids\b|group", re.I)
CHILD = re.compile(r"child|kid|girl|boy|son\b|daughter|toddler|little|years? old|\byo\b|\d ?y\.?o\b", re.I)
# the title must say what the class is, not only the search: Freesound matches tags and descriptions too
SAYS = {
    "whine": re.compile(r"whin|wanna|not fair|moan|complain|nag|fuss", re.I),
    "begging": re.compile(r"pleas|beg|can i|i want|gimme|mommy|mummy", re.I),
    "calm": re.compile(r"talk|speak|story|read|count|says|saying|telling|chat|explain|question", re.I),
    "crying": re.compile(r"cry|sob|tears", re.I),
}


def search(query, page):
    url = "https://freesound.org/search/?" + urllib.parse.urlencode({"q": query, "page": page})
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        h = r.read().decode("utf-8")
    out = []
    for block in h.split('class="bw-player"')[1:]:
        get = lambda k: html.unescape(m.group(1)) if (m := re.search(k + r'="([^"]*)"', block)) else ""
        lic = re.search(r'title="License: ([^"]+)"', block)
        out.append({"id": get("data-sound-id"), "user": get("data-username"), "title": get("data-title"),
                    "mp3": get("data-mp3"), "seconds": get("data-duration"), "licence": lic.group(1) if lic else ""})
    return out


def grab(mp3, dst):
    tmp = dst + ".mp3"
    with urllib.request.urlopen(urllib.request.Request(mp3, headers=UA), timeout=60) as r, open(tmp, "wb") as f:
        f.write(r.read())
    ok = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", tmp, "-t", "30",
                         "-ac", "1", "-ar", "16000", "-sample_fmt", "s16", dst], capture_output=True).returncode == 0
    os.remove(tmp)
    return ok


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--per", type=int, default=30)
    a = p.parse_args()
    os.makedirs(DATA, exist_ok=True)
    meta_path = os.path.join(DATA, "meta.csv")
    seen = set()
    if os.path.exists(meta_path):
        with open(meta_path, encoding="utf-8") as f:
            seen = {r["id"] for r in csv.DictReader(f)}
    new = not os.path.exists(meta_path)
    with open(meta_path, "a", newline="", encoding="utf-8") as mf:
        w = csv.DictWriter(mf, fieldnames=["class", "id", "user", "title", "licence", "seconds", "page"])
        if new:
            w.writeheader()
        for cls, queries in CLASSES.items():
            folder = os.path.join(DATA, cls)
            os.makedirs(folder, exist_ok=True)
            have = len([f for f in os.listdir(folder) if f.endswith(".wav")])
            for q in queries:
                for page in (1, 2, 3, 4, 5):
                    if have >= a.per:
                        break
                    try:
                        results = search(q, page)
                    except Exception as e:
                        print(f"  search {q!r} p{page}: {e}")
                        break
                    if not results:
                        break
                    for s in results:
                        if have >= a.per:
                            break
                        t = s["title"]
                        if s["id"] in seen or not s["mp3"] or NOT.search(t) or not SAYS[cls].search(t) or not CHILD.search(t):
                            continue
                        if s["seconds"] and float(s["seconds"]) < 1.0:
                            continue
                        seen.add(s["id"])
                        try:
                            ok = grab(s["mp3"], os.path.join(folder, f"fs{s['id']}.wav"))
                        except Exception as e:
                            print(f"  {s['id']}: {e}")
                            ok = False
                        if ok:
                            have += 1
                            w.writerow({"class": cls, "id": s["id"], "user": s["user"], "title": t, "licence": s["licence"],
                                        "seconds": s["seconds"],
                                        "page": f"https://freesound.org/people/{s['user']}/sounds/{s['id']}/"})
                            mf.flush()
                            print(f"  {cls} {have}/{a.per}: {t}", flush=True)
                        time.sleep(0.3)
            print(f"{cls}: {have}", flush=True)


if __name__ == "__main__":
    main()
