// Tidy or Whiny in the browser: AppViewModel.kt and the screens, as one small page.
// The verdicts come from this site's own server (server/server.py); the page holds no key.

import { STRINGS } from "./strings.js";
import { mascot, face, icon, confetti } from "./art.js";
import { Recording, LEVELS, speechSupported } from "./voice.js";

const SEND_SIDE = 1280;     // long side of a photo sent; plenty to tell a mess from a tidy room
const JPEG_QUALITY = 0.85;
const SLOW_AFTER = 25000;   // the server asks a second time after 30 s; say so a little before
const GIVE_UP = 75000;

// ------------------------------------------------------------------ language
function pickLang() {
  const q = new URLSearchParams(location.search).get("lang");
  if (q === "ru" || q === "en") return q;
  try { const s = localStorage.getItem("lang"); if (s === "ru" || s === "en") return s; } catch (_) {}
  return (navigator.languages || [navigator.language || "en"]).some(l => /^(ru|be|uk|kk)\b/i.test(l)) ? "ru" : "en";
}
let lang = pickLang();
const t = (k, arg) => {
  const s = STRINGS[lang][k] ?? STRINGS.en[k] ?? k;
  return arg === undefined ? s : s.replace("%s", arg);
};
const esc = s => String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const T = (k, arg) => esc(t(k, arg));

function setLang(l) {
  lang = l;
  try { localStorage.setItem("lang", l); } catch (_) {}
  document.documentElement.lang = l;
  render();
}

// ------------------------------------------------------------------ state
// screen: home | photo | preview | recorder | analyzing | mess | whine
const S = {
  screen: "home",
  photo: null,            // {url, b64}
  photoError: false,
  rec: null,              // the Recording in progress
  recState: "idle",       // idle | denied | listening | measuring | ready | empty
  levels: [], elapsed: 0, partial: "",
  heard: null,            // {transcript, voice, speech}
  check: null,            // mess | whine, while analyzing
  error: null,            // null | generic | busy
  slow: false, step: 1,
  verdict: null,
};
let ask = null;           // the AbortController of the verdict in flight

const app = document.getElementById("app");

function go(screen, push = true) {
  if (S.screen === "recorder" && screen !== "recorder") dropRecording();
  S.screen = screen;
  if (push) history.pushState({ screen }, "");
  render();
  window.scrollTo(0, 0);
}

/** One step up the flow, as the app's system back. */
function back() {
  switch (S.screen) {
    case "preview": return go("photo", false);
    case "analyzing":
      cancelAsk();
      return go(S.check === "mess" ? (S.photo ? "preview" : "photo") : "recorder", false);
    default: return go("home", false);
  }
}
addEventListener("popstate", back);

function home() {
  cancelAsk();
  go("home");
}

// ------------------------------------------------------------------ pieces
const btn = (action, text, cls, ic) =>
  `<button class="btn ${cls}" data-action="${action}">${ic ? icon(ic) : ""}<span>${esc(text)}</span></button>`;

function topBar(title, action, ic, label, trailing = "") {
  return `<header class="bar">
    <button class="round" data-action="${action}" aria-label="${esc(label)}">${icon(ic)}</button>
    <h1 class="bar-title">${esc(title)}</h1>${trailing}</header>`;
}

function dots(current, color) {
  return `<div class="dots" aria-hidden="true">` +
    [1, 2, 3].map(i => `<i class="${i === current ? "now" : ""}" style="${i <= current ? `background:var(--${color})` : ""}"></i>`).join("") +
    `</div>`;
}

// ------------------------------------------------------------------ screens
function homeScreen() {
  return `<div class="home">
    <div class="backdrop" aria-hidden="true">
      <i class="sunblob"></i>
      <svg class="squiggle" viewBox="0 0 80 20"><path d="M4 12q12-14 24 0t24 0t24 0" fill="none" stroke="var(--violet)" stroke-width="5" stroke-linecap="round"/></svg>
      <i class="dot d1"></i><i class="dot d2"></i><i class="dot d3"></i>
    </div>
    <div class="wordmark">
      <span class="logo">${mascot("lens")}</span>
      <span class="wm"><b class="wm-t">${T("wordmark_tidy")}</b> <span class="wm-or">${T("wordmark_or")}</span> <b class="wm-w">${T("wordmark_whiny")}</b></span>
      <button class="lang" data-action="lang" aria-label="${T("other_lang_name")}">${T("other_lang")}</button>
    </div>
    <h1 class="hero">${T("slogan_1_lead")}<mark class="m-o">${T("slogan_1_mark")}</mark><br>${T("slogan_2_lead")}<mark class="m-v">${T("slogan_2_mark")}</mark><br>${T("slogan_3")}</h1>
    <p class="sub">${T("home_subtitle")}</p>
    ${card("photo", "mess_check", "mess_check_desc", "chip_photo", "camera", "orange")}
    ${card("recorder", "whine_check", "whine_check_desc", "chip_audio", "mic", "violet")}
    <p class="privacy">${T("privacy")}</p>
    <div class="peek">
      ${mascot("lens", "peek-m")}
      <p class="bubble say">${T("mascot_hello").replace("\n", "<br>")}</p>
    </div>
  </div>`;
}

