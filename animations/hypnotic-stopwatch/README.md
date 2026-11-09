# Hypnotic stopwatch: pixel art on green screen

A gold stopwatch on a chain swings slowly back and forth, drawn as pixel art on flat chroma green (`#00FF00`) so it can be keyed out in any editor.

| File | What it is |
|---|---|
| `output/hypnotic-stopwatch_green.mp4` | 1920×1080, 30 fps, 60 s, H.264, BT.709, no audio. A seamless loop on #00FF00. |
| `output/hypnotic-stopwatch_alpha.mov` | The same 60 s loop, lossless PNG-in-MOV with a real alpha channel, so there's nothing to key. Imports into Premiere, After Effects, Resolve and Final Cut. |
| `directions/three-directions.png` | The three style directions tried first (spiral, parlour POV, shadow play). |

## What happens in the clip

- **Swing:** the watch swings like a pendulum. Each full swing takes exactly 2.0 s, so it passes the centre once every second.
- **Second hand:** the red sweep hand ticks each time the watch passes the centre, and a white glint flashes on the rim at the same moment.
- **Small motion:** the watch body lags slightly behind the chain (follow-through). A glare slides across the glass, and a glint runs down the chain every 4 s.
- **Loop:** in 60 s there are 30 swings and one full turn of the second hand, so the last frame flows straight into the first. Put copies end to end for as long as you need.
- **Chain:** the chain runs off the top of the frame, so the watch looks like it hangs from something above the shot.

## Keying notes

- **Background:** one exact colour, with no dithering, gradients, shadows or motion blur.
- **Sprite colours:** every sprite pixel is fully opaque, and none of the sprite colours are green. The gold has some green in it, so leave spill suppression at a gentle setting.
- **Pixels:** each art pixel is a 6×6 block, and the MP4 was encoded with nearest-neighbour chroma sampling. After decoding, every background pixel stays within 9/255 of pure #00FF00.
- **Edge fringe:** most editors blend colour across edges when they decode 4:2:0 video, which can leave a 1px green line along the outline. Choke the matte by 1px, or use `hypnotic-stopwatch_alpha.mov` and skip keying.

## How it's made

It's drawn entirely in code with the `huashu-art-motion` skill's render engine (`.agents/skills/huashu-art-motion`), following its 8-bit recipe:

- **Low resolution:** every frame is computed pixel by pixel on a 320×180 canvas.
- **Palette:** colours come from a small fixed palette, with ordered (Bayer) dithering for shading.
- **Outlines:** each part (chain, case, crown) gets its own 1px dark outline.
- **Scaling:** the frame is scaled up ×6 with nearest-neighbour, so pixels stay sharp.

| Source | Role |
|---|---|
| `src/sw_lib.js` | Pixel toolkit: camera, dithering, outlined sprite layers, the stopwatch, the chain. |
| `src/scenes/stopwatch_green.js` | The final shot. |
| `src/eras_stopwatch.js` | Film definition (one 60 s shot). |
| `src/scenes/dir*.js`, `src/eras_dirs.js` | The three direction studies. |

### Re-render

You need `uv`, `ffmpeg` and Chromium for Playwright.

```sh
B=/tmp/sw-build
./sync.sh $B                                     # copy the skill's engine once, overlay src/
# one still to check
uv run --with playwright python $B/render.py --film stopwatch --solo stopwatch_green --no-counter --stills 0,0.5,1 --out stills/
# all frames as PNG, then encode
T=$(python3 -c "print(','.join(f'{i/30:.6f}' for i in range(1800)))")
uv run --with playwright python $B/render.py --film stopwatch --solo stopwatch_green --no-counter --stills "$T" --out frames/
ffmpeg -framerate 30 -pattern_type glob -i 'frames/*.png' \
  -vf "scale=out_color_matrix=bt709:out_range=tv:flags=neighbor+accurate_rnd+full_chroma_inp" \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 \
  -c:v libx264 -preset slow -crf 8 -tune animation -pix_fmt yuv420p -movflags +faststart output/hypnotic-stopwatch_green.mp4
ffmpeg -framerate 30 -pattern_type glob -i 'frames/*.png' -vf "colorkey=0x00FF00:0.01:0" \
  -c:v png -pred mixed -pix_fmt rgba output/hypnotic-stopwatch_alpha.mov
```

In a sandbox where Chromium is preinstalled for a specific Playwright version, pin it, for example `--with playwright==1.63.0`.
