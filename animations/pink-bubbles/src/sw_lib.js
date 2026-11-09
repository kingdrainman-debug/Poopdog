// sw_lib.js · pixel-art toolkit for the hypnotic stopwatch film.
// Every frame is computed per pixel on a 320×180 low-res buffer, then scaled ×6 (nearest neighbour) to 1920×1080.
// Shapes are evaluated in world coordinates through a camera, so the camera can move and zoom
// without ever breaking the pixel grid. Sprites go through a mask layer that adds a 1px dark outline
// (one layer per part, so parts keep their inner outlines, as in the skill's 14_8bit recipe).
// Deterministic: no Math.random; all noise is hashed from integer coordinates.
window.SW = (() => {
  const LW = 320, LH = 180, S = 6, N = LW * LH, TAU = Math.PI * 2;
  const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const ramp = a => a.map(hex);
  const clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
  const BAY = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
  const bayer = (x, y) => (BAY[((y & 3) << 2) | (x & 3)] + 0.5) / 16;
  // ordered-dither pick from a tone ramp (dark → light)
  const pick = (R, v, x, y) => { const n = R.length - 1, f = clamp(v) * n; let i = f | 0; if (f - i > bayer(x, y)) i++; return R[i > n ? n : i]; };
  const hash = (x, y, s = 0) => { let h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(s | 0, 1013904223);
    h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return (h >>> 0) / 4294967296; };
  const vnoise = (x, y, s = 0) => { const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi, u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
    const a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v; };
  const step = (t, fps) => Math.floor(t * fps + 1e-6) / fps;
  const ease = { sineInOut: p => 0.5 - 0.5 * Math.cos(Math.PI * clamp(p)), outCubic: p => 1 - Math.pow(1 - clamp(p), 3), inCubic: p => Math.pow(clamp(p), 3) };

  // ---------- frame buffer ----------
  const cv = document.createElement('canvas'); cv.width = LW; cv.height = LH;
  const g = cv.getContext('2d', { willReadFrequently: true });
  const im = g.createImageData(LW, LH), D = im.data;
  const put = (x, y, c) => { if (x < 0 || y < 0 || x >= LW || y >= LH) return; const i = (y * LW + x) << 2; D[i] = c[0]; D[i + 1] = c[1]; D[i + 2] = c[2]; D[i + 3] = 255; };
  const getPx = (x, y) => { const i = (y * LW + x) << 2; return [D[i], D[i + 1], D[i + 2]]; };

  // ---------- camera ----------
  const cam = { x: 0, y: 0, z: 1 };
  const setCam = (x, y, z) => { cam.x = x; cam.y = y; cam.z = z; };
  const wx = sx => (sx + 0.5 - LW / 2) / cam.z + cam.x;
  const wy = sy => (sy + 0.5 - LH / 2) / cam.z + cam.y;
  const sxOf = x => (x - cam.x) * cam.z + LW / 2;
  const syOf = y => (y - cam.y) * cam.z + LH / 2;

  // full-frame fill: fn(worldX, worldY, sx, sy) → [r,g,b]
  function fill(fn) {
    for (let y = 0; y < LH; y++) { const Y = wy(y); for (let x = 0; x < LW; x++) { const c = fn(wx(x), Y, x, y); const i = (y * LW + x) << 2; D[i] = c[0]; D[i + 1] = c[1]; D[i + 2] = c[2]; D[i + 3] = 255; } }
  }

  // ---------- sprite layer with outline ----------
  const LM = new Uint8Array(N), LC = new Uint8ClampedArray(N * 3);
  const lput = (x, y, c) => { if (x < 0 || y < 0 || x >= LW || y >= LH) return; const i = y * LW + x; LM[i] = 1; LC[i * 3] = c[0]; LC[i * 3 + 1] = c[1]; LC[i * 3 + 2] = c[2]; };
  // stamp the layer onto the frame, outlining the outside edge with `outline`
  function stamp(outline) {
    for (let y = 0; y < LH; y++) for (let x = 0; x < LW; x++) {
      const i = y * LW + x, j = i << 2;
      if (LM[i]) { D[j] = LC[i * 3]; D[j + 1] = LC[i * 3 + 1]; D[j + 2] = LC[i * 3 + 2]; continue; }
      if (outline && ((x > 0 && LM[i - 1]) || (x < LW - 1 && LM[i + 1]) || (y > 0 && LM[i - LW]) || (y < LH - 1 && LM[i + LW]))) { D[j] = outline[0]; D[j + 1] = outline[1]; D[j + 2] = outline[2]; }
    }
    LM.fill(0);
  }
  // iterate screen pixels inside a world-space box
  function each(x0, y0, x1, y1, fn) {
    const a = Math.max(0, Math.floor(sxOf(x0))), b = Math.min(LW - 1, Math.ceil(sxOf(x1))), c = Math.max(0, Math.floor(syOf(y0))), d = Math.min(LH - 1, Math.ceil(syOf(y1)));
    for (let y = c; y <= d; y++) { const Y = wy(y); for (let x = a; x <= b; x++) fn(wx(x), Y, x, y); }
  }

  // ---------- the stopwatch ----------
  // w = { x, y (centre, world), R, rot, light:[lx,ly] (unit, towards the light), sec, sub, glint 0..1, press 0..1, pal }
  // Geometry is in local units (y down, 12 o'clock = −y). region() is shared by the colour pass and the shadow pass.
  function geom(R) {
    return { R, rim: R * 0.13, fr: R * 0.87, stemW: R * 0.085, stemTop: -R - R * 0.12, crownW: R * 0.16, crownH: R * 0.13,
      bowC: -R - R * 0.4, bowR: R * 0.15, bowT: R * 0.045, pushA: TAU * 0.11, pushW: R * 0.065, pushL: R * 0.11 };
  }
  // 0 none · 1 rim · 2 face · 3 stem · 4 crown · 5 bow · 6 pusher
  function region(ux, uy, G, press, px) {
    const r = Math.hypot(ux, uy);
    if (r <= G.R) return r > G.fr ? 1 : 2;
    const pr = press * G.crownH * 0.7;
    if (Math.abs(ux) <= G.stemW && uy >= G.stemTop && uy <= -G.R + 1) return 3;
    const ct = G.stemTop - G.crownH + pr, cb = G.stemTop + pr;
    if (uy >= ct && uy <= cb && Math.abs(ux) <= G.crownW - (uy < ct + px ? px : 0)) return 4;
    const bd = Math.hypot(ux, uy - G.bowC);
    if (Math.abs(bd - G.bowR) <= Math.max(G.bowT, 0.55 * px) && uy < G.stemTop - G.crownH * 0.5) return 5;
    for (const s of [-1, 1]) {           // the two side pushers of a stopwatch, at about 10 and 2 o'clock
      const a = s * G.pushA, ca = Math.cos(a), sa = Math.sin(a), vx = ux * ca + uy * sa, vy = -ux * sa + uy * ca;
      if (Math.abs(vx) <= G.pushW && vy >= -G.R - G.pushL && vy <= -G.R + 1) return 6;
      if (Math.abs(vx) <= G.pushW * 1.5 && vy >= -G.R - G.pushL - G.pushW * 1.2 && vy < -G.R - G.pushL) return 6;
    }
    return 0;
  }
  const segDist = (px_, py_, ax, ay, bx, by) => { const dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy; const t = l2 ? clamp(((px_ - ax) * dx + (py_ - ay) * dy) / l2) : 0; return Math.hypot(px_ - ax - t * dx, py_ - ay - t * dy); };
  function watchColor(ux, uy, dx, dy, w, G, code, px, x, y) {
    const P = w.pal, L = w.light, cr = Math.cos(w.rot), sr = Math.sin(w.rot);
    const llx = L[0] * cr + L[1] * sr, lly = -L[0] * sr + L[1] * cr;          // light in local frame
    const r = Math.hypot(ux, uy);
    if (code === 1) {                                                         // rim: rounded band, lit like a torus
      if (r - G.fr < 0.6 * px) return P.gold[0];                               // bezel line between rim and face
      const across = (r - G.fr) / G.rim, s = clamp((across - 0.5) * 1.7, -0.95, 0.95);
      const nx = ux / r * s, ny = uy / r * s, nz = Math.sqrt(1 - s * s);
      const dif = clamp(nx * llx + ny * lly + nz * 0.45, 0, 1.3);
      return pick(P.gold, clamp(dif * 0.82 + 0.08), x, y);
    }
    if (code === 3 || code === 4 || code === 6) {                             // cylinders: lateral shading + knurl
      const hw = code === 4 ? G.crownW : code === 3 ? G.stemW : G.pushW * 1.5;
      let v = 0.55 + 0.4 * clamp(ux / hw) * Math.sign(llx || 1) * Math.min(1, Math.abs(llx) * 1.5) + 0.15 * -lly;
      if (code === 4 && (Math.floor((ux + hw) / Math.max(px * 1.0, G.crownW * 0.28)) & 1)) v -= 0.28;
      return pick(P.gold, clamp(v), x, y);
    }
    if (code === 5) { const bd = Math.hypot(ux, uy - G.bowC); return pick(P.gold, clamp(0.5 + 0.45 * ((ux / bd) * llx + ((uy - G.bowC) / bd) * lly)), x, y); }
    // ---- face ----
    const fr = G.fr;
    let col = P.face[2];
    const nx = ux / (r || 1), ny = uy / (r || 1);
    if (fr - r < G.rim * 0.55 && nx * llx + ny * lly > 0.05) col = P.face[1];          // the rim's shadow on the dial (light side)
    if (fr - r < G.rim * 0.25 && nx * llx + ny * lly > 0.45) col = P.face[0];
    // minute track: 60 ticks, longer every 5
    const a = Math.atan2(ux, -uy), k = Math.round(a / TAU * 60), ta = k * TAU / 60, perp = Math.abs(ux * Math.cos(ta) + uy * Math.sin(ta));
    const major = ((k % 5) + 5) % 5 === 0, rin = major ? fr - Math.max(3.2 * px, fr * 0.13) : fr - Math.max(1.6 * px, fr * 0.06), rout = fr - Math.max(0.7 * px, fr * 0.02);
    if (r > rin && r < rout && perp < (major ? 0.62 : 0.5) * px) col = P.ink;
    // small 30-minute register
    const scx = 0, scy = fr * 0.42, rs = fr * 0.25, sd = Math.hypot(ux - scx, uy - scy);
    if (sd < rs - 0.5 * px && col === P.face[2] && ((x + y) & 1) && sd > rs * 0.55) col = P.face[1];
    if (Math.abs(sd - rs) < 0.5 * px) col = P.ink;
    const sa = w.sub / 30 * TAU;
    if (segDist(ux, uy, scx, scy, scx + Math.sin(sa) * rs * 0.82, scy - Math.cos(sa) * rs * 0.82) < 0.5 * px) col = P.ink;
    // sweep second hand (red) with a 1px drop shadow
    const ha = w.sec / 60 * TAU, hx = Math.sin(ha), hy = -Math.cos(ha), tip = fr - Math.max(1.4 * px, fr * 0.05), tail = -fr * 0.24;
    const sh = px * 1.0;
    if (segDist(ux - sh * 0.7, uy - sh, tail * hx, tail * hy, tip * hx, tip * hy) < 0.55 * px && col !== P.ink) col = P.face[0];
    if (segDist(ux, uy, tail * hx, tail * hy, tip * hx, tip * hy) < 0.6 * px) col = P.red;
    if (Math.hypot(ux - tail * 0.8 * hx, uy - tail * 0.8 * hy) < Math.max(1.1 * px, fr * 0.05)) col = P.red;
    if (r < Math.max(1.6 * px, fr * 0.07)) col = r < 0.75 * px ? P.gold[4] : P.red;
    // glass reflection: a world-fixed streak facing the light (it slides across the dial as the watch swings)
    if (w.glint > 0) {
      const q = (dx * L[0] + dy * L[1]) / fr, pp = Math.abs(-dx * L[1] + dy * L[0]) / fr;
      const band = (q > 0.5 && q < 0.66 && pp < 0.62) || (q > 0.36 && q < 0.41 && pp < 0.55);
      if (band && bayer(x, y) < w.glint) col = P.glass;
    }
    return col;
  }
  // draw the watch into the sprite layer (call stamp() after)
  function drawWatch(w, px) {
    const G = geom(w.R), ext = w.R * 1.75, cr = Math.cos(w.rot), sr = Math.sin(w.rot);
    each(w.x - ext, w.y - ext, w.x + ext, w.y + ext, (X, Y, x, y) => {
      const dx = X - w.x, dy = Y - w.y, ux = dx * cr + dy * sr, uy = -dx * sr + dy * cr;
      const code = region(ux, uy, G, w.press || 0, px);
      if (code) lput(x, y, watchColor(ux, uy, dx, dy, w, G, code, px, x, y));
    });
  }
  // inside test for shadows (world point)
  function watchInside(X, Y, w, px) {
    const G = geom(w.R), cr = Math.cos(w.rot), sr = Math.sin(w.rot), dx = X - w.x, dy = Y - w.y;
    if (dx * dx + dy * dy > (w.R * 1.75) ** 2) return 0;
    return region(dx * cr + dy * sr, -dx * sr + dy * cr, G, w.press || 0, px);
  }
  // top of the bow ring in world space (where the chain attaches)
  // hang a watch from a chain: pivot, chain length, chain angle θ (0 = straight down, + = swung right), extra body wobble
  function hang(w, pivot, len, th, wob = 0) {
    const G = geom(w.R), ly = G.bowC - G.bowR, bx = pivot[0] + len * Math.sin(th), by = pivot[1] + len * Math.cos(th), phi = -(th + wob);
    w.rot = phi; w.x = bx + ly * Math.sin(phi); w.y = by - ly * Math.cos(phi); w.bow = [bx, by]; return w;
  }

  // ---------- chain ----------
  // links alternate: an open oval seen face-on, then one seen edge-on (a short bar)
  function chainAt(X, Y, p0, p1, lk) {
    const dx = p1[0] - p0[0], dy = p1[1] - p0[1], len = Math.hypot(dx, dy), ux = dx / len, uy = dy / len;
    const a = (X - p0[0]) * ux + (Y - p0[1]) * uy, p = -(X - p0[0]) * uy + (Y - p0[1]) * ux;
    if (a < 0 || a > len || Math.abs(p) > lk * 0.6) return 0;
    const k = Math.floor(a / lk), u = a - (k + 0.5) * lk;
    if (k & 1) { const e = (u / (lk * 0.68)) ** 2 + (p / (lk * 0.42)) ** 2; return e > 0.38 && e <= 1 ? 1 : 0; }
    return Math.abs(p) < lk * 0.17 && Math.abs(u) < lk * 0.62 ? 2 : 0;
  }
  function drawChain(p0, p1, lk, gold, light, x0) {
    const pad = lk;
    each(Math.min(p0[0], p1[0]) - pad, Math.min(p0[1], p1[1]) - pad, Math.max(p0[0], p1[0]) + pad, Math.max(p0[1], p1[1]) + pad, (X, Y, x, y) => {
      const k = chainAt(X, Y, p0, p1, lk); if (!k) return;
      const dx = p1[0] - p0[0], dy = p1[1] - p0[1], len = Math.hypot(dx, dy), pp = (-(X - p0[0]) * dy + (Y - p0[1]) * dx) / len;
      const side = Math.sign(pp) * (-dy / len * light[0] + dx / len * light[1]);
      lput(x, y, pick(gold, k === 2 ? 0.7 + 0.2 * side : 0.5 + 0.35 * side, x, y));
    });
  }

  // ---------- small helpers ----------
  function sparkle(sx, sy, size, col, core) {
    sx = Math.round(sx); sy = Math.round(sy); if (size <= 0) return;
    for (let i = 1; i <= size; i++) { put(sx + i, sy, col); put(sx - i, sy, col); put(sx, sy + i, col); put(sx, sy - i, col); }
    if (size >= 2) { put(sx + 1, sy + 1, col); put(sx - 1, sy - 1, col); put(sx + 1, sy - 1, col); put(sx - 1, sy + 1, col); }
    put(sx, sy, core || col);
  }
  function present(c) { g.putImageData(im, 0, 0); c.save(); c.setTransform(1, 0, 0, 1, 0, 0); c.imageSmoothingEnabled = false; c.drawImage(cv, 0, 0, LW * S, LH * S); c.restore(); }

  return { LW, LH, S, TAU, hex, ramp, clamp, bayer, pick, hash, vnoise, step, ease, put, getPx, cam, setCam, wx, wy, sxOf, syOf, fill, lput, stamp, each,
    geom, region, drawWatch, watchInside, hang, chainAt, drawChain, segDist, sparkle, present };
})();
