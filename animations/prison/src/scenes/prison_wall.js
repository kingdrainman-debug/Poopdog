// Prison base spiral · style 5 · "Video wall": spiral A spread across a 4×3 bank of CRT monitors in a dark
// frame, the way a control room or cell-block wall would show it. The image is continuous across the wall
// but the bezels hide strips of it. Each tube is its own: slightly different brightness and warmth, its own
// rounded corners, vignette and scanlines. One runs a fraction of a second behind the others; one has a
// faulty refresh bar; one drops to static now and then. Small power LEDs sit under each screen.
// Loop: the spiral is the shared one (4 bands per 60 s); faults happen at fixed times inside the minute and
// the lagging screen's offset is constant, so everything repeats at 60 s.
SCENES['prison_wall'] = (() => {
  const { clamp, pick, hash, put } = HD, R = PRISON.R;
  const MW = 150, MH = 110, GX = 8, GY = 8, X0 = 8, Y0 = 7, RC = 7;
  const mons = [];
  for (let j = 0; j < 3; j++) for (let i = 0; i < 4; i++) {
    const id = j * 4 + i;
    mons.push({ i, j, x: X0 + i * (MW + GX), y: Y0 + j * (MH + GY),
      gain: 0.82 + 0.28 * hash(id, 1, 51), warm: (hash(id, 2, 51) - 0.5) * 0.12,
      lag: id === 6 ? 0.45 : 0, badBar: id === 9, staticAt: id === 3 ? [3.1, 21.4, 39.0, 50.2] : [] });
  }
  const STATIC = 0.6;
  function monAt(x, y) {
    const i = Math.floor((x - X0) / (MW + GX)), j = Math.floor((y - Y0) / (MH + GY));
    if (i < 0 || i > 3 || j < 0 || j > 2) return null;
    const m = mons[j * 4 + i], u = x - m.x, v = y - m.y;
    if (u < 0 || v < 0 || u >= MW || v >= MH) return null;
    const cu = Math.min(u, MW - 1 - u), cv = Math.min(v, MH - 1 - v);            // rounded tube corners
    if (cu < RC && cv < RC && Math.hypot(RC - cu, RC - cv) > RC) return null;
    return { m, u, v };
  }
  return {
    draw(c, lt) {
      const fr = Math.round(lt * 30) % 1800;
      HD.fill((x, y) => {
        const hit = monAt(x, y);
        if (!hit) {                                                                  // the frame and bezels
          const inner = monAt(x + 1, y + 1) || monAt(x, y + 1) || monAt(x + 1, y);
          const outer = monAt(x - 1, y - 1) || monAt(x, y - 1) || monAt(x - 1, y);
          return inner ? R[2] : outer ? R[0] : R[1];
        }
        const { m, u, v } = hit, t = lt - m.lag;
        if (m.staticAt.some(s => lt >= s && lt < s + STATIC))                       // dropped signal: snow
          return pick(R, clamp(0.12 + hash(x, y * 613 + fr, 21) * 0.55) * ((y & 1) ? 0.7 : 1), x, y);
        const p = PRISON.spiral(x + 0.5, y + 0.5, t);
        const nx = (u - MW / 2) / (MW / 2), ny = (v - MH / 2) / (MH / 2);
        const tube = clamp(1 - 0.5 * Math.pow(nx * nx * 0.9 + ny * ny, 1.8));
        let lvl = (0.03 + p.lvl * m.gain) * tube + m.warm;
        if (m.badBar) lvl += 0.12 * Math.exp(-(((v - ((lt * 0.4) % 1) * (MH + 40) + 20) / 6) ** 2));
        if (y & 1) lvl *= 0.8;
        return pick(R, clamp(lvl), x, y);
      });
      for (const m of mons) {                                                        // power LEDs
        const blink = m.staticAt.length && (Math.floor(lt * 2) & 1);
        const lx = m.x + MW - 10, ly = m.y + MH + 3;
        put(lx, ly, blink ? R[2] : R[6]); put(lx + 1, ly, blink ? R[2] : R[6]);
      }
      HD.present(c);
    },
  };
})();
