// ui_lib.js · pixel-art UI drawing on the HD 640×360 grid, for keyable overlays.
// Anything a 2D canvas can draw (paths, any font) is rasterised into a binary mask (alpha > 50%), then
// painted into the HD buffer with a flat colour or a per-pixel colour function, optionally with a hard
// outline ring. No anti-aliasing and no partial alpha ever reach the frame, so a chroma key stays exact.
window.UI = (() => {
  const W = HD.LW, H = HD.LH;
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
  const g = cv.getContext('2d', { willReadFrequently: true });
  // draw(g) with anything; returns a Uint8Array mask
  function mask(draw) {
    g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, W, H); g.save(); g.fillStyle = g.strokeStyle = '#fff'; draw(g); g.restore();
    const d = g.getImageData(0, 0, W, H).data, m = new Uint8Array(W * H);
    for (let i = 0; i < W * H; i++) m[i] = d[i * 4 + 3] > 127 ? 1 : 0;
    return m;
  }
  const text = (str, font, x, y, align = 'left') => mask(c => { c.font = font; c.textAlign = align; c.textBaseline = 'top'; c.fillText(str, x, y); });
  const rrect = (x, y, w, h, r) => mask(c => { c.beginPath(); c.roundRect(x, y, w, h, r); c.fill(); });
  // grow a mask by n pixels (8-neighbourhood)
  function dilate(m, n = 1) {
    let a = m;
    for (let k = 0; k < n; k++) {
      const b = new Uint8Array(a);
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
        const i = y * W + x; if (a[i]) continue;
        for (let dy = -1; dy <= 1 && !b[i]; dy++) for (let dx = -1; dx <= 1; dx++) {
          const xx = x + dx, yy = y + dy; if (xx >= 0 && yy >= 0 && xx < W && yy < H && a[yy * W + xx]) { b[i] = 1; break; }
        }
      }
      a = b;
    }
    return a;
  }
  // paint a mask; col is [r,g,b] or (x, y) => [r,g,b]; optional outline colour and thickness
  function paint(m, col, outline, thick = 1) {
    if (outline) { const o = dilate(m, thick); for (let i = 0; i < W * H; i++) if (o[i] && !m[i]) HD.put(i % W, (i / W) | 0, outline); }
    const f = typeof col === 'function';
    for (let i = 0; i < W * H; i++) if (m[i]) { const x = i % W, y = (i / W) | 0; HD.put(x, y, f ? col(x, y) : col); }
  }
  const and = (a, b) => { const o = new Uint8Array(W * H); for (let i = 0; i < W * H; i++) o[i] = a[i] & b[i]; return o; };
  return { W, H, mask, text, rrect, dilate, paint, and };
})();
