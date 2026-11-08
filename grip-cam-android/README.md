# GripCam: control your Android phone's camera with a Sony GP-VPT2BT grip

GripCam is an Android camera app that pairs with a **Sony GP-VPT2BT wireless shooting
grip** (and should also work with the **RMT-P1BT** remote). Once paired, the grip's buttons
control the phone's camera:

| Grip control | What the phone does |
|---|---|
| **PHOTO**, half press | Autofocus on the centre and hold it (green box = focused) |
| **PHOTO**, full press | Take a photo |
| **MOVIE** | Start / stop video recording |
| **Zoom lever T / W** | Smooth zoom in / out; speed follows the lever if the grip reports it |
| **C1** | Your choice: switch front/back camera (default), torch, or flash mode |
| AF-ON / Focus +/– (RMT-P1BT) | Focus lock / manual focus (with *Treat focus codes as zoom* off) |

Photos go to `Pictures/GripCam` and videos to `Movies/GripCam`, so they appear in your
gallery. The on-screen buttons also work, as do tap-to-focus and pinch-to-zoom.

> **Status: built and tested in software, not yet on a physical grip.** The Bluetooth
> protocol comes from community reverse engineering of Sony cameras (sources below). The app
> is unit-tested against those byte layouts, and an Android-framework simulation covers a
> full grip session, but no real GP-VPT2BT has been tried yet. If something doesn't work,
> turn on **Settings → Show control log** and see [Troubleshooting](#troubleshooting).

## Install

**Option A: sideload a prebuilt APK.** Copy the debug APK (`GripCam-debug.apk`, or
`app-debug.apk` from a build) to the phone, open it, and allow "install unknown apps" for
your file manager when Android asks.

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
   The status chip at the top should say *Waiting for grip · pairing open*.
2. On the grip, make sure the **lock switch is off**.
3. Hold **PHOTO** and the **T** side of the zoom lever together for about **7 seconds**
   (Sony's pairing gesture for this grip).
4. If Android asks to pair, tap **Pair**. The chip turns green with the grip's name.

Next time, press any grip button to reconnect. If it doesn't reconnect (see below), pair again.
To forget a grip, unpair it in Android's Bluetooth settings.

If the grip was paired to a Sony camera before, it now belongs to the phone. Re-pair it with
the camera when you want to use it there again (a grip remembers one camera at a time).

## Settings

- **C1 button**: switch camera, toggle torch, cycle photo flash, or nothing.
- **Zoom speed**: overall multiplier on how fast the lever zooms.
- **Reverse zoom direction**: swaps T and W.
- **Treat focus codes as zoom** (default **on**): the sources disagree about which command
  family the zoom lever sends (see below). With this on, whichever one your grip sends
  zooms. Turn it off for an RMT-P1BT to get manual focus from its Focus +/– buttons.
- **Shutter and record sounds**: audible feedback when the phone is out of reach.
- **Show control log**: every byte the grip sends, plus pairing and connection events.
- **Accept new grip pairings**: whether to advertise "camera in pairing mode".
- **Advanced: camera identity**: the model code and protocol byte the phone advertises.
  Leave these alone unless the grip refuses to connect.

## Troubleshooting

- **Grip never connects.** Check the chip says *pairing open*, the grip's lock switch is off,
  and you held PHOTO + T for 7+ seconds. Then try **Advanced → Protocol version 0x65**, and
  after that the other model codes, re-pairing each time.
- **Pairs once, won't reconnect next time.** Android advertises from a private, rotating
  Bluetooth address; a grip that doesn't resolve private addresses can only find the phone
  by pairing again. Do the 7-second pairing gesture again; it takes seconds.
- **Lever zooms the wrong way.** Turn on *Reverse zoom direction*.
- **A button does nothing.** Open the control log, press the button, and look for a
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
| `grip/GripActionMapper.kt` | Press/release stream → camera actions, settings-aware (pure Kotlin) |
| `grip/GripServer.kt` | BLE advertiser + GATT server, bonding, notification queue, Bluetooth on/off handling |
| `camera/CameraController.kt` | CameraX: photo, video, continuous zoom, AF lock, Camera2 manual focus, torch/flash |
| `MainViewModel.kt` | Wires grip events → camera, and camera state → grip notifications |
| `ui/` | Compose viewfinder, status chip, pairing help, control log, settings sheet |

## Tests

```bash
./gradlew testDebugUnitTest
```

- `SonyRemoteProtocolTest` checks the advertisement against every published remote-side
  check, and parses each button, jog, concatenated and malformed write.
- `GripActionMapperTest` covers the shutter sequence, AF-ON, record toggling, both
  zoom-code families, invert, C1 and cleanup on disconnect.
- `GripServerTest` (Robolectric, Android 11 and 14) plays the grip's side: it registers the
  service, advertises, connects, subscribes, presses buttons, disconnects, and toggles
  Bluetooth and permissions.

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
