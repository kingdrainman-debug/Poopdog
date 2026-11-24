// crt.js · a CRT-TV pass for keyable overlays, applied to the finished 1920×1080 frame.
// Only the graphic is treated: any pixel that is exactly the key green (#00FF00) is left untouched, and
// neighbours that are key green are never sampled, so the background stays one exact colour and keys cleanly.
// No colour-fringing (chromatic aberration) on purpose: splitting channels at orange/black or cyan/black edges
// would create green-ish pixels that a keyer would eat.
//   · scanlines on every 3-pixel art row (bright, mid, dark)
//   · faint vertical phosphor columns
//   · a soft horizontal phosphor glow (each pixel bleeds into its neighbours on the same line)
//   · a slight frame-to-frame brightness flicker
//   · a soft refresh bar rolling down the screen once per cycle
//   · a brief tracking jitter (a band of lines slips sideways for two frames) once per cycle
// Everything repeats every `cycle` seconds (5 s here), so clips that are a multiple of 5 s still loop seamlessly.
window.CRT = (() => {
  const W = 1920, H = 1080, ROW = [1.0, 0.9, 0.6];
  const hash = n => { let h = Math.imul(n | 0, 2654435761); h ^= h >>> 15; h = Math.imul(h, 2246822519); h ^= h >>> 13; return (h >>> 0) / 4294967296; };
  // region (optional): [x0, y0, x1, y1] in screen pixels; only that rectangle is treated (default: the whole frame)
  function apply(c, lt, cycle = 5, region) {
    const im = c.getImageData(0, 0, W, H), d = im.data, s = new Uint8ClampedArray(d);
    const key = j => s[j] === 0 && s[j + 1] === 255 && s[j + 2] === 0;
    const per = Math.round(cycle * 30), fr = ((Math.round(lt * 30) % per) + per) % per;
    const flick = 1 + 0.025 * (hash(fr + 7) - 0.5) * 2;
    const barY = (fr / per) * (H + 300) - 150;
    const jit = fr === Math.round(per * 0.44) || fr === Math.round(per * 0.44) + 1, jy0 = 380 + Math.round(hash(fr) * 40), jy1 = jy0 + 42;
    const [rx0, ry0, rx1, ry1] = region || [0, 0, W, H];
    for (let y = ry0; y < ry1; y++) {
      const rf = ROW[y % 3] * flick * (1 + 0.07 * Math.exp(-(((y - barY) / 60) ** 2)));
      const shift = jit && y >= jy0 && y < jy1 ? (y & 4 ? 6 : -4) : 0;
      for (let x = rx0; x < rx1; x++) {
        const i = (y * W + x) * 4, sx = x - shift;
        if (sx < 0 || sx >= W) { if (shift) { d[i] = 0; d[i + 1] = 255; d[i + 2] = 0; } continue; }
        const j = (y * W + sx) * 4;
        if (key(j)) { if (shift) { d[i] = 0; d[i + 1] = 255; d[i + 2] = 0; } continue; }
        let r = s[j] * 0.7, g = s[j + 1] * 0.7, b = s[j + 2] * 0.7;
        const l = sx > 0 && !key(j - 4) ? j - 4 : j, rr = sx < W - 1 && !key(j + 4) ? j + 4 : j;
        r += (s[l] + s[rr]) * 0.15; g += (s[l + 1] + s[rr + 1]) * 0.15; b += (s[l + 2] + s[rr + 2]) * 0.15;
        const f = rf * (x % 3 === 2 ? 0.94 : 1);
        d[i] = r * f; d[i + 1] = g * f; d[i + 2] = b * f;
      }
    }
    c.putImageData(im, 0, 0);
  }
  return { apply };
})();
