// med_label.js · the chosen medicine-prompt style (option 3, "medication label"), animated.
// A pharmacy / gas-canister label with an "LTX" mark, small print and a barcode, inside a hazard border
// whose orange-and-black stripes run clockwise around the box like a marquee. Black corner blocks with
// orange rivets sit where the stripes turn. Keyable: exact #00FF00 background, every pixel opaque.
// Loop: the border is exactly 96 stripes round (period 12 px on a 1152 px perimeter) and moves exactly 2 art
// pixels per frame at 30 fps (25 stripes per 5 s), so the motion is even and the 5 s clip loops seamlessly
// (any multiple of 5 s does too).
(() => {
  const { hex, put } = HD;
  const C = {
    key: [0, 255, 0], black: hex('#050302'), orange: hex('#ff8418'), orangeL: hex('#ffad3d'), orangeD: hex('#e0600f'),
    white: hex('#f3f0e8'), grey: hex('#8f897c'), cyan: hex('#4cc9ea'), cyanD: hex('#1b8fb5'),
  };
  const PX = '"PressStart2P-400"', AB = '"ArchivoBlack-400"';
  // label geometry (640×360 art grid)
  const X0 = 118, Y0 = 94, X1 = 522, Y1 = 266, BW = 11, W = X1 - X0, H = Y1 - Y0;
  const PER = 2 * (W + H), P = PER / 96, LOOP = 5, SPEED = 25 * P / LOOP;   // 60 px/s = 2 px per frame
  const DIV = 236, RX = 250;                                    // column divider, right-column text x
  const meas = document.createElement('canvas').getContext('2d');
  function fit(str, family, maxW, size) { for (; size > 8; size--) { meas.font = `${size}px ${family}`; if (meas.measureText(str).width <= maxW) break; } return { size, w: meas.measureText(str).width }; }
  const plus = (cx, cy, arm, w) => UI.mask(c => { c.fillRect(cx - w / 2, cy - arm, w, arm * 2); c.fillRect(cx - arm, cy - w / 2, arm * 2, w); });

  function drawStill() {                                        // everything except the moving border
    UI.paint(UI.mask(c => c.fillRect(X0 + BW, Y0 + BW, W - 2 * BW, H - 2 * BW)), C.white, C.black, 1);
    const ltx = fit('LTX', AB, DIV - 142 - 8, 50);
    UI.paint(UI.text('LTX', `${ltx.size}px ${AB}`, 142, 116), C.black);
    UI.paint(UI.mask(c => c.fillRect(142, 116 + Math.round(ltx.size * 0.86) + 6, Math.round(ltx.w), 4)), C.orange);
    UI.paint(UI.mask(c => c.fillRect(DIV, 114, 2, 132)), C.black);
    UI.paint(plus(142 + Math.round(ltx.w / 2), 214, 14, 8), C.cyan, C.black, 1);
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

  function border(lt) {
    const off = Math.round(SPEED * lt);                                   // whole pixels: an even 2 px every frame
    for (let y = Y0; y < Y1; y++) for (let x = X0; x < X1; x++) {
      const dl = x - X0, dr = X1 - 1 - x, dt = y - Y0, db = Y1 - 1 - y, d = Math.min(dl, dr, dt, db);
      if (d >= BW) continue;
      if (d === 0) { put(x, y, C.black); continue; }                                    // outer outline
      if ((dl < BW || dr < BW) && (dt < BW || db < BW)) {                                  // corner block with a rivet
        const cx = dl < BW ? X0 + BW / 2 : X1 - BW / 2, cy = dt < BW ? Y0 + BW / 2 : Y1 - BW / 2;
        const rv = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
        put(x, y, rv < 2 ? (x + 0.5 < cx && y + 0.5 < cy ? C.orangeL : C.orange) : rv < 3 ? C.orangeD : C.black); continue;
      }
      let s;                                                                              // distance along the perimeter, clockwise
      if (d === dt) s = dl; else if (d === dr) s = W + dt; else if (d === db) s = W + H + dr; else s = 2 * W + H + db;
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
