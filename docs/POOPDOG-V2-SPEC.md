# Poopdog V2 — Desktop Application Product Specification

**Product:** Poopdog — automatic rave/EDM visual generation from DJ mixes
**Platforms:** Windows 10/11 (x64), macOS 13+ (Apple Silicon + Intel)
**Document type:** Full product & UX specification, ready for design + engineering handoff
**Status:** V2 bottom-up redesign, superseding the V1 web prototype on this branch

---

## 0. Positioning

Poopdog turns a finished DJ mix into a finished, synchronized visual show.
It is **not** a live/VJ performance tool. It is an **offline generation and
rendering application**: import audio → automatic analysis → automatic show
generation → manual refinement → MP4 render.

The competitive claim against Resolume and TouchDesigner is not more power —
it is **a finished result in minutes with zero patching**, plus a familiar
NLE-style editor for refinement. Resolume assumes you perform; TouchDesigner
assumes you program. Poopdog assumes you *imported a track and want a video*.

The product's spine, proven in V1, is:

> **Understand the music first; drive every visual from that understanding.**

---

## 1. V1 Retrospective — what worked, what carries forward, what is replaced

V1 was built and validated on this branch as a zero-dependency web app
(`index.html` + `js/`, plus the condensed `mobile.html`). It was tested
end-to-end in automated browser runs against a synthetic 128 BPM track.

### 1.1 Keep (proven in V1, promoted to V2 foundations)

| V1 element | Evidence it worked | V2 disposition |
|---|---|---|
| **Analysis-first pipeline** (decode → energy envelope → onset flux → BPM → sections) | Detected 129.2 BPM on a 128 BPM test track; correct drop/build/outro segmentation | Becomes the V2 Analysis Service, upgraded with spectral-band onset detection, chorus detection, and confidence scores (§5) |
| **Section taxonomy** `intro / build / drop / breakdown / groove / outro` | Sections mapped cleanly to musically-correct scene changes | Kept verbatim as the core structural vocabulary; adds `chorus` and user-defined types (§5.4) |
| **Show Director: section → scene pools, deterministic per track** | One click produced a coherent full-length show; same track always yields same show | Kept as the Auto-Generate pass that populates the V2 timeline with *editable clips* instead of transient state (§9.8) |
| **Audio-reactive binding map** (laser width→bass, tunnel speed→BPM+kick, rotation→kick, hue→snare, glow→highs, shake→bass) | Bindings read as musical on first render | Generalized into the V2 Modulation System — every parameter, every source (§8) |
| **Kick/snare detection via per-band energy vs. rolling average + sensitivity control** | Reliable hits at default sensitivity 1.3 | Kept as the realtime preview detector; render path uses the offline beat grid for frame accuracy (§5.2) |
| **Palette engine**: 10 genre palettes, snare-driven hue cycling, white-flash transitions | Instant genre "look" switching; strong reads on screenshots | Kept; palettes become first-class presets with editor (§12) |
| **Timeline strip with section colours + energy curve overlay + click-to-seek** | Users immediately understand track structure at a glance | Becomes the Navigator bar above the full multi-track timeline (§9.2) |
| **One-click "it just plays" flow** | From file-pick to moving synced visuals in <10 s on a 40 s track | Preserved as the golden path: Import → Auto-Generate → Preview with zero required decisions (§4.9) |
| **Deterministic tuning lessons**: trail-fade vs. glow accumulation, fast strobe decay (0.72/frame), flash alpha 0.4 | Grey-haze washout bug found and fixed by measurement | Encoded as engine defaults and as the "Haze" and "Strobe Decay" universal parameters (§7) |

### 1.2 Replace (V1 limits that define V2's redesign)

| V1 limitation | V2 replacement |
|---|---|
| 2D canvas, additive blending, CPU-bound | Native GPU engine (wgpu: Metal/Vulkan/DX12), real 3D scenes, HDR internal pipeline (§3, §14) |
| Realtime capture via MediaRecorder → WebM, dropped-frame risk | Offline deterministic renderer, fixed timestep, **MP4 (H.264 default, H.265 optional)** via hardware encoders (§13) |
| One active scene at a time, global settings object | Multi-track timeline; every effect is a clip with its own parameters and keyframes (§9) |
| Global sliders shared by all scenes | Per-clip Inspector with universal + effect-specific parameters (§10) |
| Director output was transient runtime state | Director writes real clips/markers into the project — fully editable, non-destructive (§9.8) |
| No persistence | `.pdog` project files, autosave, crash recovery (§16, §15.7) |
| Browser sandbox | Native shell with file system, GPU, and codec access (§3) |

---

## 2. Personas & primary workflows

- **The DJ with a mix to post** (primary): wants a YouTube/festival-recap
  video tonight. Golden path only: Import → Auto → Export. Success = never
  opens the Inspector.
- **The content editor**: makes visuals for artists/labels. Uses Auto as a
  starting point, then re-times sections, swaps generators, brands colours.
  Lives in the timeline + Inspector.
- **The visual designer**: builds preset packs and scene looks. Lives in the
  Preset system and Inspector; needs deep parameters, keyframes, and export
  of reusable assets.

Every screen below is judged against: *does the DJ still get a video with
zero learning, and does the editor never hit a wall?*

---

## 3. Architecture overview (for engineering)

