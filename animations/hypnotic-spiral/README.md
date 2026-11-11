# Hypnotic spiral: pink pixel-art background loop

A soft two-arm pink spiral that turns slowly inward, made as a calm meditation background to sit behind the bubbles and the stopwatch. It's a seamless 60-second loop.

| File | What it is |
|---|---|
| `output/hypnotic-spiral_pink.mp4` | 1920×1080, 30 fps, 60 s, H.264, BT.709, no audio. Seamless loop. |
| `directions/three-tests.png` | The three test frames: A soft swirl (chosen), B rose tunnel, C bloom. |
| `directions/preview-with-elements.png` | One spiral frame with the big bubbles and the stopwatch composited on top. |

## The look

- **Pixel size:** "high-definition" pixel art on a 640×360 art grid, scaled ×3 with nearest-neighbour. Each art pixel is a crisp 3×3 block, half the size of the pixels in the bubble and stopwatch films.
- **Colours:** only the bubbles' own pinks: `#d9468f` `#ef68ad` `#ff8cc6` `#ffaad5` `#ffc4e1`.
- **Bands:** soft rather than striped. Each band fades through the five tones with a fine 8×8 ordered dither that's fixed to the screen, so the texture doesn't jitter from frame to frame.
- **Light:** a gentle glow at the eye of the spiral, and a slight darkening toward the corners.

## The motion, chosen not to distract

- **Turn speed:** the spiral turns inward by exactly 3 bands per minute, so one band passes every 20 s. Even at the corners, the edges move less than half an art pixel per frame.
- **Breathing:** a very faint brightness swell every 10 s, which is 6 breaths a minute, a common slow-breathing pace for meditation.
- **Nothing sudden:** there are no cuts, flashes or new elements appearing.
- **Loop:** both motions complete a whole number of cycles in 60 s, so the frame at 60 s is pixel-identical to frame 0. Place copies end to end for as long as you need.

## How it's made

It's drawn in code with the `huashu-art-motion` skill's render engine.

| Source | Role |
|---|---|
| `src/hd_lib.js` | 640×360 pixel toolkit: 8×8 Bayer dithering, tone ramps, ×3 nearest-neighbour present. |
| `src/scenes/spiral_a.js` | The chosen spiral. |
| `src/eras_spiral.js` | Film definition (one 60 s shot). |
| `src/scenes/spiral_b.js`, `spiral_c.js`, `src/eras_spiraltests.js` | The other two test directions. |

### Re-render

You need `uv`, `ffmpeg` and Chromium for Playwright.

```sh
B=/tmp/spiral-build
./sync.sh $B
T=$(python3 -c "print(','.join(f'{i/30:.6f}' for i in range(1800)))")
uv run --with playwright python $B/render.py --film spiral --solo spiral_a --no-counter --stills "$T" --out frames/
ffmpeg -framerate 30 -pattern_type glob -i 'frames/*.png' \
  -vf "scale=out_color_matrix=bt709:out_range=tv:flags=neighbor+accurate_rnd+full_chroma_inp" \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 \
  -c:v libx264 -preset slow -crf 18 -tune animation -pix_fmt yuv420p -movflags +faststart output/hypnotic-spiral_pink.mp4
```

The fine dither makes this file larger than the others (about 58 MB). Lower quality settings barely shrink it, because most of the encoding difference comes from colour subsampling, not the quality level.

In a sandbox where Chromium is preinstalled for a specific Playwright version, pin it, for example `--with playwright==1.63.0`.
