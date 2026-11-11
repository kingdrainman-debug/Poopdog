// Test C · "Bloom": a sunflower (phyllotaxis) spiral of soft pink pixel dots on a pastel glow. Each dot sits
// at the golden angle from the last, which makes interlocking spiral arms; colouring every third dot deeper
// picks those arms out. Ties to the bubble film without competing with it (flat dots, no outlines).
// Loop: every dot slides outward to where its neighbour was, exactly 3 places per 60 s, so new dots are born
// at the centre and the arms turn slowly. Colour follows the dot (id mod 3) and 3 places ≡ 0 mod 3, so
// positions and colours both match at 60 s; a faint breath every 10 s. Whole numbers per loop → seamless.
// (Direction study only; test A was chosen.)
SCENES['spiral_c'] = (() => {
  const { ramp, hex, clamp, pick, put, TAU } = HD;
  const BG = ramp(['#ef68ad', '#ff8cc6', '#ffaad5', '#ffc4e1']);
  const DOT = [hex('#d9468f'), hex('#ef68ad'), hex('#ffc4e1')];
  const CX = HD.LW / 2, CY = HD.LH / 2, RMAX = Math.hypot(CX, CY);
  const GA = Math.PI * (3 - Math.sqrt(5)), C = 11.5, N = Math.ceil((RMAX + 12) ** 2 / (C * C));
  return {
    draw(c, lt) {
      const s = 3 * lt / 60, breath = 0.03 * Math.sin(TAU * lt / 10);
      HD.fill((x, y) => {
        const r = Math.hypot(x + 0.5 - CX, y + 0.5 - CY);
        return pick(BG, clamp(0.95 - 0.75 * (r / RMAX) + breath), x, y);
      });
      // dot j sits at k = j + f along the sunflower; at f → 1 every dot reaches its outer neighbour's place
      const base = Math.floor(s), f = s - base;
      for (let j = 0; j < N; j++) {
        const k = j + f; if (k < 0.15) continue;
        const id = j - base;                                   // stays with the dot as it travels outward
        const r = C * Math.sqrt(k), a = k * GA, x = CX + Math.cos(a) * r, y = CY + Math.sin(a) * r;
        const rad = 0.9 + r * 0.016, col = (((id % 3) + 3) % 3 === 0) ? DOT[0] : DOT[1];
        const x0 = Math.floor(x - rad - 1), x1 = Math.ceil(x + rad + 1), y0 = Math.floor(y - rad - 1), y1 = Math.ceil(y + rad + 1);
        for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
          const d = Math.hypot(px + 0.5 - x, py + 0.5 - y);
          if (d <= rad) put(px, py, (rad > 2.5 && Math.hypot(px + 0.5 - x + rad * 0.35, py + 0.5 - y + rad * 0.35) < rad * 0.3) ? DOT[2] : col);
        }
      }
      HD.present(c);
    },
  };
})();
