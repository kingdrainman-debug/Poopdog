# Poopdog V2

Implementation of [`docs/POOPDOG-V2-SPEC.md`](../docs/POOPDOG-V2-SPEC.md) in
web tech (ES modules, no build step, no dependencies) — the same UI/engine
codebase an Electron/Tauri desktop shell would host.

## Run

**Just double-clicking a file?** Use the single-file builds — they work
straight from disk, no server needed:

- `dist/poopdog-v2.html` — desktop editor
- `dist/poopdog-v2-mobile.html` — mobile shell

**Working on the source?** The source pages use ES modules, which browsers
block on `file://` — serve them over http (opening the source pages from
disk shows an explanatory banner instead of failing silently):

```sh
python3 -m http.server 8000     # any static server
# open http://localhost:8000/v2/          (desktop editor)
# open http://localhost:8000/v2/mobile.html  (mobile shell)
```

After changing source, regenerate the dist builds with `node v2/build.js`
(dependency-free bundler; relies on the repo's restricted module style —
single-line imports, declaration-prefix exports only).

**Mobile:** `mobile.html` is a touch-first shell over the *same* core
modules — it imports `analysis.js`, `engine.js`, `director.js`,
`playback.js` and `exporter.js` directly (unlike V1's condensed copy).
One-tap flow: pick file → auto-analysed → Director show plays fullscreen.
Controls: play, ⚡ new show (deterministic seed reroll), palette override,
section scrubber, tap-to-hide. Exports real MP4 (vertical/landscape
720p–1080p) through the same offline WebCodecs pipeline, with realtime
WebM capture as the no-WebCodecs fallback.

## What's implemented (spec § references)

- **Screens (§4):** Home dashboard (drop target, templates, recent projects
  with audio relink), Analysis screen (waveform + live structure overlay,
  draggable section boundaries, click-to-retype sections, BPM ×½/×2),
  Editor (asset browser · preview · inspector · navigator · timeline),
  Export overlay with presets.
- **Analysis service (§5):** offline FFT (radix-2, 1024/512 hop), per-band
  onset flux → kick/snare/hat event lists with strengths, autocorrelation
  BPM + phase-searched beat/bar grid, energy-quantile section detection,
  normalised bass/mid/high/energy/loudness curves. Pure: everything
  downstream reads only the analysis.
- **Determinism law (§3):** rendering is a pure function of
  (project, analysis, time); all randomness is seeded (`prng.js`). The E2E
  test asserts two renders of the same frame are byte-identical. Particles
  are closed-form functions of time — scrubbing backwards works.
- **Modulation system (§8):** per-clip slots (param, source, amount,
  attack/release), event envelopes + curve sources incl. `buildProgress`,
  Auto/Manual modes, factory default bindings per generator.
- **Generator registry (§6):** 35 generators/effects across Lasers,
  Tunnels, Geometry, Particles, Backgrounds, Spectrum, Colour, FX,
  Transitions, Camera — each with param schema + default audio bindings.
- **Timeline (§9):** 6 default tracks (transition/adjust/camera/visual×3),
  canvas NLE with move/trim/blade/duplicate/delete, snapping (sections >
  bars > beats > clip edges), wheel zoom/pan, mute/solo, drag-and-drop from
  the asset browser, V1 navigator strip with viewport.
- **Director (§9.8):** writes real clips tagged ⚡; regenerate replaces only
  still-tagged clips — user-edited clips survive (tested). Seed field
  rerolls the whole show deterministically.
- **Inspector (§10):** universal + effect params, blend modes, modulation
  slot editor, randomise (seeded jitter), reset, mode switch.
- **Export (§13):** offline fixed-timestep re-render (never capture) →
  WebCodecs → hand-written ISO-BMFF muxer → **MP4 default**. H.264 + AAC
  where the platform provides encoders (desktop Chrome/Edge); automatic
  VP9 + Opus in MP4 fallback elsewhere (labelled in the UI). Validated
  with ffprobe/ffmpeg: exact frame count, clean decode. Realtime WebM
  capture kept as a legacy fallback. Progress, ETA, cancel.
- **UX (§15):** undo/redo (snapshot, 100 steps), autosave to localStorage
  every 2 min + on close, keyboard shortcuts (space/JKL-lite/arrows/
  N/F/B/⌘Z/⌘D/⌘E/⌘⇧G), adaptive preview quality with fps monitor.

## Not yet (per spec, next in line)

Keyframes + curve editor, Hybrid mod mode, preset save/load UI (`.pdogfx`),
render queue with resume, chorus detection (self-similarity), multi-monitor
preview, dockable panels. The spec remains the source of truth.

## Layout

```
v2/
  index.html            desktop app shell (all screens)
  mobile.html           mobile shell (same core modules, touch UI)
  css/app.css
  js/core/              engine-side (no DOM): prng, palettes, analysis,
                        modulation, project, generators, engine, director,
                        playback
  js/ui/                app, home, analysisScreen, editor, timeline,
                        inspector, exportPage
  js/export/            exporter (WebCodecs), mp4muxer (ISO-BMFF)
```
