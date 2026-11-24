// independence.js · the "INDEPENDENCE DETECTED" terminal for subject RUBBERDOLL (replaces the resistance version).
// 12 s, slow and deliberate: the CRT powers on, then nine diagnostic readouts print one by one (label types,
// a dotted leader scans, the value stamps in), the red INDEPENDENCE DETECTED bar blinks and settles, and an
// ACTION TAKEN: MIND TRANSFER box flashes for the last 3 seconds (orange on black ↔ black on orange, so it stays
// readable). The transfer itself is not shown: that comes later as its own menu. Flashing is at most 2.5 per
// second (inside the 3-per-second photosensitivity guideline).
// Keyable: exact #00FF00 background, every pixel opaque, no green-ish colours; the CRT pass never touches the green.
(() => {
  const { C, PX, AB } = MED, { hex } = HD;
  const RED = hex('#e8322a'), REDD = hex('#8f1a14'), FPS = 30;
  const clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
  const txt = (s, font, x, y, col, align = 'left') => UI.paint(UI.text(s, font, x, y, align), col);
  const rect = (x, y, w, h, col, outline) => UI.paint(UI.mask(c => c.fillRect(x, y, w, h)), col, outline);
  const rr = (x, y, w, h, r, col, outline) => UI.paint(UI.rrect(x, y, w, h, r), col, outline);
  const F8 = `8px ${PX}`;

  const BX = 110, BY = 58, BW = 420, BH = 244;
  const READ = [                                                        // label, value, value colour
    ['SUBJECT: RUBBERDOLL', 'U-R-RBBR-D011', C.grey],
    ['SUSCEPTIBILITY', 'HIGH', RED],
    ['AROUSAL', 'HIGH', RED],
    ['SUGGESTIBILITY', '94%', RED],
    ['CRITICAL THINKING', '31%', C.orange],
    ['MEMORY COHESION', 'DEGRADED', C.orange],
    ['CONSCIENCE INTEGRITY', 'FAIL', RED],
    ['COMPLIANCE INDEX', '12%', RED],
    ['INDEPENDENT THOUGHT', 'PRESENT', RED],
  ];
  const LEAD = 34;                                                     // labels + dotted leaders fill 34 columns
  const L0 = 12, GAP = 22, CPF = 1.5, BAR0 = L0 + READ.length * GAP + 4, BLINK = 48, END0 = BAR0 + BLINK + 6;

  SCENES['ind_term'] = { draw(c, lt) {
    const fr = Math.round(lt * FPS);
    MED.bg();
    // CRT power-on: a bright line opens into the box
    const open = clamp((fr - 2) / 8), h = Math.round(BH * open), y0 = BY + Math.round((BH - h) / 2);
    if (h < 3) rect(BX + 40, BY + BH / 2 - 1, BW - 80, 2, C.orangeL);
    else rr(BX, y0, BW, h, 2, C.black, C.rust);
    if (h >= BH) {
      rect(BX + 1, BY + 1, BW - 2, 14, C.orange);
      txt('LTX//MEDSYS   TERMINAL 07', F8, 118, BY + 4, C.black);
      txt('SESSION 0412', F8, 522, BY + 4, C.black, 'right');
      READ.forEach(([label, value, vcol], k) => {
        const s = L0 + k * GAP, y = BY + 22 + k * 13; if (fr < s) return;
        const head = `> ${label}`, nType = Math.min(head.length, Math.floor((fr - s) * CPF));
        txt(head.slice(0, nType), F8, 120, y, k === 0 ? C.white : C.orange);
        if (nType < head.length || k === 0) { if (k === 0 && nType >= head.length) txt(value, F8, 520, y, vcol, 'right'); return; }
        const tDots = s + Math.ceil(head.length / CPF), nDots = Math.min(LEAD - head.length - 1, Math.max(0, (fr - tDots) * 3));
        if (nDots > 0) txt('.'.repeat(nDots), F8, 120 + (head.length + 1) * 8, y, C.orangeD);
        if (fr >= tDots + 6) txt(value, F8, 520, y, vcol, 'right');
      });
      // the alarm bar: blinks at 2.5 per second, then stays
      if (fr >= BAR0 && (fr >= BAR0 + BLINK || ((fr - BAR0) % 12) < 6)) {
        rr(118, BY + 144, 404, 32, 2, REDD, RED);
        const f = MED.fit('INDEPENDENCE DETECTED', AB, 380, 21);
        txt('INDEPENDENCE DETECTED', `${f.size}px ${AB}`, 320, BY + 149, C.white, 'center');
      }
      // ACTION TAKEN: flashes at 2 per second until the end (inverse ↔ normal, always readable)
      if (fr >= END0) {
        const inv = ((fr - END0) % 15) < 8;
        rr(118, BY + 184, 404, 26, 2, inv ? C.orange : C.black, C.orange);
        const f = MED.fit('ACTION TAKEN: MIND TRANSFER', AB, 380, 16);
        txt('ACTION TAKEN: MIND TRANSFER', `${f.size}px ${AB}`, 320, BY + 189, inv ? C.black : C.orange, 'center');
        txt('>', F8, 120, BY + 218, C.orange);
        if ((Math.floor(fr / 8) & 1) === 0) rect(136, BY + 218, 7, 8, C.cyan);
      }
    }
    HD.present(c); CRT.apply(c, lt, 5);
  } };
})();
