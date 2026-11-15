# Prison film: layered brainwashing-screen loops

Looping elements for a film, showing what a sci-fi prison might play to slowly condition a prisoner. Each element is a seamless 60 s loop at 1920×1080, 30 fps, designed to be layered. They're in the same "HD pixel art" style as `../hypnotic-spiral`: a 640×360 art grid scaled ×3.

All layers share one orange-and-black palette (`src/prison_palette.js`): `#050302` `#1a0b03` `#3d1704` `#6e2a05` `#a8420a` `#e0600f` `#ff8418` `#ffad3d` `#ffd98a`.

## Base spiral: done

`output/prison-base-spiral_30s.mp4` (1920×1080, 30 fps, 30 s, H.264, BT.709, no audio) is a seamless loop, made from `src/scenes/prison_base.js` (film `prisonbase`). It's option A with these changes:

- **No screen effects:** the scanlines and the rolling refresh bar are removed.
- **Much faster turn:** the spiral turns inward exactly 20 bands per 30 s, one every 1.5 s. That's about 10× the test speed.
- **Vignette:** a strong vignette falls to near-black in the corners.
- **Loop:** nothing else moves, so the frame at 30 s is identical to frame 0.

## How the base spiral was chosen

**Chosen direction: A, the "Conditioning screen"** (`src/scenes/prison_a.js`): a hard-edged two-arm orange-and-black spiral on an institutional CRT.

- **Spiral:** even-width bands that feel mechanical rather than natural.
- **Screen:** scanlines, a darkened tube edge, and a slow refresh bar rolling down the screen.
- **Motion:** the spiral turns inward 4 bands per minute, and the refresh bar passes 3 times per minute. The frame at 60 s is identical to frame 0.

### Screen-style tests

These show spiral A on different "screens". Every style uses the same spiral and motion, from `PRISON.spiral` in `src/prison_palette.js`. The 5-second test clips are in `tests/`, and `directions/screen-style-tests.png` shows one frame of each.

| Style | Source | What it adds |
|---|---|---|
| A · CRT conditioning screen (the original) | `src/scenes/prison_a.js` | Fine HD pixels, crisp bands, scanlines, a darkened tube edge and a slow refresh bar. |
| 2 · Surveillance monitor | `src/scenes/prison_surv.js` | Chunky 6×6 pixels, heavy scanlines, grain, a drifting VHS tracking band, and brief signal tears (one at 2.3 s). |
| 3 · Terminal | `src/scenes/prison_term.js` | An 80×45 orange-phosphor text grid of `COMPLY·OBEY·REST·`. The spiral lights the words, and they stay as a faint ghost in the dark bands. |
| 5 · Video wall | `src/scenes/prison_wall.js` | A 4×3 bank of CRTs with bezels. Each screen has its own brightness, one runs 0.45 s behind, one has a faulty refresh bar, and one drops to static (first at 3.1 s). |

All three are built to loop seamlessly at 60 s.

**Size note for the surveillance style:** its per-frame grain is costly to encode, at about 16 MB per 5 s. A full minute would need lighter or slower grain to stay under GitHub's 100 MB file limit.

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
uv run --with playwright python $B/render.py --film prisonstyles --solo prison_term --no-counter --stills 0,1.5 --out stills/
```

In a sandbox where Chromium is preinstalled for a specific Playwright version, pin it, for example `--with playwright==1.63.0`.
