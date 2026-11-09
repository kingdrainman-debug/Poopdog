// A screen full of pink pixel bubbles: each one pops in, inflates (breathing a little), trembles at full size,
// then bursts into a small pink splash. Drawn on flat chroma green; the white version is made by swapping
// the exact key colour afterwards, so both renders have identical bubbles.
//
// Layout: a 12×7 jittered grid of "slots" covers the whole 320×180 frame (each art pixel = 6×6 on screen).
// Seamless 60 s loop: every slot repeats on a 5 or 6 s cycle (both divide 60) and each cycle's position/size
// comes from hash(slot, cycle index mod cycles-per-loop), so t = 60 is exactly t = 0.
// Keying: every drawn pixel is opaque; no palette colour is near white or green.
SCENES['pink_bubbles'] = (() => {
  const { hex, hash, clamp, bayer, put, TAU } = SW;
  const LW = SW.LW, LH = SW.LH;
  const KEY = [0, 255, 0];
  const C = {
    outline: hex('#7a1048'), rim: hex('#d9468f'), mid: hex('#ef68ad'),
    body: hex('#ff8cc6'), light: hex('#ffaad5'), hi: hex('#ffc4e1'),
  };
  const LOOP = 60, COLS = 12, ROWS = 7, CW = LW / COLS, CH = LH / ROWS;
  const APPEAR = 0.22, TREM = 0.36, POP = 0.42;
  const LENS = [5, 6];
  const slots = [];
  for (let j = 0; j < ROWS; j++) for (let i = 0; i < COLS; i++) {
    const id = j * COLS + i;
    slots.push({ id, i, j, L: LENS[Math.floor(hash(id, 1, 11) * LENS.length)], off: hash(id, 2, 11) * LOOP });
  }
  const backOut = p => { const s = 2.2; p = clamp(p) - 1; return 1 + p * p * ((s + 1) * p + s); };
  const smooth = p => { p = clamp(p); return p * p * (3 - 2 * p); };

  // where a slot is in its life at time t → null, or { phase, x, y, rx, ry, r, q }
  function stateOf(s, t) {
    const tt = ((t + s.off) % LOOP + LOOP) % LOOP, k = Math.floor(tt / s.L), u = tt - k * s.L, n = LOOP / s.L, kk = k % n;
    const h = q => hash(s.id, kk, 100 + q);
    const rmax = 5.5 + 5.5 * Math.pow(h(1), 1.2);
    const gap = 0.12 + 0.55 * h(3), life = s.L - gap, inflate = life - APPEAR - TREM - POP;   // each slot is busy ~90% of its cycle
    const v = u - gap; if (v < 0 || v >= life) return null;
    const cx = (s.i + 0.5 + (h(4) - 0.5) * 0.75) * CW, cy = (s.j + 0.5 + (h(5) - 0.5) * 0.75) * CH;
    const drift = 2 + 4 * h(6), ph = h(7) * TAU;
    const x = cx + 1.2 * Math.sin(v * 2.1 + ph), y = cy + 2 - drift * (v / life);
    const r0 = Math.max(1.6, rmax * 0.3);
    if (v < APPEAR) { const r = r0 * backOut(v / APPEAR); return { phase: 'grow', x, y, rx: r, ry: r, r }; }
    if (v < APPEAR + inflate) {
      const p = (v - APPEAR) / inflate, r = r0 + (rmax - r0) * smooth(p), b = 0.05 * Math.sin(v * 7 + ph);
      return { phase: 'grow', x, y, rx: r * (1 + b), ry: r * (1 - b), r };
    }
    if (v < APPEAR + inflate + TREM) {          // about to burst: quick stepped squash-and-stretch
      const st = SW.step(v, 15), a = 0.04 + 0.1 * ((v - APPEAR - inflate) / TREM), w = Math.sin(st * 47 + ph);
      return { phase: 'grow', x, y, rx: rmax * (1 + a * w), ry: rmax * (1 - a * w), r: rmax };
    }
    return { phase: 'pop', x, y, r: rmax, q: SW.step(v - APPEAR - inflate - TREM, 15) / POP, seed: s.id * 97 + kk };
  }

  function bubble(b) {
    const { x, y, rx, ry } = b, r = Math.min(rx, ry);
    const x0 = Math.floor(x - rx - 2), x1 = Math.ceil(x + rx + 2), y0 = Math.floor(y - ry - 2), y1 = Math.ceil(y + ry + 2);
    const inside = (px, py) => { const nx = (px + 0.5 - x) / rx, ny = (py + 0.5 - y) / ry; return nx * nx + ny * ny <= 1; };
    for (let py = Math.max(0, y0); py <= Math.min(LH - 1, y1); py++) for (let px = Math.max(0, x0); px <= Math.min(LW - 1, x1); px++) {
      const nx = (px + 0.5 - x) / rx, ny = (py + 0.5 - y) / ry, d2 = nx * nx + ny * ny;
      if (d2 > 1) { if (inside(px - 1, py) || inside(px + 1, py) || inside(px, py - 1) || inside(px, py + 1)) put(px, py, C.outline); continue; }
      const d = Math.sqrt(d2);
      let col = C.body;
      if (r < 2.6) { put(px, py, (nx < -0.1 && ny < -0.1) ? C.hi : C.body); continue; }
      const s = nx * 0.6 + ny * 0.8;                                   // light from the upper left
      if (s < -0.25 && d > 0.3) col = C.light;
      if (s > 0.3 && (s > 0.55 || bayer(px, py) < (s - 0.3) / 0.25)) col = C.mid;
      if (d > 1 - 1.15 / r) col = C.rim;                                // thin film band just inside the outline
      const a = Math.atan2(ny, nx), da = Math.abs(((a + 2.36) % TAU + TAU + Math.PI) % TAU - Math.PI);
      if (d > 0.5 && d < 0.8 && da < 0.55) col = C.hi;                  // highlight crescent
      if (Math.hypot(nx + 0.4, ny + 0.42) < 0.2 + 0.5 / r) col = C.hi;    // shine spot
      else if (Math.hypot(nx - 0.42, ny - 0.5) < 0.12 + 0.3 / r && d < 1 - 1.15 / r) col = C.light;   // little bounce light
      put(px, py, col);
    }
  }

  // the burst: a broken ring, eight short streaks flying out, then a few droplets that fall a little
  function pop(b) {
    const { x, y, r, q, seed } = b;
    const line = (a, r0, r1, col) => { for (let rr = r0; rr <= r1; rr += 0.5) put(Math.floor(x + Math.cos(a) * rr), Math.floor(y + Math.sin(a) * rr), col); };
    if (q < 0.2) {
      for (let k = 0; k < 24; k++) { if (k % 4 > 1) continue; const a = k / 24 * TAU; put(Math.floor(x + Math.cos(a) * r * 1.02), Math.floor(y + Math.sin(a) * r * 1.02), C.rim); }
    }
    if (q >= 0.12 && q < 0.8) {
      const len = Math.max(1, 3.2 * (1 - q) * Math.min(1, r / 6));
      for (let k = 0; k < 8; k++) {
        const a = k / 8 * TAU + (hash(seed, k, 31) - 0.5) * 0.35, rr = r * (0.75 + 0.85 * q);
        line(a, rr, rr + len, q < 0.45 ? C.body : C.rim);
      }
    }
    if (q >= 0.1) {
      for (let k = 0; k < 5; k++) {
        const a = (k + 0.5) / 5 * TAU + hash(seed, k, 41) * 0.6, dist = r * (0.8 + 1.3 * q) + 1, fall = 5 * q * q;
        const px = Math.floor(x + Math.cos(a) * dist), py = Math.floor(y + Math.sin(a) * dist + fall);
        put(px, py, q < 0.55 ? C.light : C.mid);
        if (q < 0.5) { put(px + 1, py, C.body); put(px, py + 1, C.rim); put(px + 1, py + 1, C.rim); }
      }
    }
  }

  return {
    draw(c, lt) {
      SW.fill(() => KEY);
      const live = slots.map(s => stateOf(s, lt)).filter(Boolean);
      live.filter(b => b.phase === 'grow').sort((a, b) => a.r - b.r).forEach(bubble);
      live.filter(b => b.phase === 'pop').forEach(pop);
      SW.present(c);
    },
  };
})();
