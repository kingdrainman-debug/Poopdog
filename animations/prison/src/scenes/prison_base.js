// Prison base spiral · final · option A, revised: the hard-edged two-arm orange-and-black conditioning
// spiral, now clean (no scanlines, no refresh bar), turning much faster, under a strong vignette that falls
// to near-black in the corners. Fine HD pixels (640×360 art grid, ×3).
// Loop: 30 s. The spiral turns inward exactly 20 bands per 30 s (one every 1.5 s); nothing else moves, so the
// frame at 30 s is the frame at 0.
SCENES['prison_base'] = (() => {
  const { clamp, pick, TAU } = HD, R = PRISON.R;
  const CX = HD.LW / 2, CY = HD.LH / 2, BW = 34, LOOP = 30, BANDS = 20;
  return {
    draw(c, lt) {
      const ph = BANDS * lt / LOOP;
      HD.fill((x, y) => {
        const dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
        const s = Math.sin(TAU * (2 * a / TAU + r / BW - ph));
        const on = clamp(0.5 + s * (BW / TAU) / 1.4);                       // crisp edge, ~1.5 px transition
        const nx = dx / CX, ny = dy / CY, vig = clamp(1 - 0.62 * Math.pow(nx * nx + ny * ny, 1.25));   // vignette
        const lvl = on * (0.52 + 0.38 * Math.exp(-r / 70)) * vig;
        return pick(R, clamp(lvl + 0.02), x, y);
      });
      HD.present(c);
    },
  };
})();
