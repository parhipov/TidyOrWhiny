"""Tidy or Whiny in the browser: the page from web/ and the Inspector's two verdicts.

    python server/server.py                serve on 127.0.0.1:8808 (TOW_PORT to change)
    python server/server.py --check        ask the LLM one tiny thing, print how it went, exit

Caddy hands it https://tidy.rybinskai.ru. Listens on 127.0.0.1 only.

    POST /api/mess    {"image": "<base64 JPEG>", "lang": "ru"|"en", "time": "HH:MM"}
    POST /api/whine   {"transcript": "...", "voice": {...} | null, "lang": ..., "time": ...}

Both answer {"verdict", "level", "title", "why": [...], "tip"}, or {"error": "busy"|"llm"|"bad"}
with 429, 502 or 400. The prompts are the app's (app/.../ai/Inspector.kt) and live here, not in
the page: the page sends a photo or words and numbers, never prompt text, so the server cannot be
used to ask the model anything else.

The LLM comes from llm.json beside this file (git-ignored; llm.example.json is the shape), the
same file AITable reads: "use" picks "cloud" (the HAL gateway, OpenAI-style) or "ollama". The
key stays in that file and on this machine. Nothing is kept: no photo, no words -- one log line
per verdict, without them.
"""
import argparse, base64, binascii, collections, datetime as dt, json, mimetypes, os, re, sys, threading, time
import urllib.error, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.normpath(os.path.join(HERE, "..", "web"))
LLM_JSON = os.path.join(HERE, "llm.json")
PORT = int(os.environ.get("TOW_PORT", "8808"))

PER_IP_HOUR = 30         # verdicts one address may ask for in an hour
PER_DAY = 1000           # and everybody together in a day
MAX_BODY = 6 * 1024 * 1024   # a 1280 px JPEG is a few hundred KB; base64 adds a third
MAX_TRANSCRIPT = 2000
ATTEMPTS = 2             # the gateway stalls sometimes, a second ask usually answers
TIMEOUT = 30             # one ask; a minute in all, nobody waits longer for a verdict
MAX_TOKENS = 700         # a verdict is 100-200 tokens; this only stops a loop
RETRY = {429, 500, 502, 503, 504}

mimetypes.add_type("font/ttf", ".ttf")
mimetypes.add_type("text/javascript", ".js")


