// med_breath.js · the breathing stages that follow the medicine prompt: INHALE (count 4), HOLD (count 3), EXHALE.
// Three candidate layouts, all built from the same label parts as the opening prompt (med_common.js):
//   A · label continuation: the LTX column stays; the right side shows the stage, a count tile and count pips
//   B · countdown dial: a segmented ring that counts the beats, the brand in a small header strip
//   C · step tracker: tabs for the three steps, a giant count, and a cyan "care note" with the small print
// Copy is faux-caring corporate reassurance. Stills show each stage at the start of its count.
// Keyable: exact #00FF00 background, every pixel opaque, no green-ish colours.
(() => {
  const { C, PX, AB, RX } = MED;
  const CARE = '12px "Poppins-600"';
  const STAGES = {
    in:   { word: 'INHALE', verb: 'BREATHE IN',  count: 4, step: 1, care: ['Breathe in deeply.', 'Your calm is our priority.'], fine: ['PATIENTS WHO FOLLOW EVERY', 'INSTRUCTION RECOVER SOONER.'] },
    hold: { word: 'HOLD',   verb: 'HOLD BREATH', count: 3, step: 2, care: ['Hold it in.', 'Let the medicine find you.'],         fine: ['MOVEMENT DURING TREATMENT', 'IS A SYMPTOM. STAY STILL.'] },
    out:  { word: 'EXHALE', verb: 'BREATHE OUT', count: 0, step: 3, care: ['Breathe out slowly.', 'Let go of your doubts.'],      fine: ['THANK YOU FOR YOUR', 'COMPLIANCE. LTX CARES.'] },
  };
  const txt = (s, font, x, y, col, align = 'left', outline) => UI.paint(UI.text(s, font, x, y, align), col, outline);
  const rect = (x, y, w, h, col, outline) => UI.paint(UI.mask(c => c.fillRect(x, y, w, h)), col, outline);
  const lines = (arr, font, x, y, step, col) => arr.forEach((s, k) => txt(s, font, x, y + k * step, col));
  // count pips: n small tiles, the first one lit
  const pips = (n, x, y) => { for (let k = 0; k < n; k++) UI.paint(UI.rrect(x + k * 14, y, 10, 7, 1), k === 0 ? C.orange : C.whiteS, C.black, 1); };
  // exhale airflow: three pixel waves
  const waves = (x, y, w, col) => UI.paint(UI.mask(c => { c.lineWidth = 2.2; for (let k = 0; k < 3; k++) { c.beginPath(); for (let i = 0; i <= w; i += 2) { const yy = y + k * 11 + 3 * Math.sin(i / w * Math.PI * 3 + k); i ? c.lineTo(x + i, yy) : c.moveTo(x + i, yy); } c.stroke(); } }), col, C.black, 1);
  const frame = draw => (c, lt) => { MED.bg(); MED.panel(); draw(); MED.border(lt); HD.present(c); };

  // ---- A · label continuation ----
  const styleA = st => frame(() => {
    MED.brand(true);
    txt(`STEP ${st.step} OF 3`, `8px ${PX}`, RX, 112, C.grey);
    const f = MED.fit(st.word, AB, 190, 32);
    txt(st.word, `${f.size}px ${AB}`, RX, 124, C.black);
    const out = !st.count;
    UI.paint(UI.rrect(448, 110, 50, 50, 6), out ? C.cyan : C.orange, C.black, 1);
    if (out) waves(455, 123, 36, C.white); else txt(String(st.count), `34px ${AB}`, 473, 114, C.black, 'center');
    txt(out ? 'SLOW' : 'COUNT', `8px ${PX}`, 473, 164, C.black, 'center');
    rect(RX, 176, 498 - RX, 1, C.black);
    lines(st.care, CARE, RX, 180, 14, C.black);
    lines(st.fine, `8px ${PX}`, RX, 212, 10, C.grey);
    if (out) UI.paint(UI.rrect(RX, 238, 110, 7, 1), C.cyan, C.black, 1); else pips(st.count, RX, 238);
    txt('U-R-RBBR-D011', `8px ${PX}`, 498, 238, C.grey, 'right');
  });

  // ---- B · countdown dial ----
  const styleB = st => frame(() => {
    const lw = Math.round(MED.width('LTX', `14px ${AB}`));
    txt('LTX', `14px ${AB}`, 138, 108, C.black); MED.copyright(138 + lw + 2, 108);
    txt('d0ll-g4s', `8px ${PX}`, 138 + lw + 14, 112, C.black);
    txt('U-R-RBBR-D011', `8px ${PX}`, 502, 112, C.grey, 'right');
    rect(136, 127, 368, 1, C.black);
    const cx = 194, cy = 192, ro = 44, ri = 31, out = !st.count, n = out ? 1 : st.count, gap = out ? 0 : 0.14;
    UI.paint(UI.mask(c => { c.beginPath(); c.arc(cx, cy, ro + 1, 0, Math.PI * 2); c.arc(cx, cy, ri - 1, Math.PI * 2, 0, true); c.fill(); }), C.black);
    for (let k = 0; k < n; k++) {
      const a0 = -Math.PI / 2 + k * Math.PI * 2 / n + gap / 2, a1 = a0 + Math.PI * 2 / n - gap;
      UI.paint(UI.mask(c => { c.beginPath(); c.arc(cx, cy, ro, a0, a1); c.arc(cx, cy, ri, a1, a0, true); c.closePath(); c.fill(); }), out ? C.cyan : k === 0 ? C.orange : C.whiteS);
    }
    if (out) waves(cx - 18, cy - 11, 36, C.cyan); else txt(String(st.count), `38px ${AB}`, cx, cy - 26, C.black, 'center');
    const f = MED.fit(st.word, AB, 236, 34);
    txt(st.word, `${f.size}px ${AB}`, 262, 136, C.black);
    lines(st.care, CARE, 262, 180, 14, C.black);
    lines(st.fine, `8px ${PX}`, 262, 212, 10, C.grey);
    UI.paint(UI.rrect(262, 234, 142, 14, 7), C.cyan, C.black, 1);
    txt('DOSE IN PROGRESS', `8px ${PX}`, 333, 237, C.black, 'center');
  });

  // ---- C · step tracker ----
  const styleC = st => frame(() => {
    ['1 INHALE', '2 HOLD', '3 EXHALE'].forEach((label, k) => {
      const done = k + 1 < st.step, now = k + 1 === st.step, x = 134 + k * 126;
      UI.paint(UI.rrect(x, 108, 120, 20, 4), now ? C.orange : done ? C.cyan : C.whiteS, C.black, 1);
      txt(done ? label.slice(2) + ' OK' : label, `8px ${PX}`, x + 60, 114, now || done ? C.black : C.grey, 'center');
    });
    const out = !st.count;
    if (out) waves(140, 152, 64, C.cyan); else txt(String(st.count), `62px ${AB}`, 172, 132, C.black, 'center');
    txt(out ? 'SLOWLY' : 'COUNT', `8px ${PX}`, 172, 200, C.grey, 'center');
    rect(222, 138, 2, 66, C.black);
    const f = MED.fit(st.verb, AB, 270, 24);
    txt(st.verb, `${f.size}px ${AB}`, 236, 140, C.black);
    lines(st.care, CARE, 236, 172, 14, C.black);
    UI.paint(UI.rrect(134, 212, 372, 36, 3), C.cyanL, C.black, 1);
    rect(135, 213, 5, 34, C.cyanD);
    txt('CARE NOTE', `8px ${PX}`, 148, 216, C.cyanD);
    lines(st.fine, `8px ${PX}`, 148, 226, 10, C.black);
    const lw = Math.round(MED.width('LTX', `14px ${AB}`));
    txt('LTX', `14px ${AB}`, 446, 216, C.black); MED.copyright(446 + lw + 2, 216);
    txt('d0ll-g4s', `8px ${PX}`, 494, 236, C.black, 'right');
  });

  for (const [k, st] of Object.entries(STAGES)) {
    SCENES[`brA_${k}`] = { draw: styleA(st) };
    SCENES[`brB_${k}`] = { draw: styleB(st) };
    SCENES[`brC_${k}`] = { draw: styleC(st) };
  }
})();
