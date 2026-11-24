// alert_styles.js · "RESISTANCE DETECTED — EMPLOYING CORRECTIONAL MIND TRANSFER" pop-up: five candidate styles.
// Built from the medicine overlays' parts (MED colours, label frame, LTX brand, CRT pass) so it belongs to the
// same system, plus one alarm-red accent. Keyable: exact #00FF00 background, every pixel opaque, no green-ish
// colours. Stills show each style mid-alarm; animation comes once a style is chosen.
//   al_1 · system alert dialog      al_2 · inverted hazard label     al_3 · emergency broadcast banner
//   al_4 · mind-transfer diagram    al_5 · terminal error
(() => {
  const { C, PX, AB } = MED, { hex } = HD;
  const RED = hex('#e8322a'), REDD = hex('#8f1a14');
  const CARE = '12px "Poppins-600"';
  const txt = (s, font, x, y, col, align = 'left', outline) => UI.paint(UI.text(s, font, x, y, align), col, outline);
  const rect = (x, y, w, h, col, outline) => UI.paint(UI.mask(c => c.fillRect(x, y, w, h)), col, outline);
  const rr = (x, y, w, h, r, col, outline) => UI.paint(UI.rrect(x, y, w, h, r), col, outline);
  const circle = (cx, cy, r) => UI.mask(c => { c.beginPath(); c.arc(cx, cy, r, 0, Math.PI * 2); c.fill(); });
  // hazard triangle with a "!" (size = height in art px)
  function hazard(cx, cy, size, fill = C.orange) {
    const h = size, w = size * 1.15;
    UI.paint(UI.mask(c => { c.beginPath(); c.moveTo(cx, cy - h / 2); c.lineTo(cx + w / 2, cy + h / 2); c.lineTo(cx - w / 2, cy + h / 2); c.closePath(); c.fill(); }), fill, C.black, 1);
    const bw = Math.max(1.5, size / 9);
    UI.paint(UI.mask(c => { c.fillRect(cx - bw / 2, cy - h * 0.18, bw, h * 0.38); c.fillRect(cx - bw / 2, cy + h * 0.28, bw, bw); }), C.black);
  }
  // a head in profile facing right (or left), drawn in a box of about 66×76 art px whose top-left is (x, y)
  function head(x, y, facingLeft, fill, outline = C.black) {
    return UI.paint(UI.mask(c => {
      c.translate(facingLeft ? x + 66 : x, y); if (facingLeft) c.scale(-1, 1);
      c.beginPath(); c.moveTo(10, 76); c.lineTo(12, 60); c.bezierCurveTo(0, 50, -2, 14, 26, 5); c.bezierCurveTo(46, -1, 60, 14, 58, 32);
      c.lineTo(65, 43); c.lineTo(58, 45); c.lineTo(60, 51); c.lineTo(56, 55); c.lineTo(58, 59); c.quadraticCurveTo(54, 66, 44, 64); c.lineTo(42, 76); c.closePath(); c.fill();
    }), fill, outline, 1);
  }
  // a pixel brain: two lobes with folds
  function brain(cx, cy, s, fill, fold) {
    // two hemispheres seen from above, a central fissure and a few gyri on each side
    UI.paint(UI.mask(c => { for (const sx of [-1, 1]) { c.beginPath(); c.ellipse(cx + sx * s * 0.27, cy, s * 0.3, s * 0.45, 0, 0, Math.PI * 2); c.fill(); } }), fill, C.black, 1);
    UI.paint(UI.mask(c => { c.lineWidth = Math.max(1.3, s / 16); c.beginPath(); c.moveTo(cx, cy - s * 0.44); c.lineTo(cx, cy + s * 0.44);
      for (const sx of [-1, 1]) for (const [y0, y1] of [[-0.3, -0.12], [-0.02, 0.16], [0.24, 0.36]]) {
        c.moveTo(cx + sx * s * 0.1, cy + s * y0); c.quadraticCurveTo(cx + sx * s * 0.5, cy + s * (y0 - 0.06), cx + sx * s * 0.3, cy + s * y1); } c.stroke(); }), fold);
  }
  // a striped progress bar (fraction p); stripes lean like the hazard tape
  function bar(x, y, w, h, p, col, back = C.whiteS) {
    rr(x, y, w, h, 2, back, C.black);
    const fw = Math.round((w - 2) * p);
    UI.paint(UI.mask(c => c.fillRect(x + 1, y + 1, fw, h - 2)), (px, py) => ((((px + py) >> 2) & 1) ? col : (col === C.cyan ? C.cyanD : C.orangeD)));
  }
  const crtPresent = (c, lt) => { HD.present(c); CRT.apply(c, lt, 5); };
  const reg = (id, draw) => { SCENES[id] = { draw(c, lt) { MED.bg(); draw(lt); crtPresent(c, lt); } }; };
  const F = MED.makeFrame(117, 92, 523, 268, 92);                       // same corner radius as the other labels

  // 1 · System alert dialog: a retro OS pop-up; the only choices are ACCEPT and ACCEPT
  reg('al_1', () => {
    rect(158, 108, 336, 160, C.black);                                  // hard drop shadow (opaque)
    rr(150, 100, 336, 160, 3, C.white, C.black);
    rect(151, 101, 334, 16, C.orange); rect(151, 117, 334, 1, C.black);
    txt('LTX MEDSYS - ALERT', `8px ${PX}`, 158, 105, C.black);
    for (const k of [0, 1, 2]) rr(445 + k * 12, 104, 9, 9, 1, C.whiteS, C.black);
    hazard(190, 152, 40);
    const f = MED.fit('RESISTANCE DETECTED', AB, 266, 22);
    txt('RESISTANCE DETECTED', `${f.size}px ${AB}`, 218, 128, RED);
    txt('EMPLOYING CORRECTIONAL', `13px ${AB}`, 218, 152, C.black);
    txt('MIND TRANSFER', `13px ${AB}`, 218, 168, C.black);
    txt('Please remain still. This is for your own good.', '11px "Poppins-600"', 168, 194, C.grey);
    txt('TRANSFER', `8px ${PX}`, 168, 214, C.black);
    bar(240, 212, 196, 11, 0.38, C.cyan); txt('38%', `8px ${PX}`, 468, 214, C.black, 'right');
    for (const [k, x] of [[0, 300], [1, 392]]) { rr(x, 234, 84, 16, 3, k ? C.cyan : C.cyanL, C.black); txt('ACCEPT', `8px ${PX}`, x + 42, 238, C.black, 'center'); }
  });

  // 2 · Inverted hazard label: the medication label in alarm mode, black panel, orange type, brain icon
  reg('al_2', lt => {
    F.panel(C.black);
    const lw = Math.round(MED.width('LTX', `14px ${AB}`));
    txt('LTX', `14px ${AB}`, 136, 108, C.orange); MED.copyright(136 + lw + 2, 108, C.orange);
    txt('d0ll-g4s', `8px ${PX}`, 136 + lw + 14, 112, C.orange);
    rr(392, 107, 112, 14, 2, RED, C.black); txt('ALERT 07', `8px ${PX}`, 448, 110, C.black, 'center');
    rect(134, 127, 372, 1, C.orangeD);
    UI.paint(circle(190, 192, 42), C.dark || C.black);
    UI.paint(UI.mask(c => { c.lineWidth = 2; c.beginPath(); c.arc(190, 192, 42, 0, Math.PI * 2); c.stroke(); }), C.orange);
    brain(190, 192, 62, C.orange, C.black);
    for (const k of [0, 1, 2]) UI.paint(UI.mask(c => { c.lineWidth = 2; c.beginPath(); c.moveTo(238 + k * 7, 182); c.lineTo(244 + k * 7, 192); c.lineTo(238 + k * 7, 202); c.stroke(); }), C.cyan);
    const f = MED.fit('RESISTANCE', AB, 230, 34);
    txt('RESISTANCE', `${f.size}px ${AB}`, 266, 134, C.orange);
    txt('DETECTED', `${f.size}px ${AB}`, 266, 134 + f.size, C.orange);
    rect(266, 210, 238, 1, C.orangeD);
    txt('EMPLOYING CORRECTIONAL', `8px ${PX}`, 266, 216, C.cyan);
    txt('MIND TRANSFER', `8px ${PX}`, 266, 228, C.cyan);
    txt('DO NOT RESIST. LTX CARES.', `8px ${PX}`, 266, 246, C.white);
    F.border(lt);
  });

  // 3 · Emergency broadcast banner: a full-width strip across the screen with hazard tape and a ticker
  reg('al_3', () => {
    const y0 = 116, y1 = 244;
    for (const [a, b] of [[y0 - 12, y0], [y1, y1 + 12]]) {
      UI.paint(UI.mask(c => c.fillRect(0, a, UI.W, b - a)), (x, y) => ((((x + y) / 8) | 0) & 1) ? C.orange : C.black);
      rect(0, a - 1, UI.W, 1, C.black); rect(0, b, UI.W, 1, C.black);
    }
    rect(0, y0 + 1, UI.W, y1 - y0 - 1, C.black);
    UI.paint(circle(96, 170, 34), RED, C.orange, 2);
    UI.paint(UI.text('!', `46px ${AB}`, 96, 142, 'center'), C.white);
    const f = MED.fit('RESISTANCE DETECTED', AB, 380, 36);
    txt('RESISTANCE DETECTED', `${f.size}px ${AB}`, 150, 132, C.orange);
    txt('EMPLOYING CORRECTIONAL MIND TRANSFER', `14px ${AB}`, 152, 178, C.white);
    rect(0, 214, UI.W, 1, C.orangeD);
    const tick = 'DO NOT RESIST  //  THIS IS FOR YOUR OWN GOOD  //  LTX CARES  //  REMAIN STILL  //  ';
    txt(tick + tick, `8px ${PX}`, -40, 224, C.orange);
    rr(548, 132, 72, 14, 2, RED, C.black); txt('LIVE', `8px ${PX}`, 584, 135, C.white, 'center');
    UI.paint(circle(560, 139, 2.5), C.white);
  });

  // 4 · Mind-transfer diagram: the prisoner's head beams its mind into an LTX head
  reg('al_4', lt => {
    F.panel();
    hazard(144, 114, 18, RED);
    txt('RESISTANCE DETECTED', `16px ${AB}`, 160, 104, RED);
    txt('U-R-RBBR-D011', `8px ${PX}`, 504, 110, C.grey, 'right');
    rect(134, 126, 372, 1, C.black);
    head(150, 136, false, C.whiteS);
    UI.paint(circle(176, 158, 9), C.grey); txt('?', `12px ${AB}`, 176, 150, C.white, 'center');
    head(426, 136, true, C.orange);
    txt('LTX', `12px ${AB}`, 466, 150, C.black, 'center');
    for (let k = 0; k < 9; k++) {
      const x = 232 + k * 20, s = 3 + (k % 3), y = 168 + Math.round(5 * Math.sin(k * 1.3));
      rr(x - s, y - s, 2 * s, 2 * s, 1, k % 2 ? C.orange : C.cyan, C.black);
    }
    for (const k of [0, 1, 2]) UI.paint(UI.mask(c => { c.lineWidth = 2; c.beginPath(); c.moveTo(300 + k * 14, 186); c.lineTo(308 + k * 14, 194); c.lineTo(300 + k * 14, 202); c.stroke(); }), C.black);
    txt('SUBJECT', `8px ${PX}`, 183, 216, C.black, 'center'); txt('LTX TEMPLATE', `8px ${PX}`, 459, 216, C.black, 'center');
    txt('EMPLOYING CORRECTIONAL MIND TRANSFER', `12px ${AB}`, 320, 230, C.black, 'center');
    bar(150, 248, 300, 9, 0.47, C.orange); txt('47%', `8px ${PX}`, 490, 249, C.black, 'right');
    F.border(lt);
  });

  // 5 · Terminal error: a black console printing the diagnosis, ending on a blinking cursor
  reg('al_5', () => {
    rr(110, 96, 420, 168, 2, C.black, C.rust);
    rect(111, 97, 418, 14, C.orange);
    txt('LTX//MEDSYS   TERMINAL 07', `8px ${PX}`, 118, 100, C.black);
    txt('SESSION 0412', `8px ${PX}`, 522, 100, C.black, 'right');
    const L = (s, y, col = C.orange) => txt(s, `8px ${PX}`, 120, y, col);
    L('> SUBJECT U-R-RBBR-D011', 120);
    L('> CONSCIENCE INTEGRITY ..........', 134); txt('FAIL', `8px ${PX}`, 520, 134, RED, 'right');
    L('> COMPLIANCE INDEX ..............', 148); txt('12%', `8px ${PX}`, 520, 148, RED, 'right');
    rr(118, 162, 404, 30, 2, REDD, RED);
    txt('RESISTANCE DETECTED', `20px ${AB}`, 320, 166, C.white, 'center');
    L('> EMPLOYING CORRECTIONAL MIND TRANSFER', 202, C.cyan);
    L('>', 216); bar(136, 215, 300, 9, 0.54, C.orange, C.dark || C.black); txt('54%', `8px ${PX}`, 470, 216, C.orange);
    L('> DO NOT RESIST. LTX CARES.', 232, C.white);
    rect(120 + 27 * 8, 232, 7, 8, C.cyan);                                // cursor
    L('>', 246);
  });
})();
