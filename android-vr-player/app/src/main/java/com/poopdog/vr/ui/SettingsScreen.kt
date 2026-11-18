@file:OptIn(ExperimentalMaterial3Api::class)

package com.poopdog.vr.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.poopdog.vr.BuildConfig
import com.poopdog.vr.VrApp
import com.poopdog.vr.data.VrSettings
import com.poopdog.vr.model.AspectMode
import com.poopdog.vr.vr.ViewMode
import com.poopdog.vr.vr.ViewerProfile
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val app = VrApp.from(LocalContext.current)
    val s by app.settings.settings.collectAsState()
    fun set(t: (VrSettings) -> VrSettings) = app.settings.update(t)
    /** Editing any lens value turns the preset into "Custom". */
    fun lens(t: (VrSettings) -> VrSettings) = app.settings.update { t(it).copy(viewerPreset = ViewerProfile.Preset.CUSTOM) }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { TopAppBar(title = { Text("Settings") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SectionHeader("Headset")
                ChoiceRow("Viewer", ViewerProfile.Preset.entries, s.viewerPreset, { it.label }) { p ->
                    set { it.withPreset(p) }
                }
                ChoiceRow("View mode", ViewMode.entries, s.viewMode, {
                    if (it == ViewMode.VR) "VR headset (split screen)" else "Phone only (no headset)"
                }) { v -> set { it.copy(viewMode = v) } }
                SwitchRow("Lens distortion correction", "Pre-warps each eye so straight lines look straight through the lenses",
                    s.distortionCorrection) { v -> set { it.copy(distortionCorrection = v) } }
                SliderRow("Lens spacing (IPD)", s.interLensMm, 50f..75f, "${s.interLensMm.roundToInt()} mm",
                    subtitle = "Match your headset's lens distance / your eye distance") { v -> lens { it.copy(interLensMm = v) } }
                SliderRow("Screen to lens", s.screenToLensMm, 25f..60f, "${s.screenToLensMm.roundToInt()} mm") { v ->
                    lens { it.copy(screenToLensMm = v) }
                }
                SliderRow("Distortion k1", s.k1, 0f..1f, "%.2f".format(s.k1)) { v -> lens { it.copy(k1 = v) } }
                SliderRow("Distortion k2", s.k2, 0f..1.5f, "%.2f".format(s.k2)) { v -> lens { it.copy(k2 = v) } }
                SliderRow("Field of view (per eye, half-angle)", s.fovHalfDeg, 30f..75f, "${s.fovHalfDeg.roundToInt()}°") { v ->
                    lens { it.copy(fovHalfDeg = v) }
                }
                SliderRow("Vertical lens offset", s.verticalOffsetMm, -15f..15f, "${s.verticalOffsetMm.roundToInt()} mm",
                    subtitle = "Raise or lower the picture if the eyes don't line up") { v -> lens { it.copy(verticalOffsetMm = v) } }
                SwitchRow("Swap eyes", "For headsets or files with the eyes reversed", s.swapEyes) { v -> set { it.copy(swapEyes = v) } }
            }
            item {
                SectionHeader("Flat & 3D movies (virtual cinema)")
                SliderRow("Screen size", s.screenSizeDeg, 40f..140f, "${s.screenSizeDeg.roundToInt()}°") { v ->
                    set { it.copy(screenSizeDeg = v) }
                }
                SliderRow("Screen distance", s.screenDistanceM, 1.5f..12f, "%.1f m".format(s.screenDistanceM)) { v ->
                    set { it.copy(screenDistanceM = v) }
                }
                SwitchRow("Curved screen", null, s.curvedScreen) { v -> set { it.copy(curvedScreen = v) } }
                SwitchRow("Screen follows head", "Keeps the screen in front of you — good for lying down",
                    s.headLockedScreen) { v -> set { it.copy(headLockedScreen = v) } }
                ChoiceRow("Aspect ratio", AspectMode.entries, s.aspectMode, { it.label }) { v -> set { it.copy(aspectMode = v) } }
                SwitchRow("Convert 2D videos to 3D", "Adds synthetic depth to ordinary flat videos", s.auto2dTo3d) { v ->
                    set { it.copy(auto2dTo3d = v) }
                }
                SliderRow("2D→3D depth", s.depthStrength, 0.1f..1f, "${(s.depthStrength * 100).roundToInt()}%") { v ->
                    set { it.copy(depthStrength = v) }
                }
                SwitchRow("Max brightness while playing", null, s.maxBrightness) { v -> set { it.copy(maxBrightness = v) } }
            }
            item {
                SectionHeader("Hands-free controls")
                SwitchRow("Head tracking", "Look around 180°/360° videos by turning your head", s.headTracking) { v ->
                    set { it.copy(headTracking = v) }
                }
                SwitchRow("Gaze control", "Look at a button to press it", s.gazeControl) { v -> set { it.copy(gazeControl = v) } }
                SliderRow("Gaze dwell time", s.dwellMs / 1000f, 0.6f..3f, "%.1f s".format(s.dwellMs / 1000f)) { v ->
                    set { it.copy(dwellMs = (v * 1000).roundToInt()) }
                }
                SwitchRow("Look down to open menu", "Glance at the floor for a moment to bring up the controls", s.lookDownMenu) { v ->
                    set { it.copy(lookDownMenu = v) }
                }
                SwitchRow("Show gaze pointer", null, s.showReticle) { v -> set { it.copy(showReticle = v) } }
                SliderRow("Hide menu after", s.hudTimeoutSec.toFloat(), 3f..30f, "${s.hudTimeoutSec} s", steps = 26) { v ->
                    set { it.copy(hudTimeoutSec = v.roundToInt()) }
                }
                SwitchRow("Volume keys control playback", "Vol+ = next video, Vol− = play/pause", s.volumeKeysControl) { v ->
                    set { it.copy(volumeKeysControl = v) }
                }
                SwitchRow("Recenter includes tilt", "Recenter makes your current head angle 'forward' (lying down)",
                    s.recenterFull) { v -> set { it.copy(recenterFull = v) } }
            }
            item {
                SectionHeader("Playback")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Default loops for new videos", style = MaterialTheme.typography.bodyLarge)
                        Text("How many times each video plays before the next", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    LoopStepper(s.defaultLoops, { v -> set { it.copy(defaultLoops = v) } })
                }
                SliderRow("Pause between videos", s.autoAdvanceDelaySec.toFloat(), 0f..10f, "${s.autoAdvanceDelaySec} s", steps = 9,
                    subtitle = "Silent pause; open the menu to see what's next") { v ->
                    set { it.copy(autoAdvanceDelaySec = v.roundToInt()) }
                }
                SwitchRow("Resume where you left off", null, s.resumePlayback) { v -> set { it.copy(resumePlayback = v) } }
                SwitchRow("Auto-detect VR format", "From file name tags (_180_LR, _360_TB, SBS…), metadata and shape",
                    s.autoDetectFormat) { v -> set { it.copy(autoDetectFormat = v) } }
                SwitchRow("Show subtitles", "Embedded tracks and attached .srt/.vtt files", s.showSubtitles) { v ->
                    set { it.copy(showSubtitles = v) }
                }
                SwitchRow("Skip videos that fail to play", null, s.skipOnError) { v -> set { it.copy(skipOnError = v) } }
            }
            item {
                SectionHeader("Help")
                HelpText()
            }
        }
    }
}

@Composable
private fun HelpText() {
    val body = MaterialTheme.typography.bodySmall
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "In the headset: tap the screen (or the viewer's button), or look down, to open the menu. " +
                "Look at a button until the ring fills to press it.",
            style = body, color = c,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Bluetooth remotes / gamepads / keyboards: Play/Pause, A or Enter = play/pause or press the button you're looking at · " +
                "Next/Prev or R1/L1 = next/previous video · D-pad ←/→ = seek 10 s · D-pad ↑ / Y / Menu = menu · " +
                "D-pad ↓ / X = recenter · L = add a loop · F = change format.",
            style = body, color = c, modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Format tags in file names: _180, _360, _fisheye190, _LR/_SBS/_3dh, _TB/_OU/_3dv, _RL, _BT, _2D.",
            style = body, color = c, modifier = Modifier.padding(top = 8.dp),
        )
        Text("Version ${BuildConfig.VERSION_NAME}", style = body, color = c, modifier = Modifier.padding(top = 8.dp))
    }
}
