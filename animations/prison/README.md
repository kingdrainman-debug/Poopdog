# Prison film: layered brainwashing-screen loops

Looping elements for a film, showing what a sci-fi prison might play to slowly condition a prisoner. Each element is a seamless 60 s loop at 1920×1080, 30 fps, designed to be layered. They're in the same "HD pixel art" style as `../hypnotic-spiral`: a 640×360 art grid scaled ×3.

All layers share one orange-and-black palette (`src/prison_palette.js`): `#050302` `#1a0b03` `#3d1704` `#6e2a05` `#a8420a` `#e0600f` `#ff8418` `#ffad3d` `#ffd98a`.

## Base spiral (in progress)

**Chosen direction: A, the "Conditioning screen"** (`src/scenes/prison_a.js`): a hard-edged two-arm orange-and-black spiral on an institutional CRT.

- **Spiral:** even-width bands that feel mechanical rather than natural.
- **Screen:** scanlines, a darkened tube edge, and a slow refresh bar rolling down the screen.
- **Motion:** the spiral turns inward 4 bands per minute, and the refresh bar passes 3 times per minute. The frame at 60 s is identical to frame 0.

Next step: try other rendering styles for this spiral (for example a degraded surveillance monitor, a text terminal, an LED dot-matrix panel, or a video wall), then animate the chosen one.

The other test directions are kept for reference:

| Source | Direction |
|---|---|
| `src/scenes/prison_b.js` | "Corridor": a twisting vector tunnel of square rings. Its ring-geometry fix hasn't been rendered yet. |
| `src/scenes/prison_c.js` | "The Iris": a mechanical eye with a breathing pupil. |

`directions/base-spiral-tests.png` shows test frames A and C.

## How to render

You need `uv`, `ffmpeg` and Chromium for Playwright.

```sh
B=/tmp/prison-build
./sync.sh $B
uv run --with playwright python $B/render.py --film prisontests --solo prison_a --no-counter --stills 0,7.5 --out stills/
```

In a sandbox where Chromium is preinstalled for a specific Playwright version, pin it, for example `--with playwright==1.63.0`.
