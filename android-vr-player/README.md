# Poopdog VR — Android VR video player for phone headsets

An Android app (APK) that turns any phone into a VR video player for Cardboard‑style
headsets (Google Cardboard, VR Box, BOBOVR and other "drop‑in" viewers). It converts
ordinary and VR videos into the side‑by‑side, lens‑corrected image a phone headset
needs, and plays a **queue of videos hands‑free**, with an optional **loop count per
video** before moving on to the next.

- **Download:** [`dist/PoopdogVR-1.0.0.apk`](dist/PoopdogVR-1.0.0.apk) (Android 7.0+, ~2.5 MB)
- **Source:** this folder (Kotlin, Jetpack Compose, Media3/ExoPlayer, OpenGL ES 2.0)

## Install

1. Copy/download `PoopdogVR-1.0.0.apk` to the phone (or open the file's GitHub page on the phone and tap *Download raw file*).
2. Open it and allow *Install unknown apps* for your browser/file manager when Android asks.
3. Open **Poopdog VR**, tap **Allow access** (or **Pick files**) to see your videos.

> The APK is signed with a local development key. If you later install a build signed
> with a different key, Android will ask you to uninstall the old one first (your queue
> and settings are then reset). See *Build from source* for using your own key.

## Using it

**Play one video:** Library → tap the headset button on a video. Put the phone in the headset.

**Hands‑free queue:**
1. Library → **⋮ → Add to queue** (or long‑press to select several → add).
2. Queue tab → set **Loops** for each video with **−/+** (×1 … ×99, then ∞ = until skipped).
   *⋮ → Set loops for all* applies one count to every video.
3. Pick **Shuffle**, **Repeat queue**, a **Sleep** timer and the **Gap** (countdown between videos).
4. **Play queue.** Each video plays its loop count, an "Up next" countdown shows, and the next one starts — no touching needed.
   *Resume* continues where you stopped last time.

**In the headset**

| Do this | Result |
|---|---|
| Tap the screen / press the viewer's button | Show or hide the control panel (or press the button you're looking at) |
| Look down at the floor for a moment | Show the control panel |
| Look at a button until the ring fills | Press it (Prev, −10 s, Play/Pause, +10 s, Next, Loop −/+, Format, Speed, Recenter, Exit, seek bar) |
| Drag on the screen | Look around (when head tracking is off) |
| Pinch (phone‑only mode) | Zoom |

**Bluetooth remotes, gamepads, keyboards:** Play/Pause, A or Enter = play/pause (or press the
gazed‑at button) · Next/Prev, R1/L1 = next/previous video · D‑pad ←/→ = seek 10 s ·
D‑pad ↑, Y, Menu = control panel · D‑pad ↓, X = recenter · B = hide panel · L = add a
loop · F = change format. Optionally, volume keys can do next / play‑pause (Settings).

## How "conversion" works

Every frame is decoded by ExoPlayer into an OpenGL texture and redrawn for the headset:

1. **Projection.** Flat video goes on a virtual cinema screen (size, distance and curve
   adjustable); 180°/360° equirectangular video is wrapped on a sphere; 180°/190°/200°
   fisheye video on a dome. Head tracking (gyro) lets you look around.
2. **Stereo.** For 3D files each eye samples its own half (side‑by‑side, top/bottom, or
   swapped RL/BT). **2D→3D** adds synthetic depth to flat 2D video (lower and brighter areas
   come forward).
3. **Lenses.** Each eye is rendered off‑screen and barrel‑pre‑warped with the
   Cardboard distortion model (k1, k2), centred on your headset's lens spacing, so straight
   lines stay straight through the lenses.

The format is detected automatically from the file name (`_180_LR`, `_360_TB`,
`_fisheye190`, `SBS`, `HSBS`, `OU`, `3D`, …), MP4 stereo/spherical metadata, and the
frame shape. You can override it per video (Library ⋮ → *Set VR format…*, or the
**Format** button in the headset); the choice is remembered.

**Convert to VR file** (Library ⋮ → *Convert to VR file…*) writes a real side‑by‑side
MP4 to `Movies/PoopdogVR` using hardware encoding: 2D → 3D (synthetic depth), 2D → VR
(same image both eyes), or 3D top/bottom → side‑by‑side; full SBS (2× wide) or half SBS.
The output plays in this app and in other VR players.

## Research: what similar apps offer, and what's included

I looked at the common phone‑VR players — **VaR's VR Video Player**, **Mobile VR Station**,
**HeadVR** (open source), **AAA VR Cinema**, **VRTV**, **3D VR Player / VR Player**,
**Cardboard Theater**, and the auto‑detect conventions used by **DeoVR** and **SKYBOX**.
Their shared baseline, and where it lives in this app:

