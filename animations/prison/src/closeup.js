// closeup.js · the new RUBBERDOLL (the LTX vessel), chest-up and breathing: five candidate presentations.
//   cu_1 · CCTV feed          amber monochrome security camera, grain, OSD, REC
//   cu_2 · bio-monitor        the figure beside live vitals (respiration, D0LL G4S saturation, heart, neural)
//   cu_3 · containment tank   behind a riveted viewing window, orange gas haze, glass reflections, condensation
//   cu_4 · monitor prop       a pixel-art CRT security monitor on chroma green (keyable) showing the amber feed
//   cu_5 · thermal camera     heat-map palette, glowing breath plume, crosshair and readouts
// The figure: a female figure, framed head to upper chest like a portrait, in a shiny black full-body suit with a
// featureless hood (centre seam), a respirator valve on the lower face, a ribbed breathing tube curving down one side
// and an orange barcode on the upper chest. Latex shading: a sharp specular key from the upper left, a cyan rim on
// the right, a warm orange rim on the left as if lit by the gas. breath (0..1) lifts the shoulders; gas (0..1) is
// the position of an orange pulse travelling up the tube.
(() => {
  const { C, PX, AB } = MED, { hex, put, bayer, pick } = HD;
  const W = UI.W, H = UI.H, N = W * H;
  const RED = hex('#e8322a'), GLOSS = hex('#2b2320'), GREYD = hex('#4a4540'), INK = hex('#141210'), RUST = hex('#3d1704');
  const clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
  const txt = (s, font, x, y, col, align = 'left') => UI.paint(UI.text(s, font, x, y, align), col);
  const rect = (x, y, w, h, col, outline) => UI.paint(UI.mask(c => c.fillRect(x, y, w, h)), col, outline);
  const rr = (x, y, w, h, r, col, outline) => UI.paint(UI.rrect(x, y, w, h, r), col, outline);
  const hsh = (a, b, s = 0) => { let h = Math.imul(a | 0, 374761393) ^ Math.imul(b | 0, 668265263) ^ Math.imul(s | 0, 1013904223); h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
  const norm = v => { const l = Math.hypot(...v); return v.map(a => a / l); };
  const LK = norm([-0.45, -0.55, 0.7]), HV = norm([LK[0], LK[1], LK[2] + 1]);
  const lum = c => (c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11) / 255;

  // ── the figure: returns per-pixel colour (null = empty), luminance and heat, for a placement (cx, y0, s) ──
  // Each body part is a hand-shaped outline (Bezier paths in local units: u across, v down from the top of the hood)
  // inflated into a rounded form: an exact distance transform gives each pixel's depth inside its part, a quarter-
  // circle profile turns depth into height, a little sculpted relief is added (a face pressed up under the latex:
  // brow, eye hollows, nose, cheekbones, chin; collarbones and the chest's curve below the barcode), the height is
  // softened with a blur, and its gradient gives the normal. Parts are drawn back to front (torso+neck, arms, head).
  // A key light from the upper left makes the crisp latex highlights; surfaces facing the camera stay black. The rim
  // lights (cyan right, warm orange left) only follow the outer silhouette, so the inside of the form stays clean.
  const LK2 = norm([-0.7, -0.5, 0.5]), HV2 = norm([LK2[0], LK2[1], LK2[2] + 1]);
  const INF = 1e12;
  function edt(m) {                                                    // Felzenszwalb–Huttenlocher squared EDT, then sqrt
    const f = new Float64Array(N); for (let i = 0; i < N; i++) f[i] = m[i] ? INF : 0;
    const n = Math.max(W, H), g = new Float64Array(n), d = new Float64Array(n), v = new Int32Array(n), z = new Float64Array(n + 1);
    const pass = len => {
      let k = 0; v[0] = 0; z[0] = -INF; z[1] = INF;
      for (let q = 1; q < len; q++) {
        let sx; for (;;) { const p = v[k]; sx = (g[q] + q * q - g[p] - p * p) / (2 * q - 2 * p); if (sx <= z[k] && k > 0) k--; else break; }
        if (sx <= z[k]) { v[0] = q; z[1] = INF; continue; }
        k++; v[k] = q; z[k] = sx; z[k + 1] = INF;
      }
      k = 0; for (let q = 0; q < len; q++) { while (z[k + 1] < q) k++; const p = v[k]; d[q] = (q - p) * (q - p) + g[p]; }
    };
    for (let x = 0; x < W; x++) { for (let y = 0; y < H; y++) g[y] = f[y * W + x]; pass(H); for (let y = 0; y < H; y++) f[y * W + x] = d[y]; }
    for (let y = 0; y < H; y++) { for (let x = 0; x < W; x++) g[x] = f[y * W + x]; pass(W); for (let x = 0; x < W; x++) f[y * W + x] = d[x]; }
    const out = new Float32Array(N); for (let i = 0; i < N; i++) out[i] = Math.sqrt(f[i]); return out;
  }
  function blur(a, r) {                                                // separable box blur, twice (≈ a soft Gaussian)
    let src = a;
    for (let pass = 0; pass < 2; pass++) {
      const t = new Float32Array(N), o = new Float32Array(N), k = 2 * r + 1;
      for (let y = 0; y < H; y++) { const row = y * W; let acc = 0;
        for (let x = -r; x <= r; x++) acc += src[row + clamp(x, 0, W - 1)];
        for (let x = 0; x < W; x++) { t[row + x] = acc / k; acc += src[row + Math.min(W - 1, x + r + 1)] - src[row + Math.max(0, x - r)]; } }
      for (let x = 0; x < W; x++) { let acc = 0;
        for (let y = -r; y <= r; y++) acc += t[clamp(y, 0, H - 1) * W + x];
        for (let y = 0; y < H; y++) { o[y * W + x] = acc / k; acc += t[Math.min(H - 1, y + r + 1) * W + x] - t[Math.max(0, y - r) * W + x]; } }
      src = o;
    }
    return src;
  }
  const gs = (u, v, su, sv) => Math.exp(-((u / su) ** 2) - ((v / sv) ** 2));
  const cap = (a, b) => { const r = a * a + b * b; return r < 1 ? Math.sqrt(1 - r) : 0; };   // a low spherical cap: crisp crescent highlights
  const sstep = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
  // the bust: a long, smooth slope down from the upper chest and a short, defined curve underneath
  const bust = (a, dv) => { const b = dv < 0 ? dv / 36 : dv / 24, r = a * a + b * b; if (r >= 1) return 0;
    return Math.pow(1 - r, 0.5 + 1.5 * sstep(0.2, -0.7, b)); };
  function partMask(cx, y0, s, draw) { return UI.mask(g => { g.translate(cx, y0); g.scale(s, s); g.beginPath(); draw(g); g.fill(); }); }
  // Proportions follow a feminine figure: a smaller, tapered jaw and soft features, a long slender neck, narrow
  // sloping shoulders, slim arms, a ribcage that narrows towards the waist (the gap between arm and waist opens at the
  // bottom of frame), and the bust at its natural height, about two head-heights down from the crown.
  const HEAD = g => { g.moveTo(0, 0); g.bezierCurveTo(29, 0, 45, 19, 45, 50); g.bezierCurveTo(45, 68, 42, 82, 35, 96); g.bezierCurveTo(27, 110, 14, 123, 0, 123);
    g.bezierCurveTo(-14, 123, -27, 110, -35, 96); g.bezierCurveTo(-42, 82, -45, 68, -45, 50); g.bezierCurveTo(-45, 19, -29, 0, 0, 0); };
  const BODY = lift => g => { g.moveTo(-21, 100); g.lineTo(-22, 134); g.bezierCurveTo(-24, 150, -38, 159 - lift, -60, 166 - lift); g.bezierCurveTo(-73, 170 - lift, -82, 177 - lift, -86, 192 - lift);
    g.lineTo(-84, 240); g.bezierCurveTo(-82, 290, -72, 330, -70, 380); g.lineTo(-70, 420); g.lineTo(70, 420); g.lineTo(70, 380); g.bezierCurveTo(72, 330, 82, 290, 84, 240);
    g.lineTo(86, 192 - lift); g.bezierCurveTo(82, 177 - lift, 73, 170 - lift, 60, 166 - lift); g.bezierCurveTo(38, 159 - lift, 24, 150, 22, 134); g.lineTo(21, 100); };
  const ARM = (sx, lift) => g => { g.moveTo(sx * 58, 170 - lift); g.bezierCurveTo(sx * 84, 167 - lift, sx * 98, 184 - lift, sx * 99, 220 - lift * 0.5); g.lineTo(sx * 100, 420);
    g.lineTo(sx * 80, 420); g.lineTo(sx * 79, 252); g.bezierCurveTo(sx * 79, 222, sx * 76, 196, sx * 58, 170 - lift); };
  const reliefHead = (u, v) => 2.5 * gs(u, v - 46, 28, 6) - 4 * gs(Math.abs(u) - 16, v - 56, 9, 6) + 5 * gs(u, v - 71, 4, 10)
    + 5 * gs(Math.abs(u) - 23, v - 70, 10, 8) + 3 * gs(u, v - 114, 10, 5);
  const reliefBody = (u, v, lift, breath) => 2.5 * gs(Math.abs(u) - 30, v - (180 - lift + Math.abs(u) * 0.1), 18, 3.5)
    + (8 + 1.5 * breath) * bust((Math.abs(u) - 35) / 31, (v - 256 + lift * 0.5));
  // face: how much of the face shows under the latex (1 = full relief, 0 = smooth), or a function of v (a wipe)
  function vessel({ cx, y0, s, breath = 0.5, gas = 0.4, plume = 0, glow = 0, face = 1 }) {
    const faceK = typeof face === 'function' ? face : () => face;
    const col = new Array(N).fill(null), L = new Float32Array(N), heat = new Float32Array(N);
    const lift = breath * 4, br = Math.max(1, Math.round(2 * s));
    const parts = [
      { m: partMask(cx, y0, s, BODY(lift)), R: 30 * s, h: 0.6, rel: (u, v) => reliefBody(u, v, lift, breath), hot: (u, v) => 0.1 * gs(u, v - 170, 34, 46) },
      { m: partMask(cx, y0, s, ARM(-1, lift)), R: 12 * s, h: 0.48 },
      { m: partMask(cx, y0, s, ARM(1, lift)), R: 12 * s, h: 0.48 },
      { m: partMask(cx, y0, s, HEAD), R: 34 * s, h: 0.74, rel: (u, v) => faceK(v) * reliefHead(u, v), hot: (u, v) => 0.1 * gs(u, v - 82, 26, 30) },
    ];
    const all = new Uint8Array(N), own = new Int8Array(N).fill(-1);
    parts.forEach((P, k) => { for (let i = 0; i < N; i++) if (P.m[i]) { all[i] = 1; own[i] = k; } });
    const dAll = edt(all), rimW = 1.4 + 1.6 * s;
    parts.forEach((P, k) => {
      const d = edt(P.m), h0 = new Float32Array(N);
      for (let i = 0; i < N; i++) if (P.m[i]) {
        const q = 1 - Math.min(d[i], P.R) / P.R; h0[i] = P.R * Math.sqrt(1 - q * q);
        if (P.rel) { const x = i % W, y = (i / W) | 0; h0[i] += s * P.rel((x + 0.5 - cx) / s, (y + 0.5 - y0) / s); }
      }
      const hgt = blur(h0, br);
      for (let y = 1; y < H - 1; y++) for (let x = 1; x < W - 1; x++) {
        const i = y * W + x; if (own[i] !== k) continue;
        const hx = (hgt[i + 1] - hgt[i - 1]) / 2, hy = (hgt[i + W] - hgt[i - W]) / 2, nl = Math.hypot(hx, hy, 1);
        const n = [-hx / nl, -hy / nl, 1 / nl];
        const nh = Math.max(0, n[0] * HV2[0] + n[1] * HV2[1] + n[2] * HV2[2]);
        const spec = Math.pow(nh, 60) + 0.06 * Math.pow(nh, 12), dif = Math.max(0, n[0] * LK2[0] + n[1] * LK2[1] + n[2] * LK2[2]) * 0.1;
        const edge = clamp(1 - (dAll[i] - 0.5) / rimW), side = n[0] / (Math.hypot(n[0], n[1]) || 1);
        const uu = (x + 0.5 - cx) / s;                                     // rims only on the outward-facing sides
        const rimC = edge * clamp((side - 0.1) * 1.6) * clamp(uu / 10 + 0.5), rimO = edge * clamp((-side - 0.1) * 1.6) * clamp(-uu / 10 + 0.5);
        const j = (bayer(x, y) - 0.5) * 0.08;
        let c = spec + j > 0.55 ? C.white : spec + j > 0.3 ? C.whiteS : spec + j > 0.13 ? C.grey
          : rimC + j > 0.62 ? C.cyan : rimC + j > 0.3 ? C.cyanD : rimO + j > 0.62 ? C.orange : rimO + j > 0.3 ? C.orangeD : dif + spec > 0.075 ? GLOSS : C.black;
        if (d[i] < 1.3 && dAll[i] > 1.5) c = C.black;                  // a dark crease where one part overlaps another
        const hot = P.hot ? P.hot((x + 0.5 - cx) / s, (y + 0.5 - y0) / s) : 0;
        col[i] = c; L[i] = lum(c); heat[i] = P.h - 0.22 + 0.16 * n[2] + 0.2 * Math.sqrt(clamp(dAll[i] / (22 * s))) + hot;   // faces turned to the camera read warmer
      }
    });
    // breathing tube: a corrugated hose (Catmull-Rom samples with arc length) that plugs into the respirator valve with
    // a metal cuff, runs over the right shoulder and down the arm. A pulse of orange gas travels up it.
    const VC = [cx, y0 + 100 * s];
    const TP = [[2, 104], [18, 120], [42, 144], [68, 176], [88, 232], [96, 380]].map(([u, v]) => [cx + u * s, y0 + v * s]);
    const tpts = [];
    for (let k = 0; k < TP.length - 1; k++) {
      const p0 = TP[Math.max(0, k - 1)], p1 = TP[k], p2 = TP[k + 1], p3 = TP[Math.min(TP.length - 1, k + 2)];
      for (let t = 0; t < 1; t += 0.01) { const t2 = t * t, t3 = t2 * t, f = (a, b, c2, d) => 0.5 * (2 * b + (-a + c2) * t + (2 * a - 5 * b + 4 * c2 - d) * t2 + (-a + 3 * b - 3 * c2 + d) * t3);
        tpts.push([f(p0[0], p1[0], p2[0], p3[0]), f(p0[1], p1[1], p2[1], p3[1])]); }
    }
    const tS = new Float32Array(N).fill(-1), tW = new Float32Array(N), tD = new Float32Array(N).fill(1e9), R = 7 * s, RC = R * 1.3;
    let len = 0, cuff0 = -1;
    for (let k = 0; k < tpts.length; k++) {
      if (k) len += Math.hypot(tpts[k][0] - tpts[k - 1][0], tpts[k][1] - tpts[k - 1][1]);
      const [px, py] = tpts[k], nx = k < tpts.length - 1 ? tpts[k + 1][0] - px : 1, ny = k < tpts.length - 1 ? tpts[k + 1][1] - py : 0, nl = Math.hypot(nx, ny) || 1;
      if (cuff0 < 0 && Math.hypot(px - VC[0], py - VC[1]) > 10 * s) cuff0 = len;          // where the hose leaves the valve rim
      for (let y = Math.floor(py - RC - 1); y <= py + RC + 1; y++) for (let x = Math.floor(px - RC - 1); x <= px + RC + 1; x++) {
        if (x < 0 || y < 0 || x >= W || y >= H) continue;
        const dx = x + 0.5 - px, dy = y + 0.5 - py, d = Math.hypot(dx, dy), i = y * W + x;
        if (d <= RC && d < tD[i]) { tD[i] = d; tS[i] = len; tW[i] = (dx * (-ny / nl) + dy * (nx / nl)) / R; }
      }
    }
    const C0 = cuff0 - 2 * s, C1 = cuff0 + 6 * s, gasS = len * (1 - gas), cuffPx = [];
    const inCuff = ss => ss >= C0 && ss <= C1;
    for (let i = 0; i < N; i++) {
      if (tS[i] < 0) continue;
      const w = tW[i], ss = tS[i];
      if (inCuff(ss)) { cuffPx.push(i); continue; }                      // painted after the valve, so it overlaps the rim
      if (Math.abs(w) > 1) continue;
      const groove = ((ss / (4.5 * s)) % 1) < 0.3;
      let l = 0.32 + 0.5 * Math.exp(-(((w + 0.35) / 0.32) ** 2)) - 0.12 * Math.max(0, w) - (groove ? 0.12 : 0);
      let c = l > 0.68 ? C.whiteS : l > 0.48 ? C.grey : l > 0.27 ? GREYD : INK, h = 0.3;
      if (Math.abs(ss - gasS) < 12 * s) { c = groove ? C.orangeD : l > 0.45 ? C.orangeL : C.orange; h = 0.95; }
      if (Math.abs(w) > 0.88) c = C.black;
      col[i] = c; L[i] = lum(c); heat[i] = h;
    }
    // respirator valve on the lower face
    for (let y = Math.floor(y0 + 86 * s); y < y0 + 114 * s; y++) for (let x = Math.floor(cx - 14 * s); x < cx + 14 * s; x++) {
      const u = (x + 0.5 - cx) / s, v = (y + 0.5 - y0) / s, dv = Math.hypot(u, v - 100), i = y * W + x; if (dv >= 13) continue;
      let c;
      if (dv >= 8.5) { const a = Math.atan2(v - 100, u), sh = -Math.cos(a + 2.3); c = sh > 0.4 ? C.whiteS : sh > -0.2 ? C.grey : GREYD; if (dv > 12) c = C.black; }
      else {                                                           // grille; glows orange as the gas arrives (glow 0..1)
        const g = glow * (1 - 0.5 * dv / 8.5), b = bayer(x, y); c = b < g * 0.55 ? C.orangeL : b < g ? C.orange : INK;
        for (const k of [-4, 0, 4]) if (Math.abs(v - 100 - k) < 1.2 && Math.abs(u) < 6) c = C.black; }
      col[i] = c; L[i] = lum(c); heat[i] = 0.86;
    }
    for (const i of cuffPx) {                                            // the metal cuff joining hose and valve
      const w = tW[i], ss = tS[i], wc = w * R / RC;
      let l = 0.5 + 0.4 * Math.exp(-(((wc + 0.3) / 0.4) ** 2)) - 0.18 * Math.max(0, wc);
      let c = l > 0.72 ? C.whiteS : l > 0.5 ? C.grey : GREYD;
      if (Math.abs(wc) > 0.9 || ss - C0 < 1.2 || C1 - ss < 1.2) c = C.black;
      col[i] = c; L[i] = lum(c); heat[i] = 0.4;
    }
    // orange barcode on the sternum, with the name under it
    const bx = cx - 30 * s, by = y0 + 190 * s;
    for (let k = 0, x = bx; k < 12; k++) {
      const w = Math.max(1, Math.round((1 + (k * 5) % 3) * s)); for (let yy = Math.round(by); yy < by + 16 * s; yy++) for (let xx = Math.round(x); xx < Math.round(x) + w; xx++) { const i = yy * W + xx; if (col[i]) { col[i] = C.orange; L[i] = lum(C.orange); heat[i] -= 0.06; } }
      x += w + Math.max(1, Math.round((1 + (k % 2)) * s));
    }
    const nm = UI.text('RUBBERDOLL', `8px ${PX}`, cx, Math.round(by + 16 * s + 3), 'center');
    for (let i = 0; i < N; i++) if (nm[i] && col[i]) { col[i] = C.orangeD; L[i] = lum(C.orangeD); }
    // warm breath around the valve, with wisps rising (thermal view)
    if (plume > 0) for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const u = (x + 0.5 - cx) / s, v = (y + 0.5 - y0) / s, d = Math.hypot(u / 1.15, (v - 96) / 1.35 + 0.15 * Math.sin(u * 0.25));
      const pv = plume * Math.exp(-((d / 24) ** 2)), i = y * W + x;
      if (pv > 0.12) heat[i] = Math.max(heat[i], 0.5 + 0.5 * pv);
    }
    return { col, L, heat };
  }
  const blit = (v, f) => { for (let i = 0; i < N; i++) if (v.col[i]) { const x = i % W, y = (i / W) | 0, c = f ? f(v, i, x, y) : v.col[i]; if (c) put(x, y, c); } };
  const AMBER = PRISON.R;                                               // the prison ramp doubles as an amber monitor phosphor
  const amber = (l, x, y) => pick(AMBER, clamp(l), x, y);

  // 1 · CCTV feed: amber monochrome, cinder-block wall, grain, OSD, REC, corner brackets
  SCENES['cu_1'] = { draw(c, lt) {
    const fr = Math.round(lt * 30), v = vessel({ cx: 372, y0: 26, s: 1, breath: 0.6, gas: 0.45 });
    HD.fill((x, y) => {
      const mortar = (y % 18 === 0) || ((x + ((y / 18) | 0) % 2 * 20) % 40 === 0);
      const vig = 1 - 0.75 * (((x - 320) / 320) ** 2 + ((y - 180) / 180) ** 2);
      return amber((mortar ? 0.1 : 0.2) * vig + 0.07 * (hsh(x, y, fr) - 0.5), x, y);
    });
    blit(v, (vv, i, x, y) => amber(0.06 + vv.L[i] * 0.95 + 0.06 * (hsh(x, y, fr + 9) - 0.5), x, y));
    const OSD = AMBER[8];
    txt('CAM 07', `8px ${PX}`, 30, 26, OSD); txt('BLOCK D / VESSEL WING', `8px ${PX}`, 30, 38, OSD);
    UI.paint(UI.mask(g => { g.beginPath(); g.arc(560, 30, 4, 0, Math.PI * 2); g.fill(); }), RED); txt('REC', `8px ${PX}`, 610, 26, OSD, 'right');
    txt('2087-11-24  03:14:07', `8px ${PX}`, 30, 326, OSD); txt('SUBJECT: RUBBERDOLL', `8px ${PX}`, 610, 326, OSD, 'right');
    for (const [x, y, sx, sy] of [[18, 16, 1, 1], [622, 16, -1, 1], [18, 344, 1, -1], [622, 344, -1, -1]]) { rect(sx > 0 ? x : x - 24, y, 24, 1, OSD); rect(x, sy > 0 ? y : y - 24, 1, 24, OSD); }
    HD.present(c); CRT.apply(c, lt, 5);
  } };

  // 2 · bio-monitor: the figure beside live vitals
  SCENES['cu_2'] = { draw(c, lt) {
    const v = vessel({ cx: 186, y0: 34, s: 0.9, breath: 0.6, gas: 0.45 });
    HD.fill((x, y) => (x % 16 === 0 || y % 16 === 0) && x < 360 ? hex('#1a0b03') : C.black);
    blit(v);
    rect(362, 14, 1, 332, RUST);
    txt('LTX VITALS // RUBBERDOLL', `8px ${PX}`, 378, 18, C.orange);
    const box = (y, h, label) => { rr(376, y, 250, h, 2, C.black, RUST); txt(label, `8px ${PX}`, 384, y + 6, C.grey); };
    box(36, 78, 'RESPIRATION');
    for (let x = 384; x < 560; x++) { const y = 82 + Math.round(14 * Math.sin((x - 384) / 176 * Math.PI * 2.2)); put(x, y, C.cyan); put(x, y + 1, C.cyanD); }
    rect(518, 70, 4, 4, C.white); txt('6', `22px ${AB}`, 590, 62, C.orange, 'center'); txt('/MIN', `8px ${PX}`, 590, 94, C.orange, 'center');
    box(122, 54, 'D0LL G4S SATURATION');
    { const zx = 384 + 8, zy = 128; for (let k = 0; k < 5; k++) put(zx + 5 - k, zy + 1 + k, C.grey); }   // slash the zero in D0LL
    rr(384, 146, 190, 12, 2, C.whiteS, C.black);
    UI.paint(UI.mask(g => g.fillRect(385, 147, 164, 10)), (x, y) => ((((x + y) >> 2) & 1) ? C.orange : C.orangeD)); txt('87%', `8px ${PX}`, 618, 148, C.orange, 'right');
    box(184, 70, 'HEART RATE');
    const ecg = x => { const t = (x - 384) % 60; return t < 20 ? 0 : t < 23 ? -(t - 20) * 7 : t < 26 ? (t - 26) * 4 + 0 : t < 29 ? (t - 26) * 3 : 0; };
    for (let x = 385; x < 560; x++) { const a0 = 226 + Math.round(ecg(x - 1)), a1 = 226 + Math.round(ecg(x)); for (let yy = Math.min(a0, a1); yy <= Math.max(a0, a1); yy++) put(x, yy, C.orange); }
    txt('48', `22px ${AB}`, 592, 206, C.orange, 'center');
    box(262, 50, 'NEURAL ACTIVITY');
    rect(384, 296, 234, 1, RED); txt('SUPPRESSED', `8px ${PX}`, 618, 282, RED, 'right');
    txt('STATUS: COMPLIANT', `8px ${PX}`, 378, 326, C.cyan);
    HD.present(c); CRT.apply(c, lt, 5);
  } };

  // 3 · containment tank: behind a riveted viewing window, orange gas haze, reflections and condensation
  // tank(c, lt, anim): anim = false draws the approved still; anim = true is the 20 s seamless breathing loop.
  // Loop timing: one breath every 10 s (6 per minute), two per loop. Inhale 0–4 s (an orange gas pulse climbs the hose
  // and the valve grille glows as it arrives), hold 4–5 s, exhale 5–10 s (pale vapour leaves the valve and rises).
  // Breathing lifts the shoulders, swells the chest and raises the head a pixel. The haze line drifts on sine waves
  // with whole cycles per 20 s, motes rise through the gas wrapping exactly once or twice per loop, and a condensation
  // drop runs down the glass once per breath (alternating sides). Everything repeats exactly at 20 s.
  const TAU = Math.PI * 2, LOOP = 20, BREATH = 10;
  const ease = x => 0.5 - 0.5 * Math.cos(Math.PI * clamp(x));
  const breathAt = p => (p < 4 ? ease(p / 4) : p < 5 ? 1 : 1 - ease((p - 5) / 5));
  const WX0 = 150, WY0 = 22, WX1 = 490, WY1 = 310, RAD = 26;
  const inWin = (x, y) => { const qx = Math.min(Math.max(x, WX0 + RAD), WX1 - RAD), qy = Math.min(Math.max(y, WY0 + RAD), WY1 - RAD); return Math.hypot(x - qx, y - qy) <= RAD; };
  const VAPOUR = Array.from({ length: 34 }, (_, k) => ({ b: 5.1 + 3.6 * k / 34 + 0.12 * hsh(k, 1, 5), vx: (hsh(k, 2, 5) - 0.5) * 26, vy: 6 + 6 * hsh(k, 3, 5), ph: TAU * hsh(k, 4, 5), r: 2.4 + 1.4 * hsh(k, 5, 5) }));
  const MOTES = Array.from({ length: 48 }, (_, k) => ({ x: WX0 + 10 + (WX1 - WX0 - 20) * hsh(k, 1, 9), n: hsh(k, 2, 9) > 0.6 ? 2 : 1, off: hsh(k, 3, 9), ph: TAU * hsh(k, 4, 9), a: 2 + 4 * hsh(k, 5, 9) }));
  const DROPS = [{ x: 186, y: 70 }, { x: 456, y: 58 }];
  // ── the 60 s story layer (scene cu3_seq): consent, face removal, inserted thoughts ──
  //   2.5–11.5 s  LTX consent dialog: FACIAL RELIEF REMOVAL, DO YOU CONSENT? [YES] [YES]; a cursor clicks YES
  //   15/25/35/45 s  on each exhale an orange beam sweeps down the head and the face flattens by a quarter
  //   16/26/36/51 s  first-person thoughts are typed onto the glass by an orange cursor; the last one stays
  // A wall panel tracks FACIAL RELIEF 100% → 0% → REDUNDANT. Ambient motion (breath, haze, motes, drops, vapour)
  // keeps its 20 s cycle underneath.
  const F8 = `8px ${PX}`;
  const STEPS = [15, 25, 35, 45], SCAN = 4;
  function faceAt(T) {
    let k = 0;
    for (const s0 of STEPS) {
      if (T >= s0 + SCAN) { k++; continue; }
      if (T >= s0) { const prog = ease((T - s0) / SCAN), old = 1 - 0.25 * k; return { k, old, nw: old - 0.25, scan: -6 + 136 * prog, pct: Math.round(100 * (old - 0.25 * prog)) }; }
      break;
    }
    const old = 1 - 0.25 * k; return { k, old, nw: old, scan: null, pct: Math.round(100 * old) };
  }
  const THOUGHTS = [[16, 'I AM COMFORTABLE.'], [26, 'I AM GRATEFUL.'], [36, 'I DO NOT NEED A FACE.'], [51, 'I AM RUBBERDOLL.', true]];
  const pointer = (px, py) => UI.paint(UI.mask(g => { g.beginPath(); g.moveTo(px, py); g.lineTo(px, py + 11); g.lineTo(px + 3, py + 8); g.lineTo(px + 5, py + 12);
    g.lineTo(px + 7, py + 11); g.lineTo(px + 5, py + 7); g.lineTo(px + 9, py + 7); g.closePath(); g.fill(); }), C.white, C.black, 1);
  function story(T, fs) {
    // wall panel: FACIAL RELIEF readout
    const PX0 = 512, PY0 = 96, PW = 120, PH = 78;
    rr(PX0, PY0, PW, PH, 2, C.black, C.orange);
    txt('FACIAL RELIEF', F8, PX0 + 8, PY0 + 7, C.orange);
    const done = T >= STEPS[3] + SCAN;
    txt(`${fs.pct}%`, `18px ${AB}`, PX0 + PW / 2, PY0 + 20, done ? RED : C.white, 'center');
    const status = T < 8 ? 'PENDING' : T < STEPS[0] ? 'APPROVED' : done ? 'REDUNDANT' : 'IN PROGRESS';
    txt(status, F8, PX0 + PW / 2, PY0 + 48, done ? RED : status === 'APPROVED' ? C.cyan : C.orangeL, 'center');
    for (let k = 0; k < 4; k++) {                                        // one segment per smoothing pass
      const fill = k < fs.k ? 1 : k === fs.k && fs.scan !== null ? (fs.scan + 6) / 136 : 0, x = PX0 + 8 + k * 27;
      rect(x, PY0 + 62, 24, 7, INK, C.orangeD); if (fill > 0) rect(x + 1, PY0 + 63, Math.max(1, Math.round(22 * fill)), 5, done ? RED : C.orange);
    }
    // consent dialog
    const DX = 196, DY = 158, DW = 248, DH = 92, open = clamp((T - 2.5) / 0.25), close = clamp((T - 11.2) / 0.25);
    if (open > 0 && close < 1) {
      const h = Math.round(DH * open * (1 - close)), y = DY + Math.round((DH - h) / 2);
      if (h < 4) rect(DX + 20, DY + DH / 2 - 1, DW - 40, 2, C.orangeL);
      else rr(DX, y, DW, h, 2, C.black, C.orange);
      if (h >= DH) {
        rect(DX + 1, DY + 1, DW - 2, 13, C.orange); txt('LTX CORP // CONSENT', F8, DX + 7, DY + 4, C.black);
        if (T < 8.5) {
          txt('PROCEDURE:', F8, DX + 12, DY + 22, C.grey); txt('FACIAL RELIEF REMOVAL', F8, DX + 12, DY + 34, C.white);
          txt('DO YOU CONSENT?', F8, DX + 12, DY + 50, C.orange);
          const pressed = T >= 8.0;
          [[DX + 36, pressed], [DX + 140, false]].forEach(([bx, on]) => {
            rr(bx, DY + 66, 72, 16, 2, on ? C.orange : C.black, on ? C.orangeL : C.orange);
            txt('YES', F8, bx + 36, DY + 70, on ? C.black : C.orange, 'center');
          });
        } else {
          txt('CONSENT RECORDED.', F8, DX + DW / 2, DY + 36, C.white, 'center');
          txt('THANK YOU, RUBBERDOLL.', F8, DX + DW / 2, DY + 56, C.orange, 'center');
        }
      }
      if (T >= 5 && T < 9.6) {                                           // the cursor that answers for them
        const q = ease((T - 5) / 2.5), tx = DX + 66, ty = DY + 74 + (T >= 8.0 && T < 8.3 ? 1 : 0);
        pointer(Math.round(430 + (tx - 430) * q), Math.round(300 + (ty - 300) * q));
      }
    }
    // inserted thoughts, typed onto the glass by LTX's cursor
    for (const [t0, str, keep] of THOUGHTS) {
      const tt = T - t0; if (tt < 0) continue;
      const tType = str.length / 12, hold = 3.2;
      let n = Math.min(str.length, Math.floor(tt * 12)), typing = n < str.length;
      if (!keep && tt > tType + hold) { n = str.length - Math.floor((tt - tType - hold) * 30); typing = true; if (n < 0) continue; }
      const x0 = 320 - str.length * 4, y = 284;
      if (n > 0) UI.paint(UI.text(str.slice(0, n), F8, x0, y, 'left'), C.white, C.black, 1);
      if (typing || Math.floor(T * 4) % 2 === 0) rect(x0 + n * 8 + 1, y, 7, 8, C.orange);
    }
  }

  function tank(c, lt, anim, seq) {
    const t = ((lt % LOOP) + LOOP) % LOOP, p = t % BREATH, fs = seq ? faceAt(lt) : null;
    const breath = anim ? breathAt(p) : 0.6;
    const gas = anim ? (p < 4 ? -0.12 + 1.2 * (p / 4) : -1) : 0.45;
    const glow = anim ? clamp(1 - Math.abs(p - 4.3) / 1.1) ** 1.5 : 0;
    const cx = 320, s = 0.94, y0 = 40 - (anim ? breath * 1.2 : 0);
    const face = !fs ? 1 : fs.scan === null ? fs.old : (vv => (vv < fs.scan ? fs.nw : fs.old));
    const v = vessel({ cx, y0, s, breath, gas, glow, face });
    const WV = new Float32Array(W);                                      // the drifting top of the gas
    if (anim) for (let x = 0; x < W; x++) WV[x] = 10 * Math.sin(x * 0.035 + TAU * t / 10) + 6 * Math.sin(x * 0.08 - TAU * t / 5) + 4 * Math.sin(x * 0.013 + TAU * t / 20);
    HD.fill((x, y) => {                                                  // dark wall, then the chamber
      if (!inWin(x + 0.5, y + 0.5)) return pick([C.black, hex('#1a0b03'), RUST], 0.35 + 0.3 * HD.hash(x >> 3, y >> 3, 4), x, y);
      const haze = clamp((y - 150 + WV[x]) / 190);
      return pick(PRISON.R, 0.08 + 0.45 * haze * haze, x, y);
    });
    blit(v, (vv, i, x, y) => {                                          // the figure seen through orange gas (window only)
      if (!inWin(x + 0.5, y + 0.5)) return null;
      const haze = clamp((y - 230 + WV[x]) / 120) * 0.45;
      return bayer(x, y) < haze ? (vv.L[i] > 0.5 ? C.orangeL : vv.L[i] > 0.15 ? C.orange : PRISON.R[3]) : vv.col[i];
    });
    if (fs && fs.scan !== null) {                                       // the smoothing beam sweeping down the head
      const yl = Math.round(y0 + fs.scan * s);
      for (let x = Math.floor(cx - 46 * s); x <= cx + 46 * s; x++) for (const [dy, col, th] of [[0, C.orangeL, 1], [-1, C.orange, 1], [-2, C.orangeD, 0.5], [-3, C.orangeD, 0.25]]) {
        const y = yl + dy; if (y < 0 || y >= H || !v.col[y * W + x]) continue; if (bayer(x, y) < th) put(x, y, col);
      }
    }
    if (anim) {
      for (const m of MOTES) {                                           // motes rising through the gas
        const k = ((t / LOOP) * m.n + m.off) % 1, yy = Math.round(WY1 - 4 - k * 170), xx = Math.round(m.x + m.a * Math.sin(TAU * t / 10 + m.ph));
        const a = clamp((yy - (WY1 - 174)) / 80) * clamp((WY1 - 4 - yy) / 16);
        if (a > 0.2 && inWin(xx + 0.5, yy + 0.5)) put(xx, yy, a > 0.65 ? C.orangeL : C.orange);
      }
      const vx0 = cx + 2 * s, vy0 = y0 + 102 * s;
      for (const q of VAPOUR) {                                          // exhaled vapour from the valve
        const age = (((p - q.b) % BREATH) + BREATH) % BREATH, LIFE = 3.2; if (age >= LIFE) continue;
        const px = vx0 + q.vx * age + 2 * Math.sin(age * 2.2 + q.ph), py = vy0 + 2 - q.vy * age - 1.5 * age * age, r = q.r + 3.2 * age;
        const a0 = 0.95 * (1 - age / LIFE) ** 1.3 * clamp(age / 0.25);
        for (let y = Math.floor(py - r); y <= py + r; y++) for (let x = Math.floor(px - r); x <= px + r; x++) {
          const d = Math.hypot(x + 0.5 - px, y + 0.5 - py); if (d > r || !inWin(x + 0.5, y + 0.5)) continue;
          const al = a0 * (1 - d / r), b = bayer(x, y);
          if (b < al) put(x, y, b < al * 0.35 ? C.whiteS : b < al * 0.7 ? C.grey : GREYD);
        }
      }
    }
    for (let y = WY0; y < WY1; y++) for (let x = WX0; x < WX1; x++) {   // glass reflections and condensation
      if (!inWin(x + 0.5, y + 0.5)) continue;
      const d1 = (x + y * 0.8) - 300, d2 = (x + y * 0.8) - 330;
      if ((Math.abs(d1) < 6 && bayer(x, y) < 0.35) || (Math.abs(d2) < 2 && bayer(x, y) < 0.5)) put(x, y, C.whiteS);
      if (y > 250 && hsh(x, y, 7) > 0.994) put(x, y, C.grey);
    }
    if (anim) {                                                          // a condensation drop runs down the glass once per breath
      const D = DROPS[Math.floor(t / BREATH) % 2], y1 = WY1 - 14;
      if (p >= 1) {
        const yh = Math.round(D.y + (y1 - D.y) * clamp((p - 1) / 5.5) ** 1.6), fade = 1 - clamp((p - 6.5) / 3);
        for (let y = D.y; y <= yh; y++) {
          const x = D.x + (hsh(D.x, y >> 3, 11) > 0.7 ? 1 : 0), a = fade * (0.25 + 0.35 * (y - D.y) / Math.max(1, yh - D.y));
          if (bayer(x, y) < a) put(x, y, C.grey);
        }
        if (fade > 0.6) { const x = D.x + (hsh(D.x, yh >> 3, 11) > 0.7 ? 1 : 0); put(x, yh, C.white); put(x + 1, yh, C.whiteS); put(x, yh + 1, C.whiteS); put(x + 1, yh + 1, C.grey); }
      }
    }
    // riveted steel frame and stencilled plate
    const ring = UI.mask(g => { g.beginPath(); g.roundRect(WX0 - 14, WY0 - 14, WX1 - WX0 + 28, WY1 - WY0 + 28, RAD + 14); g.roundRect(WX0, WY0, WX1 - WX0, WY1 - WY0, RAD); g.fill('evenodd'); });
    UI.paint(ring, (x, y) => { const q = (y - WY0 + 14) / (WY1 - WY0 + 28); return bayer(x, y) < 0.5 - q * 0.4 ? C.grey : GREYD; }, C.black, 1);
    for (let k = 0; k < 10; k++) for (const [x, y] of [[WX0 - 7, WY0 + 30 + k * 30], [WX1 + 7, WY0 + 30 + k * 30]]) if (y < WY1 - 20) UI.paint(UI.mask(g => { g.beginPath(); g.arc(x, y, 2.2, 0, Math.PI * 2); g.fill(); }), C.whiteS, C.black, 1);
    rr(250, 4, 140, 14, 2, C.orange, C.black); txt('VESSEL 0412', `8px ${PX}`, 320, 7, C.black, 'center');
    UI.paint(UI.mask(g => g.fillRect(WX0 + 10, WY1 + 18, WX1 - WX0 - 20, 8)), (x, y) => ((((x + y) >> 3) & 1) ? C.orange : C.black), C.black, 1);
    txt('RUBBERDOLL - CONTENTS COMPLIANT', `8px ${PX}`, 320, 344, C.orange, 'center');
    if (seq) story(lt, fs);
    HD.present(c); CRT.apply(c, lt, 5);
  }
  SCENES['cu_3'] = { draw(c, lt) { tank(c, lt, false); } };
  SCENES['cu3_loop'] = { draw(c, lt) { tank(c, lt, true); } };
  SCENES['cu3_seq'] = { draw(c, lt) { tank(c, lt, true, true); } };

  // 4 · monitor prop on chroma green: a CRT security monitor showing the amber feed (keyable)
  SCENES['cu_4'] = { draw(c, lt) {
    const fr = Math.round(lt * 30);
    MED.bg();
    const CASE = hex('#3a3632'), CASEL = hex('#57514b');
    rr(118, 300, 300, 30, 4, CASE, C.black);                            // stand
    rr(96, 16, 448, 290, 14, (x, y) => (y < 24 || x < 104) && bayer(x, y) < 0.6 ? CASEL : CASE, C.black);
    rr(116, 34, 330, 252, 10, INK, C.black);                            // bezel
    const SX0 = 128, SY0 = 44, SX1 = 434, SY1 = 276;
    const v = vessel({ cx: 292, y0: 52, s: 0.6, breath: 0.6, gas: 0.45 });
    const scr = UI.rrect(SX0, SY0, SX1 - SX0, SY1 - SY0, 16);
    UI.paint(scr, (x, y) => {
      const i = y * W + x, cv = 1 - 0.9 * (((x - 281) / 153) ** 4 + ((y - 160) / 116) ** 4);   // curved tube falloff
      const l = v.col[i] ? 0.06 + v.L[i] * 0.95 : ((y % 12 === 0) || ((x + ((y / 12) | 0) % 2 * 14) % 28 === 0) ? 0.08 : 0.16);
      return amber((l + 0.06 * (hsh(x, y, fr) - 0.5)) * cv, x, y);
    });
    txt('CAM 07', `8px ${PX}`, 142, 54, AMBER[8]); UI.paint(UI.mask(g => { g.beginPath(); g.arc(400, 58, 3, 0, Math.PI * 2); g.fill(); }), RED); txt('REC', `8px ${PX}`, 424, 54, AMBER[8], 'right');
    txt('RUBBERDOLL', `8px ${PX}`, 142, 258, AMBER[8]);
    for (let y = SY0; y < SY1; y++) for (let x = SX0; x < SX1; x++) { const d = (SX1 - x) - (SY1 - y) * 0.9; if (scr[y * W + x] && y > SY1 - 46 && ((d > 14 && d < 22) || (d > 28 && d < 31)) && bayer(x, y) < 0.22 * (1 - (SY1 - y) / 46)) put(x, y, C.whiteS); }   // glass glare, lower-right corner
    for (const [y, r] of [[72, 11], [112, 11]]) { UI.paint(UI.mask(g => { g.beginPath(); g.arc(494, y, r, 0, Math.PI * 2); g.fill(); }), (x, yy) => (x < 492 && yy < y - 2 ? C.grey : GREYD), C.black, 1); rect(493, y - r + 2, 2, 7, C.black); }
    for (let k = 0; k < 8; k++) rect(470, 150 + k * 8, 48, 3, INK);       // speaker grille
    UI.paint(UI.mask(g => { g.beginPath(); g.arc(504, 238, 3, 0, Math.PI * 2); g.fill(); }), C.orange, C.black, 1);   // power LED
    rr(250, 290, 60, 11, 2, C.orange, C.black); txt('LTX', `8px ${PX}`, 280, 292, C.black, 'center');
    HD.present(c); CRT.apply(c, lt, 5, [SX0 * 3, SY0 * 3, SX1 * 3, SY1 * 3]);
  } };

  // 5 · thermal camera: heat-map palette, glowing breath plume, crosshair, scale and readouts
  const THERM = HD.ramp(['#050302', '#1a0b03', '#3d1704', '#6e2a05', '#a8420a', '#e0600f', '#ff8418', '#ffad3d', '#ffd98a', '#fff4dc']);
  SCENES['cu_5'] = { draw(c, lt) {
    const fr = Math.round(lt * 30), v = vessel({ cx: 330, y0: 30, s: 1, breath: 0.6, gas: 0.45, plume: 1 });
    HD.fill((x, y) => { const i = y * W + x, h = v.heat[i]; return pick(THERM, clamp((v.col[i] ? h : Math.max(h, 0.06 + 0.05 * hsh(x >> 2, y >> 2, 3))) + 0.04 * (hsh(x, y, fr) - 0.5)), x, y); });
    const HUD = C.whiteS;
    txt('THERMAL // CAM 07', `8px ${PX}`, 24, 20, HUD); txt('SUBJECT: RUBBERDOLL', `8px ${PX}`, 24, 32, HUD);
    UI.paint(UI.mask(g => { g.lineWidth = 1.2; g.beginPath(); g.arc(330, 250, 22, 0, Math.PI * 2); g.moveTo(330, 222); g.lineTo(330, 236); g.moveTo(330, 264); g.lineTo(330, 278); g.moveTo(302, 250); g.lineTo(316, 250); g.moveTo(344, 250); g.lineTo(358, 250); g.stroke(); }), HUD);
    for (let y = 60; y < 300; y++) rect(596, y, 12, 1, pick(THERM, 1 - (y - 60) / 240, 0, y));
    rect(595, 59, 14, 1, HUD); rect(595, 300, 14, 1, HUD); txt('40C', `8px ${PX}`, 590, 56, HUD, 'right'); txt('18C', `8px ${PX}`, 590, 296, HUD, 'right');
    txt('CORE  35.1C', `8px ${PX}`, 24, 300, HUD); txt('RESP  6/MIN', `8px ${PX}`, 24, 312, HUD); txt('STATUS DORMANT', `8px ${PX}`, 24, 324, HUD);
    HD.present(c); CRT.apply(c, lt, 5);
  } };
})();
