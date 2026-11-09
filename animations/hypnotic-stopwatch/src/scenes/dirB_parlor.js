// Direction B · "Parlour POV": we are the one being hypnotised. A hand in a dark sleeve reaches in from the
// top-left and dangles the watch close to our face; behind it a lamp-lit parlour wall (damask wallpaper,
// wainscot, a small painting, a fringed floor lamp). The lamp is the only light: it rims the watch from the right.
SCENES['dirB_parlor'] = (() => {
  const { ramp, hex, pick, bayer, clamp, TAU, hash } = SW;
  const PAL = {
    paper: ramp(['#0a1512', '#12241e', '#1b342b', '#26493b', '#3a6650', '#5e8a66']),
    damask: ramp(['#0e1c17', '#183028', '#244638', '#33604a', '#4f7c5c', '#7aa47a']),
    wood: ramp(['#140b06', '#2a170c', '#472a16', '#6c4222', '#9a6232', '#c88a48']),
    lamp: ramp(['#5a2e0c', '#a45a16', '#e09030', '#f8c460', '#fff0b8']),
    gold: ramp(['#3e2408', '#7a4c10', '#c08420', '#f0be44', '#fff3b0']),
    face: ramp(['#a89070', '#d8c6a0', '#f6ecd6']),
    sleeve: ramp(['#0c0c0e', '#18181c', '#26262c', '#3a3a42']),
    cuff: ramp(['#6e6a5e', '#aaa494', '#e6e0d0']),
    skin: ramp(['#5a2e1c', '#94522e', '#c8804e', '#eab07a']),
    paint: ramp(['#3a1a10', '#8a3a1c', '#d8742c', '#f4b05a', '#fbe0a0']),
    ink: hex('#1c120c'), red: hex('#c41e28'), glass: hex('#fffbe8'), outline: hex('#070504'),
  };
  const LAMP = [112, -22], PIV = [-62, -56], LEN = 34, AMP = 0.36, PER = 2.4, HY = 23;
  const theta = t => AMP * Math.sin(TAU * t / PER);
  const illum = (X, Y, f) => clamp(0.08 + 1.05 / (1 + ((X - LAMP[0]) ** 2 + (Y - LAMP[1]) ** 2) / (78 * 78)) * f);
  const inRect = (X, Y, x0, y0, x1, y1) => X >= x0 && X <= x1 && Y >= y0 && Y <= y1;
  function room(t) {
    const f = 0.94 + 0.06 * Math.sin(t * 7.3) * Math.sin(t * 3.1);       // lamp flicker
    SW.fill((X, Y, x, y) => {
      const l = illum(X, Y, f);
      // painting with gilt frame
      if (inRect(X, Y, 2, -66, 70, -14)) {
        if (!inRect(X, Y, 6, -62, 66, -18)) return pick(PAL.gold, 0.15 + 0.6 * l, x, y);
        const hz = -40 + 6 * Math.sin(X * 0.09) + 3 * Math.sin(X * 0.23 + 1);
        if (Y > hz) return pick(PAL.paint, 0.05 + 0.35 * l * (1 - (Y - hz) / 30), x, y);
        return pick(PAL.paint, 0.35 + 0.6 * l * (1 - (Y + 62) / 40) + 0.2, x, y);
      }
      if (Y > 40) {                                                   // wainscot
        if (Y < 44) return pick(PAL.wood, 0.25 + 0.75 * l, x, y);
        const gx = ((X + 400) % 56 + 56) % 56; if (gx < 1.2) return PAL.wood[0];
        if (gx < 2.2) return pick(PAL.wood, 0.2 + 0.6 * l, x, y);
        return pick(PAL.wood, 0.05 + 0.6 * l, x, y);
      }
      // damask: diamond lattice with a small fleur in every cell
      const u = ((X + Y) / 2 % 16 + 16) % 16, v = ((X - Y) / 2 % 16 + 16) % 16, cu = Math.abs(u - 8), cv = Math.abs(v - 8);
      const motif = (cu + cv < 3.2) || (Math.min(u, 16 - u) < 0.6) || (Math.min(v, 16 - v) < 0.6);
      return pick(motif ? PAL.damask : PAL.paper, 0.05 + 0.85 * l, x, y);
    });
    // floor lamp: pole, base, pleated shade lit from inside, fringe
    SW.each(LAMP[0] - 2, LAMP[1], LAMP[0] + 2, 95, (X, Y, x, y) => { if (Math.abs(X - LAMP[0]) < 1.2) SW.put(x, y, pick(PAL.gold, 0.4, x, y)); });
    SW.each(LAMP[0] - 22, LAMP[1] - 20, LAMP[0] + 22, LAMP[1] + 16, (X, Y, x, y) => {
      const yy = Y - (LAMP[1] - 18), hw = 11 + yy * 0.42;
      if (yy < 0 || yy > 26 || Math.abs(X - LAMP[0]) > hw) return;
      const pleat = Math.abs(((X - LAMP[0]) / hw * 4 + 8) % 1 - 0.5) < 0.12;
      SW.lput(x, y, pick(PAL.lamp, 0.55 + 0.45 * (1 - Math.abs(X - LAMP[0]) / hw) - (pleat ? 0.25 : 0), x, y));
    });
    SW.stamp(PAL.outline);
    for (let i = 0; i < 17; i++) { const X = LAMP[0] - 21 + i * 2.6, Y = LAMP[1] + 9; const sx = Math.round(SW.sxOf(X)), sy = Math.round(SW.syOf(Y)); SW.put(sx, sy, PAL.lamp[3]); SW.put(sx, sy + 1 + (i & 1), PAL.lamp[2]); }
    // dust in the lamplight
    for (let i = 0; i < 26; i++) {
      const r0 = hash(i, 1, 7), r1 = hash(i, 2, 7), sp = 0.03 + 0.05 * hash(i, 3, 7);
      const ph = (r1 + t * sp) % 1, X = LAMP[0] - 60 + 120 * r0 + 6 * Math.sin(t * 0.7 + i), Y = LAMP[1] + 70 - 140 * ph;
      const l = illum(X, Y, 1); if (l > 0.35 && hash(i, Math.floor(t * 4), 9) > 0.25) SW.put(Math.round(SW.sxOf(X)), Math.round(SW.syOf(Y)), PAL.lamp[l > 0.7 ? 4 : 3]);
    }
  }
  // capsule helper: distance from segment, with lighting from the lamp side
  function capsule(a, b, r, R, light, X, Y, x, y) {
    const dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy, t = clamp(((X - a[0]) * dx + (Y - a[1]) * dy) / l2);
    const ox = X - a[0] - t * dx, oy = Y - a[1] - t * dy, d = Math.hypot(ox, oy); if (d > r) return null;
    const s = d / r, n = (ox * light[0] + oy * light[1]) / (d || 1) * s;
    return pick(R, clamp(0.45 + 0.5 * n + 0.1 * (1 - s)), x, y);
  }
  function hand(light) {
    const parts = [
      [PAL.sleeve, [[-200, -150], [-97, -96]], 21],
      [PAL.cuff, [[-103, -96], [-93, -89]], 14],
      [PAL.skin, [[-85, -86], [-74, -83]], 10.5],
      [PAL.skin, [[-75, -88], [-63, -82]], 3.6],       // index finger
      [PAL.skin, [[-79, -76], [-65, -77]], 3.4],       // thumb
      [PAL.skin, [[-77, -94], [-67, -91]], 3.4],       // curled middle finger
    ].map(([R, ab, r]) => [R, ab.map(([x, y]) => [x, y + HY]), r]);
    for (const [R, [a, b], r] of parts) {
      SW.each(Math.min(a[0], b[0]) - r, Math.min(a[1], b[1]) - r, Math.max(a[0], b[0]) + r, Math.max(a[1], b[1]) + r, (X, Y, x, y) => { const col = capsule(a, b, r, R, light, X, Y, x, y); if (col) SW.lput(x, y, col); });
      SW.stamp(PAL.outline);
    }
  }
  return {
    draw(c, lt) {
      SW.setCam(0, 0, 1);
      room(lt);
      const light = [0.96, 0.28];
      const sec = Math.floor(lt), w = SW.hang({ R: 33, light, sub: lt / 60, glint: 0.75, pal: PAL }, PIV, LEN, theta(lt), -0.12 * AMP * Math.sin(TAU * lt / PER - 0.9));
      w.sec = sec + Math.min(1, (lt - sec) / 0.1);
      SW.drawChain(PIV, w.bow, 3.2, PAL.gold, light); SW.stamp(PAL.outline);
      SW.drawWatch(w, 1); SW.stamp(PAL.outline);
      hand([0.9, 0.44]);
      // breathing vignette: the edges of our vision dim as we go under
      const br = 0.5 + 0.12 * Math.sin(TAU * lt / 4.8);
      for (let y = 0; y < SW.LH; y++) for (let x = 0; x < SW.LW; x++) {
        const nx = (x - SW.LW / 2) / (SW.LW / 2), ny = (y - SW.LH / 2) / (SW.LH / 2), v = clamp((Math.hypot(nx * 0.92, ny) - (1.15 - br * 0.4)) / 0.5);
        if (v > bayer(x, y)) SW.put(x, y, PAL.outline);
      }
      SW.present(c);
    },
  };
})();
