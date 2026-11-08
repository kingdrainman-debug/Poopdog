# GripCam: control your Android phone's camera with a Sony GP-VPT2BT grip

GripCam is a pro-style Android camera app that pairs with a **Sony GP-VPT2BT wireless
shooting grip** (and should also work with the **RMT-P1BT** remote). Once paired, the grip's
buttons drive the phone's camera:

| Grip control | What the phone does |
|---|---|
| **PHOTO**, half press | Autofocus on the centre and hold it (green box = focused) |
| **PHOTO**, full press | Take a photo. In *Burst* drive mode, keeps shooting while held |
| **MOVIE** | Start / stop video recording |
| **Zoom lever T / W**, hold | Smooth zoom; speed follows the lever if the grip reports it |
| **Zoom lever T / W**, double press | Jump to the next / previous zoom stop (1× → 3× → 6×, and back) |
| **C1** | Switch front / back camera, **even mid-recording** (or another action of your choice) |
| **C1**, double press | Optional second action (torch, AE/AF lock, grid, next zoom stop…) |
| AF-ON / Focus +/– (RMT-P1BT) | Focus lock / manual focus (with *Treat focus codes as zoom* off) |

Photos go to `Pictures/GripCam` and videos to `Movies/GripCam`, so they appear in your gallery.

> **Status.** Pairing and zoom are confirmed working with a real GP-VPT2BT. Version 2's
> camera features are built on CameraX 1.6 and covered by unit tests, simulations and rendered
> screenshots, but each phone's camera hardware differs, so see [Troubleshooting](#troubleshooting)
> if a mode doesn't behave.

## Features

**Video**
- **Stabilization**: *Standard* (smooth recorded video) or *Steady* (the strongest mode;
  steadies the viewfinder too, with a slightly tighter crop). Optical stabilization stays on.
- **Switch cameras mid-recording**: C1 (or the on-screen button, or a double tap on the
  viewfinder) flips between back and front without stopping. It's one continuous file.
- **Quality**: 4K / 1080p / 720p / 480p, 24 / 25 / 30 / 60 fps, **HDR (HLG 10-bit)**, bitrate
  Standard / High / Max, low-light boost, sound on/off. Only what your phone supports is offered.
- **Smart fallback**: if a combination isn't possible (say 4K60 + HDR + Steady), GripCam
  steps down least-important-first and tells you what it dropped.
- **Recording HUD**: timer, live audio meter, resolution / fps / HDR / stabilization badges,
  recording time left on storage, battery.

**Photo**
- **Night, HDR, Portrait, Auto, Retouch** modes where the phone maker provides them.
- **RAW + JPEG** (DNG) and **Ultra HDR** formats where supported; JPEG quality.
- Shutter priority: best quality, fast, or zero shutter lag.
- **Burst while held**, **self-timer** 3 s / 10 s with countdown beeps (works from the grip),
  mirror-front-camera option.

**Viewfinder and controls**
- **What you see is what you get**: the viewfinder shows exactly the 16:9 or 4:3 frame
  being captured.
- **Zoom stops** row (ultra-wide, 1×, 3×, 6×, …) with smooth animated jumps; pinch to zoom.
- Tap to focus, **long-press to lock focus and exposure (AE/AF lock)**, double tap to switch camera.
- **Exposure compensation** slider and **white balance** presets (Auto / Daylight / Cloudy /
  Tungsten / Fluorescent).
- **Grid** (thirds, golden ratio, centre cross) and an **electronic level** that turns green
  when the shot is straight. It works with the phone turned sideways on the grip too.
- Quick-settings bar for flash, stabilization, HDR, photo mode, timer, grid, EV, WB, frame.
- **Volume buttons** (and Bluetooth selfie remotes) as shutter, record or zoom.
- Gallery thumbnail of the last shot.

