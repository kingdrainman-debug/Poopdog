# Poopdog — Music-Aware Rave Visuals

Generate rave visuals from a DJ mix in one click.

> **V2 is here:** open `v2/index.html` for the full editor build — Home →
> Analysis → multi-track Editor → deterministic MP4 export. Implements
> [`docs/POOPDOG-V2-SPEC.md`](docs/POOPDOG-V2-SPEC.md); see
> [`v2/README.md`](v2/README.md). The files below are the V1 prototype,
> kept as the spec's reference implementation.

Poopdog understands the music first, then drives the visuals automatically:
drop in an MP3/WAV, the engine detects **BPM, beats, kicks, snares, drops,
build-ups and energy levels**, then **Auto Show Mode** generates a full
synchronized visual show — lasers, tunnels, particles, strobes, colour
changes, camera motion and transitions — that you can tweak and export as
video.

Zero dependencies. Pure HTML/CSS/JS + Web Audio API + Canvas. Runs in any
modern browser and is ready to be wrapped in Electron/Tauri as a desktop app.

## Run it

```sh
# any static server works, e.g.:
python3 -m http.server 8000
# open http://localhost:8000
```

Or just open `index.html` in Chrome/Edge/Firefox.

**On a phone?** Open `mobile.html` — a condensed single-file build of the
same engine with a touch-first UI: full-screen visuals, a thumb-friendly
control sheet (play / Auto Show / record, scene + palette pickers, seekable
section timeline), safe-area-aware layout, and tap-anywhere to hide the
controls for a clean full-screen show. Recording prefers MP4 where the
browser supports it (iOS Safari), falling back to WebM.

## How it works

```
audio file ─► decode ─► OFFLINE ANALYSIS          ─► SHOW DIRECTOR ─► timeline of cues
                         · RMS energy envelope        (Auto Show Mode)
                         · onset/flux detection
                         · BPM (autocorrelation)
                         · sections: intro / build /
                           drop / breakdown / outro
playback  ─► analyser ─► REALTIME FEATURES        ─► VISUAL ENGINE ─► canvas ─► WebM export
                         · bass / mids / highs        · lasers ×7 modes
                         · loudness                   · tunnels ×5 types
                         · kick + snare hits          · particles, strobe
                                                      · colour engine, camera
```

### Music analysis (`js/analysis.js`, `js/audio.js`)

- **BPM detection** — autocorrelation of the onset-strength envelope (80–180 BPM)
- **Beat grid** — phase-locked to the strongest early onset
- **Kick/snare detection** — per-band energy vs. rolling average with adjustable sensitivity
- **Drop / build-up / breakdown detection** — energy-quantile segmentation of the smoothed loudness curve
- **Energy + frequency band analysis** — realtime bass/mid/high extraction from the FFT

### Audio-reactive bindings (`js/visuals.js`)

| Parameter | Driven by |
|---|---|
| Laser width | Bass |
| Tunnel speed | BPM + kicks |
| Glow / brightness | Highs & loudness |
| Rotation | Kick envelope |
| Hue cycling | Snare hits |
| Strobe / flash | Kicks & drops |
| Camera shake & bass zoom | Bass + kicks |

### Auto Show Mode (`js/director.js`)

One click. Each detected section is assigned a scene that matches its musical
role — drops get laser fans/bursts and strobes at full intensity, build-ups
get accelerating wireframe tunnels, breakdowns calm down to particles, with a
white-flash transition and palette change at every section boundary.
Deterministic per track: the same mix always generates the same show.

### Visual library

- **Lasers**: horizontal, vertical, cross, fan, circle, spiral, random burst
- **Tunnels**: neon rings, hex, wireframe, grid, particle
- **Particles**: kick-burst spark system (up to 900 live particles)
- **Strobe**: beat flash + scan bars + snare pulse rings
- **Palettes**: Cyberpunk, Synthwave, Hardstyle, Techno, Psytrance, Trance,
  Drum & Bass, Acid, Neon, Blacklight

### Controls

Scene / laser mode / tunnel type / palette selectors, plus live sliders for
beat sensitivity, glow, speed, strobe amount, and camera shake. The timeline
shows the detected sections and energy curve — click to seek. `Space` toggles
playback.

### Export (`js/export.js`)

Records canvas + audio to **WebM** via `MediaRecorder` (VP9/VP8 + Opus,
12 Mbps, 60 fps). Export plays the show from the top and auto-stops at the
end of the track.

## Roadmap

- [ ] More tunnel/laser/particle types (fractal, liquid, DNA, mandala…)
- [ ] Timeline editor: drag section boundaries, per-section overrides
- [ ] AI preset generator ("dark warehouse techno" → full setup)
- [ ] WebGL renderer for volumetric light / god rays / bloom
- [ ] Masking (background/subject) and alpha-channel export
- [ ] 4K / vertical / square render presets, ffmpeg MP4 remux
- [ ] Live performance mode (MIDI + audio input)
- [ ] Preset marketplace
- [ ] Electron/Tauri desktop packaging with GPU-accelerated capture
