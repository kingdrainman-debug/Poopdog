// Direction C · "Shadow play": side-lit and asymmetric. A single candle at lower left throws the swinging
// watch's shadow, magnified ×2.2, across a plaster wall. The shadow swings wider than the watch and is the
// real hypnotist; the watch itself is small, sharp and gold in the foreground-left.
SCENES['dirC_shadow'] = (() => {
  const { ramp, hex, pick, bayer, clamp, TAU, vnoise, hash } = SW;
  const PAL = {
    wall: ramp(['#1c0f08', '#3a2010', '#5e3618', '#8a5424', '#b87a36', '#dca452', '#f4c87a']),
    shelf: ramp(['#120a05', '#2a170b', '#4a2a12', '#74441c', '#a86a2c']),
    wax: ramp(['#7a5a3a', '#c8a678', '#f2dcb0', '#fff4dc']),
    flame: ramp(['#c8401a', '#f08a1c', '#ffd040', '#fff8d0']),
    gold: ramp(['#3e2408', '#7a4c10', '#c08420', '#f0be44', '#fff3b0']),
    face: ramp(['#a88a64', '#d8c296', '#f6e8cc']),
    ink: hex('#1c120c'), red: hex('#c41e28'), glass: hex('#fff6dc'), outline: hex('#0c0603'),
  };
  const CAN = [-138, 32], SHELF = 60, K = 1.6, PIV = [-38, -250], LEN = 215, AMP = 0.16, PER = 2.4, R = 28;
  const theta = t => AMP * Math.sin(TAU * t / PER);
  return {
    draw(c, lt) {
      SW.setCam(0, 0, 1);
      const ft = SW.step(lt, 10), fl = 0.9 + 0.1 * Math.sin(ft * 11.7) * Math.cos(ft * 5.3);   // candle flicker, stepped
      const fx = CAN[0] + 0.8 * Math.sin(ft * 9.1), fy = CAN[1] - 4;
      const sec = Math.floor(lt), light = clamp(0, 0, 0);
      const w = SW.hang({ R, sub: lt / 60, glint: 0.7, pal: PAL }, PIV, LEN, theta(lt), -0.12 * AMP * Math.sin(TAU * lt / PER - 0.9));
      const ldx = fx - w.x, ldy = fy - w.y, ll = Math.hypot(ldx, ldy); w.light = [ldx / ll, ldy / ll];
      w.sec = sec + Math.min(1, (lt - sec) / 0.1);
      // wall + shelf, with the projected shadow (3 samples across the flame for a dithered penumbra)
      SW.fill((X, Y, x, y) => {
        if (Y > SHELF) {
          if (Y < SHELF + 4) return pick(PAL.shelf, 0.35 + 0.6 * clamp(1 - Math.abs(X - fx) / 160), x, y);
          return pick(PAL.shelf, 0.05 + 0.3 * clamp(1 - Math.hypot(X - fx, Y - fy) / 140) - (Y - SHELF - 4) * 0.01, x, y);
        }
        const d = Math.hypot(X - fx, Y - fy), plaster = vnoise(X / 9, Y / 9, 3) * 0.6 + vnoise(X / 3, Y / 3, 4) * 0.4;
        let l = clamp(0.06 + 1.15 / (1 + (d / 105) ** 2) * fl) * (0.86 + 0.24 * plaster);
                let sh = 0;
        for (const o of [-2.2, 0, 2.2]) {
          const lx = fx + o, px_ = lx + (X - lx) / K, py_ = fy + (Y - fy) / K;
          if (SW.watchInside(px_, py_, w, 1 / K) || SW.chainAt(px_, py_, PIV, w.bow, 3.0)) sh++;
        }
        if (sh) l *= 1 - 0.78 * (sh / 3);
        return pick(PAL.wall, l, x, y);
      });
      // candle in a brass holder
      SW.each(CAN[0] - 7, CAN[1] - 2, CAN[0] + 7, SHELF, (X, Y, x, y) => {
        if (Y > SHELF - 7 && Math.abs(X - CAN[0]) < 7 - (Y - SHELF + 7) * 0.3) SW.lput(x, y, pick(PAL.gold, 0.3 + 0.5 * clamp((CAN[0] - X) / 7 + 0.5), x, y));
        else if (Y >= CAN[1] && Y <= SHELF - 7 && Math.abs(X - CAN[0]) < 3.6) SW.lput(x, y, pick(PAL.wax, 0.5 + 0.5 * clamp((CAN[0] - X) / 4 + 0.3) - (Y - CAN[1]) * 0.01, x, y));
      });
      SW.stamp(PAL.outline);
      // flame: stepped shapes, glow ring
      const fh = 7 + 1.5 * Math.sin(ft * 13.3);
      SW.each(fx - 8, fy - 12, fx + 8, fy + 6, (X, Y, x, y) => {
        const d = Math.hypot(X - fx, Y - fy + 1);
        const yy = (fy + 2 - Y) / fh, hw = 2.4 * Math.sin(Math.PI * clamp(yy)) * (1 - 0.35 * yy);
        if (yy >= 0 && yy <= 1 && Math.abs(X - fx - 0.6 * yy * Math.sin(ft * 7)) < hw) SW.put(x, y, pick(PAL.flame, 1 - yy * 0.8 + (Math.abs(X - fx) < 0.8 ? 0.2 : 0), x, y));
        else if (d < 7 && bayer(x, y) < 0.35 * (1 - d / 7)) SW.put(x, y, PAL.flame[2]);
      });
      // the watch
      SW.drawChain(PIV, w.bow, 3.0, PAL.gold, w.light); SW.stamp(PAL.outline);
      SW.drawWatch(w, 1); SW.stamp(PAL.outline);
      SW.present(c);
    },
  };
})();