function card(target, title, desc, tag, ic, color) {
  return `<button class="card sticker ${color}" data-action="open" data-target="${target}">
    <span class="card-top">
      <span class="card-icon">${icon(ic)}</span>
      <span class="card-head"><span class="tag">${T(tag)}</span><span class="card-title">${T(title)}</span></span>
    </span>
    <span class="card-bottom"><span class="card-desc">${T(desc)}</span><span class="go">${icon("arrow")}</span></span>
  </button>`;
}

function photoScreen() {
  return `<div class="screen">
    ${topBar(t("mess_check"), "home", "back", t("back"), dots(1, "orange"))}
    <main class="col center">
      <div class="halo orange">${mascot("lens", "big")}</div>
      <h2 class="title">${T("photo_title")}</h2>
      <p class="body">${T("camera_hint")}</p>
      ${S.photoError ? `<p class="note">${T("photo_failed")}</p>` : ""}
    </main>
    <footer class="actions">
      <label class="btn orange">${icon("camera")}<span>${T("take_photo")}</span>
        <input type="file" accept="image/*" capture="environment" data-photo hidden></label>
      <label class="btn paper">${icon("gallery")}<span>${T("pick_gallery")}</span>
        <input type="file" accept="image/*" data-photo hidden></label>
    </footer>
  </div>`;
}

function previewScreen() {
  return `<div class="screen">
    ${topBar(t("mess_check"), "photo", "back", t("back"), dots(2, "orange"))}
    <main class="col">
      <img class="photo" src="${S.photo.url}" alt="${T("your_photo")}">
      <h2 class="title center-text">${T("preview_title")}</h2>
      <p class="body center-text">${T("preview_body")}</p>
    </main>
    <footer class="actions">
      ${btn("send-photo", t("send_to_ai"), "orange", "sparkle")}
      <label class="btn paper">${icon("retry")}<span>${T("retake")}</span>
        <input type="file" accept="image/*" capture="environment" data-photo hidden></label>
    </footer>
  </div>`;
}

function recorderScreen() {
  const st = S.recState, listening = st === "listening";
  const chip = listening ? ["rec_listening", "coral", true] : st === "ready" ? ["rec_ready", "mint"] : ["rec_idle", "line"];
  let below = "";
  if (st === "denied") below = `<p class="note"><b>${T("mic_permission_title")}</b><br>${T("mic_permission_body")}</p>`;
  else if (listening) below = S.partial ? heardBox(S.partial, true) : "";
  else if (st === "measuring") below = `<p class="note">${T("rec_measuring")}</p>`;
  else if (st === "empty") below = `<p class="note">${T("rec_heard_nothing")}</p>`;
  else if (st === "ready") below = heardBox(S.heard.transcript || t("heard_no_words"), true) +
    (S.heard.speech ? "" : `<p class="note">${T("speech_unavailable")}</p>`);
  else below = `<div class="tipbox">${mascot("phones")}<p>${T("rec_tip")}</p></div>` +
    (speechSupported() ? "" : `<p class="note">${T("speech_unavailable")}</p>`);
  const hint = st === "denied" ? "" : `<p class="hint">${T(listening ? "rec_tap_stop" : "rec_tap_start")}</p>`;
  return `<div class="screen">
    ${topBar(t("whine_check"), "home", "back", t("back"), dots(st === "ready" ? 2 : 1, "violet"))}
    <main class="col center">
      <span class="chip"><i class="${chip[1]} ${chip[2] ? "blink" : ""}"></i>${T(chip[0])}</span>
      <div class="timer" id="timer">${clock(S.elapsed)}</div>
      <div class="wave ${listening ? "live" : S.levels.length ? "has" : ""}" id="wave">${waveBars()}</div>
      <div class="mic-wrap ${listening ? "on" : ""}">
        <i class="ring r1"></i><i class="ring r2"></i>
        <button class="mic sticker" data-action="${st === "denied" ? "record" : listening ? "stop" : "record"}"
          aria-label="${T(listening ? "stop_recording" : "start_recording")}" ${st === "measuring" ? "disabled" : ""}>
          ${listening ? `<i class="stop"></i>` : icon("mic")}</button>
      </div>
      ${st === "denied" ? btn("record", t("allow"), "violet small", "mic") : hint}
      <div class="below" id="below">${below}</div>
    </main>
    ${st === "ready" ? `<footer class="actions">${btn("send-words", t("send_to_ai"), "violet", "sparkle")}${btn("record", t("record_again"), "paper", "retry")}</footer>` : ""}
  </div>`;
}