def say(*a):
    print(dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S"), *a, flush=True)


# ------------------------------------------------------------------ the prompts (Inspector.kt)
MESS_PROMPT = """You are "the Inspector", a kind and fair judge in a family app called Tidy or Whiny. A parent has photographed a child's room or play area. Judge how messy it is.

Answer with one JSON object and nothing else:
{"verdict": "tidy" | "mess", "level": 0-10, "title": "...", "why": ["...", "..."], "tip": "..."}

verdict  "mess" if things are left lying around: clothes, toys, dishes, papers on the floor, bed, desk or chairs. Otherwise "tidy".
level    how messy it is: 0 spotless, 10 total chaos.
title    the verdict in 2 to 5 plain words, stated matter-of-factly: no jokes, no puns, no exclamation marks.
why      why you decided so: 2 to 4 short points, each naming one concrete thing you can see in the photo and where it is (for a tidy room, what is in its place).
tip      one short sentence for the child: if it is a mess, the one thing to tidy first; if tidy, a word of praise. Warm and plain, never mean, no jokes.

TIME Never suggest anything that does not suit this time of day, in the tip or anywhere else.

Write title, why and tip in LANG. If the photo is not of a room, judge what you can see and say so kindly in the tip."""

WHINE_PROMPT = """You are "the Inspector", a kind and fair judge in a family app called Tidy or Whiny. A parent recorded a child. Below are measurements of how the voice sounded, when the phone could take them, and the speech-to-text transcript of the words. The transcript may lack punctuation, may be empty when there were only sounds, and drawn-out or repeated words (pleeease, noooo, but but) come through spelled out or repeated.

Judge by both the sound and the words. The sound that gives a whine away is drawn-out vowels: a whine can be told by it even when the words are harmless. Rough guides, to weigh, not rules:
calm talk  the longest vowel under about 0.6 s, little of the voiced time (under about 25%) in vowels held over 0.5 s
whining    vowels held a second or longer, much of the voiced time (over about 50%) in long vowels
begging    often the same drawn-out sound on "pleeease", with words asking for something: the words tell begging from whining
Between those, go mostly by the words.

Decide which it is:
whining  complaining, moaning, protesting, "it's not fair", "I don't want to", sad or grumpy
begging  asking for something again and again: please, can I, buy me, just one more, I want
champ    calm, polite, cheerful or helpful: no whining and no begging

Answer with one JSON object and nothing else:
{"verdict": "whining" | "begging" | "champ", "level": 0-10, "title": "...", "why": ["...", "..."], "tip": "..."}

level    how strong the whining or begging is: 0 none, 10 maximum. For champ, 0 to 2.
title    the verdict in 2 to 5 plain words, stated matter-of-factly: no jokes, no puns, no exclamation marks.
why      why you decided so: 2 to 4 short points, each quoting the words or naming the sound that gave it away (drawn-out vowels, short even syllables). Describe sounds in plain words a parent understands, no numbers or units.
tip      one short sentence for the child: a kind, plain tip, or praise for a champ. Never mean, no jokes.

TIME Never suggest anything that does not suit this time of day, in the tip or anywhere else.

Write title, why and tip in LANG.

"""

LANGS = {"ru": "Russian", "en": "English"}


def time_of_day(hhmm):
    """The child's clock (the page's), and what a tip may ask of a child then: nothing lively after 18:00."""
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", str(hhmm or ""))
    if m and int(m[1]) < 24 and int(m[2]) < 60:
        h, mi = int(m[1]), int(m[2])
    else:
        now = dt.datetime.now()
        h, mi = now.hour, now.minute
    clock = f"{h:02d}:{mi:02d}"
    if 5 <= h <= 11:
        return f"It is {clock}, morning. The tip may suit getting ready for the day: dressing, breakfast, packing a bag."
    if 12 <= h <= 17:
        return f"It is {clock}, daytime. Any tip is fine, active ones included."
    if 18 <= h <= 21:
        return (f"It is {clock}, evening: winding down before bed. The tip must be calm and quiet: tidying up quietly, "
                "a bath, pyjamas, a book, a cuddle. No dancing, running, jumping, loud games, screens or sweets.")
    return f"It is {clock}, night, past bedtime. The tip is to get to bed and sleep, quietly. Nothing active, no games, no screens."


def with_context(prompt, ask):
    return prompt.replace("LANG", LANGS.get(ask.get("lang"), "English")).replace("TIME", time_of_day(ask.get("time")))


def num(v, lo, hi):
    """A measure from the page, or None when it is not a sane number."""
    try:
        f = float(v)
    except (TypeError, ValueError):
        return None
    return f if lo <= f <= hi else None


def voice_for_prompt(v):
    """VoiceFeatures.forPrompt(), from the numbers the page measured: only those, never its text."""
    if not isinstance(v, dict):
        return None
    seconds, voiced = num(v.get("seconds"), 0, 120), num(v.get("voicedShare"), 0, 1)
    pitch = num(v.get("pitchMedianHz"), 0, 2000)
    longest, share = num(v.get("longestVowelS"), 0, 120), num(v.get("longVowelShare"), 0, 1)
    if None in (seconds, voiced, pitch, longest, share):
        return None
    lines = [f"recording length: {seconds:.1f} s", f"voice in {voiced * 100:.0f}% of the time"]
    lines.append(f"longest drawn-out vowel: {longest:.2f} s; {share * 100:.0f}% of the voiced time is in vowels held over 0.5 s"
                 if pitch > 0 else "no clear voice found")
    return "\n".join(lines)


# ------------------------------------------------------------------ the LLM (AITable's detect/llm.py, cut down)
class LLMError(Exception):
    pass


def llm_config():
    try:
        cfg = json.load(open(LLM_JSON, encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise LLMError(f"llm.json: {e}")
    kind = cfg.get("use", "cloud")
    c = cfg.get(kind) or {}
    if not c.get("base_url") or not c.get("model") or (kind == "cloud" and not c.get("api_key")):
        raise LLMError(f"llm.json: '{kind}' needs base_url, model" + (" and api_key" if kind == "cloud" else ""))
    return kind, c


def post_json(url, body, head, timeout):
    req = urllib.request.Request(url, json.dumps(body).encode(), {"Content-Type": "application/json", **head})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def chat(text, image_b64=None, max_tokens=MAX_TOKENS, attempts=ATTEMPTS, timeout=TIMEOUT):
    """One user turn, thinking off; the reply text. Retried once on what the gateway fails quickly on."""
    kind, c = llm_config()
    base = c["base_url"].rstrip("/")
    if kind == "cloud":
        content = ([{"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + image_b64}}] if image_b64 else [])
        content.append({"type": "text", "text": text})
        # the vLLM gateway obeys only chat_template_kwargs for thinking
        url, head = base + "/chat/completions", {"Authorization": f"Bearer {c['api_key']}"}
        body = {"model": c["model"], "messages": [{"role": "user", "content": content}], "stream": False,
                "temperature": 0.3, "max_tokens": max_tokens,
                "enable_thinking": False, "chat_template_kwargs": {"enable_thinking": False}}
        pick = lambda r: r["choices"][0]["message"]["content"]
    else:
        # Ollama's own /api/chat: its "think" switch is reliable, the OpenAI-style one's is not
        msg = {"role": "user", "content": text, **({"images": [image_b64]} if image_b64 else {})}
        url, head = base + "/api/chat", {}
        body = {"model": c["model"], "messages": [msg], "stream": False, "think": False,
                "keep_alive": c.get("keep_alive", "30m"),
                "options": {"num_ctx": 8192, "temperature": 0.3, "num_predict": max_tokens, **(c.get("options") or {})}}
        pick = lambda r: r["message"]["content"]
    err = "no answer"
    for k in range(attempts):
        if k:
            time.sleep(1.5)
        try:
            code, raw = post_json(url, body, head, timeout)
        except (OSError, urllib.error.URLError) as e:
            err = str(e)
            continue
        if code in RETRY:
            err = f"HTTP {code}: {' '.join(raw.split())[:200]}"
            continue
        if code >= 400:
            raise LLMError(f"HTTP {code}: {' '.join(raw.split())[:200]}")
        try:
            return pick(json.loads(raw)) or ""
        except (ValueError, KeyError, IndexError, TypeError) as e:
            raise LLMError(f"odd reply ({e}): {raw[:200]}")
    raise LLMError(err)


def json_in(raw):
    """The first {...} in a reply, past any <think> the gateway let through."""
    text = re.sub(r"<think>[\s\S]*?</think>", "", raw)
    a, b = text.find("{"), text.rfind("}")
    if a < 0 or b <= a:
        raise LLMError("no JSON in the reply: " + text[:200])
    try:
        o = json.loads(text[a:b + 1])
    except ValueError as e:
        raise LLMError(f"bad JSON ({e}): {text[:200]}")
    if not isinstance(o, dict):
        raise LLMError("the reply is not an object")
    return o


def level_of(o, default=5):
    try:
        return max(0, min(10, int(round(float(o.get("level", default))))))
    except (TypeError, ValueError):
        return default


def why_of(o):
    """The reasons, one short line each; a bare string is taken as one."""
    w = o.get("why")
    if isinstance(w, str):
        return [w.strip()] if w.strip() else []
    if isinstance(w, list):
        return [str(x).strip() for x in w if str(x).strip()][:5]
    return []


def text_of(o, key):
    v = o.get(key)
    return v.strip() if isinstance(v, str) else ""


def judge_room(ask):
    o = json_in(chat(with_context(MESS_PROMPT, ask), image_b64=ask["image"]))
    level = level_of(o)
    verdict = text_of(o, "verdict").lower()
    tidy = level <= 4 if not verdict else "tidy" in verdict
    return {"verdict": "tidy" if tidy else "mess", "level": level, "title": text_of(o, "title"),
            "why": why_of(o), "tip": text_of(o, "tip")}


def judge_words(ask):
    voice = voice_for_prompt(ask.get("voice"))
    transcript = str(ask.get("transcript") or "").strip()[:MAX_TRANSCRIPT]
    text = (with_context(WHINE_PROMPT, ask)
            + (f"How it sounded, measured on the phone:\n{voice}\n\n" if voice else "")
            + "Transcript:\n" + (transcript or "(no clear words, only voice sounds)"))
    o = json_in(chat(text))
    verdict = text_of(o, "verdict").lower()
    kind = "begging" if "beg" in verdict else "whining" if "whin" in verdict else "champ"
    return {"verdict": kind, "level": level_of(o), "title": text_of(o, "title"),
            "why": why_of(o), "tip": text_of(o, "tip")}


def check_image(b64):
    """A JPEG, PNG or WebP in base64, or None."""
    if not isinstance(b64, str) or not b64:
        return None
    b64 = b64.split(",", 1)[-1]
    try:
        head = base64.b64decode(b64[:64], validate=True)
    except (binascii.Error, ValueError):
        return None
    if head[:3] == b"\xff\xd8\xff" or head[:8] == b"\x89PNG\r\n\x1a\n" or (head[:4] == b"RIFF" and head[8:12] == b"WEBP"):
        return b64
    return None


# ------------------------------------------------------------------ limits
class Limits:
    def __init__(self):
        self.lock = threading.Lock()
        self.by_ip = collections.defaultdict(collections.deque)
        self.day = (dt.date.today(), 0)

    def allow(self, ip):
        now = time.time()
        with self.lock:
            q = self.by_ip[ip]
            while q and q[0] < now - 3600:
                q.popleft()
            day, n = self.day
            if day != dt.date.today():
                day, n = dt.date.today(), 0
            if len(q) >= PER_IP_HOUR or n >= PER_DAY:
                return False
            q.append(now)
            self.day = (day, n + 1)
            return True


LIMITS = Limits()


# ------------------------------------------------------------------ http
class Handler(BaseHTTPRequestHandler):
    server_version = "tidy"
    sys_version = ""

    def log_message(self, fmt, *a):
        pass    # Caddy keeps the access log; this one logs verdicts only

    def ip(self):
        # Caddy sets X-Forwarded-For; the server listens on loopback only, so nobody else can
        return (self.headers.get("X-Forwarded-For") or self.client_address[0]).split(",")[0].strip()

    def send(self, code, body, ctype="application/json; charset=utf-8", cache="no-store"):
        data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", cache)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = self.path.split("?", 1)[0].split("#", 1)[0]
        if path == "/api/health":
            return self.send(200, {"ok": True})
        if path.endswith("/"):
            path += "index.html"
        full = os.path.normpath(os.path.join(WEB, path.lstrip("/")))
        if not full.startswith(WEB + os.sep) or not os.path.isfile(full):
            return self.send(404, b"not found", "text/plain; charset=utf-8")
        ctype = mimetypes.guess_type(full)[0] or "application/octet-stream"
        if ctype.startswith("text/") or ctype.endswith(("json", "javascript", "+xml")):
            ctype += "; charset=utf-8"
        # the page and its code are checked every time (no-cache revalidates); fonts and icons keep a day
        cache = "public, max-age=86400" if full.endswith((".ttf", ".png", ".svg", ".ico")) else "no-cache"
        with open(full, "rb") as f:
            self.send(200, f.read(), ctype, cache)

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        if path not in ("/api/mess", "/api/whine"):
            return self.send(404, {"error": "bad"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            n = -1
        if n <= 0 or n > MAX_BODY:
            return self.send(413 if n > MAX_BODY else 400, {"error": "bad"})
        try:
            ask = json.loads(self.rfile.read(n))
            assert isinstance(ask, dict)
        except (ValueError, AssertionError):
            return self.send(400, {"error": "bad"})
        if path == "/api/mess":
            ask["image"] = check_image(ask.get("image"))
            if not ask["image"]:
                return self.send(400, {"error": "bad"})
        ip = self.ip()
        if not LIMITS.allow(ip):
            say(f"{ip:<15} {path[5:]:<5} busy")
            return self.send(429, {"error": "busy"})
        t = time.time()
        try:
            v = judge_room(ask) if path == "/api/mess" else judge_words(ask)
        except LLMError as e:
            say(f"{ip:<15} {path[5:]:<5} failed in {time.time() - t:.1f} s: {e}")
            return self.send(502, {"error": "llm"})
        say(f"{ip:<15} {path[5:]:<5} {v['verdict']} {v['level']}/10 in {time.time() - t:.1f} s, {ask.get('lang')}")
        self.send(200, v)


def warm_up():
    """An Ollama box loads the model on the first ask; better now than while a child waits."""
    try:
        kind, c = llm_config()
        t = time.time()
        chat("Hi", max_tokens=1, attempts=1, timeout=120)
        say(f"llm: {kind} {c['model']} answers ({time.time() - t:.1f} s)")
    except LLMError as e:
        say(f"llm: not ready: {e}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="ask the LLM one tiny thing and exit")
    a = ap.parse_args()
    if a.check:
        warm_up()
        return
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    srv.daemon_threads = True
    say(f"tidy: http://127.0.0.1:{PORT}/  page from {WEB}")
    threading.Thread(target=warm_up, daemon=True).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    sys.exit(main())
