// Big pixel bubbles in pink, purple and cream: each one pops in, inflates quickly (about 1.5 s from appearing
// to popping) and bursts the moment it reaches full size, releasing a splash in its own colours. Drawn on flat chroma green; the white version is
// made by swapping the exact key colour afterwards, so both renders have identical bubbles.
//
// Layout: a 6×3 jittered grid of "slots" covers the whole 320×180 frame (each art pixel = 6×6 on screen).
// Seamless 60 s loop: every slot repeats on a 2.5 or 3 s cycle (both divide 60) and each cycle's position,
// size and colour come from hash(slot, cycle index mod cycles-per-loop), so t = 60 is exactly t = 0.
// Keying: every drawn pixel is opaque; no palette colour is green, and every outline is dark.
SCENES['big_bubbles'] = (() => {
  const { hex, hash, clamp, bayer, put, TAU } = SW;
  const LW = SW.LW, LH = SW.LH;
  const KEY = [0, 255, 0];
  const pal = a => ({ outline: hex(a[0]), rim: hex(a[1]), mid: hex(a[2]), body: hex(a[3]), light: hex(a[4]), hi: hex(a[5]) });
  const PALS = {
    pink:   pal(['#7a1048', '#d9468f', '#ef68ad', '#ff8cc6', '#ffaad5', '#ffc4e1']),
    purple: pal(['#3e1258', '#8a3fbf', '#a35fd6', '#b97ae6', '#cc9cf0', '#dcbcf6']),
    cream:  pal(['#6b4a1c', '#cf9e56', '#e2ba74', '#efd197', '#f4dca9', '#f8e4b4']),
  };
  const pickPal = v => (v < 0.5 ? PALS.pink : v < 0.75 ? PALS.purple : PALS.cream);
  const LOOP = 60, COLS = 6, ROWS = 3, CW = LW / COLS, CH = LH / ROWS;
  const APPEAR = 0.2, INFLATE = 1.3, POP = 0.5;   // appear → pop = 1.5 s, then the burst
  const LENS = [2.5, 3];
  const slots = [];
  for (let j = 0; j < ROWS; j++) for (let i = 0; i < COLS; i++) {
    const id = j * COLS + i;
    slots.push({ id, i, j, L: LENS[Math.floor(hash(id, 1, 13) * LENS.length)], off: hash(id, 2, 13) * LOOP });
  }
  const backOut = p => { const s = 2.0; p = clamp(p) - 1; return 1 + p * p * ((s + 1) * p + s); };
  // fast, nearly steady growth that is still expanding when it bursts (no slow ease-in, no stop before the pop)
  const grow = p => { p = clamp(p); return 0.6 * p + 0.4 * (1 - (1 - p) * (1 - p)); };

  // where a slot is in its life at time t → null, or { phase, x, y, rx, ry, r, q, C }
  function stateOf(s, t) {
    const tt = ((t + s.off) % LOOP + LOOP) % LOOP, k = Math.floor(tt / s.L), u = tt - k * s.L, n = LOOP / s.L, kk = k % n;
    const h = q => hash(s.id, kk, 200 + q);
    const rmax = 16 + 11 * Math.pow(h(1), 1.1), C = pickPal(h(8));
    const life = APPEAR + INFLATE + POP, gap = h(3) * (s.L - life);
    const v = u - gap; if (v < 0 || v >= life) return null;
    const cx = (s.i + 0.5 + (h(4) - 0.5) * 0.45) * CW, cy = (s.j + 0.5 + (h(5) - 0.5) * 0.45) * CH;
    const drift = 3 + 5 * h(6), ph = h(7) * TAU;
    const vv = Math.min(v, APPEAR + INFLATE);                 // the burst stays where the bubble was
    const x = cx + 1.4 * Math.sin(vv * 1.9 + ph), y = cy + 3 - drift * (vv / (APPEAR + INFLATE));
    const r0 = Math.max(2, rmax * 0.2);
    if (v < APPEAR) { const r = r0 * backOut(v / APPEAR); return { phase: 'grow', x, y, rx: r, ry: r, r, C }; }
    if (v < APPEAR + INFLATE) { const r = r0 + (rmax - r0) * grow((v - APPEAR) / INFLATE); return { phase: 'grow', x, y, rx: r, ry: r, r, C }; }
    return { phase: 'pop', x, y, r: rmax, q: SW.step(v - APPEAR - INFLATE, 15) / POP, seed: s.id * 97 + kk, C };
  }

  function bubble(b) {
    const { x, y, rx, ry, C } = b, r = Math.min(rx, ry);
    const x0 = Math.floor(x - rx - 2), x1 = Math.ceil(x + rx + 2), y0 = Math.floor(y - ry - 2), y1 = Math.ceil(y + ry + 2);
    const inside = (px, py) => { const nx = (px + 0.5 - x) / rx, ny = (py + 0.5 - y) / ry; return nx * nx + ny * ny <= 1; };
    const rimW = Math.max(1.15, r * 0.07) / r;
    for (let py = Math.max(0, y0); py <= Math.min(LH - 1, y1); py++) for (let px = Math.max(0, x0); px <= Math.min(LW - 1, x1); px++) {
      const nx = (px + 0.5 - x) / rx, ny = (py + 0.5 - y) / ry, d2 = nx * nx + ny * ny;
      if (d2 > 1) { if (inside(px - 1, py) || inside(px + 1, py) || inside(px, py - 1) || inside(px, py + 1)) put(px, py, C.outline); continue; }
      const d = Math.sqrt(d2);
      let col = C.body;
      if (r < 2.6) { put(px, py, (nx < -0.1 && ny < -0.1) ? C.hi : C.body); continue; }
      const s = nx * 0.6 + ny * 0.8;                                    // light from the upper left
      if (s < -0.25 && d > 0.3) col = C.light;
      if (s > 0.3 && (s > 0.55 || bayer(px, py) < (s - 0.3) / 0.25)) col = C.mid;
      if (s > 0.62 && d > 0.78) col = C.rim;                            // core shadow on the lower right
      if (d > 1 - rimW) col = C.rim;                                    // film band just inside the outline
      const a = Math.atan2(ny, nx), da = Math.abs(((a + 2.36) % TAU + TAU + Math.PI) % TAU - Math.PI);
      if (d > 0.62 && d < 0.8 && da < 0.5) col = C.hi;                  // highlight crescent
      if (Math.hypot(nx + 0.4, ny + 0.42) < 0.17 + 0.5 / r) col = C.hi;   // shine spot
      else if (Math.hypot(nx + 0.12, ny + 0.6) < 0.05 + 0.6 / r) col = C.hi;   // second, smaller shine
      else if (Math.hypot(nx - 0.42, ny - 0.5) < 0.1 + 0.3 / r && d < 1 - rimW) col = C.light;   // bounce light
      put(px, py, col);
    }
  }

  // the burst, scaled to the bubble: a broken ring, streaks flying out, then droplets that fall a little
  function pop(b) {
    const { x, y, r, q, seed, C } = b;
    const line = (a, r0, r1, col) => { for (let rr = r0; rr <= r1; rr += 0.5) put(Math.floor(x + Math.cos(a) * rr), Math.floor(y + Math.sin(a) * rr), col); };
    const blob = (px, py, sz, col, edge) => { for (let j = 0; j < sz; j++) for (let i = 0; i < sz; i++) put(px + i, py + j, (edge && (i === sz - 1 || j === sz - 1)) ? edge : col); };
    if (q < 0.2) {
      const n = 4 * Math.round(r * 0.45);
      for (let k = 0; k < n; k++) { if (k % 4 > 1) continue; const a = k / n * TAU; put(Math.floor(x + Math.cos(a) * r * 1.02), Math.floor(y + Math.sin(a) * r * 1.02), C.rim); put(Math.floor(x + Math.cos(a) * (r * 1.02 - 1)), Math.floor(y + Math.sin(a) * (r * 1.02 - 1)), C.body); }
    }
    if (q >= 0.12 && q < 0.8) {
      const len = Math.max(1, (1.5 + 0.22 * r) * (1 - q)), nS = 16;
      for (let k = 0; k < nS; k++) {
        const a = k / nS * TAU + (hash(seed, k, 31) - 0.5) * 0.3, rr = r * (0.8 + 0.6 * q);
        line(a, rr, rr + len, q < 0.45 ? C.body : C.rim);
        if (q < 0.4) line(a + 0.03, rr, rr + len * 0.6, C.light);
      }
    }
    if (q >= 0.1) {
      for (let k = 0; k < 10; k++) {
        const a = (k + 0.5) / 10 * TAU + hash(seed, k, 41) * 0.5, dist = r * (0.85 + 0.75 * q) + 1, fall = 9 * q * q;
        const px = Math.floor(x + Math.cos(a) * dist), py = Math.floor(y + Math.sin(a) * dist + fall);
        const sz = q < 0.4 ? 3 : q < 0.75 ? 2 : 1;
        blob(px, py, sz, q < 0.55 ? C.light : C.mid, sz > 1 ? C.rim : null);
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
