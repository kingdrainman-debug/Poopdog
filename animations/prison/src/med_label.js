// med_label.js · the chosen medicine-prompt style (option 3, "medication label"), animated.
// A pharmacy / gas-canister label with an "LTX©" mark over a "d0ll-g4s" sub-label, small print and a barcode, inside a rounded hazard
// border whose orange-and-black stripes run clockwise around the box like a marquee, flowing unbroken round
// the corners. Keyable: exact #00FF00 background, every pixel opaque.
// Loop: the border's centre line is exactly 91 stripes round (period 12 px); it moves exactly 2 art pixels per
// frame at 30 fps (25 stripes per 5 s), so the motion is even and the 5 s clip loops seamlessly (any multiple
// of 5 s does too).
(() => {
  const { hex, put } = HD;
  const C = {
    key: [0, 255, 0], black: hex('#050302'), orange: hex('#ff8418'), orangeL: hex('#ffad3d'), orangeD: hex('#e0600f'),
    white: hex('#f3f0e8'), grey: hex('#8f897c'), cyan: hex('#4cc9ea'), cyanD: hex('#1b8fb5'),
  };
  const PX = '"PressStart2P-400"', AB = '"ArchivoBlack-400"';
  // label geometry (640×360 art grid)
  const X0 = 118, Y0 = 94, X1 = 522, Y1 = 266, BW = 11, W = X1 - X0, H = Y1 - Y0;
  // Rounded corners sized so the centre-line perimeter is exactly N stripes of P = 12 px:
  // perimeter = 2(W + H − 2·BW) − (8 − 2π)·RM, solved for the centre-line corner radius RM.
  const P = 12, N = 91, LOOP = 5, SPEED = 25 * P / LOOP;                   // 60 px/s = 2 px per frame; 25 stripes per 5 s
  const RM = (2 * (W + H - 2 * BW) - P * N) / (8 - 2 * Math.PI), RO = RM + BW / 2;   // centre-line and outer radius
  const A = W - 2 * RO, B = H - 2 * RO, Q = Math.PI / 2 * RM;              // straight lengths, quarter-arc length
  const DIV = 236, RX = 250;                                    // column divider, right-column text x
  const meas = document.createElement('canvas').getContext('2d');
  function fit(str, family, maxW, size) { for (; size > 8; size--) { meas.font = `${size}px ${family}`; if (meas.measureText(str).width <= maxW) break; } return { size, w: meas.measureText(str).width }; }
  const plus = (cx, cy, arm, w) => UI.mask(c => { c.fillRect(cx - w / 2, cy - arm, w, arm * 2); c.fillRect(cx - arm, cy - w / 2, arm * 2, w); });

  function drawStill() {                                        // everything except the moving border
    UI.paint(UI.rrect(X0 + BW - 1, Y0 + BW - 1, W - 2 * BW + 2, H - 2 * BW + 2, Math.max(1, RO - BW + 1)), C.white);
    const ltx = fit('LTX', AB, DIV - 142 - 18, 50), lw = Math.round(ltx.w), lcx = 142 + Math.round(ltx.w / 2);   // leave room for the ©
    const mark = UI.text('LTX', `${ltx.size}px ${AB}`, 142, 116);
    let top = 0; while (top < UI.H && !mark.subarray(top * UI.W, (top + 1) * UI.W).some(v => v)) top++;   // cap top of the mark
    UI.paint(mark, C.black);
    // small © raised at the top-right of the mark, drawn as a 7×7 pixel glyph
    const COPY = ['..###..', '.#...#.', '#.###.#', '#.#...#', '#.###.#', '.#...#.', '..###..'];
    COPY.forEach((row, j) => [...row].forEach((ch, i) => { if (ch === '#') put(142 + lw + 3 + i, top - 2 + j, C.black); }));
    const rule = 116 + Math.round(ltx.size * 0.86) + 6;
    UI.paint(UI.mask(c => c.fillRect(142, rule, lw, 4)), C.orange);
    UI.paint(UI.text('d0ll-g4s', `8px ${PX}`, lcx, rule + 9, 'center'), C.black);   // sub-label under the mark
    UI.paint(UI.mask(c => c.fillRect(DIV, 114, 2, 132)), C.black);
    UI.paint(plus(lcx, 214, 14, 8), C.cyan, C.black, 1);
    UI.paint(UI.text('PREPARE FOR', `19px ${AB}`, RX, 112), C.black);
    UI.paint(UI.text('YOUR MEDICINE', `19px ${AB}`, RX, 136), C.black);
    UI.paint(UI.mask(c => c.fillRect(RX, 166, 498 - RX, 1)), C.black);
    UI.paint(UI.text('INHALATION AEROSOL', `8px ${PX}`, RX, 174), C.black);
    UI.paint(UI.text('1 DOSE - AS DIRECTED', `8px ${PX}`, RX, 186), C.grey);
    UI.paint(UI.rrect(RX, 204, 126, 14, 7), C.cyan, C.black, 1);
    UI.paint(UI.text('BEGINS SHORTLY', `8px ${PX}`, RX + 63, 207, 'center'), C.black);
    UI.paint(UI.mask(c => { for (let i = 0, x = 398; x < 497; i++) { const w = 1 + ((i * 7919) % 3); if (i % 2 === 0) c.fillRect(x, 202, w, 30); x += w + (((i * 104729) >> 2) % 2) + 1; } }), C.black);
    UI.paint(UI.text('U-R-RBBR-D011', `8px ${PX}`, 447, 236, 'center'), C.black);
  }

  // A rounded-corner band. Every band pixel gets s (distance along the band's centre line, clockwise from the
  // start of the top edge, through straight sides and quarter-circle corners alike) and d (depth in from the
  // outer edge). Stripes are u = (s + d − offset) / P, so they lean diagonally and flow unbroken round corners.
  function border(lt) {
    const off = Math.round(SPEED * lt);                                   // whole pixels: an even 2 px every frame
    const ax0 = X0 + RO, ax1 = X1 - RO, ay0 = Y0 + RO, ay1 = Y1 - RO;   // corner-circle centres
    for (let y = Y0; y < Y1; y++) for (let x = X0; x < X1; x++) {
      const px = x + 0.5, py = y + 0.5;
      const cx = Math.min(Math.max(px, ax0), ax1), cy = Math.min(Math.max(py, ay0), ay1);
      const inCorner = cx !== px && cy !== py;
      // depth from the outer edge of the rounded rectangle
      const d = inCorner ? RO - Math.hypot(px - cx, py - cy) : Math.min(px - X0, X1 - px, py - Y0, Y1 - py);
      if (d < 0 || d >= BW) continue;
      if (d < 1 || d >= BW - 1) { put(x, y, C.black); continue; }                            // outer and inner outlines
      let s;
      if (inCorner) {
        const th = Math.atan2(py - cy, px - cx);
        if (px > ax1 && py < ay0) s = A + (th + Math.PI / 2) * RM;                      // top-right
        else if (px > ax1) s = A + Q + B + th * RM;                                         // bottom-right
        else if (py > ay1) s = 2 * A + 2 * Q + B + (th - Math.PI / 2) * RM;                 // bottom-left
        else s = 2 * A + 3 * Q + 2 * B + (th + Math.PI) * RM;                               // top-left
      } else if (d === py - Y0) s = px - ax0;                                               // top, heading right
      else if (d === X1 - px) s = A + Q + (py - ay0);                                       // right, heading down
      else if (d === Y1 - py) s = A + 2 * Q + B + (ax1 - px);                               // bottom, heading left
      else s = 2 * A + 3 * Q + B + (ay1 - py);                                              // left, heading up
      const u = (s + d - off) / P, f = u - Math.floor(u);
      put(x, y, f < 0.5 ? C.orange : C.black);
    }
  }

  SCENES['med_label'] = {
    draw(c, lt) {
      HD.fill(() => C.key);
      drawStill();
      border(lt);
      HD.present(c);
    },
  };
})();
