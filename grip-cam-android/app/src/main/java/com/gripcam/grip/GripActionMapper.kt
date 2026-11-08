package com.gripcam.grip

import com.gripcam.grip.SonyRemoteProtocol.Button
import com.gripcam.grip.SonyRemoteProtocol.Command
import com.gripcam.grip.SonyRemoteProtocol.Jog

/** User-tunable parts of the button mapping. */
data class MapperConfig(
    /**
     * Treat the 0x6A–0x6D jog family as zoom. The GP-VPT2BT has a zoom lever but no focus
     * buttons, and community sources disagree on which jog family is zoom, so with this on the
     * lever zooms whichever family the grip sends. Turn it off for a remote with Focus +/–.
     */
    val focusJogsAsZoom: Boolean = true,
    /** Swap T and W. */
    val invertZoom: Boolean = false,
    /** A quick double press of T / W jumps to the next / previous zoom stop. */
    val zoomSnap: Boolean = true,
)

/** A camera operation requested by the grip. */
sealed interface CameraAction {
    /** Shutter half press / AF-ON: run autofocus and hold it. */
    data object FocusStart : CameraAction

    /** Half press released: let go of the focus lock. */
    data object FocusRelease : CameraAction

    /** Shutter fully pressed: a photo (or the start of a burst / self-timer). */
    data object ShutterPress : CameraAction

    /** Shutter full press released: ends a burst. */
    data object ShutterRelease : CameraAction

    /** REC button: start or stop video. */
    data object ToggleRecording : CameraAction

    /** Continuous zoom. direction: +1 tele (in), -1 wide (out), 0 stop. speed: 0..1. */
    data class Zoom(val direction: Int, val speed: Float) : CameraAction

    /** Double press of T (+1) or W (-1): jump to the next zoom stop in that direction. */
    data class ZoomSnap(val direction: Int) : CameraAction

    /** Continuous manual focus. direction: +1 nearer, -1 farther, 0 stop. speed: 0..1. */
    data class ManualFocus(val direction: Int, val speed: Float) : CameraAction

    /** C1 pressed. What it does (and single vs double press) is decided by the caller. */
    data object C1Press : CameraAction
}

/**
 * Turns the press/release stream from the grip into camera actions, keeping just enough state
 * to make it robust: repeated presses are de-duplicated, a release only stops the jog that is
 * actually running, double presses are recognised, and [reset] cleans up if the grip vanishes
 * mid-press. Time comes in as a parameter so the logic stays testable.
 */
class GripActionMapper {
    private var halfPressed = false
    private var afOnPressed = false
    private var fullPressed = false
    private var recordDown = false

    private var activeZoomJog: Jog? = null
    private var activeZoomDirection = 0
    private var activeZoomIsSnap = false
    private var zoomPressedAt = 0L
    private var lastTapDirection = 0
    private var lastTapReleasedAt = Long.MIN_VALUE / 2

    private var activeFocusJog: Jog? = null

    fun map(command: Command, config: MapperConfig, nowMillis: Long): List<CameraAction> = when (command) {
        is Command.ButtonEvent -> mapButton(command)
        is Command.JogEvent -> mapJog(command, config, nowMillis)
        is Command.Unknown -> emptyList()
    }

    private fun mapButton(e: Command.ButtonEvent): List<CameraAction> =
        when (e.button) {
            Button.SHUTTER_HALF -> if (e.pressed) {
                if (halfPressed) emptyList() else {
                    halfPressed = true
                    if (afOnPressed) emptyList() else listOf(CameraAction.FocusStart)
                }
            } else {
                if (!halfPressed) emptyList() else {
                    halfPressed = false
                    if (afOnPressed) emptyList() else listOf(CameraAction.FocusRelease)
                }
            }

            Button.AF_ON -> if (e.pressed) {
                if (afOnPressed) emptyList() else {
                    afOnPressed = true
                    if (halfPressed) emptyList() else listOf(CameraAction.FocusStart)
                }
            } else {
                if (!afOnPressed) emptyList() else {
                    afOnPressed = false
                    if (halfPressed) emptyList() else listOf(CameraAction.FocusRelease)
                }
            }

            Button.SHUTTER_FULL -> if (e.pressed) {
                if (fullPressed) emptyList() else {
                    fullPressed = true
                    listOf(CameraAction.ShutterPress)
                }
            } else {
                if (!fullPressed) emptyList() else {
                    fullPressed = false
                    listOf(CameraAction.ShutterRelease)
                }
            }

            // Toggle on press. Some sources describe 01 0E alone as "toggle record", so a release
            // that arrives without a press before it toggles too.
            Button.RECORD -> if (e.pressed) {
                recordDown = true
                listOf(CameraAction.ToggleRecording)
            } else {
                val toggle = !recordDown
                recordDown = false
                if (toggle) listOf(CameraAction.ToggleRecording) else emptyList()
            }

            Button.C1 -> if (e.pressed) listOf(CameraAction.C1Press) else emptyList()
        }

