// Test A · "Soft swirl": a classic two-arm logarithmic spiral in the bubbles' own pinks, light and airy.
// Bands are soft (a cosine profile dithered across five tones) rather than hard stripes, so nothing flickers.
// Loop: the spiral's phase advances exactly 3 bands per 60 s (one every 20 s, read as a slow inward turn);
// a faint brightness "breath" repeats every 10 s (6 per minute). Both are whole numbers per loop → seamless.
SCENES['spiral_a'] = (() => {
  const { ramp, clamp, pick, TAU } = HD;
  const R = ramp(['#d9468f', '#ef68ad', '#ff8cc6', '#ffaad5', '#ffc4e1']);   // bubble pinks: rim → highlight
  const CX = HD.LW / 2, CY = HD.LH / 2, RMAX = Math.hypot(CX, CY);
  return {
    draw(c, lt) {
      const ph = 3 * lt / 60, breath = 0.035 * Math.sin(TAU * lt / 10);
      HD.fill((x, y) => {
        const dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
        const v = 2 * a / TAU + 1.55 * Math.log(r + 6) - ph;
        const band = 0.5 + 0.5 * Math.cos(TAU * v);                       // 0..1 across one band
        const edge = r / RMAX;                                             // gentle vignette toward the corners
        const glow = 0.28 * Math.exp(-r / 34);                             // soft light at the eye
        return pick(R, clamp(0.18 + 0.55 * band - 0.32 * edge * edge + glow + breath), x, y);
      });
      HD.present(c);
    },
  };
})();
