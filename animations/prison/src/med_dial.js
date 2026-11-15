// med_dial.js · the breathing sequence, layout B ("countdown dial"), animated: 25 s, every beat 1.5 s.
//   0–1 s      INHALE  buffer: empty ring, READY, so the patient can take it all in
//   1–8.5 s    INHALE  five segments light orange one per beat, count 1 → 5
//   8.5–16 s   HOLD    ring resets; five segments light one per beat, count 1 → 5
//   16–23.5 s  EXHALE  the full orange ring drains to cyan in one smooth clockwise sweep, count 5 → 1 per beat
//   23.5–25 s  EXHALE  ring fully cyan, calm air-flow waves in the centre
// The centre number (and READY, and the waves) is centred on its real pixel bounding box, not the font's box.
// The card is the opening prompt's label, taller (406×200) to fit the warnings, with the same corner radius,
// stripe pitch and speed (96 stripes round), and the brand in a header strip. Timing is counted in whole frames
// at 30 fps, so beats land exactly. 25 s is a multiple of the border's 5 s cycle, so the whole clip loops.
// Keyable: exact #00FF00 background, every pixel opaque, no green-ish colours.
(() => {
  const { C, PX, AB } = MED;
  const F = MED.makeFrame(117, 80, 523, 280, 96);
  const CARE = '12px "Poppins-600"', FPS = 30, TAU = Math.PI * 2;
  const STAGES = {
    in:   { word: 'INHALE', care: 'Breathe in deeply. D0LL G4S is highly effective.',
            warn: 'WARNING: D0LL G4S IS DESIGNED TO INDUCE A HIGHLY RECEPTIVE STATE' },
    hold: { word: 'HOLD',   care: 'Hold it in. D0LL G4S Influences brain function.',
            warn: 'WARNING: D0LL G4S WILL TURN YOU INTO A MINDLESS PRISONER FOR LTX CORP.' },
    out:  { word: 'EXHALE', care: 'Breathe out slowly. Thank you for your compliance LTX Corp cares.',
            warn: 'WARNING: D0LL G4S IS HIGHLY ADDICTIVE. PROGRAMMING IS PERMANENT.' },
  };
  const txt = (s, font, x, y, col, align = 'left') => UI.paint(UI.text(s, font, x, y, align), col);
  // left-aligned text whose zeros get a slash through the counter (Ø-like), so "D0LL" never reads as "DOLL"
  function txtZ(s, font, x, y, col) {
    txt(s, font, x, y, col);
    for (let i = s.indexOf('0'); i >= 0; i = s.indexOf('0', i + 1)) {
      const m = UI.text('0', font, x + MED.width(s.slice(0, i), font), y);
      let x0 = UI.W, x1 = -1, y0 = UI.H, y1 = -1;
      for (let k = 0; k < m.length; k++) if (m[k]) { const px = k % UI.W, py = (k / UI.W) | 0; x0 = Math.min(x0, px); x1 = Math.max(x1, px); y0 = Math.min(y0, py); y1 = Math.max(y1, py); }
      if (x1 < 0) continue;
      const n = Math.max(x1 - x0, y1 - y0);
      for (let j = 0; j <= n; j++) {
        const px = Math.round(x0 + 1 + (x1 - x0 - 2) * j / n), py = Math.round(y1 - 1 - (y1 - y0 - 2) * j / n);
        if (!m[py * UI.W + px]) HD.put(px, py, col);
      }
    }
  }
  // greedy word wrap: by measured width, or by character count for the monospaced pixel font
  function wrap(str, fits) { const out = []; let line = ''; for (const w of str.split(' ')) { const t = line ? line + ' ' + w : w; if (line && !fits(t)) { out.push(line); line = w; } else line = t; } if (line) out.push(line); return out; }
  // sentence-aware wrap for the reassurance: each sentence starts its own line
  const careLines = s => s.split(/(?<=\.)\s+/).flatMap(sent => wrap(sent, t => MED.width(t, CARE) <= 244));
  const warnLines = s => wrap(s, t => t.length <= 28);
  const waves = (x, y, w, col) => UI.paint(UI.mask(c => { c.lineWidth = 2.2; for (let k = 0; k < 3; k++) { c.beginPath(); for (let i = 0; i <= w; i += 2) { const yy = y + k * 11 + 3 * Math.sin(i / w * Math.PI * 3 + k); i ? c.lineTo(x + i, yy) : c.moveTo(x + i, yy); } c.stroke(); } }), col, C.black, 1);

  // the dial: five segments with black gaps; lit = segments lit in order; drain = fraction swept to cyan
  const CX = 194, CY = 194, RO = 46, RI = 32, SEG = 5, GAP = 0.14;
  // optical centring: the shift that puts a mask's real pixel bounding box centre on the ring centre
  function shiftTo(m) {
    let x0 = UI.W, x1 = -1, y0 = UI.H, y1 = -1;
    for (let k = 0; k < m.length; k++) if (m[k]) { const x = k % UI.W, y = (k / UI.W) | 0; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    return { dx: Math.round(CX - (x0 + x1 + 1) / 2), dy: Math.round(CY - (y0 + y1 + 1) / 2) };
  }
  function centred(m, col) {
    const { dx, dy } = shiftTo(m), o = new Uint8Array(m.length);
    for (let k = 0; k < m.length; k++) if (m[k]) { const x = k % UI.W + dx, y = ((k / UI.W) | 0) + dy; if (x >= 0 && y >= 0 && x < UI.W && y < UI.H) o[y * UI.W + x] = 1; }
    UI.paint(o, col);
  }
  function dial({ lit = 0, flash = -1, drain = null, center }) {
    UI.paint(UI.mask(c => { c.beginPath(); c.arc(CX, CY, RO + 1, 0, TAU); c.arc(CX, CY, RI - 1, TAU, 0, true); c.fill(); }), C.black);
    for (let k = 0; k < SEG; k++) {
      const a0 = -Math.PI / 2 + k * TAU / SEG + GAP / 2, a1 = a0 + TAU / SEG - GAP;
      const seg = UI.mask(c => { c.beginPath(); c.arc(CX, CY, RO, a0, a1); c.arc(CX, CY, RI, a1, a0, true); c.closePath(); c.fill(); });
      if (drain !== null) UI.paint(seg, (x, y) => { const rel = ((Math.atan2(y + 0.5 - CY, x + 0.5 - CX) + Math.PI / 2) % TAU + TAU) % TAU; return rel < drain * TAU ? C.cyan : C.orange; });
      else UI.paint(seg, k < lit ? (k === flash ? C.orangeL : C.orange) : C.whiteS);
    }
    if (center.digit) centred(UI.text(String(center.digit), `38px ${AB}`, CX, CY - 26, 'center'), C.black);
    else if (center.ready) centred(UI.text('READY', `8px ${PX}`, CX, CY - 4, 'center'), C.black);
    else if (center.waves) {                                         // waves: measure, then redraw shifted
      const m = UI.mask(c => { c.lineWidth = 2.2; for (let k = 0; k < 3; k++) { c.beginPath(); for (let i = 0; i <= 36; i += 2) { const yy = CY - 11 + k * 11 + 3 * Math.sin(i / 36 * Math.PI * 3 + k); i ? c.lineTo(CX - 18 + i, yy) : c.moveTo(CX - 18 + i, yy); } c.stroke(); } });
      const { dx, dy } = shiftTo(m); waves(CX - 18 + dx, CY - 11 + dy, 36, C.cyan);
    }
  }

  // what is on screen at frame fr (0..749); every beat is 45 frames (1.5 s)
  const BEAT = 45, BUF = 30, IN0 = BUF, HOLD0 = IN0 + 5 * BEAT, OUT0 = HOLD0 + 5 * BEAT, REST0 = OUT0 + 5 * BEAT, TOTAL = REST0 + 45;
  function stateAt(fr) {
    if (fr < HOLD0) {                                                 // INHALE: 1 s buffer, then 5 beats
      if (fr < IN0) return { st: STAGES.in, d: { center: { ready: true } } };
      const k = Math.min(4, Math.floor((fr - IN0) / BEAT));
      return { st: STAGES.in, d: { lit: k + 1, flash: (fr - IN0) - k * BEAT < 6 ? k : -1, center: { digit: k + 1 } } };
    }
    if (fr < OUT0) {                                                  // HOLD: 5 beats
      const k = Math.min(4, Math.floor((fr - HOLD0) / BEAT));
      return { st: STAGES.hold, d: { lit: k + 1, flash: (fr - HOLD0) - k * BEAT < 6 ? k : -1, center: { digit: k + 1 } } };
    }
    if (fr < REST0) {                                                 // EXHALE: drain over 5 beats, count 5 → 1
      return { st: STAGES.out, d: { drain: (fr - OUT0) / (REST0 - OUT0), center: { digit: 5 - Math.floor((fr - OUT0) / BEAT) } } };
    }
    return { st: STAGES.out, d: { drain: 1, center: { waves: true } } };   // EXHALE: rest, all cyan
  }

  SCENES['med_breath_seq'] = {
    draw(c, lt) {
      const fr = ((Math.round(lt * FPS) % TOTAL) + TOTAL) % TOTAL, { st, d } = stateAt(fr);
      MED.bg(); F.panel();
      // header strip
      const lw = Math.round(MED.width('LTX', `14px ${AB}`));
      txt('LTX', `14px ${AB}`, 136, 96, C.black); MED.copyright(136 + lw + 2, 96);
      txt('d0ll-g4s', `8px ${PX}`, 136 + lw + 14, 100, C.black);
      txt('U-R-RBBR-D011', `8px ${PX}`, 504, 100, C.grey, 'right');
      UI.paint(UI.mask(g => g.fillRect(134, 115, 372, 1)), C.black);
      dial(d);
      // right column: stage word, reassurance, warning with a hazard triangle, status pill
      const f = MED.fit(st.word, AB, 236, 34);
      txt(st.word, `${f.size}px ${AB}`, 262, 124, C.black);
      const cl = careLines(st.care);
      cl.forEach((s, k) => txtZ(s, CARE, 262, 162 + k * 14, C.black));
      const wy = 162 + cl.length * 14 + 6, wl = warnLines(st.warn);
      UI.paint(UI.mask(g => { g.beginPath(); g.moveTo(268, wy - 2); g.lineTo(275, wy + 10); g.lineTo(261, wy + 10); g.closePath(); g.fill(); }), C.orange, C.black, 1);
      UI.paint(UI.mask(g => { g.fillRect(267.5, wy + 1, 1.5, 5); g.fillRect(267.5, wy + 7.5, 1.5, 1.5); }), C.black);
      wl.forEach((s, k) => {
        if (k === 0 && s.startsWith('WARNING:')) { txt('WARNING:', `8px ${PX}`, 280, wy, C.orangeD); txtZ(s.slice(9), `8px ${PX}`, 280 + 9 * 8, wy, C.black); }
        else txtZ(s, `8px ${PX}`, 280, wy + k * 10, C.black);
      });
      UI.paint(UI.rrect(262, 252, 142, 14, 7), C.cyan, C.black, 1);
      txt('DOSE IN PROGRESS', `8px ${PX}`, 333, 255, C.black, 'center');
      F.border(lt);
      HD.present(c);
    },
  };
})();