    private fun mapJog(e: Command.JogEvent, config: MapperConfig, now: Long): List<CameraAction> {
        val zoomDirection = zoomDirectionOf(e.jog, config)
        return if (zoomDirection != 0) mapZoom(e, zoomDirection, config, now) else mapFocus(e)
    }

    private fun mapZoom(e: Command.JogEvent, direction: Int, config: MapperConfig, now: Long): List<CameraAction> {
        if (e.pressed) {
            if (activeZoomJog == e.jog) {
                // Repeated "pressed" while held: a speed change, not a new press.
                return if (activeZoomIsSnap) emptyList() else listOf(CameraAction.Zoom(direction, e.speed))
            }
            activeZoomJog = e.jog
            activeZoomDirection = direction
            val isDoublePress = config.zoomSnap &&
                lastTapDirection == direction &&
                now - lastTapReleasedAt <= DOUBLE_PRESS_WINDOW_MS
            lastTapDirection = 0
            return if (isDoublePress) {
                activeZoomIsSnap = true
                listOf(CameraAction.ZoomSnap(direction))
            } else {
                activeZoomIsSnap = false
                zoomPressedAt = now
                listOf(CameraAction.Zoom(direction, e.speed))
            }
        }
        if (activeZoomJog != e.jog) return emptyList()
        activeZoomJog = null
        if (activeZoomIsSnap) {
            activeZoomIsSnap = false
            return emptyList()
        }
        if (now - zoomPressedAt <= TAP_MAX_MS) {
            lastTapDirection = activeZoomDirection
            lastTapReleasedAt = now
        } else {
            lastTapDirection = 0
        }
        return listOf(CameraAction.Zoom(0, 0f))
    }

    private fun mapFocus(e: Command.JogEvent): List<CameraAction> {
        val focusDirection = if (e.jog == Jog.FOCUS_NEAR) 1 else -1
        return if (e.pressed) {
            activeFocusJog = e.jog
            listOf(CameraAction.ManualFocus(focusDirection, e.speed))
        } else if (activeFocusJog == e.jog) {
            activeFocusJog = null
            listOf(CameraAction.ManualFocus(0, 0f))
        } else {
            emptyList()
        }
    }

    /** +1 tele, -1 wide, 0 when this jog is a focus jog under [config]. */
    private fun zoomDirectionOf(jog: Jog, config: MapperConfig): Int {
        val raw = when (jog) {
            Jog.ZOOM_TELE -> 1
            Jog.ZOOM_WIDE -> -1
            // Read as zoom, the sources that call this family zoom have 0x6D = tele, 0x6B = wide.
            Jog.FOCUS_FAR -> if (config.focusJogsAsZoom) 1 else 0
            Jog.FOCUS_NEAR -> if (config.focusJogsAsZoom) -1 else 0
        }
        return if (config.invertZoom) -raw else raw
    }

    /** Releases anything still held, e.g. when the grip disconnects mid-press. */
    fun reset(): List<CameraAction> {
        val out = mutableListOf<CameraAction>()
        if (activeZoomJog != null && !activeZoomIsSnap) out += CameraAction.Zoom(0, 0f)
        if (activeFocusJog != null) out += CameraAction.ManualFocus(0, 0f)
        if (fullPressed) out += CameraAction.ShutterRelease
        if (halfPressed || afOnPressed) out += CameraAction.FocusRelease
        halfPressed = false
        afOnPressed = false
        fullPressed = false
        recordDown = false
        activeZoomJog = null
        activeZoomIsSnap = false
        activeFocusJog = null
        lastTapDirection = 0
        return out
    }

    companion object {
        /** A press shorter than this counts as a tap (first half of a double press). */
        const val TAP_MAX_MS = 300L

        /** The second press must start within this long after the first tap is released. */
        const val DOUBLE_PRESS_WINDOW_MS = 400L
    }
}
