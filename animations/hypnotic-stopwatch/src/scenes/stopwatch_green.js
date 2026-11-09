// Final film · the stopwatch alone on flat chroma green (#00FF00), for keying in an editor.
// A seamless 60 s loop: a "seconds pendulum" (period 2.0 s) passes the centre exactly on every whole second,
// which is when the sweep hand ticks and a glint flashes on the rim. 30 swings and one full turn of the
// second hand per loop, so frame 0 and frame 1800 are identical.
// Keying rules: background is one exact colour (no dither, no gradient, no shadow, no afterimage);
// every sprite pixel is opaque and nothing in the palette is green.
SCENES['stopwatch_green'] = (() => {
  const { ramp, hex, TAU } = SW;
  const KEY = [0, 255, 0];
  const PAL = {
    gold: ramp(['#4a2a08', '#8a5410', '#c88a1c', '#f0c040', '#fff2a8']),
    face: ramp(['#b39a74', '#e0cca4', '#f8eed8']),
    ink: hex('#24120c'), red: hex('#c41e28'), glass: hex('#ffffff'), outline: hex('#170806'),
  };
  const R = 40, PIV = [0, -230], LEN = 190, AMP = 0.3, PER = 2.0, LIGHT = [-0.6, -0.8];
  const theta = t => AMP * Math.sin(TAU * t / PER);
  return {
    draw(c, lt) {
      SW.setCam(0, 0, 1);
      SW.fill(() => KEY);
      const sec = Math.floor(lt + 1e-6);
      const w = SW.hang({ R, light: LIGHT, sec: sec + SW.ease.outCubic((lt - sec) / 0.1), sub: 0, glint: 0.85, pal: PAL },
        PIV, LEN, theta(lt), -0.12 * AMP * Math.sin(TAU * lt / PER - 0.9));
      SW.drawChain(PIV, w.bow, 3.4, PAL.gold, LIGHT); SW.stamp(PAL.outline);
      SW.drawWatch(w, 1); SW.stamp(PAL.outline);
      // a glint runs down the chain every 4 s
      const gp = (lt % 4) / 0.6;
      if (gp < 1) {
        const s0 = (-SW.LH / 2 - PIV[1]) / (w.bow[1] - PIV[1]), s = s0 + (1 - s0) * SW.ease.sineInOut(gp);
        SW.sparkle(SW.sxOf(PIV[0] + s * (w.bow[0] - PIV[0])), SW.syOf(PIV[1] + s * (w.bow[1] - PIV[1])), 1, PAL.gold[4], PAL.glass);
      }
      // rim glint as the watch passes the centre (on every tick)
      const bump = Math.exp(-(((theta(lt) / AMP) / 0.18) ** 2));
      SW.sparkle(SW.sxOf(w.x + LIGHT[0] * R * 1.0), SW.syOf(w.y + LIGHT[1] * R * 1.0), Math.round(6 * bump), PAL.glass, PAL.glass);
      SW.present(c);
    },
  };
})();
