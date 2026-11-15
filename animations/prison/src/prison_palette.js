// prison_palette.js · shared pieces for every layer of the prison film.
// R: one colour ramp, near-black through rust and orange to a hot amber; every prison layer picks its tones
//    from it so the layers composite as one world.
// spiral(x, y, lt): the chosen base spiral (test A) as a field, so each screen style (CRT, surveillance
//    monitor, text terminal, video wall…) shows exactly the same spiral and motion. x, y are in 640×360 art
//    pixels. Two even-width arms; turns inward exactly 4 bands per 60 s, so it loops seamlessly.
window.PRISON = (() => {
  const R = HD.ramp(['#050302', '#1a0b03', '#3d1704', '#6e2a05', '#a8420a', '#e0600f', '#ff8418', '#ffad3d', '#ffd98a']);
  const CX = HD.LW / 2, CY = HD.LH / 2, BW = 34, TAU = Math.PI * 2;
  function spiral(x, y, lt, edge = 1.4) {
    const dx = x - CX, dy = y - CY, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    const s = Math.sin(TAU * (2 * a / TAU + r / BW - 4 * lt / 60));
    const on = Math.min(1, Math.max(0, 0.5 + s * (BW / TAU) / edge));   // crisp band edge
    return { on, lvl: on * (0.5 + 0.38 * Math.exp(-r / 70)), r, dx, dy };
  }
  return { R, spiral, CX, CY };
})();
