// Prison base spiral · Test C · "The Iris": a mechanical eye. Fine striations in the orange iris twist into
// a spiral and turn slowly; the black pupil dilates and contracts like slow breathing; faint rings pulse
// outward across the dark, like a broadcast or a scan. Reads as being watched.
// Loop: the iris striations turn once per 60 s (the deeper crypt pattern turns once the other way), the pupil
// breathes every 10 s, and the scan rings advance exactly 5 places per 60 s. Whole numbers per loop → seamless.
SCENES['prison_c'] = (() => {
  const { clamp, pick, TAU } = HD, R = PRISON.R;
  const CX = HD.LW / 2, CY = HD.LH / 2, RI = 128, RING = 34;
  return {
    draw(c, lt) {
      const rot = TAU * lt / 60, rp = 36 + 6 * Math.sin(TAU * lt / 10), ph = 5 * lt / 60;
      HD.fill((x, y) => {
        const dx = x + 0.5 - CX, dy = y + 0.5 - CY, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
        let lvl;
        if (r < rp) {                                                        // pupil, with a cold glint
          lvl = 0.02 + (Math.hypot(dx + rp * 0.38, dy + rp * 0.4) < rp * 0.16 ? 0.85 : 0);
        } else if (r < RI) {
          const q = (r - rp) / (RI - rp);                                    // 0 at the pupil, 1 at the rim
          const v = 56 * (a - rot) / TAU + 7 * Math.log(r), stri = 0.5 + 0.5 * Math.cos(TAU * v);
          const v2 = 13 * (a + rot) / TAU - 3 * Math.log(r), crypt = 0.5 + 0.5 * Math.cos(TAU * v2);
          lvl = 0.3 + 0.38 * stri * (0.6 + 0.4 * crypt);
          lvl += 0.3 * Math.exp(-(((q - 0.18) / 0.07) ** 2));               // bright collar around the pupil
          lvl -= 0.38 * Math.exp(-(((q - 1) / 0.09) ** 2));                 // dark limbal ring
          if (q < 0.04) lvl = 0.1;                                           // pupil edge
        } else {
          const u = r / RING - ph, f = u - Math.floor(u), d = Math.min(f, 1 - f) * RING;
          lvl = 0.03 + clamp(1 - d / 1.2) * 0.22 * clamp(1 - (r - RI) / 260) + 0.1 * Math.exp(-(r - RI) / 14);
        }
        return pick(R, clamp(lvl), x, y);
      });
      HD.present(c);
    },
  };
})();