**Settings**: a full settings screen in five sections (Video, Photo, Viewfinder, Grip
buttons, Connection) that only shows options your phone supports. It's built from one
catalog, so a new setting needs one declaration to get storage and a screen row
(see [Adding a setting](#adding-a-setting)).

## Install

**Option A: sideload a prebuilt APK.** Copy the debug APK (`GripCam-debug.apk`, or
`app-debug.apk` from a build) to the phone, open it, and allow "install unknown apps" for
your file manager when Android asks. Installing over the previous version keeps your
pairing and settings.

**Option B: build it yourself** (JDK 17+ and the Android SDK, or just open the folder in Android Studio):

```bash
cd grip-cam-android
./gradlew assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requirements: Android 8.0+ with Bluetooth LE **peripheral** support. Almost every phone from
the last several years has it; if yours doesn't, the status chip says
*"This phone can't host a Bluetooth grip"*.

## Pair the grip

1. Open GripCam and allow **Camera**, **Nearby devices** (Bluetooth) and **Microphone**.
   The status chip at the top should say *Ready to pair*.
2. On the grip, make sure the **lock switch is off**.
3. Hold **PHOTO** and the **T** side of the zoom lever together for about **7 seconds**
   (Sony's pairing gesture for this grip).
4. If Android asks to pair, tap **Pair**. The chip turns green with the grip's name.

Next time, press any grip button to reconnect. If it doesn't reconnect (see below), pair again.
To forget a grip, unpair it in Android's Bluetooth settings.

If the grip was paired to a Sony camera before, it now belongs to the phone. Re-pair it with
the camera when you want to use it there again (a grip remembers one camera at a time).

## Troubleshooting

- **A setting says "Not available together on this camera".** The phone can't combine
  everything you picked; the message names what was dropped. Lower the frame rate or turn
  off HDR / Steady to get it back.
- **Switching cameras mid-recording stops with a message.** The other lens can't continue
  with the current video settings (often 4K or Steady on the front camera). Use 1080p and
  Standard stabilization for recordings you'll flip during.
- **The front-camera part of a flipped recording plays upside down.** This has been reported
  for CameraX's camera switching on some phones. Please report your phone model.
- **Grip never connects.** Check the chip says *Ready to pair*, the grip's lock switch is off,
  and you held PHOTO + T for 7+ seconds. Then try **Settings → Connection → Advertised
  protocol version 0x65**, and after that the other model codes, re-pairing each time.
- **Pairs once, won't reconnect next time.** Android advertises from a private, rotating
  Bluetooth address; a grip that doesn't resolve private addresses can only find the phone
  by pairing again. Do the 7-second pairing gesture again; it takes seconds.
- **Double press zooms a little instead of jumping.** Press faster: each press under 0.3 s,
  the second within 0.4 s of the first.
- **A button does nothing.** Turn on **Show control log**, press the button, and look for a
  `◀` line. If it says `unknown command`, the byte values in that line are what the
  mapping needs; please report them.
- **No sound in videos.** Grant the Microphone permission in Android's app settings.

## How it works

A Sony remote is a Bluetooth LE **central**: it scans for a camera, connects, bonds and
then writes button presses to it. The camera is the **peripheral**. GripCam makes the phone
the peripheral that looks like a Sony camera:

**1. Advertising.** Manufacturer data under Sony's company ID `0x012D`:

```
2D 01      Sony company ID (little-endian)
03 00      device type: camera
64 00      protocol version, reserved
45 31      model code "E1" (E-mount body)
22 EF 00   tag 0x22: pairing supported/enabled, remote control enabled (0xAF when not pairing)
21 40 00   tag 0x21: camera powered on
```

`0xEF` sets every bit that any published remote-side check looks for (freemote and furble
test `0x40|0x02`; alpharemote and ekutner observed `0x04` as "remote enabled" on newer bodies).

**2. GATT service** `8000FF00-FF00-FFFF-FFFF-FFFFFFFFFFFF`:

| Characteristic | Direction | Content |
|---|---|---|
| `FF01` (write) | grip → phone | `01 cc` buttons, `02 cc ss` jogs (`cc` odd = pressed, even = released; `ss` = speed) |
| `FF02` (notify) | phone → grip | `02 3F xx` focus, `02 A0 xx` shutter, `02 D5 xx` recording (`xx` = `20` true / `00` false) |

| Code (press / release) | Meaning |
|---|---|
| `07` / `06` | Shutter half press |
| `09` / `08` | Shutter full press |
| `0F` / `0E` | Record |
| `15` / `14` | AF-ON |
| `21` / `20` | C1 |
| `45` / `44`, `47` / `46` | Zoom T / W (alpharemote, sarnau) |
| `6B` / `6A`, `6D` / `6C` | Focus near / far (alpharemote, sarnau); listed as zoom W / T by Greg Leeds and CameraSync |

The phone reports focus, shutter and recording back over `FF02`, as a real camera does.

### Code layout

| Path | Role |
|---|---|
| `grip/SonyRemoteProtocol.kt` | UUIDs, advertisement bytes, command parser, status encoder (pure Kotlin) |
| `grip/GripActionMapper.kt` | Press/release stream → camera actions: double-press detection, burst press/release (pure Kotlin) |
| `grip/GripServer.kt` | BLE advertiser + GATT server, bonding, notification queue, Bluetooth on/off handling |
| `camera/CameraController.kt` | CameraX 1.6 engine: binding, persistent recording + mid-recording lens switch, photo/RAW/burst, AF/AE lock, EV, WB, manual focus, zoom |
| `camera/BindPlanner.kt` | Which use-case/feature combinations to try, in what order (pure Kotlin) |
| `camera/ZoomMath.kt`, `VideoMath.kt` | Zoom stops, bitrate targets, storage estimates (pure Kotlin) |
| `settings/Settings.kt`, `Options.kt` | Every setting, its storage, and the settings catalog |
| `MainViewModel.kt` | Wires grip and hardware keys → camera, camera state → grip notifications; self-timer, C1 double press, thumbnails |
| `ui/` | Compose viewfinder, quick bar, overlays (grid, level, reticle, countdown, audio meter), settings screen |

### Adding a setting

1. Declare it in `Prefs` (`settings/Settings.kt`): a `ToggleSetting`, `SliderSetting`, or a
   `ChoiceSetting` over an enum that implements `Labeled`. Give it a key, default, title and
   summary, and optionally `isSupported` / `visibleWhen` to tie it to camera capabilities.
2. Add it to a section in `SettingsCatalog`. It's now saved and shown on the settings screen.
3. Read it anywhere with `settings[Prefs.YOUR_SETTING]`.

## Tests

```bash
./gradlew testDebugUnitTest
```

- `SonyRemoteProtocolTest`: the advertisement against every published remote-side check;
  every button, jog, concatenated and malformed write.
- `GripActionMapperTest`: the shutter sequence, burst press/release, AF-ON, record
  toggling, both zoom-code families, invert, double-press snapping (timing windows, T vs W,
  triple press), and cleanup on disconnect.
- `GripServerTest` (Robolectric, Android 11 and 14): plays the grip's side end to end:
  service registration, advertising, connect, subscribe, button presses, disconnect,
  Bluetooth and permission changes.
- `CameraMathTest`: zoom stops, bind-plan ordering, bitrate/storage maths, level angles,
  audio meter scale.
- `SettingsStoreTest` (Robolectric): defaults, persistence, migration from version 1,
  corrupt values, capability filtering.
- `UiScreenshotTest` (Robolectric native graphics): renders the viewfinder, exposure panel,
  recording HUD, pairing help and settings with a simulated camera into `app/build/screenshots/`.

Robolectric downloads Android framework jars from Maven Central on first run. If that's
blocked or rate-limited, download the `android-all-instrumented` jars yourself and pass
`-ProbolectricDepsDir=/path/to/jars` to run offline.

## Sources

Community reverse engineering; none of it is official Sony documentation:

- Greg Leeds, *Reverse Engineering Sony Camera Bluetooth* (gregleeds.com)
- [coral/freemote](https://github.com/coral/freemote): advertisement and pairing flags, command table
- [Staacks/alpharemote](https://github.com/Staacks/alpharemote): Android remote app; zoom/focus code families
- [gkoh/furble](https://github.com/gkoh/furble): tag 0x22 bit layout
- [sarnau/sony-camera-protocol](https://github.com/sarnau/sony-camera-protocol): GATT dump of an A7R V; bonding requirement
- [ekutner/camera-gps-link](https://github.com/ekutner/camera-gps-link): advertisement tags 0x21/0x22/0x23
- [rock3r/CameraSync](https://github.com/rock3r/CameraSync): remote service notes

GripCam is an independent project, not made by or affiliated with Sony. "Sony", "GP-VPT2BT"
and "RMT-P1BT" are used only to describe compatibility.
