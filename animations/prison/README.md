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

## Medicine prompt overlay (in progress)

**Chosen style: 3, the medication label.** It's animated in `src/med_label.js` (film `medlabel`), and `output/med-prompt-label_green_5s.mp4` is a 5 s seamless loop on `#00FF00` at 1080p, 30 fps. Changes from the still:

- **Mark:** "LTX" replaces "Rx". It's sized automatically to fit the widened left column, with a small pixel © raised at its top right and the sub-label `d0ll-g4s` under the orange rule.
- **Barcode number:** `U-R-RBBR-D011`.
- **Moving border:** the orange-and-black hazard stripes run clockwise around the box like a marquee, flowing unbroken round rounded corners. Each stripe's position is measured along the band's centre line, through straight sides and quarter-circle corners alike. The corner radius (about 15 art px outside) is solved so the centre line is exactly 91 stripes of 12 px.
- **Motion:** the stripes move exactly 2 art pixels per frame, so the motion is perfectly even. The clip loops at 5 s or any multiple of it.
- **Edge fringe:** where the curved corners meet the green, about 90 edge pixels pick up a 1px colour-bleed fringe from 4:2:0 encoding. A 1px matte choke removes it.

Still to come in this style: INHALE (count of 4), HOLD (count of 3) and EXHALE.

A keyable overlay on pure `#00FF00`. It tells the prisoner to prepare for their medicine, an inhaled gas, then counts them through **INHALE** (4), **HOLD** (3) and **EXHALE**.

**Colours:** the prison's black, rust and orange, plus two medical colours: a warm clinical white (`#f3f0e8`) and a sterile azure-cyan (`#4cc9ea` / `#1b8fb5`). The cyan is roughly the complement of orange and well away from the key green. Every pixel is opaque, and no colour is green-ish. Crosses are cyan or orange, never a red cross on white.

Five candidate styles of the opening prompt are in `directions/med-prompt/` (`all-five-styles.png` shows them side by side). The source is `src/med_prompts.js` (film `medstyles`), built on `src/ui_lib.js`, which rasterises shapes and type to hard-edged pixels.

| Style | Look |
|---|---|
| 1 · Clinical card | A white notice card with an orange "MEDICAL NOTICE" bar, a cyan cross badge, and a dose progress bar. |
| 2 · Dispensary LED | Cyan dot-matrix lettering and an orange LED cross on a black sign. |
| 3 · Medication label | A pharmacy or gas-canister label: Rx mark, small print and barcode, inside an orange-and-black hazard border. |
| 4 · Respirator HUD | No panel. Floating cyan corner brackets, an orange respirator icon, and outlined sci-fi type. |
| 5 · Capsule | The prompt printed across a giant half-orange, half-white pill, with a cyan cross badge. |

### Breathing stages (in progress)

After the "prepare" prompt come **INHALE** (count of 4), **HOLD** (count of 3) and **EXHALE**. Each stage carries faux-caring corporate copy:

| Stage | Caring line | Small print |
|---|---|---|
| INHALE · 4 | *Breathe in deeply. Your calm is our priority.* | PATIENTS WHO FOLLOW EVERY INSTRUCTION RECOVER SOONER. |
| HOLD · 3 | *Hold it in. Let the medicine find you.* | MOVEMENT DURING TREATMENT IS A SYMPTOM. STAY STILL. |
| EXHALE | *Breathe out slowly. Let go of your doubts.* | THANK YOU FOR YOUR COMPLIANCE. LTX CARES. |

Three candidate layouts are in `src/med_breath.js` (film `medbreath`, scenes `br{A,B,C}_{in,hold,out}`). Stills are in `directions/breathing/`, and `all-styles-grid.png` shows all nine. Every card is built from the same parts as the opening prompt (`src/med_common.js`: border, panel, LTX© brand), so the sequence reads as one system.

| Layout | Look |
|---|---|
| A · Label continuation | The LTX column stays. The right side shows "STEP n OF 3", the stage word, an orange count tile (a cyan wave tile for exhale) and count pips. |
| B · Countdown dial | A segmented ring that counts the beats (4 for inhale, 3 for hold, a full cyan ring for exhale), with the brand in a header strip. |
| C · Step tracker | Three tabs (done steps turn cyan, the current step is orange), a giant count, and a cyan "CARE NOTE" box holding the small print. |