| Baseline feature in those apps | Seen in | Poopdog VR |
|---|---|---|
| 2D, 3D SBS, 3D top/bottom, 180°, 360° playback | all | ✅ plus fisheye 180/190/200 and swapped eyes |
| Auto‑detect format (file‑name tags) | DeoVR, Skybox, Cardboard Theater | ✅ tags + MP4 metadata + frame shape |
| 2D → 3D conversion / SBS converter | AAA VR Cinema, VaR's | ✅ live 2D→3D, and export to SBS file |
| Head tracking + recenter | all | ✅ yaw‑only or full (lying‑down) recenter |
| Gaze / head control without touching | Mobile VR Station, HeadVR | ✅ dwell buttons, look‑down menu |
| Bluetooth controller / headset buttons | Mobile VR Station, HeadVR | ✅ media keys, gamepads, keyboards |
| Playlists / queue browsable in headset | HeadVR | ✅ queue with Prev/Next in headset, saved playlists |
| Virtual cinema screen size & distance | VaR's, Mobile VR Station | ✅ plus curved screen, screen‑follows‑head |
| Lens distortion & IPD settings / viewer profiles | Cardboard apps, VaR's | ✅ presets + k1/k2/IPD/FOV/offset sliders |
| Local files and network streams | VaR's, HeadVR, VR Player | ✅ device library, file picker, HTTP/HLS/DASH URLs |
| Subtitles and track selection | HeadVR | ✅ embedded + attached .srt/.vtt/.ass |
| Force 2D for eye comfort | HeadVR | ✅ Format → 2D, or Phone‑only mode |
| Phone‑only (no headset) "magic window" | most | ✅ with pinch zoom |
| Resume position, playback speed | common | ✅ |
| Loop **N times** per video then advance | — (rare: apps offer loop‑one/loop‑all only) | ✅ |
| Sleep timer, "up next" countdown | — | ✅ |

Not included (yet): scanning a Cardboard viewer QR code, DLNA/SMB network shares,
spatial/ambisonic audio, and a VR file browser inside the headset (the queue is built on
the phone screen, then played hands‑free).

## Settings worth knowing

- **Viewer** presets (Cardboard 2015, Cardboard 2014, plastic VR‑Box style, wide‑FOV, no
  lenses). If lines look curved or the two images don't merge, adjust **Lens spacing**,
  **k1/k2** and **Vertical lens offset** — moving any slider switches to *Custom*.
- **Screen follows head** keeps flat movies in front of you when lying down.
- **Gaze dwell time**, **Look down to open menu**, **Hide menu after** tune hands‑free use.
- **Default loops**, **Pause between videos**, **Skip videos that fail** for queue behaviour.

## Build from source

Requirements: JDK 17+ and the Android SDK (platform 36, build‑tools 36).

```sh
cd android-vr-player
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

To sign with your own key, create `keystore.properties` next to `settings.gradle.kts`
(never commit it):

```properties
storeFile=my-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without it the release build is signed with the local debug key.

**Shader check:** `./gradlew testDebugUnitTest` also dumps all GLSL shaders and meshes to
`app/build/shader-dump/`; serve the folder (`python3 -m http.server`) and open
`tools/shader-harness.html` to compile them with WebGL and see the rendered headset output.

## Code map

| Path | What it does |
|---|---|
| `vr/VrActivity.kt` | Full‑screen headset player: ExoPlayer, queue/loop execution, gaze/tap/key input, sleep timer, resume |
| `vr/VrRenderer.kt` | OpenGL pipeline: projection meshes, per‑eye render, HUD/subtitles/reticle, lens pre‑warp |
| `vr/Shaders.kt`, `vr/Meshes.kt`, `vr/ViewerProfile.kt` | GLSL, sphere/fisheye/screen geometry, Cardboard distortion model |
| `vr/HeadTracker.kt`, `vr/Quat.kt` | Rotation‑vector sensor → camera orientation, recenter |
| `vr/Hud.kt`, `vr/GazeController.kt` | In‑headset control panel (Canvas → texture) and dwell‑to‑click |
| `playback/QueueEngine.kt` | Pure state machine for queue order, loop passes, shuffle/repeat |
| `model/FormatDetector.kt` | VR format auto‑detection |
| `convert/` | Media3 Transformer export to side‑by‑side MP4 |
| `ui/` | Compose screens: Library, Queue, Settings |
| `data/` | Settings, queue/playlists, per‑video memory, MediaStore access |

## Testing status

- 40+ JVM unit tests cover the queue/loop engine, format detection, head‑pose maths,
  distortion and projection meshes, gaze dwell, export sizing and queue persistence.
- All shaders compile and render correctly in WebGL (GLSL ES 1.00, same as the phone).
- Lint passes on the release build.
- **Not yet tested on a physical phone/headset** — the build machine has no Android
  device or emulator. Please report anything that looks off (especially lens settings for
  your specific viewer).
