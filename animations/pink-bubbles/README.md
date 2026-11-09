# Pink bubbles: pixel art for keying

Pixel-art pink bubbles fill the whole screen. Each one pops in, inflates, trembles, then bursts into a small pink splash. It's a seamless 60-second loop, rendered twice: once on pure white and once on pure green.

| File | Background |
|---|---|
| `output/pink-bubbles_green.mp4` | `#00FF00` |
| `output/pink-bubbles_white.mp4` | `#FFFFFF` |

Both files are 1920×1080, 30 fps, 60 s, H.264, BT.709, with no audio. The bubbles are pixel-identical in the two renders; only the background colour differs.

## What happens

- **Spread:** 84 bubble spots on a jittered 12×7 grid cover the whole frame. Each spot is busy about 90% of the time, so the screen stays full.
- **Size:** bubbles grow to a radius of 5.5–11 art pixels, which is about 65–130 px across on a 1080p frame.
- **Life of a bubble:**
  1. It pops in with a little overshoot.
  2. It inflates while gently breathing and drifting upward.
  3. At full size it trembles with a quick squash-and-stretch.
  4. It bursts: a broken ring, eight short streaks, and a few droplets that fall a little.
- **Loop:** every spot repeats on a 5 or 6 s cycle, and both divide 60. Each cycle's position and size come from a fixed seed, so the frame at 60 s is identical to frame 0.

## Keying notes

- **Background:** one exact colour, with no dithering, gradients, shadows or soft edges.
- **Bubble colours:** every bubble pixel is fully opaque. The palette is six pinks from `#7a1048` (outline) to `#ffc4e1` (highlight). None is close to white or green, so either background keys cleanly.
- **Outline:** a dark magenta outline separates every bubble from the background, which also helps a luma key on the white version.
- **Pixels:** each art pixel is a 6×6 block, and the files were encoded with nearest-neighbour chroma sampling. Many editors still blend colour across edges when they decode 4:2:0 video, which can leave a 1px fringe; choke the matte by 1px if you see one.

## How it's made

It's drawn in code with the `huashu-art-motion` skill's render engine: per-pixel on a 320×180 canvas, then scaled up ×6 with nearest-neighbour.

| Source | Role |
|---|---|
| `src/scenes/pink_bubbles.js` | The bubble field: slots, life cycle, drawing and the pop. |
| `src/sw_lib.js` | Shared pixel toolkit, copied from `../hypnotic-stopwatch`. |
| `src/eras_bubbles.js` | Film definition (one 60 s shot). |

### Re-render

You need `uv`, `ffmpeg` and Chromium for Playwright.

```sh
B=/tmp/bubbles-build
./sync.sh $B
T=$(python3 -c "print(','.join(f'{i/30:.6f}' for i in range(1800)))")
uv run --with playwright python $B/render.py --film bubbles --solo pink_bubbles --no-counter --stills "$T" --out frames/
ENC="-colorspace bt709 -color_primaries bt709 -color_trc bt709 -c:v libx264 -preset slow -crf 8 -tune animation -pix_fmt yuv420p -movflags +faststart"
YUV="scale=out_color_matrix=bt709:out_range=tv:flags=neighbor+accurate_rnd+full_chroma_inp"
ffmpeg -framerate 30 -pattern_type glob -i 'frames/*.png' -vf "$YUV" $ENC output/pink-bubbles_green.mp4
# white: swap the exact key colour for white, then encode the same way
ffmpeg -f lavfi -i color=white:s=1920x1080:r=30 -framerate 30 -pattern_type glob -i 'frames/*.png' \
  -filter_complex "[1:v]colorkey=0x00FF00:0.01:0[k];[0:v][k]overlay=shortest=1:format=gbrp,$YUV" $ENC output/pink-bubbles_white.mp4
```

In a sandbox where Chromium is preinstalled for a specific Playwright version, pin it, for example `--with playwright==1.63.0`.
