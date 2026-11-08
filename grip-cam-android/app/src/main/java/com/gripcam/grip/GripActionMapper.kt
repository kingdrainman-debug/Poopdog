package com.gripcam.grip

import com.gripcam.grip.SonyRemoteProtocol.Button
import com.gripcam.grip.SonyRemoteProtocol.Command
import com.gripcam.grip.SonyRemoteProtocol.Jog

/** What the C1 (custom) button does on the phone. */
enum class C1Action(val label: String) {
    SWITCH_CAMERA("Switch front/back camera"),
    TOGGLE_TORCH("Toggle torch"),
    CYCLE_FLASH("Cycle photo flash"),
    NONE("Nothing"),
}

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
    val c1Action: C1Action = C1Action.SWITCH_CAMERA,
)

/** A camera operation requested by the grip. */
sealed interface CameraAction {
    /** Shutter half press / AF-ON: run autofocus and hold it. */
    data object FocusStart : CameraAction

    /** Half press released: let go of the focus lock. */
    data object FocusRelease : CameraAction

    /** Shutter fully pressed: take a photo. */
    data object Capture : CameraAction

    /** REC button: start or stop video. */
    data object ToggleRecording : CameraAction

    /** Continuous zoom. direction: +1 tele (in), -1 wide (out), 0 stop. speed: 0..1. */
    data class Zoom(val direction: Int, val speed: Float) : CameraAction

    /** Continuous manual focus. direction: +1 nearer, -1 farther, 0 stop. speed: 0..1. */
    data class ManualFocus(val direction: Int, val speed: Float) : CameraAction

    data class Custom(val action: C1Action) : CameraAction
}

/**
 * Turns the press/release stream from the grip into camera actions, keeping just enough state
 * to make it robust: repeated presses are de-duplicated, a release only stops the jog that is
 * actually running, and [reset] cleans up if the grip vanishes mid-press.
 */
class GripActionMapper {
    private var halfPressed = false
    private var afOnPressed = false
    private var recordDown = false
    private var activeZoomJog: Jog? = null
    private var activeFocusJog: Jog? = null

    fun map(command: Command, config: MapperConfig): List<CameraAction> = when (command) {
        is Command.ButtonEvent -> mapButton(command, config)
        is Command.JogEvent -> mapJog(command, config)
        is Command.Unknown -> emptyList()
    }

    private fun mapButton(e: Command.ButtonEvent, config: MapperConfig): List<CameraAction> =
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

            // Every full press is a frame; the release carries no action.
            Button.SHUTTER_FULL -> if (e.pressed) listOf(CameraAction.Capture) else emptyList()

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

            Button.C1 -> if (e.pressed && config.c1Action != C1Action.NONE) {
                listOf(CameraAction.Custom(config.c1Action))
            } else {
                emptyList()
            }
        }

    private fun mapJog(e: Command.JogEvent, config: MapperConfig): List<CameraAction> {
        val zoomDirection = zoomDirectionOf(e.jog, config)
        return if (zoomDirection != 0) {
            if (e.pressed) {
                activeZoomJog = e.jog
                listOf(CameraAction.Zoom(zoomDirection, e.speed))
            } else if (activeZoomJog == e.jog) {
                activeZoomJog = null
                listOf(CameraAction.Zoom(0, 0f))
            } else {
                emptyList()
            }
        } else {
            val focusDirection = if (e.jog == Jog.FOCUS_NEAR) 1 else -1
            if (e.pressed) {
                activeFocusJog = e.jog
                listOf(CameraAction.ManualFocus(focusDirection, e.speed))
            } else if (activeFocusJog == e.jog) {
                activeFocusJog = null
                listOf(CameraAction.ManualFocus(0, 0f))
            } else {
                emptyList()
            }
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
        if (activeZoomJog != null) out += CameraAction.Zoom(0, 0f)
        if (activeFocusJog != null) out += CameraAction.ManualFocus(0, 0f)
        if (halfPressed || afOnPressed) out += CameraAction.FocusRelease
        halfPressed = false
        afOnPressed = false
        recordDown = false
        activeZoomJog = null
        activeFocusJog = null
        return out
    }
}
