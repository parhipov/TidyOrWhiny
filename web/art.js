// The app's drawings as SVG: the Inspector, the verdict faces, the stroke icons.
// Shapes and numbers are Mascot.kt's and Icons.kt's.

const INK = "#221C35", PAPER = "#FFFFFF", SUN = "#FFC53D", CHEEK = "#FF8FA3", TEAR = "#5AA9FF";
const VIOLET = "#8B7CFF", ORANGE = "#FF7A45", MINT = "#4FD1A1", CORAL = "#FF7A6B";

const line = (d, w, c = INK) =>
  `<path d="${d}" fill="none" stroke="${c}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`;

const eye = (cx, cy, r) =>
  `<g class="eye"><circle cx="${cx}" cy="${cy}" r="${r}" fill="${INK}"/>` +
  `<circle cx="${cx + r * 0.35}" cy="${cy - r * 0.38}" r="${r * 0.34}" fill="${PAPER}"/></g>`;

/** The Inspector: a sunny blob with a magnifying glass ("lens") or headphones. It blinks. */
export function mascot(gear = "lens", cls = "") {
  let g;
  if (gear === "lens") {
    g = `<circle cx="100" cy="78" r="25" fill="${PAPER}" fill-opacity=".6" stroke="${INK}" stroke-width="5"/>` +
      eye(100, 80, 13) + line("M118 96L138 118", 10);
  } else {
    g = eye(102, 80, 9) + line("M10 84A70 70 0 0 1 150 84", 7) +
      [2, 138].map(x => `<rect x="${x}" y="64" width="20" height="40" rx="9" fill="${VIOLET}" stroke="${INK}" stroke-width="4.5"/>`).join("");
  }
  return `<svg class="mascot ${cls}" viewBox="0 0 160 160" aria-hidden="true">` +
    line("M78 30C76 16 90 10 98 16", 5) +
    `<rect x="18" y="28" width="124" height="120" rx="58" fill="${SUN}" stroke="${INK}" stroke-width="5"/>` +
    `<circle cx="44" cy="106" r="8" fill="${CHEEK}" fill-opacity=".7"/><circle cx="118" cy="110" r="8" fill="${CHEEK}" fill-opacity=".7"/>` +
    eye(58, 80, 9) + g + line("M62 112Q80 126 98 112", 5) + `</svg>`;
}

const sparkle = (x, y, r) =>
  `<path d="M${x} ${y - r}Q${x} ${y} ${x + r} ${y}Q${x} ${y} ${x} ${y + r}Q${x} ${y} ${x - r} ${y}Q${x} ${y} ${x} ${y - r}Z" ` +
  `fill="${SUN}" stroke="${INK}" stroke-width="2.5" stroke-linejoin="round"/>`;

const FILL = { tidy: MINT, mess: ORANGE, whining: CORAL, begging: SUN, champ: MINT };

/** A verdict's face: round, bold, one mood each. */
export function face(kind, cls = "") {
  let f = "";
  if (kind === "mess") {
    f = line("M44 58l14 14M58 58L44 72", 6) + line("M82 58l14 14M96 58L82 72", 6) +
      line("M44 98q6.5-8 13 0t13 0t13 0t13 0", 5.5) +
      `<path d="M108 40q-8 11 0 15q8-4 0-15z" fill="${TEAR}" stroke="${INK}" stroke-width="3"/>`;
  } else if (kind === "tidy" || kind === "champ") {
    f = line("M42 66q9-12 18 0", 6) + line("M80 66q9-12 18 0", 6) +
      `<path d="M44 84q26 30 52 0z" fill="${INK}" stroke="${INK}" stroke-width="5" stroke-linejoin="round"/>` +
      `<path d="M58 96q12 7 24 0q-12-6-24 0z" fill="${CHEEK}"/>` +
      `<circle cx="36" cy="84" r="7" fill="${CHEEK}" fill-opacity=".8"/><circle cx="104" cy="84" r="7" fill="${CHEEK}" fill-opacity=".8"/>` +
      (kind === "champ"
        ? `<path d="M46 34L41 8L57 21L70 4L83 21L99 8L94 34Z" fill="${SUN}" stroke="${INK}" stroke-width="4" stroke-linejoin="round"/>`
        : sparkle(120, 26, 11) + sparkle(18, 118, 8) + sparkle(24, 28, 6));
  } else if (kind === "whining") {
    f = line("M40 54l18-7", 5) + line("M100 54l-18-7", 5) +
      `<circle cx="50" cy="68" r="6.5" fill="${INK}"/><circle cx="90" cy="68" r="6.5" fill="${INK}"/>` +
      `<path d="M48 106q22-24 44 0z" fill="${INK}" stroke="${INK}" stroke-width="4" stroke-linejoin="round"/>` +
      `<path d="M94 78q-8 11 0 16q8-5 0-16z" fill="${TEAR}" stroke="${INK}" stroke-width="3"/>`;
  } else if (kind === "begging") {
    f = line("M38 46l16 5", 5) + line("M102 46l-16 5", 5) +
      [50, 90].map(x =>
        `<circle cx="${x}" cy="70" r="15" fill="${PAPER}" stroke="${INK}" stroke-width="4"/>` +
        `<circle cx="${x + 1}" cy="73" r="10" fill="${INK}"/>` +
        `<circle cx="${x + 4.5}" cy="68.5" r="3.8" fill="${PAPER}"/><circle cx="${x - 2.5}" cy="77" r="1.8" fill="${PAPER}"/>`).join("") +
      `<circle cx="70" cy="104" r="6.5" fill="${INK}"/>` +
      `<circle cx="32" cy="92" r="7" fill="${CHEEK}" fill-opacity=".8"/><circle cx="108" cy="92" r="7" fill="${CHEEK}" fill-opacity=".8"/>`;
  }
  return `<svg class="face ${cls}" viewBox="0 0 140 140" aria-hidden="true">` +
    `<circle cx="70" cy="81" r="52" fill="${INK}"/>` +
    `<circle cx="70" cy="76" r="52" fill="${FILL[kind]}" stroke="${INK}" stroke-width="4.5"/>` + f + `</svg>`;
}

