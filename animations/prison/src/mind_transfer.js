// mind_transfer.js · the correctional mind-transfer menu (scan & dissolve), subject RUBBERDOLL → the LTX vessel.
// 10 s. A scan line sweeps the subject, who then dissolves top-down into pixels that arc across and fill the
// empty vessel from the hips up; status reads COPYING MEMORIES… OVERWRITING IDENTITY… INSTALLING OBEDIENCE…,
// the chest barcode prints, then the labels swap: the subject becomes VACANT and the vessel becomes RUBBERDOLL.
// Figures run head to hips with arms at their sides:
//   subject · a featureless, profile-picture-style blank head (no eyes, mouth or hair) in an orange jumpsuit
//   vessel  · a clearly female figure, drawn as a clean mannequin-like diagram, in a shiny black full-body suit,
//             with an orange barcode on the upper chest and an orange padlock at hip level
// Keyable: exact #00FF00 background, every pixel opaque, no green-ish colours; the CRT pass never touches the green.
(() => {
  const { C, PX, AB } = MED, { hex, put, bayer } = HD;
  const RED = hex('#e8322a'), GLOSS = hex('#2b2320'), FPS = 30;
  const NAME = 'RUBBERDOLL', CODE = 'U-R-RBBR-D011';
  const clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
  const txt = (s, font, x, y, col, align = 'left') => UI.paint(UI.text(s, font, x, y, align), col);
  const rect = (x, y, w, h, col, outline) => UI.paint(UI.mask(c => c.fillRect(x, y, w, h)), col, outline);
  const rr = (x, y, w, h, r, col, outline) => UI.paint(UI.rrect(x, y, w, h, r), col, outline);
  const hsh = (a, b) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
  function hazard(cx, cy, size, fill) {
    const h = size, w = size * 1.15;
    UI.paint(UI.mask(c => { c.beginPath(); c.moveTo(cx, cy - h / 2); c.lineTo(cx + w / 2, cy + h / 2); c.lineTo(cx - w / 2, cy + h / 2); c.closePath(); c.fill(); }), fill, C.black, 1);
    UI.paint(UI.mask(c => { c.fillRect(cx - 1, cy - h * 0.18, 2, h * 0.38); c.fillRect(cx - 1, cy + h * 0.28, 2, 2); }), C.black);
  }
  function bar(x, y, w, h, p) {
    rr(x, y, w, h, 2, C.whiteS, C.black);
    const fw = Math.round((w - 2) * clamp(p));
    if (fw > 0) UI.paint(UI.mask(c => c.fillRect(x + 1, y + 1, fw, h - 2)), (px, py) => ((((px + py) >> 2) & 1) ? C.orange : C.orangeD));
  }

  // ── layout: a taller label (406×224, 100 stripes round → the same corner radius as every other label) ──
  const F = MED.makeFrame(117, 68, 523, 292, 100);
  const TOP = 110, SX = 196, VX = 444;
  // a head-to-hip figure; w = half-widths at shoulder, chest, under-chest, waist, hip, thigh
  function figureMask(cx, w, arm) {
    return UI.mask(g => {
      g.beginPath(); g.ellipse(cx, TOP + 12, 9, 12, 0, 0, Math.PI * 2); g.fill();              // head
      g.fillRect(cx - 4, TOP + 21, 8, 11);                                                     // neck
      const L = [[4, 30], [w[0], 36], [w[1], 47], [w[2], 56], [w[3], 68], [w[4], 86], [w[5], 104]];
      g.beginPath(); g.moveTo(cx - L[0][0], TOP + L[0][1]);
      for (let k = 1; k < L.length; k++) { const [a, ya] = L[k - 1], [b, yb] = L[k]; g.quadraticCurveTo(cx - a, TOP + (ya + yb) / 2, cx - (a + b) / 2, TOP + (ya + yb) / 2); g.lineTo(cx - b, TOP + yb); }
      g.lineTo(cx + L[L.length - 1][0], TOP + 104);
      for (let k = L.length - 1; k > 0; k--) { const [a, ya] = L[k], [b, yb] = L[k - 1]; g.lineTo(cx + (a + b) / 2, TOP + (ya + yb) / 2); g.quadraticCurveTo(cx + b, TOP + (ya + yb) / 2, cx + b, TOP + yb); }
      g.closePath(); g.fill();
      g.lineCap = 'round';                                                                     // arms at the sides
      for (const s of [-1, 1]) {
        const segs = 6; for (let k = 0; k < segs; k++) {
          const q0 = k / segs, q1 = (k + 1) / segs;
          g.lineWidth = arm[0] + (arm[1] - arm[0]) * q0; g.beginPath();
          g.moveTo(cx + s * (w[0] + 1 + (w[4] - w[0] + 5) * q0), TOP + 38 + 56 * q0);
          g.lineTo(cx + s * (w[0] + 1 + (w[4] - w[0] + 5) * q1), TOP + 38 + 56 * q1); g.stroke();
        }
      }
    });
  }
  const SUBJ = figureMask(SX, [21, 19, 18, 17, 18, 17], [6.5, 5]);                                  // neutral build
  const VESS = figureMask(VX, [17, 17, 14, 11, 20, 17], [5, 3.6]);                                   // female build: bust, narrow waist, hips
  const legGap = cx => UI.mask(g => g.fillRect(cx - 0.75, TOP + 95, 1.5, 10));                     // a hint of two legs
  const sub = (a, b) => { const o = new Uint8Array(a.length); for (let i = 0; i < a.length; i++) o[i] = a[i] & !b[i]; return o; };
  const SM = sub(SUBJ, legGap(SX)), VM = sub(VESS, legGap(VX));
  const outline = m => sub(UI.dilate(m, 1), m);
  const SO = outline(SM), VO = outline(VM);
  const inHead = (cx, x, y) => ((x + 0.5 - cx) / 9) ** 2 + ((y + 0.5 - TOP - 12) / 12) ** 2 <= 1;

  // per-pixel colours, precomputed once. Run-based shading: u ∈ [−1, 1] across each horizontal run of the
  // silhouette, so torso and arms each get their own highlight band and rim.
  function runShade(m, f) {
    const col = new Array(m.length);
    for (let y = 0; y < UI.H; y++) for (let x = 0; x < UI.W; x++) {
      const i = y * UI.W + x; if (!m[i]) continue;
      let a = x, b = x; while (a > 0 && m[i - (x - a) - 1]) a--; while (b < UI.W - 1 && m[i + (b - x) + 1]) b++;
      col[i] = f(x, y, b > a ? 2 * (x - a) / (b - a) - 1 : 0);
    }
    return col;
  }
  const SCOL = runShade(SM, (x, y, u) => {
    if (inHead(SX, x, y)) return x > SX + 4 && bayer(x, y) < 0.6 ? C.grey : C.whiteS;              // blank, featureless head
    if (y < TOP + 31) return x > SX + 1 ? C.grey : C.whiteS;                                       // neck
    if (Math.abs(x + 0.5 - SX) < 0.8 && y < TOP + 92) return C.orangeD;                            // jumpsuit zip
    return u > 0.55 || (u > 0.35 && bayer(x, y) < 0.5) ? C.orangeD : C.orange;
  });
  const VCOL = runShade(VM, (x, y, u) => {
    if (inHead(VX, x, y)) {                                                                      // glossy hood: specular spot, cool rim
      const nx = (x + 0.5 - VX) / 9, ny = (y + 0.5 - TOP - 12) / 12, d = -0.6 * nx - 0.8 * ny;
      return nx > 0.8 ? C.cyanD : d > 0.72 ? C.white : d > 0.5 ? C.grey : d > 0.3 ? GLOSS : C.black;
    }
    if (u > -0.66 && u < -0.5) return u > -0.61 && u < -0.55 ? C.white : C.grey;                   // highlight band
    if (u > -0.76 && u < -0.4) return GLOSS;
    if (u > 0.86) return C.cyanD;                                                                // rim light
    return C.black;
  });

  function subject(cut, scanY) {
    for (let i = 0; i < SO.length; i++) if (SO[i]) { const x = i % UI.W, y = (i / UI.W) | 0; if (y >= cut) put(x, y, C.black); else if ((x + y) & 1) put(x, y, C.whiteS); }
    for (let i = 0; i < SM.length; i++) if (SM[i]) { const x = i % UI.W, y = (i / UI.W) | 0; if (y >= cut) put(x, y, SCOL[i]); }
    if (cut <= TOP + 45) { rect(SX - 11, TOP + 45, 22, 10, C.white, C.black); txt('0412', `8px ${PX}`, SX, TOP + 46, C.black, 'center'); }
    if (scanY !== null) rect(SX - 30, scanY, 60, 2, C.cyan, C.black);
  }
  function vessel(fillY, lit, sweep) {
    for (let i = 0; i < VO.length; i++) if (VO[i]) { const x = i % UI.W, y = (i / UI.W) | 0; if (y >= fillY) put(x, y, C.black); else if ((x + y) & 1) put(x, y, C.grey); }
    for (let i = 0; i < VM.length; i++) if (VM[i]) {
      const x = i % UI.W, y = (i / UI.W) | 0; if (y < fillY) continue;
      let col = VCOL[i]; if (Math.abs(y - sweep) < 1.5 && col !== C.white) col = col === C.cyanD ? C.cyan : C.grey;
      put(x, y, col);
    }
    // orange padlock at hip level
    if (fillY <= TOP + 80) {
      UI.paint(UI.mask(g => { g.lineWidth = 2; g.beginPath(); g.arc(VX, TOP + 82, 4, Math.PI, 0); g.lineTo(VX + 4, TOP + 84); g.moveTo(VX - 4, TOP + 82); g.lineTo(VX - 4, TOP + 84); g.stroke(); }), C.orangeL, C.black, 1);
      rr(VX - 6, TOP + 83, 12, 10, 1, C.orange, C.black);
      rect(VX - 5, TOP + 90, 10, 2, C.orangeD);
      UI.paint(UI.mask(g => { g.beginPath(); g.arc(VX, TOP + 86.5, 1.3, 0, Math.PI * 2); g.fill(); g.fillRect(VX - 0.6, TOP + 87, 1.2, 3); }), C.black);
    }
    // orange barcode on the upper chest
    if (fillY <= TOP + 38) {
      const bx = VX - 9, by = TOP + 37;
      rect(bx - 1, by - 1, 20, 9, C.black);
      for (let k = 0, x = bx; k < 7; k++) { const w = 1 + (k * 7) % 2; rect(x, by, w, 7, k < lit ? C.orange : hex('#3d1704')); x += w + 1 + (k % 2); }
    }
  }

  const STATUS = [[0, 'SCANNING SUBJECT...'], [30, 'COPYING MEMORIES...'], [105, 'OVERWRITING IDENTITY...'], [180, 'INSTALLING OBEDIENCE...'], [255, 'TRANSFER COMPLETE']];
  const SRC = [], DST = [];
  for (let i = 0; i < SM.length; i += 5) if (SM[i]) SRC.push([i % UI.W, (i / UI.W) | 0]);
  for (let i = 2; i < VM.length; i += 7) if (VM[i]) DST.push([i % UI.W, (i / UI.W) | 0]);

  SCENES['mt_scan'] = { draw(c, lt) {
    const fr = Math.round(lt * FPS), p = clamp((fr - 30) / 225), done = fr >= 255;
    let status = ''; for (const [f, s] of STATUS) if (fr >= f) status = s;
    MED.bg(); F.panel();
    hazard(144, 92, 18, RED);
    txt('MIND TRANSFER', `16px ${AB}`, 160, 82, RED);
    txt(CODE, `8px ${PX}`, 504, 88, C.grey, 'right');
    rect(134, 104, 372, 1, C.black);
    const cut = TOP + p * 106;
    subject(cut, fr < 30 ? TOP + Math.round(fr / 30 * 104) : (p < 1 ? Math.round(cut) : null));
    vessel(TOP + 106 - p * 106, fr < 240 ? 0 : Math.min(7, Math.floor((fr - 240) / 2)), done ? TOP + ((fr / 30) % 1) * 110 : -99);
    for (let k = 0; k < 260; k++) {                                       // pixels fly from the dissolve front into the vessel
      const born = 30 + k * 0.85, age = fr - born; if (age < 0 || age >= 32 || born > 252) continue;
      const q = age / 32, a = SRC[Math.floor(hsh(k, 2) * SRC.length)], b = DST[Math.floor(hsh(k, 5) * DST.length)];
      const sy0 = TOP + clamp((born - 30) / 225) * 106, x = a[0] + (b[0] - a[0]) * q, y = sy0 + (b[1] - sy0) * q - 30 * Math.sin(Math.PI * q);
      const s = k % 3 === 0 ? 3 : 2; rr(Math.round(x), Math.round(y), s, s, 0, k % 4 === 0 ? C.cyan : k % 2 ? C.orange : C.whiteS, C.black);
    }
    txt(done ? 'VACANT' : NAME, `8px ${PX}`, SX, 220, C.black, 'center');
    txt(done ? NAME : 'LTX VESSEL', `8px ${PX}`, VX, 220, done ? C.orangeD : C.black, 'center');
    if (!(done && (Math.floor(fr / 6) & 1) === 0)) txt(status, `12px ${AB}`, 320, 236, done ? C.orangeD : C.black, 'center');
    bar(150, 260, 300, 9, p); txt(`${Math.floor(p * 100)}%`, `8px ${PX}`, 490, 261, C.black, 'right');
    F.border(lt);
    HD.present(c); CRT.apply(c, lt, 5);
  } };
})();