- **Shell/UI:** native desktop shell (Tauri or Electron acceptable; Tauri
  preferred for footprint) hosting a React UI. All heavy work out of the UI
  process.
- **Engine core (Rust):** rendering, playback clock, modulation evaluation,
  project model. GPU via **wgpu** → Metal on macOS, Vulkan/DX12 on Windows.
  Internal render target: 16-bit float linear, tonemapped at output.
- **Analysis service (Rust):** offline decode (symphonia/ffmpeg), STFT,
  detectors (§5). Runs on import in a worker; results cached in-project.
- **Encoder:** ffmpeg with hardware encode — NVENC (NVIDIA), AMF (AMD),
  VideoToolbox (macOS), x264/x265 software fallback (§13).
- **Determinism rule:** all animation is a pure function of
  `(project, analysis, frame_time)`. No wall-clock, no unseeded RNG —
  every random parameter uses a per-clip seed. Preview and final render are
  the same code path at different resolutions/timesteps. (This is the V1
  "deterministic per track" property, made law.)

---

## 4. Application layout — every screen

### 4.1 Welcome screen (first launch only)

- **What:** one-time, 3-step overlay: (1) pick GPU/quality tier (auto-detected,
  confirmable), (2) default export folder, (3) optional sample project
  ("Demo mix — 2:00") that opens pre-analysed.
- **Why:** the first-run promise is *see a finished show in under a minute*
  without supplying your own file.
- **Behaviour:** never shown again; all choices editable in Settings. Skippable.

### 4.2 Home dashboard

Layout: left rail (navigation: **Home / Projects / Presets / Render Queue /
Settings**), main area:

- **"New from audio"** — a large drop target + button. Dropping an MP3/WAV
  here *is* the primary CTA; it creates a project and goes straight to
  Analysis (§4.4).
- **Recent projects** — grid of cards: hover-scrub video thumbnail (from
  cached preview), title, duration, BPM badge, last-opened.
- **Templates row** — genre starting points (Techno Warehouse, Festival
  Mainstage, Psy Forest, Synthwave Drive…): a template = director style +
  palette set + generator pool weighting.
- **Render queue widget** — live progress of background renders with pause/
  cancel; clicking opens the full queue (§13.6).

### 4.3 Project browser

Full-screen library view of all projects: search, sort (recent/name/
duration/BPM), tag filter, folder locations. Right-click: Open, Duplicate,
Reveal in Finder/Explorer, Archive (zips project + analysis cache, not the
audio unless "embed audio" is on), Delete (to OS trash). Missing-audio
projects show a relink badge; opening one prompts a relink dialog with
waveform fingerprint match to confirm the right file.

### 4.4 Analysis screen (new in V2 — the trust builder)

Shown automatically after import while analysis runs (2–10 s typical for a
60-min mix; progress per detector).

- **What:** a full-width waveform with the detected structure overlaid live
  as it computes: beat grid ticks, section blocks (colour-coded with the V1
  scheme: build=amber, drop=magenta, breakdown=teal…), drop markers, energy
  curve.
- **Why:** V1 taught us the analysis is usually right but must be *visible*
  to be trusted — and editable when wrong. This screen is the difference
  between "AI magic" and "a tool professionals rely on."
- **Interaction:** drag section boundaries; right-click a section to retype
  it (Drop → Breakdown etc.); tap-tempo override and ×½/×2 BPM buttons for
  the classic octave error; "downbeat nudge" arrows shift the bar grid by
  one beat. Every edit updates dependent markers live.
- **Exit:** one primary button — **"Generate show"** — runs the Director and
  opens the Editor. A secondary "Start empty" opens the Editor with only the
  audio + markers.

### 4.5 Editor (the main screen)

Default workspace layout (all panels dockable, §15.5):

```
┌────────────────────────────────────────────────────────────────────┐
│ Menu bar / transport / workspace switcher / render button          │
├───────────────┬───────────────────────────────┬────────────────────┤
│ ASSET BROWSER │        PREVIEW WINDOW         │     INSPECTOR      │
│ (generators,  │  (§11: playback, beat light,  │  (§10: parameters, │
│  presets,     │   fps, safe areas)            │   modulation,      │
│  palettes)    │                               │   keyframes)       │
├───────────────┴───────────────────────────────┴────────────────────┤
│ NAVIGATOR — full-track strip: sections + energy + markers (V1 bar) │
├────────────────────────────────────────────────────────────────────┤
│ TIMELINE (§9) — audio track + visual/camera/colour/adj. tracks     │
└────────────────────────────────────────────────────────────────────┘
```

- The **Navigator** is the kept V1 timeline strip: whole-track section
  colours + energy curve; a viewport rectangle shows the zoomed region the
  main timeline displays; click/drag to jump, wheel to zoom.
- The **Asset Browser** lists the visual library (§6) as live-animated
  thumbnails (rendered by the engine at 12 fps, paused when off-screen).
  Drag any item onto the timeline or preview.
- Panel sizes persist per workspace; double-click a divider resets it.

### 4.6 Export page

A focused modal-page (not a dialog buried in menus): left = summary of the
timeline (duration, sections, track count, estimated render time); centre =
format controls (§13); right = destination + filename tokens
(`{project}_{res}_{fps}`). Primary button **"Add to render queue"**;
secondary "Render now" (foreground). Presets across the top: **YouTube
1080p60 · YouTube 4K · Festival LED (custom res) · Instagram 1:1 ·
TikTok 9:16 · ProRes master** (each fully editable).

