// Test B · "Rose tunnel": a deeper four-arm vortex that seems to draw you inward, from dark rose at the
// centre out to the bubbles' pinks at the edges. Darker overall, so the pastel bubbles and the gold
// stopwatch stand out strongly in front of it.
// Loop: phase advances exactly 4 bands per 60 s (one every 15 s, a slow pull inward); a faint breath every
// 10 s. Whole numbers per loop → seamless.
SCENES['spiral_b'] = (() => {
  const { ramp, clamp, pick, TAU } = HD;
  // darker extensions of the bubble outline hue, then the bubble pinks
  const R = ramp(['#22050f', '#3c0a20', '#5a0d32', '#7a1048', '#a8256a', '#d9468f', '#ef68ad']);
  const CX = HD.LW / 2, CY = HD.LH / 2, RMAX = Math.hypot(CX, CY);
  return {
    draw(c, lt) {
      const ph = 4 * lt / 60, breath = 0.03 * Math.sin(TAU * lt / 10);
      HD.fill((x, y) => {
        const dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
        const v = 4 * a / TAU + 2.4 * Math.log(r + 4) - ph;
        const band = 0.5 + 0.5 * Math.cos(TAU * v);
        const depth = Math.pow(clamp(r / (RMAX * 0.9)), 0.55);            // dark, distant centre
        return pick(R, clamp(0.08 + depth * (0.42 + 0.42 * band) + breath), x, y);
      });
      HD.present(c);
    },
  };
})();
