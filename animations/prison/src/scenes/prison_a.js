// Prison base spiral · Test A · "Conditioning screen": a hard-edged two-arm spiral in orange and black on an
// institutional CRT. Even-width (Archimedean) bands feel mechanical rather than natural; scanlines, a
// darkened tube edge and a slow refresh bar rolling down the screen make it read as broadcast hardware.
// Loop: the spiral turns inward exactly 4 bands per 60 s (one every 15 s) and the refresh bar passes 3 times
// per 60 s. Whole numbers per loop → seamless.
SCENES['prison_a'] = (() => {
  const { clamp, pick, TAU } = HD, R = PRISON.R;
  const CX = HD.LW / 2, CY = HD.LH / 2, RMAX = Math.hypot(CX, CY), BW = 34;
  return {
    draw(c, lt) {
      const ph = 4 * lt / 60, bar = ((3 * lt / 60) % 1) * (HD.LH + 60) - 30;
      HD.fill((x, y) => {
        const dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
        const s = Math.sin(TAU * (2 * a / TAU + r / BW - ph));
        const on = clamp(0.5 + s * (BW / TAU) / 1.4);                       // crisp edge, ~1.5 px transition
        const nx = dx / CX, ny = dy / CY, tube = clamp(1 - 0.55 * Math.pow(nx * nx * 0.8 + ny * ny, 1.6));   // CRT corners
        let lvl = on * (0.5 + 0.38 * Math.exp(-r / 70)) * tube;
        lvl += 0.06 * Math.exp(-(((y - bar) / 12) ** 2));                   // rolling refresh bar
        if (y & 1) lvl *= 0.8;                                              // scanlines
        return pick(R, clamp(lvl + 0.02), x, y);
      });
      HD.present(c);
    },
  };
})();