const ICONS = {
  back: ["M15 5l-7 7 7 7"],
  close: ["M6 6l12 12", "M18 6L6 18"],
  arrow: ["M5 12h14", "M13 6l6 6-6 6"],
  camera: ["M4 8.5A2.5 2.5 0 0 1 6.5 6h1.7l1.4-2h4.8l1.4 2h1.7A2.5 2.5 0 0 1 20 8.5v9A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5z",
    "M15.5 13a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0"],
  mic: ["M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21", "M8.5 21h7"],
  sparkle: ["M11 3.5l1.9 5.1 5.1 1.9-5.1 1.9L11 17.5l-1.9-5.1L4 10.5l5.1-1.9z", "M19 15.5v5", "M16.5 18h5"],
  retry: ["M4 12a8 8 0 0 1 13.7-5.7L20 8.5", "M20 4v4.5h-4.5", "M20 12a8 8 0 0 1-13.7 5.7L4 15.5", "M4 20v-4.5h4.5"],
  gallery: ["M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z",
    "M10.6 9a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0", "M20 15l-5-5L5 20"],
  home: ["M4 11l8-7 8 7v8.5a1.5 1.5 0 0 1-1.5 1.5H15v-6H9v6H5.5A1.5 1.5 0 0 1 4 19.5z"],
  check: ["M5 12.5l4.5 4.5L19 7.5"],
  quote: ["M5 17c0-4 1.5-7 5-8.5", "M14 17c0-4 1.5-7 5-8.5"],
};

/** A stroke icon on the 24 grid, in currentColor. */
export function icon(name, cls = "") {
  const w = name === "check" ? 3 : 2.4;
  return `<svg class="icon ${cls}" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" ` +
    `stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round">` +
    ICONS[name].map(d => `<path d="${d}"/>`).join("") + `</svg>`;
}

/** A burst of paper confetti falling once over the screen (Confetti in VerdictParts.kt). */
export function confetti() {
  const c = document.createElement("canvas");
  c.className = "confetti";
  document.body.appendChild(c);
  const dpr = window.devicePixelRatio || 1;
  const W = innerWidth, H = innerHeight;
  c.width = W * dpr; c.height = H * dpr;
  const g = c.getContext("2d");
  g.scale(dpr, dpr);
  const colors = [ORANGE, VIOLET, SUN, MINT, PAPER];
  const bits = Array.from({ length: 46 }, () => ({
    x: Math.random(), delay: Math.random() * 0.35, speed: 0.7 + Math.random() * 0.6,
    spin: Math.random() * 720 - 360, color: colors[Math.floor(Math.random() * colors.length)],
    w: 8 + Math.random() * 8, h: 12 + Math.random() * 10, sway: Math.random() * 40 - 20,
  }));
  const t0 = performance.now();
  const frame = now => {
    const t = (now - t0) / 3200;
    g.clearRect(0, 0, W, H);
    if (t >= 1) { c.remove(); return; }
    for (const b of bits) {
      const p = Math.min(1, Math.max(0, (t - b.delay) / (1 - b.delay))) * b.speed;
      if (p <= 0 || p > 1.1) continue;
      const x = b.x * W + b.sway * Math.sin(p * 9), y = -40 + p * (H + 80);
      g.save();
      g.translate(x, y);
      g.rotate(b.spin * p * Math.PI / 180);
      g.fillStyle = INK; g.fillRect(-b.w / 2 - 2, -b.h / 2 - 2, b.w + 4, b.h + 4);
      g.fillStyle = b.color; g.fillRect(-b.w / 2, -b.h / 2, b.w, b.h);
      g.restore();
    }
    requestAnimationFrame(frame);
  };
  if (!matchMedia("(prefers-reduced-motion: reduce)").matches) requestAnimationFrame(frame);
  else c.remove();
}
