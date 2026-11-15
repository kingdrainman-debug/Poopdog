// med_common.js · shared pieces of the medicine-label overlays (the "prepare" prompt and the breathing stages),
// so every card in the sequence is built from exactly the same parts.
//   MED.C        colours: prison black/orange + clinical white + azure-cyan; key is exact #00FF00
//   MED.panel()  the white label inside the border (opening-prompt size)
//   MED.brand()  the left column: LTX mark with a pixel ©, orange rule, "d0ll-g4s" sub-label, cyan cross
//   MED.border() the rounded hazard border whose stripes run clockwise, unbroken round the corners.
//                Its centre line is exactly 91 stripes of 12 px; it moves exactly 2 art px per frame at 30 fps
//                (25 stripes per 5 s), so any clip that is a multiple of 5 s loops seamlessly.
//   MED.makeFrame(x0, y0, x1, y1, N) the same panel + border for another box size (N stripes round).
window.MED = (() => {
  const { hex, put } = HD;
  const C = {
    key: [0, 255, 0], black: hex('#050302'), orange: hex('#ff8418'), orangeL: hex('#ffad3d'), orangeD: hex('#e0600f'),
    white: hex('#f3f0e8'), whiteS: hex('#d6cfc0'), grey: hex('#8f897c'), cyan: hex('#4cc9ea'), cyanL: hex('#a8e6f5'), cyanD: hex('#1b8fb5'),
  };
  const PX = '"PressStart2P-400"', AB = '"ArchivoBlack-400"';
  const DIV = 236, RX = 250;                                               // column divider, right-column text x
  const meas = document.createElement('canvas').getContext('2d');
  function fit(str, family, maxW, size) { for (; size > 8; size--) { meas.font = `${size}px ${family}`; if (meas.measureText(str).width <= maxW) break; } return { size, w: meas.measureText(str).width }; }
  const width = (str, font) => { meas.font = font; return meas.measureText(str).width; };
  const plus = (cx, cy, arm, w) => UI.mask(c => { c.fillRect(cx - w / 2, cy - arm, w, arm * 2); c.fillRect(cx - arm, cy - w / 2, arm * 2, w); });
  const COPY = ['..###..', '.#...#.', '#.###.#', '#.#...#', '#.###.#', '.#...#.', '..###..'];   // 7×7 pixel ©
  const copyright = (x, y, col = C.black) => COPY.forEach((row, j) => [...row].forEach((ch, i) => { if (ch === '#') put(x + i, y + j, col); }));

  const bg = () => HD.fill(() => C.key);
  function brand(withCross = true) {
    const ltx = fit('LTX', AB, DIV - 142 - 18, 50), lw = Math.round(ltx.w), lcx = 142 + Math.round(ltx.w / 2);   // leave room for the ©
    const mark = UI.text('LTX', `${ltx.size}px ${AB}`, 142, 116);
    let top = 0; while (top < UI.H && !mark.subarray(top * UI.W, (top + 1) * UI.W).some(v => v)) top++;   // cap top of the mark
    UI.paint(mark, C.black);
    copyright(142 + lw + 3, top - 2);                                      // small © raised at the top-right of the mark
    const rule = 116 + Math.round(ltx.size * 0.86) + 6;
    UI.paint(UI.mask(c => c.fillRect(142, rule, lw, 4)), C.orange);
    UI.paint(UI.text('d0ll-g4s', `8px ${PX}`, lcx, rule + 9, 'center'), C.black);   // sub-label under the mark
    UI.paint(UI.mask(c => c.fillRect(DIV, 114, 2, 132)), C.black);
    if (withCross) UI.paint(plus(lcx, 214, 14, 8), C.cyan, C.black, 1);
    return { lcx, rule };
  }

  // A label frame: the white panel plus the rounded hazard border, for any box size. Rounded corners are sized
  // so the border's centre-line perimeter is exactly N stripes of P = 12 px:
  //   perimeter = 2(W + H − 2·BW) − (8 − 2π)·RM, solved for the centre-line corner radius RM.
  // Every band pixel gets s (distance along the centre line, clockwise from the start of the top edge, through
  // straight sides and quarter-circle corners alike) and d (depth in from the outer edge). Stripes are
  // u = (s + d − offset) / P, so they lean diagonally and flow unbroken round corners.
  function makeFrame(X0, Y0, X1, Y1, N) {
    const BW = 11, W = X1 - X0, H = Y1 - Y0;
    const P = 12, LOOP = 5, SPEED = 25 * P / LOOP;                         // 60 px/s = 2 px per frame; 25 stripes per 5 s
    const RM = (2 * (W + H - 2 * BW) - P * N) / (8 - 2 * Math.PI), RO = RM + BW / 2;   // centre-line and outer radius
    const A = W - 2 * RO, B = H - 2 * RO, Q = Math.PI / 2 * RM;            // straight lengths, quarter-arc length
    const panel = () => UI.paint(UI.rrect(X0 + BW - 1, Y0 + BW - 1, W - 2 * BW + 2, H - 2 * BW + 2, Math.max(1, RO - BW + 1)), C.white);
    function border(lt) {
      const off = Math.round(SPEED * lt);                                 // whole pixels: an even 2 px every frame
      const ax0 = X0 + RO, ax1 = X1 - RO, ay0 = Y0 + RO, ay1 = Y1 - RO; // corner-circle centres
      for (let y = Y0; y < Y1; y++) for (let x = X0; x < X1; x++) {
        const px = x + 0.5, py = y + 0.5;
        const cx = Math.min(Math.max(px, ax0), ax1), cy = Math.min(Math.max(py, ay0), ay1);
        const inCorner = cx !== px && cy !== py;
        const d = inCorner ? RO - Math.hypot(px - cx, py - cy) : Math.min(px - X0, X1 - px, py - Y0, Y1 - py);   // depth from the outer edge
        if (d < 0 || d >= BW) continue;
        if (d < 1 || d >= BW - 1) { put(x, y, C.black); continue; }                          // outer and inner outlines
        let s;
        if (inCorner) {
          const th = Math.atan2(py - cy, px - cx);
          if (px > ax1 && py < ay0) s = A + (th + Math.PI / 2) * RM;                    // top-right
          else if (px > ax1) s = A + Q + B + th * RM;                                       // bottom-right
          else if (py > ay1) s = 2 * A + 2 * Q + B + (th - Math.PI / 2) * RM;               // bottom-left
          else s = 2 * A + 3 * Q + 2 * B + (th + Math.PI) * RM;                             // top-left
        } else if (d === py - Y0) s = px - ax0;                                             // top, heading right
        else if (d === X1 - px) s = A + Q + (py - ay0);                                     // right, heading down
        else if (d === Y1 - py) s = A + 2 * Q + B + (ax1 - px);                             // bottom, heading left
        else s = 2 * A + 3 * Q + B + (ay1 - py);                                            // left, heading up
        const u = (s + d - off) / P, f = u - Math.floor(u);
        put(x, y, f < 0.5 ? C.orange : C.black);
      }
    }
    return { X0, Y0, X1, Y1, BW, RM, RO, panel, border };
  }
  // the opening prompt's frame (91 stripes round)
  const F = makeFrame(118, 94, 522, 266, 91);
  return { C, PX, AB, X0: F.X0, Y0: F.Y0, X1: F.X1, Y1: F.Y1, BW: F.BW, DIV, RX, fit, width, plus, copyright, bg, panel: F.panel, brand, border: F.border, makeFrame };
})();
