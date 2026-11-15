// Prison base spiral · style 2 · "Surveillance monitor": spiral A on a tired, much-reused security monitor.
// Chunky pixels (a 320×180 grid, 6×6 on screen), heavy scanlines, lifted blacks and crushed highlights,
// per-frame grain, a VHS-style tracking band drifting down the screen (rows slip sideways and fill with snow)
// and brief signal tears where blocks of the picture jump sideways for a few frames.
// Loop: the spiral is the shared one (4 bands per 60 s); the tracking band takes 10 s per pass; grain and
// tears are keyed to the frame number mod 1800 and fixed times inside the minute. Everything repeats at 60 s.
SCENES['prison_surv'] = (() => {
  const { clamp, pick, hash } = HD, R = PRISON.R;
  const RD = R.slice(1, 8);                                     // lifted blacks, no top highlight: a tired tube
  const TEARS = [2.3, 17.8, 31.2, 44.6, 52.1], TEAR = 0.14;
  return {
    draw(c, lt) {
      const fr = Math.round(lt * 30) % 1800;
      const band = ((lt / 10) % 1) * 230 - 25;                  // tracking band, in coarse rows
      const bar = ((3 * lt / 60) % 1) * 240 - 30;               // slow refresh bar
      const tear = TEARS.find(s => lt >= s && lt < s + TEAR), tf = tear === undefined ? -1 : Math.floor((lt - tear) * 30);
      HD.fill((x, y) => {
        const xs = x >> 1, ys = y >> 1;                         // chunky 320×180 pixels
        const inBand = Math.exp(-(((ys - band) / 4) ** 2));
        let off = inBand * (5 + 7 * (hash(ys, fr, 3) - 0.5));
        if (tf >= 0) { const blk = Math.floor(ys / 10); if (hash(blk, tear * 100, 8) > 0.45) off += (hash(blk, tf, 9) - 0.5) * 60; }
        const p = PRISON.spiral((xs + off) * 2 + 1, ys * 2 + 1, lt, 2.6);
        const nx = (xs - 160) / 160, ny = (ys - 90) / 90, tube = clamp(1 - 0.6 * Math.pow(nx * nx * 0.8 + ny * ny, 1.5));
        let lvl = (0.08 + 0.85 * p.lvl) * tube;
        lvl += 0.06 * Math.exp(-(((ys - bar) / 8) ** 2));
        lvl += (hash(xs, ys * 977 + fr, 5) - 0.5) * 0.17;            // grain
        lvl += inBand * (hash(xs, ys + fr * 331, 6) - 0.45) * 0.7;   // snow in the tracking band
        if (tf >= 0) lvl += 0.07;
        if (y & 1) lvl *= 0.68;                                      // heavy scanlines
        return pick(RD, clamp(lvl), xs, ys);
      });
      HD.present(c);
    },
  };
})();
