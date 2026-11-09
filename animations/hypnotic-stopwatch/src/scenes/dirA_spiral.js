// Direction A · "Spiral": frontal and symmetrical. The watch hangs from an off-screen pivot straight above
// and swings across a palette-cycled hypnosis spiral whose eye sits exactly at the watch's resting point.
SCENES['dirA_spiral'] = (() => {
  const { ramp, hex, pick, bayer, clamp, TAU } = SW;
  const PAL = {
    crimson: ramp(['#2a0710', '#5a0e1a', '#8f1824', '#c22a32', '#e04a3c']),
    cream: ramp(['#3e221c', '#7a4c3a', '#c4946e', '#ecca9c', '#fbecd0']),
    gold: ramp(['#4a2a08', '#8a5410', '#c88a1c', '#f0c040', '#fff2a8']),
    face: ramp(['#b39a74', '#e0cca4', '#f8eed8']),
    ink: hex('#24120c'), red: hex('#c41e28'), glass: hex('#ffffff'), outline: hex('#170806'),
  };
  const PIV = [0, -205], LEN = 182, EYE = [0, 15], AMP = 0.3, PER = 2.4;
  const theta = t => AMP * Math.sin(TAU * t / PER);
  const watchAt = (t, w) => SW.hang(w, PIV, LEN, theta(t), -0.12 * AMP * Math.sin(TAU * t / PER - 0.9));
  function spiral(t) {
    const ph = 0.5 * SW.step(t, 15);
    SW.fill((X, Y, x, y) => {
      const dx = X - EYE[0], dy = Y - EYE[1], r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
      const v = 2 * a / TAU + 2.3 * Math.log(r + 6) - ph, b = v - Math.floor(v);
      let cream = b >= 0.5;
      const e = Math.min(Math.abs(b - 0.5), b, 1 - b);
      if (e < 0.03) cream = ((x + y) & 1) === 1;
      const l = clamp(1 - clamp((r - 45) / 175) * 0.85 + 0.25 * Math.exp(-r / 22));
      return pick(cream ? PAL.cream : PAL.crimson, 0.12 + 0.82 * l, x, y);
    });
  }
  return {
    draw(c, lt) {
      SW.setCam(0, 0, 1);
      spiral(lt);
      const light = [-0.6, -0.8];
      // afterimages: the two previous positions, dithered
      for (const [dt, al] of [[0.16, 0.22], [0.08, 0.4]]) {
        const gw = watchAt(lt - dt, { R: 38 });
        SW.each(gw.x - 70, gw.y - 70, gw.x + 70, gw.y + 70, (X, Y, x, y) => { if (SW.watchInside(X, Y, gw, 1) && bayer(x, y) < al) SW.put(x, y, PAL.crimson[0]); });
      }
      const sec = Math.floor(lt), w = watchAt(lt, { R: 38, light, sec: sec + Math.min(1, (lt - sec) / 0.1), sub: lt / 60, glint: 0.85, pal: PAL });
      SW.drawChain(PIV, w.bow, 3.4, PAL.gold, light); SW.stamp(PAL.outline);
      SW.drawWatch(w, 1); SW.stamp(PAL.outline);
      const th = theta(lt), bump = Math.exp(-(((th / AMP) / 0.18) ** 2));
      SW.sparkle(SW.sxOf(w.x + light[0] * w.R * 0.9), SW.syOf(w.y + light[1] * w.R * 0.9), Math.round(4 * bump), PAL.gold[4], PAL.glass);
      SW.present(c);
    },
  };
})();
