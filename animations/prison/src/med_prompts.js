// med_prompts.js · the medicine-prompt overlay, five candidate styles (stills of the opening prompt).
// Keyable: drawn on exact #00FF00; every pixel is opaque; no colour near green.
// Colours: the prison's black, rust and orange, plus a warm clinical white and a sterile azure-cyan
// (roughly orange's complement, and far from the key green). No red-cross emblem: crosses are cyan/orange.
(() => {
  const { hex, bayer, clamp, put } = HD;
  const C = {
    key: [0, 255, 0], black: hex('#050302'), dark: hex('#1a0b03'), rust: hex('#3d1704'),
    orange: hex('#ff8418'), orangeD: hex('#e0600f'), orangeL: hex('#ffad3d'),
    white: hex('#f3f0e8'), whiteS: hex('#d6cfc0'), grey: hex('#8f897c'),
    cyan: hex('#4cc9ea'), cyanL: hex('#a8e6f5'), cyanD: hex('#1b8fb5'), navy: hex('#0d3d52'),
  };
  const PX = '"PressStart2P-400"';
  const bg = () => HD.fill(() => C.key);
  const plus = (cx, cy, arm, w) => UI.mask(c => { c.fillRect(cx - w / 2, cy - arm, w, arm * 2); c.fillRect(cx - arm, cy - w / 2, arm * 2, w); });
  const circle = (cx, cy, r) => UI.mask(c => { c.beginPath(); c.arc(cx, cy, r, 0, Math.PI * 2); c.fill(); });
  const shade = (top, mid, low, y0, y1) => (x, y) => { const q = (y - y0) / (y1 - y0); return q < 0.18 && bayer(x, y) > q / 0.18 * 0.6 ? top : q > 0.82 && bayer(x, y) < (q - 0.82) / 0.18 ? low : mid; };
  const present = c => HD.present(c);
  const reg = (id, draw) => { SCENES[id] = { draw(c, lt) { bg(); draw(lt); present(c); } }; };

  // 1 · Clinical card: a sterile white notice card with an orange header bar and a cyan medical cross
  reg('med_s1', () => {
    const card = UI.rrect(130, 108, 380, 144, 8);
    UI.paint(card, shade(C.white, C.white, C.whiteS, 108, 252), C.black, 1);
    UI.paint(UI.and(card, UI.mask(c => c.fillRect(130, 108, 380, 20))), C.orange);
    UI.paint(UI.mask(c => c.fillRect(130, 128, 380, 1)), C.black);
    UI.paint(UI.text('MEDICAL NOTICE', `8px ${PX}`, 142, 114), C.black);
    UI.paint(plus(492, 118, 5, 3), C.black);
    UI.paint(circle(176, 186, 26), shade(C.cyanL, C.cyan, C.cyanD, 160, 212), C.black, 1);
    UI.paint(plus(176, 186, 15, 9), C.white, C.cyanD, 1);
    UI.paint(UI.text('PREPARE FOR', 'bold 26px "RobotoCondensed"', 218, 142), C.black);
    UI.paint(UI.text('YOUR MEDICINE', 'bold 26px "RobotoCondensed"', 218, 170), C.black);
    UI.paint(UI.text('YOUR DOSE WILL BEGIN SHORTLY', `8px ${PX}`, 218, 206), C.cyanD);
    UI.paint(UI.rrect(218, 224, 274, 9, 2), C.whiteS, C.black, 1);
    UI.paint(UI.rrect(219, 225, 104, 7, 1), C.cyan);
  });

  // 2 · Dispensary LED board: cyan dot-matrix lettering on a black sign, orange LED cross
  reg('med_s2', () => {
    UI.paint(UI.rrect(98, 100, 444, 164, 6), C.black, C.rust, 2);
    UI.paint(UI.mask(c => { c.strokeRect(105.5, 107.5, 429, 149); }), C.orangeD);
    const lines = ['PREPARE FOR', 'YOUR MEDICINE'], P = 3;
    const src = UI.mask(c => { c.font = `8px ${PX}`; c.textBaseline = 'top'; lines.forEach((l, k) => c.fillText(l, 0, k * 11)); });
    const gx0 = 168, gy0 = 128, cols = 104, rows = 19;
    const plusCells = new Set(); for (let k = -3; k <= 3; k++) for (let w = -1; w <= 1; w++) { plusCells.add(`${3 + k},${3 + w}`); plusCells.add(`${3 + w},${3 + k}`); }
    for (let j = 0; j < rows; j++) for (let i = -13; i < cols; i++) {
      const x = gx0 + i * P, y = gy0 + j * P;
      const lit = i >= 0 && src[j * UI.W + i];
      const cross = i < -3 && plusCells.has(`${i + 13},${j - 6}`);
      if (lit || cross) { const a = cross ? C.orange : C.cyan, b = cross ? C.orangeL : C.cyanL; put(x, y, b); put(x + 1, y, a); put(x, y + 1, a); put(x + 1, y + 1, a); }
      else put(x, y, C.navy);
    }
    UI.paint(UI.text('YOUR DOSE WILL BEGIN SHORTLY', `8px ${PX}`, 320, 208, 'center'), C.orange);
    UI.paint(circle(166, 211, 2.5), C.cyan);
    UI.paint(circle(474, 211, 2.5), C.cyan);
    UI.paint(UI.text('DISPENSARY 07', `8px ${PX}`, 320, 234, 'center'), C.rust);
  });

  // 3 · Medication label: a pharmacy / gas-canister label inside an orange-and-black hazard border
  reg('med_s3', () => {
    const outer = UI.rrect(118, 94, 404, 172, 4);
    UI.paint(outer, (x, y) => (((x + y) >> 3) & 1) ? C.orange : C.black, C.black, 1);
    UI.paint(UI.rrect(129, 105, 382, 150, 2), C.white, C.black, 1);
    UI.paint(UI.text('Rx', '50px "ArchivoBlack-400"', 142, 112), C.black);
    UI.paint(UI.mask(c => c.fillRect(142, 176, 58, 4)), C.orange);
    UI.paint(UI.mask(c => c.fillRect(214, 114, 2, 132)), C.black);
    UI.paint(UI.text('PREPARE FOR', '19px "ArchivoBlack-400"', 228, 112), C.black);
    UI.paint(UI.text('YOUR MEDICINE', '19px "ArchivoBlack-400"', 228, 136), C.black);
    UI.paint(UI.mask(c => c.fillRect(228, 166, 270, 1)), C.black);
    UI.paint(UI.text('INHALATION AEROSOL', `8px ${PX}`, 228, 174), C.black);
    UI.paint(UI.text('1 DOSE - AS DIRECTED', `8px ${PX}`, 228, 186), C.grey);
    UI.paint(UI.rrect(228, 204, 126, 14, 7), C.cyan, C.black, 1);
    UI.paint(UI.text('BEGINS SHORTLY', `8px ${PX}`, 291, 207, 'center'), C.black);
    UI.paint(UI.mask(c => { for (let i = 0, x = 392; x < 496; i++) { const w = 1 + ((i * 7919) % 3); if (i % 2 === 0) c.fillRect(x, 202, w, 30); x += w + (((i * 104729) >> 2) % 2) + 1; } }), C.black);
    UI.paint(UI.text('07-3301-A', `8px ${PX}`, 444, 236, 'center'), C.black);
    UI.paint(plus(171, 214, 14, 8), C.cyan, C.black, 1);
  });

  // 4 · Respirator HUD: no panel, floating cyan brackets, an orange respirator-mask icon, outlined type
  reg('med_s4', () => {
    const br = UI.mask(c => {
      const L = 22, T = 3, x0 = 116, y0 = 106, x1 = 524, y1 = 254;
      [[x0, y0, 1, 1], [x1, y0, -1, 1], [x0, y1, 1, -1], [x1, y1, -1, -1]].forEach(([x, y, sx, sy]) => {
        c.fillRect(sx > 0 ? x : x - L, sy > 0 ? y : y - T, L, T); c.fillRect(sx > 0 ? x : x - T, sy > 0 ? y : y - L, T, L);
      });
    });
    UI.paint(br, C.cyan, C.black, 1);
    // respirator, seen from the front: head straps, two angled filter cartridges, shield facepiece, valve
    UI.paint(UI.mask(c => { c.lineWidth = 3; c.beginPath(); c.moveTo(152, 162); c.lineTo(122, 138); c.moveTo(204, 162); c.lineTo(234, 138);
      c.moveTo(150, 186); c.lineTo(120, 196); c.moveTo(206, 186); c.lineTo(236, 196); c.stroke(); }), C.cyanD, C.black, 1);
    for (const [x, rot] of [[150, 0.5], [206, -0.5]]) {
      const cart = UI.mask(c => { c.translate(x, 196); c.rotate(rot); c.beginPath(); c.roundRect(-11, -14, 22, 30, 6); c.fill(); });
      UI.paint(cart, shade(C.whiteS, C.grey, C.dark, 182, 214), C.black, 1);
      UI.paint(UI.and(cart, UI.mask(c => { c.translate(x, 196); c.rotate(rot); for (let k = -6; k <= 8; k += 4) c.fillRect(-7, k, 14, 1.5); })), C.dark);
    }
    const face = UI.mask(c => { c.beginPath(); c.moveTo(178, 134); c.bezierCurveTo(196, 140, 210, 156, 206, 184); c.quadraticCurveTo(200, 210, 178, 214);
      c.quadraticCurveTo(156, 210, 150, 184); c.bezierCurveTo(146, 156, 160, 140, 178, 134); c.closePath(); c.fill(); });
    UI.paint(face, shade(C.orangeL, C.orange, C.orangeD, 134, 214), C.black, 1);
    UI.paint(UI.mask(c => { c.beginPath(); c.moveTo(178, 142); c.quadraticCurveTo(190, 152, 186, 166); c.lineTo(170, 166); c.quadraticCurveTo(166, 152, 178, 142); c.fill(); }), C.orangeL);
    UI.paint(circle(178, 190, 11), shade(C.whiteS, C.grey, C.dark, 179, 201), C.black, 1);
    UI.paint(UI.mask(c => { for (let k = -6; k <= 6; k += 4) c.fillRect(171, 190 + k - 0.75, 14, 1.5); }), C.dark);
    UI.paint(UI.text('PREPARE FOR', '26px "Exo2-800i"', 240, 132), C.white, C.black, 2);
    UI.paint(UI.text('YOUR MEDICINE', '26px "Exo2-800i"', 240, 162), C.white, C.black, 2);
    UI.paint(UI.mask(c => c.fillRect(240, 200, 262, 2)), C.cyan, C.black, 1);
    UI.paint(UI.text('AEROSOL DELIVERY - STAND BY', `8px ${PX}`, 240, 210), C.cyan, C.black, 1);
    UI.paint(circle(506, 214, 3), C.orange, C.black, 1);
    UI.paint(UI.text('MED/SYS 07', `8px ${PX}`, 124, 114), C.cyan, C.black, 1);
  });

  // 5 · Capsule: the prompt printed on a giant half-orange, half-white pill, with a cyan cross badge
  reg('med_s5', () => {
    const pill = UI.rrect(118, 128, 404, 86, 43);
    const left = UI.and(pill, UI.mask(c => c.fillRect(0, 0, 320, UI.H)));
    const right = UI.and(pill, UI.mask(c => c.fillRect(320, 0, UI.W, UI.H)));
    UI.paint(pill, C.black, C.black, 2);
    UI.paint(left, shade(C.orangeL, C.orange, C.orangeD, 128, 214));
    UI.paint(right, shade(C.white, C.white, C.whiteS, 128, 214));
    UI.paint(UI.and(pill, UI.mask(c => c.fillRect(319, 0, 2, UI.H))), C.black);
    UI.paint(UI.and(pill, UI.mask(c => { c.beginPath(); c.roundRect(150, 136, 340, 6, 3); c.fill(); })), (x) => x < 320 ? C.orangeL : [255, 252, 245]);
    UI.paint(UI.text('PREPARE FOR YOUR MEDICINE', '21px "Poppins-800"', 320, 158, 'center'), C.black);
    UI.paint(circle(138, 132, 21), shade(C.cyanL, C.cyan, C.cyanD, 111, 153), C.black, 1);
    UI.paint(plus(138, 132, 12, 7), C.white, C.cyanD, 1);
    UI.paint(UI.text('YOUR DOSE WILL BEGIN SHORTLY', `8px ${PX}`, 320, 230, 'center'), C.white, C.black, 1);
  });
})();
