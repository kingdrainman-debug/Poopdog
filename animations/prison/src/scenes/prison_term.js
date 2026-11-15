// Prison base spiral · style 3 · "Terminal": spiral A drawn in text on an 80×45 orange-phosphor terminal.
// Every cell holds a letter of "COMPLY·OBEY·REST·" repeated across the screen (8×8 pixel font, 24×24 on
// screen). Where the spiral's orange band passes, the letters burn bright; in the black bands they stay as a
// faint ghost, so the words are always there if you look. A fine scanline overlay sits on top.
// Loop: the spiral is the shared one (4 bands per 60 s); the text never moves. Seamless at 60 s.
SCENES['prison_term'] = (() => {
  const { clamp, put } = HD, R = PRISON.R;
  const COLS = 80, ROWS = 45, CELL = 8;
  let TEXT = 'COMPLY·OBEY·REST·';
  let glyph = null;                                    // char → Uint8Array(64) bitmap
  function build() {
    const cv = document.createElement('canvas'); cv.width = cv.height = CELL;
    const g = cv.getContext('2d', { willReadFrequently: true });
    glyph = {};
    const make = ch => {
      g.clearRect(0, 0, CELL, CELL); g.fillStyle = '#fff'; g.textBaseline = 'top'; g.font = '8px "PressStart2P-400"'; g.fillText(ch, 0, 0);
      const d = g.getImageData(0, 0, CELL, CELL).data, b = new Uint8Array(CELL * CELL);
      for (let i = 0; i < CELL * CELL; i++) b[i] = d[i * 4 + 3] > 110 ? 1 : 0;
      return b;
    };
    for (const ch of new Set(TEXT)) glyph[ch] = make(ch);
    if (!glyph['·'].some(v => v)) { TEXT = TEXT.replace(/·/g, '-'); glyph['-'] = make('-'); }   // font without a middle dot
  }
  return {
    init() { build(); },
    draw(c, lt) {
      if (!glyph) build();
      HD.fill(() => R[0]);
      for (let cy = 0; cy < ROWS; cy++) for (let cx = 0; cx < COLS; cx++) {
        const ch = TEXT[(cy * COLS + cx) % TEXT.length], b = glyph[ch];
        const p = PRISON.spiral(cx * CELL + 4, cy * CELL + 4, lt, 3);
        const lvl = clamp(0.13 + 0.95 * p.lvl), col = R[Math.round(lvl * (R.length - 1))];
        for (let j = 0; j < CELL; j++) for (let i = 0; i < CELL; i++) if (b[j * CELL + i]) put(cx * CELL + i, cy * CELL + j, col);
      }
      HD.present(c);
      c.save(); c.fillStyle = 'rgba(0,0,0,0.3)';             // fine scanlines on the 1080p screen
      for (let y = 2; y < 1080; y += 3) c.fillRect(0, y, 1920, 1);
      c.restore();
    },
  };
})();
