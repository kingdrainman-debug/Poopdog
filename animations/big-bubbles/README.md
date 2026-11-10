# Big bubbles: pink, purple and cream pixel art for keying

Big pixel-art bubbles, mostly pink with some purple and cream ones mixed in. Each bubble pops in, inflates, trembles, then bursts into a splash in its own colours. It's a seamless 60-second loop, rendered twice: once on pure white and once on pure green.

| File | Background |
|---|---|
| `output/big-bubbles_green.mp4` | `#00FF00` |
| `output/big-bubbles_white.mp4` | `#FFFFFF` |

Both files are 1920×1080, 30 fps, 60 s, H.264, BT.709, with no audio. The bubbles are pixel-identical in the two renders; only the background colour differs.

This is a sibling of `../pink-bubbles`. The bubbles are about twice the size and there are far fewer of them.

## What happens

- **Spread:** 28 bubble spots on a jittered 7×4 grid cover the frame. Each spot is busy about 90% of the time.
- **Size:** bubbles grow to a radius of 11–20 art pixels, which is about 130–240 px across on a 1080p frame.
- **Colours:** each bubble's colour is picked when it appears: about 50% pink, 25% purple, 25% cream.
- **Life of a bubble:**
  1. It pops in with a little overshoot.
  2. It inflates while gently breathing and drifting upward.
  3. At full size it trembles with a quick squash-and-stretch.
  4. It bursts: a broken ring, twelve streaks, and eight droplets that fall a little, all sized to the bubble.
- **Loop:** every spot repeats on a 5 or 6 s cycle, and both divide 60. Each cycle's position, size and colour come from a fixed seed, so the frame at 60 s is identical to frame 0.

## Colours

Each colour set has six shades, from outline to highlight:

| Bubble | Outline → highlight |
|---|---|
| Pink | `#7a1048` `#d9468f` `#ef68ad` `#ff8cc6` `#ffaad5` `#ffc4e1` |
| Purple | `#3e1258` `#8a3fbf` `#a35fd6` `#b97ae6` `#cc9cf0` `#dcbcf6` |
| Cream | `#6b4a1c` `#cf9e56` `#e2ba74` `#efd197` `#f4dca9` `#f8e4b4` |

## Keying notes

- **Background:** one exact colour, with no dithering, gradients, shadows or soft edges.
- **Bubbles:** every bubble pixel is fully opaque, no colour is green, and every bubble has a dark outline.
- **Cream on white:** cream is the lightest colour here. On the white version, key by colour (chroma) rather than brightness (luma), or keep the tolerance tight, so the cream highlights don't get nibbled. The green version has no such issue.
- **Edge fringe:** each art pixel is a 6×6 block, and the files were encoded with nearest-neighbour chroma sampling. Many editors still blend colour across edges when they decode 4:2:0 video, which can leave a 1px fringe; choke the matte by 1px if you see one.

## How it's made

It's drawn in code with the `huashu-art-motion` skill's render engine: per-pixel on a 320×180 canvas, then scaled up ×6 with nearest-neighbour.

| Source | Role |
|---|---|
| `src/scenes/big_bubbles.js` | The bubble field: slots, colours, life cycle, drawing and the pop. |
| `src/sw_lib.js` | Shared pixel toolkit. |
| `src/eras_bigbubbles.js` | Film definition (one 60 s shot). |

### Re-render

You need `uv`, `ffmpeg` and Chromium for Playwright.

```sh
B=/tmp/bubbles-build
./sync.sh $B
T=$(python3 -c "print(','.join(f'{i/30:.6f}' for i in range(1800)))")
uv run --with playwright python $B/render.py --film bigbubbles --solo big_bubbles --no-counter --stills "$T" --out frames/
ENC="-colorspace bt709 -color_primaries bt709 -color_trc bt709 -c:v libx264 -preset slow -crf 8 -tune animation -pix_fmt yuv420p -movflags +faststart"
YUV="scale=out_color_matrix=bt709:out_range=tv:flags=neighbor+accurate_rnd+full_chroma_inp"
ffmpeg -framerate 30 -pattern_type glob -i 'frames/*.png' -vf "$YUV" $ENC output/big-bubbles_green.mp4
ffmpeg -f lavfi -i color=white:s=1920x1080:r=30 -framerate 30 -pattern_type glob -i 'frames/*.png' \
  -filter_complex "[1:v]colorkey=0x00FF00:0.01:0[k];[0:v][k]overlay=shortest=1:format=gbrp,$YUV" $ENC output/big-bubbles_white.mp4
```

In a sandbox where Chromium is preinstalled for a specific Playwright version, pin it, for example `--with playwright==1.63.0`.