### 4.7 Settings page

Tabs: **General** (autosave interval, recent count, language),
**Performance** (GPU selection, preview quality cap, proxy resolution,
cache size/location, background-render core limit), **Playback** (audio
device, latency offset with tap-calibration), **Shortcuts** (fully
remappable, searchable, conflict-detecting), **Appearance** (UI scale,
panel theme — dark only at launch, but contrast options for accessibility).

### 4.8 Preset manager

Library view of everything saveable (§12) with category tree, tag chips,
search, favourites, and an import/export (`.pdogfx` bundles) flow.

### 4.9 The golden path (workflow contract)

`Drop file → (watch analysis draw itself) → Generate show → space to play →
Export → queue`. **Zero required decisions**; every screen along it has
exactly one primary button. All other screens exist to *refine*, never to
gatekeep.

---

## 5. Music analysis pipeline

All detectors run offline on import; results are stored in the project and
drive markers, the Director, and modulation sources. Each result carries a
**confidence score** surfaced in the Analysis screen (low confidence renders
the overlay dashed and nudges the user to confirm).

### 5.1 Detectors

| Detector | Method (v2) | Output |
|---|---|---|
| **BPM** | Onset-envelope autocorrelation (kept from V1) cross-checked by comb-filter bank; octave disambiguation biased by genre-typical ranges | Global BPM + per-region tempo map (handles DJ-mix tempo drift, ±8% window, 8-bar granularity) |
| **Beat grid** | Phase from strongest onsets (V1) refined by dynamic-programming beat tracking against the tempo map | Beat times, bar lines (4/4 assumed, meter override in UI) |
| **Kick** | Onset flux in 30–120 Hz, adaptive threshold vs. rolling average (V1 method, offline variant) | Event list with strength 0–1 |
| **Snare** | Flux in 150–400 Hz band + broadband noise ratio | Event list with strength |
| **Hi-hats** | Flux above 6 kHz, gated against snare events | Event list with strength |
| **Drops / build-ups / breakdowns** | V1 energy-quantile segmentation, upgraded: multi-band energy + spectral-flatness features; a drop = sustained high broadband energy *entered via* a rising-energy region | Sections + a per-drop "impact" score used by the Director to rank drops |
| **Chorus / repeated sections** | Self-similarity matrix on chroma + timbre features; repeated high-similarity regions labelled `chorus` | Section labels with similarity groups (A/B/C…) |
| **Energy** | 1 s-smoothed RMS, normalised (V1) plus per-band envelopes | Curves sampled at 50 Hz, available as modulation sources |
| **Bass / mid / high** | Band envelopes 30–150 / 400–2500 / 4000–12000 Hz (V1 bands kept) | Curves |
| **Vocal presence** | Harmonic-percussive separation + mid-band harmonic ratio (coarse, honest: "vocal likelihood," not isolation) | Curve, 0–1 |
| **Volume changes** | Short-term LUFS | Curve; also drives a render-time limiter warning if the source clips |
| **Song structure** | Composition of the above into the ordered section list | `intro/build/drop/breakdown/groove/chorus/outro` |

### 5.2 Realtime vs. offline duality (V1 lesson)

Preview playback uses the lightweight realtime detectors (V1's rolling-average
kick/snare) so scrubbing feels alive even before analysis finishes; the
render path and all keyframed/quantised behaviour use the offline event
lists, which are frame-exact. Both feed the same modulation-source names so
a project behaves identically, just more precisely, when rendered.

### 5.3 User corrections

Every analysis artefact is editable (Analysis screen §4.4 and directly on
timeline markers). Corrections are stored as overrides, never mutations —
re-running analysis (e.g., after relinking audio) preserves user edits and
flags conflicts.

### 5.4 Custom section types

Users can define section types (e.g., "Vocal moment," "ID drop") with a
colour; the Director treats unknown types as `groove` unless a pool is
assigned (§9.8).

---

## 6. Visual library

Every generator is a self-contained GPU program with: universal controls
(§7), its own specific parameters, default modulation bindings (§8), and at
least 3 factory presets. Asset Browser categories below. **Bold** items are
direct ports of V1 generators (parity day one); the rest are the dramatic
expansion.

### 6.1 Lasers (18)

