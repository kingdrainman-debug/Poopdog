// alert_seq.js · the resistance-alert sequence for subject RUBBERDOLL, as separate sections with two variants each.
//   Section 1 (5 s) · the "RESISTANCE DETECTED" terminal flashes on screen
//     rd_term_a  type & blink: CRT power-on, the diagnosis types out, the red bar blinks, transfer begins
//     rd_term_b  alarm strobe: hard cut, the red bar slams in with a shake, the box alternates with a red alarm version
//   Section 2 (10 s) · the correctional mind transfer, subject → a figure in a shiny black full-body suit
//     rd_xfer_a  data stream: blocks stream head to head, the subject drains to grey, the vessel's barcode lights up
//     rd_xfer_b  scan & dissolve: the subject dissolves into pixels that fill the empty vessel; labels swap at the end
// All flashing is at or under 2.5 per second (inside the usual 3-per-second photosensitivity guideline).
// Keyable: exact #00FF00 background, every pixel opaque, no green-ish colours; the CRT pass never touches the green.
(() => {
  const { C, PX, AB } = MED, { hex, put, bayer } = HD;
  const RED = hex('#e8322a'), REDD = hex('#8f1a14'), RUST = hex('#3d1704'), GLOSS = hex('#2b2320'), FPS = 30;
  const NAME = 'RUBBERDOLL', CODE = 'U-R-RBBR-D011';
  const clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
  const txt = (s, font, x, y, col, align = 'left', outline) => UI.paint(UI.text(s, font, x, y, align), col, outline);
  const rect = (x, y, w, h, col, outline) => UI.paint(UI.mask(c => c.fillRect(x, y, w, h)), col, outline);
  const rr = (x, y, w, h, r, col, outline) => UI.paint(UI.rrect(x, y, w, h, r), col, outline);
  const hsh = (a, b) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
  function hazard(cx, cy, size, fill) {
    const h = size, w = size * 1.15;
    UI.paint(UI.mask(c => { c.beginPath(); c.moveTo(cx, cy - h / 2); c.lineTo(cx + w / 2, cy + h / 2); c.lineTo(cx - w / 2, cy + h / 2); c.closePath(); c.fill(); }), fill, C.black, 1);
    const bw = Math.max(1.5, size / 9);
    UI.paint(UI.mask(c => { c.fillRect(cx - bw / 2, cy - h * 0.18, bw, h * 0.38); c.fillRect(cx - bw / 2, cy + h * 0.28, bw, bw); }), C.black);
  }
  function bar(x, y, w, h, p, col, back) {
    rr(x, y, w, h, 2, back, C.black);
    const fw = Math.round((w - 2) * clamp(p));
    if (fw > 0) UI.paint(UI.mask(c => c.fillRect(x + 1, y + 1, fw, h - 2)), (px, py) => ((((px + py) >> 2) & 1) ? col : (col === C.cyan ? C.cyanD : C.orangeD)));
  }
  const finish = (c, lt) => { HD.present(c); CRT.apply(c, lt, 5); };

  // ─────────────────────────────── Section 1 · the terminal ───────────────────────────────
  const BX = 110, BY = 96, BW = 420, BH = 168;
  const LINES = [`> SUBJECT: ${NAME}`, '> CONSCIENCE INTEGRITY ..........', '> COMPLIANCE INDEX ..............'];
  const VALUES = [[CODE, C.grey], ['FAIL', RED], ['12%', RED]];
  // s = { open, typed:[n0,n1,n2], vals, barOn, barScale, barCol, l4, prog, l6, cursor, alarm, ox, oy }
  function terminal(s) {
    const ox = s.ox || 0, oy = s.oy || 0, alarm = !!s.alarm;
    const bg = alarm ? REDD : C.black, fg = alarm ? C.white : C.orange;
    const h = Math.round(BH * clamp(s.open)), y0 = BY + Math.round((BH - h) / 2) + oy;
    if (h < 3) { rect(BX + ox + 40, BY + BH / 2 - 1 + oy, BW - 80, 2, C.orangeL); return; }    // CRT power-on line
    rr(BX + ox, y0, BW, h, 2, bg, alarm ? RED : C.rust);
    if (h < BH) return;
    rect(BX + 1 + ox, BY + 1 + oy, BW - 2, 14, alarm ? RED : C.orange);
    txt('LTX//MEDSYS   TERMINAL 07', `8px ${PX}`, 118 + ox, 100 + oy, C.black);
    txt('SESSION 0412', `8px ${PX}`, 522 + ox, 100 + oy, C.black, 'right');
    LINES.forEach((L, k) => {
      const n = s.typed[k]; if (n > 0) txt(L.slice(0, n), `8px ${PX}`, 120 + ox, 120 + k * 14 + oy, fg);
      if (s.vals[k]) txt(VALUES[k][0], `8px ${PX}`, 520 + ox, 120 + k * 14 + oy, alarm ? C.white : VALUES[k][1], 'right');
    });
    if (s.barOn) {
      const sc = s.barScale || 1, w = Math.round(404 * sc), hh = Math.round(30 * sc), cx = 320 + ox, cy = 177 + oy;
      rr(cx - w / 2, cy - hh / 2, w, hh, 2, s.barCol || REDD, RED);
      txt('RESISTANCE DETECTED', `${Math.round(20 * sc)}px ${AB}`, cx, cy - Math.round(11 * sc), C.white, 'center');
    }
    if (s.l4 > 0) txt('> EMPLOYING CORRECTIONAL MIND TRANSFER'.slice(0, s.l4), `8px ${PX}`, 120 + ox, 202 + oy, alarm ? C.white : C.cyan);
    if (s.prog !== undefined) { txt('>', `8px ${PX}`, 120 + ox, 216 + oy, fg); bar(136 + ox, 215 + oy, 300, 9, s.prog, C.orange, C.black); txt(`${Math.floor(s.prog * 100)}%`, `8px ${PX}`, 470 + ox, 216 + oy, fg); }
    if (s.l6 > 0) txt('> DO NOT RESIST. LTX CARES.'.slice(0, s.l6), `8px ${PX}`, 120 + ox, 232 + oy, C.white);
    if (s.cursor) rect(120 + Math.max(1, s.l6 || 1) * 8 + ox, 232 + oy, 7, 8, C.cyan);
  }
  const typed = (fr, start, cps = 60) => Math.max(0, Math.floor((fr - start) * cps / FPS));

  // 1A · type & blink (150 frames)
  SCENES['rd_term_a'] = { draw(c, lt) {
    const fr = Math.round(lt * FPS); MED.bg();
    const open = clamp((fr - 2) / 8);
    const t1 = typed(fr, 10), t2 = typed(fr, 26), t3 = typed(fr, 46);
    const barOn = fr >= 64 && (fr >= 112 || ((fr - 64) % 12) < 6);
    terminal({ open, typed: [Math.min(t1, LINES[0].length), Math.min(t2, LINES[1].length), Math.min(t3, LINES[2].length)],
      vals: [t1 >= LINES[0].length, t2 >= LINES[1].length, t3 >= LINES[2].length], barOn,
      l4: typed(fr, 74), prog: fr >= 96 ? clamp((fr - 96) / 54) * 0.12 : undefined, l6: Math.min(27, typed(fr, 116)), cursor: fr >= 116 && (Math.floor(fr / 8) & 1) === 0 });
    finish(c, lt);
  } };

  // 1B · alarm strobe (150 frames)
  SCENES['rd_term_b'] = { draw(c, lt) {
    const fr = Math.round(lt * FPS); MED.bg();
    const slam = clamp(fr / 6), shake = fr < 12 ? Math.round((hsh(fr, 3) - 0.5) * 8) : 0, shakeY = fr < 12 ? Math.round((hsh(fr, 4) - 0.5) * 6) : 0;
    const alarm = fr < 48 && (Math.floor(fr / 6) & 1) === 1;             // 2.5 alarm flashes per second for 1.6 s
    const pulse = fr >= 48 && (Math.floor(fr / 15) & 1) === 1;          // then a slow 1 Hz pulse of the red bar
    terminal({ open: 1, typed: LINES.map(L => L.length), vals: [1, 1, 1], barOn: true, barScale: 1.8 - 0.8 * slam, barCol: pulse ? RED : REDD,
      l4: 99, prog: fr >= 48 ? clamp((fr - 48) / 102) * 0.2 : 0, l6: 27, cursor: (Math.floor(fr / 8) & 1) === 0, alarm, ox: shake, oy: shakeY });
    if (fr >= 70 && fr < 73) {                                           // a signal tear: a band of the box slips sideways
      const y0 = 150 + (fr - 70) * 14;
      UI.paint(UI.mask(g => g.fillRect(BX + 16, y0, BW - 16, 10)), (x, y) => ((x >> 2) & 1) ? C.orange : C.black);
    }
    finish(c, lt);
  } };

  // ─────────────────────────────── Section 2 · the transfer ───────────────────────────────
  const F = MED.makeFrame(117, 92, 523, 268, 92);
  const TOP = 132, SX = 186, VX = 454;                                   // figure top, subject x, vessel x
  // a front-on androgynous bust: head, neck, shoulders and chest (about 64 × 82 art px)
  const headM = cx => UI.mask(g => { g.beginPath(); g.ellipse(cx, TOP + 15, 12, 15, 0, 0, Math.PI * 2); g.fill(); });
  const neckM = cx => UI.mask(g => g.fillRect(cx - 5, TOP + 26, 10, 10));
  const bodyM = cx => UI.mask(g => { g.beginPath(); g.moveTo(cx - 31, TOP + 82); g.lineTo(cx - 29, TOP + 50); g.quadraticCurveTo(cx - 27, TOP + 38, cx - 9, TOP + 35);
    g.lineTo(cx + 9, TOP + 35); g.quadraticCurveTo(cx + 27, TOP + 38, cx + 29, TOP + 50); g.lineTo(cx + 31, TOP + 82); g.closePath(); g.fill(); });
  const union = (...ms) => { const o = new Uint8Array(ms[0].length); for (const m of ms) for (let i = 0; i < o.length; i++) o[i] |= m[i]; return o; };
  const M = { sH: headM(SX), sN: neckM(SX), sB: bodyM(SX), vH: headM(VX), vN: neckM(VX), vB: bodyM(VX) };
  M.s = union(M.sH, M.sN, M.sB); M.v = union(M.vH, M.vN, M.vB);
  M.sHair = UI.and(M.sH, UI.mask(g => { g.beginPath(); g.ellipse(SX, TOP + 12, 13, 13, 0, Math.PI, 0); g.lineTo(SX + 13, TOP + 16); g.lineTo(SX + 10, TOP + 12); g.lineTo(SX - 10, TOP + 12); g.lineTo(SX - 13, TOP + 16); g.closePath(); g.fill(); }));
  M.vOutline = (() => { const d = UI.dilate(M.v, 1), o = new Uint8Array(d.length); for (let i = 0; i < d.length; i++) o[i] = d[i] & !M.v[i]; return o; })();
  M.sOutline = (() => { const d = UI.dilate(M.s, 1), o = new Uint8Array(d.length); for (let i = 0; i < d.length; i++) o[i] = d[i] & !M.s[i]; return o; })();
  const grey = col => { const l = (col[0] * 0.3 + col[1] * 0.55 + col[2] * 0.15) / 255; return l > 0.75 ? C.whiteS : l > 0.45 ? C.grey : l > 0.15 ? hex('#4a4540') : C.black; };

  // the subject: cropped hair, neutral face, orange prison top. drainY: rows above it are drained to grey; cut: rows above are gone
  function subject({ drainY = -1, cut = -1 } = {}) {
    const show = (x, y) => y >= cut;
    const col = (x, y, base) => (y < drainY ? grey(base) : base);
    const paintIf = (m, f) => { for (let i = 0; i < m.length; i++) if (m[i]) { const x = i % UI.W, y = (i / UI.W) | 0; if (show(x, y)) put(x, y, f(x, y)); } };
    for (let i = 0; i < M.sOutline.length; i++) if (M.sOutline[i]) { const x = i % UI.W, y = (i / UI.W) | 0; if (show(x, y)) put(x, y, C.black); else if ((x + y) & 1) put(x, y, C.whiteS); }
    paintIf(M.sB, (x, y) => col(x, y, x > SX + 18 || (y > TOP + 72 && bayer(x, y) < 0.5) ? C.orangeD : (Math.abs(x - SX) < 1.2 && y < TOP + 48 && y > TOP + 35 ? C.orangeD : C.orange)));
    paintIf(M.sN, (x, y) => col(x, y, x > SX + 2 ? C.grey : C.whiteS));
    paintIf(M.sH, (x, y) => col(x, y, x > SX + 7 && bayer(x, y) < 0.5 ? C.grey : C.whiteS));
    paintIf(M.sHair, (x, y) => col(x, y, RUST));
    for (const [x, y, w] of [[SX - 6, TOP + 17, 2], [SX + 4, TOP + 17, 2], [SX - 2, TOP + 24, 4]]) for (let k = 0; k < w; k++) if (show(x + k, y)) put(x + k, y, col(x + k, y, y > TOP + 20 ? C.grey : C.black));
    if (cut <= TOP + 52) {                                              // prison ID patch
      rect(SX - 12, TOP + 51, 24, 10, drainY > TOP + 51 ? C.whiteS : C.white, C.black);
      txt('0412', `8px ${PX}`, SX, TOP + 52, drainY > TOP + 51 ? C.grey : C.black, 'center');
    }
  }
  // the vessel: a figure in a shiny black full-body suit, featureless, glossy highlights, orange barcode on the chest.
  // fillY: only rows at or below it are filled (the rest is a dotted outline); lit: barcode bars lit (of 9); sweep: gloss row
  function vessel({ fillY = 0, lit = 9, sweep = -99 } = {}) {
    for (let i = 0; i < M.vOutline.length; i++) if (M.vOutline[i]) { const x = i % UI.W, y = (i / UI.W) | 0; if (y >= fillY) put(x, y, C.black); else if ((x + y) & 1) put(x, y, C.grey); }
    const L = [-0.6, -0.8];
    for (let i = 0; i < M.v.length; i++) if (M.v[i]) {
      const x = i % UI.W, y = (i / UI.W) | 0; if (y < fillY) continue;
      let col = C.black;
      if (M.vH[i]) {                                                    // glossy head: a sharp specular spot and a cool rim
        const nx = (x + 0.5 - VX) / 12, ny = (y + 0.5 - TOP - 15) / 15, d = nx * L[0] + ny * L[1];
        col = d > 0.72 ? C.white : d > 0.5 ? C.grey : d > 0.3 ? GLOSS : C.black;
        if (nx > 0.82) col = C.cyanD;
      } else if (M.vB[i]) {                                             // body: a highlight band on the left, a cool rim on the right
        const hw = 29 + (y - TOP - 35) * 0.04, u = (x + 0.5 - VX) / hw;
        col = u > -0.66 && u < -0.5 ? (u > -0.6 && u < -0.56 ? C.white : C.grey) : u > -0.74 && u < -0.42 ? GLOSS : C.black;
        if (u > 0.88) col = C.cyanD;
        if (y < TOP + 40 && x < VX - 8 && x > VX - 26 && y > TOP + 35) col = C.grey;     // shoulder catch-light
      } else col = x < VX - 2 ? GLOSS : C.black;                        // neck
      if (Math.abs(y - sweep) < 1.5 && col !== C.white) col = col === C.cyanD ? C.cyan : C.grey;   // moving gloss
      put(x, y, col);
    }
    // barcode on the chest
    const bx = VX - 13, by = TOP + 49;
    if (by + 14 >= fillY) {
      rect(bx - 1, by - 1, 28, 16, C.black);
      for (let k = 0, x = bx; k < 9; k++) { const w = 1 + (k * 7) % 3; rect(x, by, w, 14, k < lit ? C.orange : RUST); x += w + 1 + (k % 2); }
    }
  }
  function card(lt, status, statusCol, p, labelL, labelR, labelRCol = C.black) {
    F.panel();
    hazard(144, 114, 18, RED);
    txt('RESISTANCE DETECTED', `16px ${AB}`, 160, 104, RED);
    txt(CODE, `8px ${PX}`, 504, 110, C.grey, 'right');
    rect(134, 126, 372, 1, C.black);
    txt(labelL, `8px ${PX}`, SX, 218, C.black, 'center'); txt(labelR, `8px ${PX}`, VX, 218, labelRCol, 'center');
    if (status) txt(status, `12px ${AB}`, 320, 231, statusCol, 'center');
    bar(150, 248, 300, 9, p, C.orange, C.whiteS); txt(`${Math.floor(clamp(p) * 100)}%`, `8px ${PX}`, 490, 249, C.black, 'right');
  }

  // 2A · data stream (300 frames)
  SCENES['rd_xfer_a'] = { draw(c, lt) {
    const fr = Math.round(lt * FPS), p = clamp((fr - 30) / 240), done = fr >= 270;
    MED.bg();
    card(lt, done ? ((Math.floor(fr / 6) & 1) ? 'TRANSFER COMPLETE' : '') : 'EMPLOYING CORRECTIONAL MIND TRANSFER', done ? C.orangeD : C.black, p, NAME, 'LTX VESSEL');
    subject({ drainY: TOP + p * 84 });
    vessel({ lit: Math.floor(p * 9 + 1e-6), sweep: TOP + ((fr / 45) % 1) * 90 });
    for (let k = 0; k < 64; k++) {                                       // data blocks stream from the subject's head to the vessel's
      const born = 30 + k * 4, age = fr - born; if (age < 0 || age >= 36 || born > 266) continue;
      const q = age / 36, x = SX + 16 + (VX - SX - 32) * q, y = TOP + 15 - 14 * Math.sin(Math.PI * q) + (hsh(k, 1) - 0.5) * 10, s = 1 + (k % 3);
      rr(Math.round(x - s), Math.round(y - s), 2 * s, 2 * s, 0, k % 2 ? C.orange : C.cyan, C.black);
    }
    F.border(lt); finish(c, lt);
  } };

  // 2B · scan & dissolve (300 frames)
  const STATUS = [[0, 'SCANNING SUBJECT...'], [30, 'COPYING MEMORIES...'], [105, 'OVERWRITING IDENTITY...'], [180, 'INSTALLING OBEDIENCE...'], [255, 'TRANSFER COMPLETE']];
  const SRC = [];                                                       // subject pixels, sampled once for the particles
  for (let i = 0; i < M.s.length; i += 7) if (M.s[i]) SRC.push([i % UI.W, (i / UI.W) | 0]);
  const DST = [];
  for (let i = 3; i < M.v.length; i += 11) if (M.v[i]) DST.push([i % UI.W, (i / UI.W) | 0]);
  SCENES['rd_xfer_b'] = { draw(c, lt) {
    const fr = Math.round(lt * FPS), p = clamp((fr - 30) / 225), done = fr >= 255;
    let status = ''; for (const [f, s] of STATUS) if (fr >= f) status = s;
    MED.bg();
    card(lt, done && (Math.floor(fr / 6) & 1) === 0 ? '' : status, done ? C.orangeD : C.black, p, done ? 'VACANT' : NAME, done ? NAME : 'LTX VESSEL', done ? C.orangeD : C.black);
    const cut = TOP + p * 84;
    subject({ cut });
    if (fr < 30) { const sy = TOP + Math.round(fr / 30 * 84); rect(SX - 34, sy, 68, 2, C.cyan, C.black); }      // scan line
    else if (p < 1) rect(SX - 33, Math.round(cut), 66, 1, C.cyan);                                               // dissolve front
    vessel({ fillY: TOP + 84 - p * 84, lit: fr < 240 ? 0 : Math.min(9, Math.floor((fr - 240) / 2)), sweep: done ? TOP + ((fr / 30) % 1) * 90 : -99 });
    for (let k = 0; k < 260; k++) {                                      // particles fly from the dissolve front to the vessel
      const born = 30 + k * 0.85, age = fr - born; if (age < 0 || age >= 32 || born > 252) continue;
      const q = age / 32, a = SRC[Math.floor(hsh(k, 2) * SRC.length)], bpt = DST[Math.floor(hsh(k, 5) * DST.length)];
      const sx0 = a[0], sy0 = TOP + clamp((born - 30) / 225) * 84, x = sx0 + (bpt[0] - sx0) * q, y = sy0 + (bpt[1] - sy0) * q - 30 * Math.sin(Math.PI * q);
      const s = k % 3 === 0 ? 3 : 2; rr(Math.round(x), Math.round(y), s, s, 0, k % 4 === 0 ? C.cyan : k % 2 ? C.orange : C.whiteS, C.black);
    }
    F.border(lt); finish(c, lt);
  } };
})();