const heardBox = (text, quote) =>
  `<div class="box heard"><p class="small">${T("what_i_heard")}</p><p class="said">${esc(quote ? t("quoted", text) : text)}</p></div>`;

const clock = ms => { const s = Math.floor(ms / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; };

function waveBars() {
  let h = "";
  for (let i = 0; i < LEVELS; i++) {
    const v = S.levels[S.levels.length - LEVELS + i] ?? 0;
    h += `<i style="height:${Math.max(14, v * 100).toFixed(0)}%"></i>`;
  }
  return h;
}

function analyzingScreen() {
  const mess = S.check === "mess", color = mess ? "orange" : "violet";
  const steps = mess ? ["step_photo_received", "step_scanning", "step_writing"] : ["step_audio_received", "step_tone", "step_writing"];
  const e = S.error;
  return `<div class="screen">
    ${topBar(t(mess ? "mess_check" : "whine_check"), "cancel", "close", t("close"), dots(3, color))}
    <main class="col center">
      <div class="spin-wrap ${color}">
        <i class="disc"></i>${e ? "" : `<svg class="ring-spin" viewBox="0 0 100 100"><circle cx="50" cy="50" r="48" fill="none" stroke="var(--${color})" stroke-width="3.5" stroke-linecap="round" stroke-dasharray="0.35 8.4"/></svg>`}
        ${mascot(mess ? "lens" : "phones", e ? "" : "bob")}
      </div>
      ${e ? `<h2 class="title center-text">${T("error_title")}</h2><p class="body center-text">${T(e === "busy" ? "error_busy" : "error_generic")}</p>`
        : `<h2 class="title center-text">${T(S.slow ? "slow_title" : mess ? "inspecting_title" : "listening_title")}</h2>
           <p class="body center-text">${T(S.slow ? "slow_sub" : "analyzing_sub")}</p>
           <ol class="steps sticker">${steps.map((s, i) =>
             `<li class="${i < S.step ? "done" : i === S.step ? "now" : ""}" style="--accent:var(--${color})"><i>${i < S.step ? icon("check") : ""}</i>${T(s)}</li>`).join("")}</ol>`}
    </main>
    ${e ? `<footer class="actions">${btn("retry", t("try_again"), color, "retry")}</footer>` : ""}
  </div>`;
}

function verdictFrame(tint, content, again, againColor, againIcon) {
  return `<div class="screen verdict" style="--wash:var(--${tint})">
    <i class="wash"></i>
    ${topBar(t("verdict"), "home", "close", t("close"))}
    <main class="col">${content}
      <div class="end">${btn(again, t(again === "photo" ? "check_again" : "record_again"), againColor, againIcon)}${btn("home", t("home"), "paper", "home")}</div>
    </main>
  </div>`;
}

function choices(labels, chosen, color) {
  return `<div class="choices">` + labels.map((l, i) =>
    `<span class="choice ${i === chosen ? "on" : ""}" style="${i === chosen ? `background:var(--${color})` : ""}">${i === chosen ? icon("check") : ""}${T(l)}</span>`).join("") + `</div>`;
}

function meter(label, level, color) {
  let seg = "";
  for (let i = 0; i < 10; i++) seg += `<i style="--c:var(--${color});--d:${350 + i * 70}ms" class="${i < level ? "on" : ""}"></i>`;
  return `<div class="box meter" role="img" aria-label="${T(label)} ${level}/10">
    <div class="meter-head"><b>${T(label)}</b><span>${level}/10</span></div><div class="segs">${seg}</div></div>`;
}

function why(reasons, color) {
  if (!reasons.length) return "";
  return `<div class="box why"><h3>${T("why")}</h3><ol>` +
    reasons.map((r, i) => `<li><i style="background:var(--${color})">${i + 1}</i><span>${esc(r)}</span></li>`).join("") + `</ol></div>`;
}

function tip(text) {
  if (!text) return "";
  return `<div class="says">${mascot("lens")}<div class="bubble"><p class="small">${T("inspector_says")}</p><p>${esc(text)}</p></div></div>`;
}

function messScreen() {
  const v = S.verdict, tidy = v.verdict === "tidy";
  return verdictFrame(tidy ? "mint-tint" : "orange-tint", `
    <div class="photo-wrap"><img class="photo" src="${S.photo.url}" alt="${T("your_photo")}">
      <span class="stamp pop">${face(tidy ? "tidy" : "mess")}</span></div>
    <h2 class="headline">${esc(v.title || t(tidy ? "tidy" : "mess"))}</h2>
    ${choices(["tidy", "mess"], tidy ? 0 : 1, tidy ? "mint" : "orange")}
    ${meter("mess_meter", v.level, tidy ? "mint" : "coral")}
    ${why(v.why, tidy ? "mint" : "orange")}
    ${tip(v.tip)}`, "photo", "orange", "camera");
}

function whineScreen() {
  const v = S.verdict;
  const [color, tint] = { whining: ["coral", "coral-tint"], begging: ["sun", "sun-tint"], champ: ["mint", "mint-tint"] }[v.verdict];
  const said = S.heard.transcript;
  return verdictFrame(tint, `
    <span class="face-big pop">${face(v.verdict)}</span>
    <h2 class="headline">${esc(v.title || t(v.verdict))}</h2>
    ${choices(["whining", "begging", "champ"], ["whining", "begging", "champ"].indexOf(v.verdict), color)}
    ${meter(v.verdict === "begging" ? "beg_meter" : "whine_meter", v.level, color)}
    <div class="box heard"><p class="small">${icon("quote", "q")}${T("what_i_heard")}</p>
      <p class="said">${said ? esc(t("quoted", said)) : T("heard_no_words")}</p></div>
    ${why(v.why, color)}
    ${tip(v.tip)}`, "record", "violet", "mic");
}

const SCREENS = { home: homeScreen, photo: photoScreen, preview: previewScreen, recorder: recorderScreen,
  analyzing: analyzingScreen, mess: messScreen, whine: whineScreen };

function render() {
  document.title = S.screen === "home" ? t("title") : `${t("title")} · ${t(S.screen === "photo" || S.screen === "preview" || S.check === "mess" && S.screen === "analyzing" || S.screen === "mess" ? "mess_check" : "whine_check")}`;
  app.innerHTML = SCREENS[S.screen]();
  app.querySelectorAll("[data-photo]").forEach(i => i.addEventListener("change", onPhoto));
}

// ------------------------------------------------------------------ photo
async function onPhoto(e) {
  const file = e.target.files && e.target.files[0];
  e.target.value = "";
  if (!file) return;
  try {
    S.photo = await shrink(file);
    S.photoError = false;
    go("preview", S.screen !== "preview");
  } catch (_) {
    S.photoError = true;
    go("photo", false);
  }
}

/** The photo upright and at most SEND_SIDE on its long side, as a JPEG. */
async function shrink(file) {
  let src;
  try {
    src = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch (_) {
    src = await new Promise((ok, fail) => {
      const img = new Image();
      img.onload = () => ok(img);
      img.onerror = fail;
      img.src = URL.createObjectURL(file);
    });
  }
  const w0 = src.width || src.naturalWidth, h0 = src.height || src.naturalHeight;
  const s = Math.min(1, SEND_SIDE / Math.max(w0, h0));
  const c = document.createElement("canvas");
  c.width = Math.round(w0 * s); c.height = Math.round(h0 * s);
  const g = c.getContext("2d");
  g.fillStyle = "#fff"; g.fillRect(0, 0, c.width, c.height);
  g.drawImage(src, 0, 0, c.width, c.height);
  if (src.close) src.close();
  const url = c.toDataURL("image/jpeg", JPEG_QUALITY);
  return { url, b64: url.split(",", 2)[1] };
}

// ------------------------------------------------------------------ recording
function startRecording() {
  dropRecording();
  Object.assign(S, { recState: "listening", levels: [], elapsed: 0, partial: "", heard: null });
  render();
  S.rec = new Recording(lang, {
    level: v => { S.levels.push(v); if (S.levels.length > LEVELS) S.levels.shift(); paintLive(); },
    tick: ms => { S.elapsed = ms; paintLive(); },
    partial: text => {
      S.partial = text;
      const b = document.getElementById("below");
      if (b && S.recState === "listening") b.innerHTML = heardBox(text, true);
    },
    measuring: () => { S.recState = "measuring"; render(); },
    done: heard => {
      S.rec = null;
      S.heard = heard;
      S.recState = !heard.transcript && heard.voice.voicedShare < 0.05 ? "empty" : "ready";
      if (S.screen === "recorder") render();
    },
    fail: () => { S.rec = null; S.recState = "denied"; render(); },
  });
  S.rec.start();
}

let painting = false;
function paintLive() {
  if (painting) return;
  painting = true;
  requestAnimationFrame(() => {
    painting = false;
    const tm = document.getElementById("timer"), w = document.getElementById("wave");
    if (tm) tm.textContent = clock(S.elapsed);
    if (w) w.innerHTML = waveBars();
  });
}

function dropRecording() {
  if (S.rec) { S.rec.cancel(); S.rec = null; }
  if (S.recState === "listening" || S.recState === "measuring") S.recState = "idle";
}

// ------------------------------------------------------------------ the verdict
function cancelAsk() {
  if (ask) { ask.abort(); ask = null; }
}

async function judge(check) {
  cancelAsk();
  const ctl = new AbortController();
  ask = ctl;
  Object.assign(S, { check, error: null, slow: false, step: 1, verdict: null });
  go("analyzing", S.screen !== "analyzing");
  const stepUp = setTimeout(() => { if (ask === ctl) { S.step = 2; render(); } }, 1800);
  const slow = setTimeout(() => { if (ask === ctl) { S.slow = true; render(); } }, SLOW_AFTER);
  const giveUp = setTimeout(() => ctl.abort(), GIVE_UP);
  const now = new Date();
  const body = { lang, time: `${now.getHours()}:${String(now.getMinutes()).padStart(2, "0")}` };
  if (check === "mess") body.image = S.photo.b64;
  else { body.transcript = S.heard.transcript; body.voice = S.heard.voice; }
  let next = null, error = "generic";
  try {
    const r = await fetch(`api/${check === "mess" ? "mess" : "whine"}`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body), signal: ctl.signal,
    });
    if (r.ok) next = await r.json();
    else if (r.status === 429) error = "busy";
  } catch (_) { /* aborted or offline */ }
  clearTimeout(stepUp); clearTimeout(slow); clearTimeout(giveUp);
  if (ask !== ctl) return;             // cancelled, or a newer ask took over
  ask = null;
  if (next && next.verdict) {
    S.verdict = next;
    go(check === "mess" ? "mess" : "whine", false);
    history.replaceState({ screen: S.screen }, "");
    if (next.verdict === "tidy" || next.verdict === "champ") confetti();
  } else {
    S.error = error;
    render();
  }
}

// ------------------------------------------------------------------ actions
app.addEventListener("click", e => {
  const el = e.target.closest("[data-action]");
  if (!el || el.disabled) return;
  const a = el.dataset.action;
  if (a === "open") {
    if (el.dataset.target === "recorder") Object.assign(S, { recState: "idle", heard: null, levels: [], elapsed: 0 });
    else S.photoError = false;
    go(el.dataset.target);
  }
  else if (a === "home") home();
  else if (a === "lang") setLang(lang === "ru" ? "en" : "ru");
  else if (a === "photo") { S.photoError = false; go("photo"); }
  else if (a === "send-photo") judge("mess");
  else if (a === "record") {
    if (S.screen !== "recorder") { Object.assign(S, { recState: "idle", heard: null }); go("recorder"); }
    startRecording();
  }
  else if (a === "stop") { if (S.rec) S.rec.stop(); }
  else if (a === "send-words") judge("whine");
  else if (a === "retry") judge(S.check);
  else if (a === "cancel") history.back();
});

document.documentElement.lang = lang;
history.replaceState({ screen: "home" }, "");
render();
