// hd_lib.js · "high-definition" pixel-art toolkit for full-frame backgrounds.
// Frames are computed per pixel on a 640×360 art grid and scaled ×3 (nearest neighbour) to 1920×1080,
// so each art pixel is a crisp 3×3 block: still pixel art, at twice the resolution of the bubble films.
// Shading uses an 8×8 ordered (Bayer) dither in screen space, so gradients between palette tones are
// smooth and the dither pattern never jitters from frame to frame. Deterministic: no Math.random.
window.HD = (() => {
  const LW = 640, LH = 360, S = 3, TAU = Math.PI * 2;
  const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const ramp = a => a.map(hex);
  const clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
  // 8×8 Bayer matrix (values 0..63)
  const B8 = (() => { let m = [[0]]; for (let n = 1; n < 8; n *= 2) { const r = []; for (let y = 0; y < 2 * n; y++) { r.push([]); for (let x = 0; x < 2 * n; x++) { const q = m[y % n][x % n] * 4; r[y].push(q + [0, 2, 3, 1][(y >= n) * 2 + (x >= n)]); } } m = r; } return m.flat(); })();
  const bayer = (x, y) => (B8[((y & 7) << 3) | (x & 7)] + 0.5) / 64;
  // ordered-dither pick from a tone ramp (dark → light)
  const pick = (R, v, x, y) => { const n = R.length - 1, f = clamp(v) * n; let i = f | 0; if (f - i > bayer(x, y)) i++; return R[i > n ? n : i]; };
  const hash = (x, y, s = 0) => { let h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(s | 0, 1013904223);
    h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return (h >>> 0) / 4294967296; };

  const cv = document.createElement('canvas'); cv.width = LW; cv.height = LH;
  const g = cv.getContext('2d', { willReadFrequently: true });
  const im = g.createImageData(LW, LH), D = im.data;
  const put = (x, y, c) => { if (x < 0 || y < 0 || x >= LW || y >= LH) return; const i = (y * LW + x) << 2; D[i] = c[0]; D[i + 1] = c[1]; D[i + 2] = c[2]; D[i + 3] = 255; };
  function fill(fn) {
    for (let y = 0; y < LH; y++) for (let x = 0; x < LW; x++) { const c = fn(x, y), i = (y * LW + x) << 2; D[i] = c[0]; D[i + 1] = c[1]; D[i + 2] = c[2]; D[i + 3] = 255; }
  }
  function present(c) { g.putImageData(im, 0, 0); c.save(); c.setTransform(1, 0, 0, 1, 0, 0); c.imageSmoothingEnabled = false; c.drawImage(cv, 0, 0, LW * S, LH * S); c.restore(); }
  return { LW, LH, S, TAU, hex, ramp, clamp, bayer, pick, hash, put, fill, present };
})();
