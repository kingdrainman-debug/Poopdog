// Prison base spiral · Test B · "Corridor": a twisting tunnel of thin orange square rings on black, like an
// old vector display. Each ring is turned a little more than the one in front of it, so the corridor
// spirals; rings slide away from the viewer into a faint amber light at the vanishing point, pulling the
// eye in. Corner ribs run down the corridor.
// Loop: rings recede exactly 6 places per 60 s (one every 10 s) and the corridor turns once per 60 s.
// Whole numbers per loop → seamless.
SCENES['prison_b'] = (() => {
  const { clamp, pick, TAU } = HD, R = PRISON.R;
  const CX = HD.LW / 2, CY = HD.LH / 2, F = 90, P = 8, TW = 0.22, ZMAX = 9;
  return {
    draw(c, lt) {
      const ph = 6 * lt / 60, spin = TAU * lt / 60;
      HD.fill((x, y) => {
        const dx = x + 0.5 - CX, dy = y + 0.5 - CY, r0 = Math.max(1, Math.hypot(dx, dy));
        // each ring is a rigid square turned by its own depth: solve depth ↔ rotation by fixed-point iteration
        let z = F / r0, qx = dx, qy = dy, rs = r0;
        for (let it = 0; it < 4; it++) {
          const rot = z * TW + spin, cr = Math.cos(rot), sr = Math.sin(rot);
          qx = dx * cr + dy * sr; qy = -dx * sr + dy * cr;
          rs = Math.max(1, Math.pow(Math.pow(Math.abs(qx), P) + Math.pow(Math.abs(qy), P), 1 / P));
          z = F / rs;
        }
        const u = z - ph, f = u - Math.floor(u), dU = Math.min(f, 1 - f);
        const w = F / (rs * rs);                                            // one pixel, in ring units
        const fog = clamp(1 - z / ZMAX) ** 1.6;                             // far rings fade out before they crowd
        let lvl = clamp(1 - dU / (w * 1.1)) * 0.85 + Math.exp(-dU / (w * 5)) * 0.22;   // line + glow
        const rib = Math.abs(Math.abs(qx) - Math.abs(qy)) < 0.9 ? 0.32 : 0;  // corridor corners
        lvl = Math.max(lvl, rib) * fog;
        lvl += 0.55 * Math.exp(-r0 / 9) + 0.12 * Math.exp(-r0 / 40);        // the light at the end
        return pick(R, clamp(lvl), x, y);
      });
      HD.present(c);
    },
  };
})();
