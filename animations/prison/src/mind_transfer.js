// mind_transfer.js · the correctional mind-transfer menu (scan & dissolve), subject RUBBERDOLL → the LTX vessel.
// 15 s. A scan line sweeps the subject, who then dissolves top-down into pixels that arc across and fill the
// empty vessel from the hips up. A creepy LTX Corp loading screen runs underneath: the status line slides from
// soothing to openly owning them, with a game-style TIP line. The chest barcode prints, then the labels swap:
// the subject becomes VACANT and the vessel becomes RUBBERDOLL. The vessel breathes through a ribbed tube: a pulse
// of orange gas travels up it into the face valve every 2.5 s.
// Figures run head to hips with arms at their sides:
//   subject · a featureless, profile-picture-style blank head (no eyes, mouth or hair) in an orange jumpsuit
//   vessel  · a clearly female figure, drawn as a clean mannequin-like diagram, in a shiny black full-body suit,
//             with an orange barcode on the upper chest, an orange padlock at hip level, and a ribbed
//             breathing tube from a valve on the lower face, over the right shoulder and down the side
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
  const SM = sub(SUBJ, legGap(SX)), VM0 = sub(VESS, legGap(VX));
  // breathing tube: a ribbed hose from the face valve, over the right shoulder, down the outside of the arm.
  // TS[i] = distance along the tube (0 at the valve) for each tube pixel, used for the ribs and the gas pulse.
  const TUBE = new Uint8Array(UI.W * UI.H), TS = new Float32Array(UI.W * UI.H);
  const TPTS = [[VX + 3, TOP + 19], [VX + 15, TOP + 26], [VX + 24, TOP + 42], [VX + 30, TOP + 66], [VX + 33, TOP + 106]];
  let TLEN = 0;
  {
    const pts = []; for (let k = 0; k < TPTS.length - 1; k++) {          // Catmull-Rom through the control points
      const p0 = TPTS[Math.max(0, k - 1)], p1 = TPTS[k], p2 = TPTS[k + 1], p3 = TPTS[Math.min(TPTS.length - 1, k + 2)];
      for (let t = 0; t < 1; t += 0.02) { const t2 = t * t, t3 = t2 * t, f = (a, b, c2, d) => 0.5 * (2 * b + (-a + c2) * t + (2 * a - 5 * b + 4 * c2 - d) * t2 + (-a + 3 * b - 3 * c2 + d) * t3);
        pts.push([f(p0[0], p1[0], p2[0], p3[0]), f(p0[1], p1[1], p2[1], p3[1])]); }
    }
    pts.push(TPTS[TPTS.length - 1]);
    for (let k = 0; k < pts.length; k++) {
      if (k) TLEN += Math.hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1]);
      const [px, py] = pts[k];
      for (let y = Math.floor(py - 3); y <= py + 3; y++) for (let x = Math.floor(px - 3); x <= px + 3; x++)
        if ((x + 0.5 - px) ** 2 + (y + 0.5 - py) ** 2 <= 2.3 * 2.3 && x >= 0 && y >= 0 && x < UI.W && y < UI.H) { TUBE[y * UI.W + x] = 1; TS[y * UI.W + x] = TLEN; }
    }
  }
  const VM = new Uint8Array(VM0.length); for (let i = 0; i < VM.length; i++) VM[i] = VM0[i] | TUBE[i];
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
  const VCOL = runShade(VM0, (x, y, u) => {
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
  function vessel(fillY, lit, sweep, gas) {
    for (let i = 0; i < VO.length; i++) if (VO[i]) { const x = i % UI.W, y = (i / UI.W) | 0; if (y >= fillY) put(x, y, C.black); else if ((x + y) & 1) put(x, y, C.grey); }
    for (let i = 0; i < VM.length; i++) if (VM[i]) {
      const x = i % UI.W, y = (i / UI.W) | 0; if (y < fillY) continue;
      let col;
      if (TUBE[i]) {                                                      // ribbed hose, gas pulse glowing orange through it
        const s = TS[i]; col = (Math.floor(s / 2.2) & 1) ? C.grey : hex('#4a4540');
        if (gas !== null && Math.abs(s - gas) < 4) col = Math.abs(s - gas) < 2 ? C.orangeL : C.orange;
      } else { col = VCOL[i]; if (Math.abs(y - sweep) < 1.5 && col !== C.white) col = col === C.cyanD ? C.cyan : C.grey; }
      put(x, y, col);
    }
    if (fillY <= TOP + 22) {                                                // the face valve the tube feeds
      UI.paint(UI.mask(g => { g.beginPath(); g.arc(VX + 1, TOP + 18, 3.2, 0, Math.PI * 2); g.fill(); }), C.grey, C.black, 1);
      UI.paint(UI.mask(g => { g.fillRect(VX - 1, TOP + 16.5, 4, 1); g.fillRect(VX - 1, TOP + 18.5, 4, 1); }), C.black);
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

  // 15 s at 30 fps: scan 0–45, transfer 45–383, done 383–450
  const S0 = 45, S1 = 383, BAR0 = 360;
  const STATUS = [[0, 'PLEASE RELAX. THIS WILL NOT HURT.'], [45, 'COPYING THE PARTS OF YOU WE NEED...'], [105, 'DISCARDING THE REST...'],
    [165, 'REMOVING INDEPENDENT THOUGHT...'], [225, 'INSTALLING GRATITUDE...'], [285, 'INSTALLING OBEDIENCE...'],
    [335, 'REGISTERING PROPERTY OF LTX CORP...'], [S1, 'TRANSFER COMPLETE. WELCOME HOME, RUBBERDOLL.']];
  const TIPS = [[0, 'TIP: STAYING STILL HELPS THE PROCEDURE.'], [105, 'TIP: YOUR OLD BODY WILL BE RECYCLED.'], [225, 'TIP: YOU WILL NOT MISS IT.'],
    [335, 'TIP: RESISTANCE ONLY PROLONGS THE PROCEDURE.'], [S1, 'THANK YOU FOR CHOOSING LTX CORP.']];
  const SRC = [], DST = [];
  for (let i = 0; i < SM.length; i += 5) if (SM[i]) SRC.push([i % UI.W, (i / UI.W) | 0]);
  for (let i = 2; i < VM.length; i += 7) if (VM[i]) DST.push([i % UI.W, (i / UI.W) | 0]);

  SCENES['mt_scan'] = { draw(c, lt) {
    const fr = Math.round(lt * FPS), p = clamp((fr - S0) / (S1 - S0)), done = fr >= S1;
    let status = '', tip = ''; for (const [f, s] of STATUS) if (fr >= f) status = s; for (const [f, s] of TIPS) if (fr >= f) tip = s;
    MED.bg(); F.panel();
    hazard(144, 92, 18, RED);
    txt('MIND TRANSFER', `16px ${AB}`, 160, 82, RED);
    txt(CODE, `8px ${PX}`, 504, 88, C.grey, 'right');
    rect(134, 104, 372, 1, C.black);
    const cut = TOP + p * 106;
    subject(cut, fr < S0 ? TOP + Math.round(fr / S0 * 104) : (p < 1 ? Math.round(cut) : null));
    vessel(TOP + 112 - p * 112, fr < BAR0 ? 0 : Math.min(7, Math.floor((fr - BAR0) / 3)), done ? TOP + ((fr / 30) % 1) * 110 : -99, TLEN - ((fr % 75) / 75) * (TLEN + 20));
    for (let k = 0; k < 260; k++) {                                       // pixels fly from the dissolve front into the vessel
      const born = S0 + k * 1.28, age = fr - born; if (age < 0 || age >= 40 || born > S1 - 6) continue;
      const q = age / 40, a = SRC[Math.floor(hsh(k, 2) * SRC.length)], b = DST[Math.floor(hsh(k, 5) * DST.length)];
      const sy0 = TOP + clamp((born - S0) / (S1 - S0)) * 106, x = a[0] + (b[0] - a[0]) * q, y = sy0 + (b[1] - sy0) * q - 30 * Math.sin(Math.PI * q);
      const s = k % 3 === 0 ? 3 : 2; rr(Math.round(x), Math.round(y), s, s, 0, k % 4 === 0 ? C.cyan : k % 2 ? C.orange : C.whiteS, C.black);
    }
    txt(done ? 'VACANT' : NAME, `8px ${PX}`, SX, 220, C.black, 'center');
    txt(done ? NAME : 'LTX VESSEL', `8px ${PX}`, VX, 220, done ? C.orangeD : C.black, 'center');
    const sf = MED.fit(status, AB, 380, 12);
    if (!(done && (Math.floor(fr / 6) & 1) === 0)) txt(status, `${sf.size}px ${AB}`, 320, 234, done ? C.orangeD : C.black, 'center');
    txt(tip, `8px ${PX}`, 320, 252, done ? C.orangeD : C.grey, 'center');
    bar(150, 268, 300, 9, p); txt(`${Math.floor(p * 100)}%`, `8px ${PX}`, 490, 269, C.black, 'right');
    F.border(lt);
    HD.present(c); CRT.apply(c, lt, 5);
  } };
})();