**Horizontal**, **Vertical**, **Cross**, **Fan** (V1's hero), **Circle**,
**Spiral**, **Burst**, Pulse (width-throb beam set), Wave (sine-deformed
beam sheet), Multi-array (grid of emitters, phase-offset), Stadium (rows of
skyward beams behind a silhouette horizon), Sweep (single beam scanning like
a lighthouse), Scanner bank (Resolume-style moving-head simulation: pan/tilt
cones), Beam matrix (crossing grid with node flares), Laser text (beam-drawn
outline of user text/logo), Tunnel lasers (beams converging to vanishing
point), Liquid laser (beam refracted through simulated water caustics),
Mirror fan (kaleidoscopic reflection of any other laser layer).

Specific params (beyond §7): beam count, spread, origin(s), sway amount/rate,
beam length, tip fade, haze density (the V1 volumetric-wash control, now
per-clip), scroll phase.

### 6.2 Tunnels (16)

**Neon rings**, **Hex**, **Wireframe**, **Grid**, **Particle ring**
(all V1), Infinite hallway (rect corridor with panel lights), Circle
(smooth torus segments), Fractal zoom (Mandelbrot/Julia fly-through),
Spiral bore, Liquid (metaball walls), Plasma (volumetric noise walls),
Data tunnel (streaming glyph walls), Warp gate (concentric portal rings +
lens distortion), Mesh tube (displaced cylinder, music-driven ripples),
City flythrough (procedural neon buildings canyon), Star tunnel (hyperspace
streaks).

Specific params: ring/segment count, depth speed, twist, camera drift
(the V1 sin/cos wander, exposed), wall brightness falloff, fog.

### 6.3 Geometry (16)

Cube field, Sphere swarm, Triangle storm, **Rings** (concentric pulse rings),
Mandala (radially mirrored parametric pattern), DNA helix, Particle mesh
(points + proximity lines), Wireframe solids (icosa/dodeca spinning),
Abstract polygon flow, Supershape morph, Lissajous knots, Voronoi shatter,
Isometric grid blocks (equalizer-driven columns), Möbius ribbon, Platonic
explode/reform (beat-driven), Terrain wireframe (scrolling displaced grid —
synthwave staple).

### 6.4 Particle systems (15)

**Sparks/burst** (V1), Dust motes, Smoke (GPU fluid-lite), Fire, Electric
arcs (branching lightning between anchor points), Plasma blobs, Starfield,
Confetti, Rain (+ splash), Snow, Embers rising, Swarm (boids following
audio-attractors), Ribbon trails, Fireworks (drop-triggered mortars),
Shockwave rings (radial particle rings on kicks).

Specific params: emission rate, lifetime, gravity/wind, turbulence, size
curve over life, collision floor on/off, burst-on-event selector.

### 6.5 Backgrounds (14)

Solid/gradient field, Animated grid (perspective floor), Space/nebula
(layered noise + stars), Clouds, Noise field (Perlin/simplex, scale/octave
controls), Digital matrix rain, Abstract texture flow (curl-noise advected
colour), Aurora curtains, VHS static bed, Bokeh field, Horizon sun
(synthwave sun with slats), Concert silhouette (crowd + stage cutout
foreground layer), Scrolling skyline, Reactive album-art (user image with
audio-driven zoom/displacement/duotone).

### 6.6 Lighting & atmosphere (11)

Bloom, Lens flare (anamorphic + classic), Volumetric light shafts, God rays
(screen-space from brightest point), Glow (thresholded), Light leaks,
Strobe blinders (stage fixture rows aimed at camera), Moving-head wash
(coloured cones sweeping), Laser haze (global volumetric fog density),
Vignette light pulse, UV blacklight wash (remaps palette to UV-reactive
look — pairs with the Blacklight palette from V1).

### 6.7 Glitch & distortion (14)

RGB split, VHS (tracking error, tape noise, timecode), Datamosh (motion
vector smear), Pixel sorting, Scanlines, CRT (curvature + phosphor + sync
roll), Digital corruption (block dropout), Wave/ripple warp, Melt, Heat
haze, Compression artifact crunch, Frame-hold stutter (repeats frames on
beat), Slice shuffle (horizontal band offsets), Feedback echo (previous
frames zoomed/rotated — classic VJ feedback, tamed with decay limits from
the V1 haze lesson).

### 6.8 Camera effects (10) — implemented as camera *clips* (§9.4)

Zoom in/out, Push/Pull (dolly), Orbit, Rotation/roll, Shake (V1: scaled by
kick+bass, kept as default binding), **Bass zoom** (V1), Tilt, Whip pan
(transition-grade fast pan with blur), Handheld drift (organic noise), Snap
zoom on drop (auto-keyframed by Director).

### 6.9 Transitions (12) — clip type on the transition track

Flash/**white flash** (V1 section-boundary default), Blur cross, Spin,
Zoom punch, Ripple, Stretch, Fade, Strobe burst, Glitch cut, Luma wipe,
Ink/liquid wipe, Shutter (venetian slats).

### 6.10 Colour effects (10) — usually on adjustment layers

**Hue cycling** (V1 snare-stepped, kept as default binding), Saturation
pulse, Colour inversion flash, Gradient mapping (energy→gradient position),
Duotone/tritone, Posterize, **Palette engine** (the V1 10-genre palette
system as an effect: Cyberpunk, Synthwave, Hardstyle, Techno, Psytrance,
Trance, DnB, Acid, Neon, Blacklight + user palettes), Temperature shift,
LUT loader (.cube), Exposure/contrast/lift-gamma-gain (basic grade).

### 6.11 Spectrum & utility (8)

Waveform ribbon, Circular spectrum, Bar equalizer (classic, but styleable),
Oscilloscope XY, Beat counter HUD (stylised), Text/typography (words timed
to markers, per-word animation), Logo stinger (user PNG/SVG with reveal
animations), Countdown (drop countdown overlay — crowd-pleaser).

*Total: 134 generators/effects at launch. Every one ships with default
audio bindings so dragging it in already moves with the music.*

---

## 7. Universal effect controls

Every clip exposes this identical block at the top of the Inspector.
Consistency is the point: learn one effect, know them all.

| Control | Type / range | Behaviour |
|---|---|---|
| **Speed** | 0.1×–10×, log slider | Multiplies the clip's internal animation clock. Does not affect clip duration. |
| **Colour source** | Palette / Custom / Inherit | Palette = project palette (default; keeps global look coherent — V1 lesson). Custom = per-clip colours. Inherit = colour from the layer below (for effects). |
| **Palette index offset** | 0–3 | Rotates which palette slot the effect uses, so two clips sharing a palette don't look identical. |
| **Hue shift** | −180°–180° | Static offset applied after palette resolution. |
| **Saturation / Brightness** | 0–200% | Post-multipliers. |
| **Glow** | 0–1 | Bloom contribution of this clip (feeds the global bloom pass with a per-clip weight — replaces V1's global glow slider). |
| **Opacity** | 0–100% | Final alpha into the blend stack. |
| **Intensity** | 0–1 | The effect's own "amount" master — every generator maps this to its most characteristic quantity (beam brightness, particle rate, distortion strength). The Director animates this, not opacity, for musical dynamics. |
| **Scale** | 1–400% | Uniform scale about the anchor. |
| **Rotation** | −∞–∞ degrees + spin rate | Static angle plus optional constant spin. |
| **Direction** | effect-defined enum/vector | E.g. laser sweep direction, tunnel travel direction, particle gravity vector. |
| **Randomness** | 0–1 + Seed (int) | Blends between ordered and randomised variants of the effect. Seed makes it reproducible (determinism rule §3). "Reroll" button randomises the seed only. |
| **Pulse strength** | 0–1 | Global gain on all of this clip's audio modulation (a per-clip "how hard does this react" master — generalises V1's beat-sensitivity slider). |
| **Beat sensitivity** | 0.5–2.5 | Threshold scale for event detectors *as seen by this clip* (kept verbatim from V1, now per-clip). |
| **Frequency response** | Low/Mid/High tri-gain | Re-weights which bands this clip's default bindings listen to, without opening the modulation editor. |
| **Motion blur** | 0–1 | Per-clip shutter for the accumulation pass; 0.5 ≈ 180° shutter. |
| **Blend mode** | Normal, Add, Screen, Multiply, Overlay, Lighten, Colour Dodge, Difference | How the clip composites onto the stack below. Lasers/particles default to Add (V1's `lighter`). |
| **Start / End time** | timecode | The clip's bounds; editable numerically here, or by dragging in the timeline. |
| **Fade in / Fade out** | 0–10 s | Opacity ramps at the clip edges, drawn as handles on the clip itself. |

**Behaviour rules:** every numeric control supports click-drag scrubbing,
double-click to type, ⌥/Alt-click to reset to default, and right-click →
*Add keyframe / Add modulation / Copy value / Paste to selected clips*.
Every control shows a small **dot** when keyframed and a **wave icon** when
modulated (§8), coloured by its modulation source.

---

## 8. Audio-reactive modulation system

The generalisation of V1's hard-wired bindings. Any parameter can be driven
by any analysis signal.

### 8.1 Sources

**Events** (pulse-shaped): Kick, Snare, Hats, Beat, Bar, Drop-hit,
Section-change.
**Curves** (continuous): Bass, Mid, High, Vocal likelihood, RMS loudness,
Energy (1 s smoothed), Build progress (0→1 across a build section — new,
enables "rise" behaviours), BPM (as a value, e.g. for tunnel speed — V1
binding kept).

### 8.2 The modulation slot

Adding modulation to a parameter creates a slot with:

- **Source** (dropdown, grouped Events/Curves)
- **Amount** (−100%–100%; negative inverts — e.g. "duck opacity on kick")
- **Attack / Release** (ms; for events this shapes the envelope — defaults
  match V1's tuned decays: kick 0/120 ms, snare 0/140 ms)
- **Curve** (linear / exponential / S-curve response mapping)
- **Quantise** (events only: every hit / every 2nd / on-bar only)
- **Range clamp** (min/max the modulated value may reach)

A parameter accepts up to 4 slots; they sum, then clamp. The Inspector shows
a live-animating mini-meter inside each slot so users *see* the signal.

### 8.3 Modes (per clip)

- **Manual Mode:** modulation slots ignored; parameters follow static values
  + keyframes only. For precise hand-authored moments.
- **Auto Reactive Mode (default):** the effect's factory bindings + user
  slots run; keyframes on modulated parameters act as the *base line* the
  modulation rides on.
- **Hybrid Mode:** like Auto, but each parameter has a Manual/Reactive
  toggle chip, letting users freeze just one parameter (e.g. hand-keyframe
  colour while beams still pump with the kick).

The mode is a 3-way switch at the top of the Inspector; switching is
non-destructive (slots are kept, just bypassed).

### 8.4 Defaults (the V1 map, now factory bindings)

Laser width←Bass · Tunnel speed←BPM+Kick · Rotation←Kick · Hue step←Snare ·
Glow←High · Camera shake←Kick+Bass · Strobe/flash←Kick (gated by section
type) · Particle burst←Kick · Build effects' Intensity←Build progress.

---

## 9. Timeline editor

An NLE-grade timeline in the tradition of Premiere/Resolve, specialised for
music-locked visuals.

### 9.1 Track types

1. **Audio track** (exactly one, locked at bottom): the mix waveform,
   read-only audio, hosts the beat/section markers visually.
2. **Visual tracks** (unlimited): generator clips; higher track = rendered
   later = on top.
3. **Adjustment tracks:** effect/colour clips that process everything below
   (glitch, grade, palette).
4. **Camera track** (one, above visuals): camera clips (§6.8); exactly one
   camera clip active at a time, crossfading where they overlap.
5. **Transition track:** short transition clips that composite over
   everything at section boundaries.
6. **Marker lane:** beats (thin ticks), bars (heavier), sections (coloured
   spans, V1 colours), drops (⚡ badges), plus user markers (M key).

### 9.2 Navigator

The kept V1 strip (§4.5): always shows the whole track; the main timeline is
a zoomed window into it. This solves hour-long-mix navigation cheaply.

### 9.3 Clip model & manipulation

- **Drag** anywhere (snapping, §9.5); vertical drag moves between compatible
  tracks.
- **Trim** edges (ripple trim with ⌘/Ctrl held: later clips on that track
  shift).
- **Stretch** (⌥/Alt-drag edge): time-remaps the clip's internal animation
  (speed indicator badge appears).
- **Loop** (clip-level toggle): the generator's cycle repeats to fill the
  clip; loop boundaries drawn as notches.
- **Crossfade:** overlapping two clips on one track auto-creates an opacity
  crossfade; the overlap region shows an X-fade curve, draggable between
  linear/equal-power.
- **Copy/Paste/Duplicate** (⌘C/⌘V/⌘D); paste lands at playhead on the
  originating track; ⌘⇧V pastes *attributes* (parameters/modulation only)
  onto selected clips.
- **Group** (⌘G): grouped clips move/trim together and collapse to a single
  bar when the group is closed.
- **Track headers:** name, colour, **Lock** 🔒, **Hide** 👁, **Solo** S,
  target-track toggle (where drops from the Asset Browser land).

### 9.4 Keyframes

Every parameter is keyframeable. A clip shows a disclosure triangle →
per-parameter keyframe lanes inside the timeline (Resolve-style), plus a
curve editor mode (value graph with bezier handles). Keyframes snap to the
beat grid by default. Right-click a keyframe: interpolation (hold / linear /
bezier / bounce), "quantise to beat," delete.

### 9.5 Snapping & the beat grid

Snap targets, in priority order: section boundaries → drop markers → bars →
beats → other clip edges → playhead. Toggle with `N`; hold `⌘/Ctrl` to
bypass momentarily. Snap strength is distance-based with a magnetic feel;
the snapped target flashes. **This is the core differentiator versus
generic NLEs: everything wants to land on the music.**

### 9.6 Editing operations

Blade (`B`/click or ⌘K at playhead), ripple delete (⇧Del closes the gap),
insert vs. overwrite drop (modifier badge while dragging), select-all-right
(`A` tool) for shifting the rest of the show, range select on the ruler for
loop-preview and targeted render preview.

### 9.7 Zoom & navigation

`+`/`-` or pinch to zoom about the playhead; `\` zoom-to-fit; ⌥-wheel zoom,
wheel scroll, middle-drag pan. `↑`/`↓` jump section boundaries; `←`/`→`
step frames; ⇧←/→ step beats; `Home`/`End` start/end. JKL shuttle.

### 9.8 Director integration (Auto-Generate as an editing action)

"Generate show" (toolbar ⚡, and per-section via right-click → *Regenerate
section*) writes **ordinary clips** onto the tracks using the V1 pools
(section type → weighted generator pool → deterministic pick by seed):

- Every generated clip is tagged "Director" (small ⚡ badge) until the user
  edits it, then the badge clears.
- Regenerating **never touches user-edited clips**; it only replaces
  still-badged ones. This is the contract that makes Auto and manual editing
  coexist.
- A seed field in the toolbar ("Show variation") lets users reroll the whole
  show deterministically — V1's determinism, now user-visible.

---

## 10. Inspector panel

Context panel, right side. Selecting a clip shows, top to bottom:

1. **Header:** effect icon + name (rename inline), enable checkbox, the
   Manual/Auto/Hybrid mode switch (§8.3), preset dropdown (factory + user
   presets for this effect; save/update buttons), **Randomise** 🎲 (rerolls
   Randomness seed + jitters unlocked parameters ±15% — every parameter has
   a tiny lock to exempt it), **Reset** (factory defaults, confirm if >5
   parameters changed).
2. **Universal controls block** (§7), collapsible.
3. **Effect-specific parameters**, grouped with headers (e.g. Lasers:
   *Beams / Motion / Look*).
4. **Modulation section:** list of all slots on this clip, each expandable
   (§8.2), with per-slot bypass and a global "×" pulse-strength mirror.
5. **Keyframe strip:** for the selected parameter, prev/add/next keyframe
   buttons and a mini-curve; "open in curve editor" jumps the timeline into
   curve mode scrolled to that parameter.

Multi-select shows shared parameters only; edits apply to all (values that
differ display "—" until touched). Colour parameters open the **palette-aware
picker**: project palette swatches first, then HSL wheel, hex field, and an
eyedropper that samples the preview.

---

## 11. Preview window

- **Playback:** realtime engine render at adaptive quality (§14.3);
  space = play/pause, JKL shuttle, scrub by dragging the transport bar.
- **Beat indicator:** a subtle pulse dot row — beat/bar flash, plus kick/
  snare/hat mini-meters (the V1 HUD, professionalised). Toggleable.
- **Audio waveform strip** under the image with playhead (mirrors Navigator
  colours).
- **Frame counter / timecode** (toggle frames↔SMPTE) and **FPS monitor**
  with a colour state (green ≥ target, amber within 20%, red below —
  clicking it opens Performance settings).
- **Camera controls:** when a camera clip is selected, orbit/pan/dolly the
  preview directly (drag/⇧-drag/wheel); a "return to clip path" button
  discards or keyframes the manual offset.
- **Full-screen preview** (`F`): image only, ESC returns.
- **Multi-monitor:** "Preview on display 2" detaches a borderless fullscreen
  output (for client review or LED-wall check) mirroring the program out.
- **Safe-area guides:** overlay menu with 16:9 title-safe, 1:1 and 9:16
  crop-preview frames (social exports), LED pixel-map grid, and a
  centre-cross. Guides never render to export.
- **Quality menu:** Full / Half / Quarter resolution + "pause background
  thumbnails" — explicit user control over the §14.3 auto behaviour.

---

## 12. Preset system

**Saveable units:** effect preset (one clip's parameters+modulation),
colour palette, camera move (camera clip + keyframes), scene (a group of
clips across tracks spanning a time range, stored duration-relative and
beat-relative so it re-times to any BPM), show template (Director pool
weights + palette set), full project template.

**Organisation:** categories (by type + genre), free tags, search-as-you-type
(name/tag/type), favourites ⭐, "Recent" smart list. Factory content ships
in every category; user content sorts above factory in results.

**Format:** `.pdogfx` — a zip of JSON + any embedded user media, versioned,
drag-in/out of the app for sharing. Applying a preset is undoable as one
step.

---

## 13. Rendering engine

### 13.1 Formats

- **MP4 is the default container.** H.264 default codec (compatibility),
  **H.265** optional (size/quality), both via hardware encoders (NVENC /
  AMF / VideoToolbox) with automatic x264/x265 software fallback.
- ProRes 422/4444 (master/alpha use), PNG sequence (LED workflows).
- **WebM is not offered** as a primary path (V1's forced WebM was a
  browser limitation, explicitly retired).

### 13.2 Resolution & frame rate

1920×1080 minimum default; 2560×1440, 3840×2160, 7680×4320; custom
resolution field (LED walls — any aspect, up to encoder limits, with a
non-standard-size warning). 30 / 60 / 120 fps. Social presets flip to
1080×1920 (9:16) and 1080×1080 (1:1) with automatic reframe (generators are
resolution-independent; no letterboxing ever).

### 13.3 Quality

Bitrate modes: CQ/CRF quality slider (default, mapped 1–100 with plain
labels) or explicit bitrate (up to 200 Mbps). 4:2:0 default; 4:2:2/4:4:4
where codec allows. Audio: source passthrough when possible, else AAC 320.

### 13.4 The render path (determinism)

Fixed-timestep re-render of the project at target fps — **never** a capture
of preview playback (V1's dropped-frame risk eliminated). Motion blur via
per-clip shutter accumulation (§7). Bit-identical across runs on the same
hardware; visually identical across GPUs within tonemap tolerance.

### 13.5 Progress & estimation

Estimated render time from a 24-frame benchmark sampled across the timeline
(busy sections included) — shown on the Export page *before* committing.
During render: fps, frame x/y, elapsed/remaining, live thumbnail every 2 s.

### 13.6 Render queue & background rendering

Multiple jobs (same or different projects) queue and run in the background
at configurable core/GPU priority ("Balanced" default keeps the editor
usable). Jobs are pausable, reorderable, and **resumable: renders are
chunked into GOP-aligned segments; a failed/cancelled render keeps finished
segments and resumes at the last complete one** (final losslessly
concatenated). Queue survives app restarts. OS notification + optional
sound on completion.

---

## 14. Performance architecture

### 14.1 GPU

wgpu backend → **Metal** (Apple Silicon/Intel Mac), **Vulkan/DX12**
(Windows: NVIDIA, AMD, Intel Arc). Encoder acceleration NVENC/AMF/
VideoToolbox (§13). No CUDA-only features: parity across vendors is a
product requirement; vendor-specific paths are optimisations only.

### 14.2 CPU

Analysis, particle simulation pre-pass, and software-encode fallback are
multi-core (rayon work-stealing). Analysis of an hour mix targets <10 s on
a 4-core baseline.

### 14.3 Staying smooth with hundreds of effects

- **Adaptive preview resolution:** playback drops to ½/¼ internal res to
  hold target fps; UI badge shows current level (§11 quality menu overrides).
- **Cached previews:** rendered frames are cached per (clip-stack hash,
  time); unchanged regions of the timeline scrub from cache instantly.
  Cache is disk-backed with an LRU size cap (Settings).
- **Proxy playback:** audio decodes once to an internal PCM proxy;
  heavy generators (fluid, fractal) precompute at import into loopable
  buffers where exact realtime isn't needed for *preview*.
- **Clip culling:** clips outside the playhead, hidden, or fully occluded by
  opaque layers above are not evaluated.
- **Thumbnail throttling:** Asset Browser animations render on a shared
  12 fps budget and pause off-screen or during playback (if fps is at risk).

---

## 15. User experience systems

### 15.1 Keyboard shortcuts (defaults; all remappable, §4.7)

| Action | macOS | Windows |
|---|---|---|
| Play/pause | Space | Space |
| Shuttle | J K L | J K L |
| Step frame / beat | ←→ / ⇧←→ | same |
| Jump section | ↑ ↓ | same |
| Blade at playhead | ⌘K | Ctrl+K |
| Blade tool / Select tool | B / V | B / V |
| Snapping toggle | N | N |
| Marker | M | M |
| Group | ⌘G | Ctrl+G |
| Duplicate | ⌘D | Ctrl+D |
| Paste attributes | ⌘⇧V | Ctrl+Shift+V |
| Ripple delete | ⇧⌫ | Shift+Del |
| Zoom fit | \ | \ |
| Fullscreen preview | F | F |
| Generate show / regenerate selection | ⌘⇧G | Ctrl+Shift+G |
| Export page | ⌘E | Ctrl+E |
| Undo / Redo | ⌘Z / ⌘⇧Z | Ctrl+Z / Ctrl+Y |
| Save | ⌘S | Ctrl+S |
| Search assets/presets | ⌘F | Ctrl+F |

### 15.2 Right-click menus (context-complete, no dead zones)

- **Clip:** Cut/Copy/Paste attributes, Duplicate, Group, Regenerate
  (Director clips), Save as preset, Change generator (keeps timing +
  compatible params), Mode (Manual/Auto/Hybrid), Colour label, Delete.
- **Track header:** Add track (type submenu), Rename, Colour, Lock/Hide/
  Solo, Delete track.
- **Marker lane / section:** Retype section, Split section at playhead,
  Merge with next, Regenerate section, Add marker.
- **Preview:** Quality, Guides, Detach to display, Copy frame (PNG to
  clipboard), Set poster frame.
- **Asset browser item:** Add at playhead, Add to section (fills current
  section), Favourite, Show presets.

### 15.3 Drag-and-drop

Audio anywhere → new project (or "replace audio?" inside a project).
Generator → timeline (clip, snapped) or → preview (adds on the target
track at playhead, full-section length). Preset → clip (applies) or →
timeline (new clip). Palette → preview (project palette) or → clip
(per-clip). Image/logo → media generators. `.pdogfx` → imports.
All drags show a valid/invalid cursor state and a ghost preview.

### 15.4 Dockable panels & workspaces

Panels (Assets, Preview, Inspector, Timeline, Navigator, Modulation mixer,
Render queue) tear off, dock to any edge, tab-stack, or float (multi-monitor
editing). Workspace presets: **Edit** (default §4.5), **Design** (huge
preview + Inspector), **Structure** (Analysis view + Navigator maximised),
**Render** (queue focus). Saved custom workspaces; `⌥1–4` switches.

### 15.5 Undo/Redo

Command-pattern history, 500 steps, scoped per project. Everything is
undoable including analysis-override edits, Director generation (one step),
preset application, and track operations. History panel (menu) shows the
named step list with click-to-jump. Redo stack survives autosave.

### 15.6 Autosave

Every 2 minutes (configurable) and before: render, generate, close.
Autosaves go to a rolling `.pdog.autosave` ring (10 deep) beside the
project; explicit ⌘S commits a real save. Idle saves are skipped
(dirty-flag), and saves are atomic (write-temp-then-rename).

### 15.7 Project recovery

On crash, next launch detects the journal + autosave ring and offers:
*Restore last autosave (2 min ago) / Open last manual save / Show both in
browser*. Render-queue jobs resume per §13.6. The recovery prompt shows
thumbnail + timestamp for each candidate so the choice is informed.

---

## 16. Project & data formats

- **`.pdog` project:** JSON (versioned, migration on open), containing:
  audio reference (path + content fingerprint) or embedded audio (opt-in),
  full analysis results + user overrides, tracks/clips/keyframes/modulation,
  palettes, workspace state, Director seed. Human-diffable by design.
- **Analysis cache:** binary sidecar keyed by audio fingerprint — re-import
  of a known file skips analysis.
- **`.pdogfx` presets:** §12.
- **Determinism contract** (§3) means a `.pdog` + audio file fully
  reproduces a render anywhere — the foundation for future collaboration
  features, and the reason support can reproduce user bugs from a project
  file alone.

---

## 17. Engineering reference — V1 code as the prototype map

The V1 sources on this branch are the living reference for the algorithms
this spec keeps:

| V2 spec section | V1 reference |
|---|---|
| §5 detectors (BPM autocorrelation, onset flux, energy-quantile sections) | `js/analysis.js` |
| §5.2 realtime detector (band energy vs. rolling average, sensitivity) | `js/audio.js` `update()` |
| §8.4 default bindings, §7 haze/strobe-decay defaults | `js/visuals.js` |
| §9.8 Director pools & deterministic per-section rotation | `js/director.js` |
| §6.10 palette definitions | `js/palettes.js` |
| §4.5 Navigator (section colours + energy curve + seek) | `js/main.js` `drawTimeline()` |

The V1 renderer, capture path (`js/export.js`), and global-settings model
are explicitly **not** carried forward (§1.2).
