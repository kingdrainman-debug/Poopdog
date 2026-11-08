package com.gripcam.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gripcam.camera.CameraUiState
import com.gripcam.camera.FocusUi
import com.gripcam.settings.GridStyle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

val FocusGreen = Color(0xFF4CD964)
val RecordRed = Color(0xFFFF3B30)
val Accent = Color(0xFFFFB74D)
val Scrim = Color.Black.copy(alpha = 0.55f)

// ---------------------------------------------------------------------------------------------
// Grid
// ---------------------------------------------------------------------------------------------

@Composable
fun GridOverlay(style: GridStyle, modifier: Modifier = Modifier) {
    if (style == GridStyle.OFF) return
    Canvas(modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.45f)
        val stroke = 1.dp.toPx()
        fun vertical(f: Float) = drawLine(line, Offset(size.width * f, 0f), Offset(size.width * f, size.height), stroke)
        fun horizontal(f: Float) = drawLine(line, Offset(0f, size.height * f), Offset(size.width, size.height * f), stroke)
        when (style) {
            GridStyle.THIRDS -> listOf(1f / 3, 2f / 3).forEach { vertical(it); horizontal(it) }
            GridStyle.GOLDEN -> listOf(0.382f, 0.618f).forEach { vertical(it); horizontal(it) }
            GridStyle.CENTER -> {
                val arm = 18.dp.toPx()
                val c = center
                drawLine(line, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke * 1.5f)
                drawLine(line, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke * 1.5f)
            }
            GridStyle.OFF -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Level
// ---------------------------------------------------------------------------------------------

/**
 * Angle of the real horizon on the (portrait-locked) screen, in degrees, clockwise; null when
 * the phone is lying flat and a horizon makes no sense. Pure math, unit tested.
 */
object LevelMath {
    fun horizonAngle(gx: Float, gy: Float, gz: Float): Float? {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g < 1e-3f || abs(gz) / g > 0.85f) return null
        return Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
    }

    /** The screen axis the horizon is closest to (0, ±90, 180). */
    fun snap(angle: Float): Float = (angle / 90f).roundToInt() * 90f

    /** Signed tilt away from level, in degrees. */
    fun deviation(angle: Float): Float = angle - snap(angle)
}

@Composable
private fun rememberHorizonAngle(enabled: Boolean): Float? {
    val context = LocalContext.current
    var angle by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(enabled) {
        if (!enabled) {
            angle = null
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val filtered = FloatArray(3)
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val a = if (primed) 0.2f else 1f
                for (i in 0..2) filtered[i] = filtered[i] + a * (event.values[i] - filtered[i])
                primed = true
                angle = LevelMath.horizonAngle(filtered[0], filtered[1], filtered[2])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return angle
}

@Composable
fun LevelOverlay(enabled: Boolean, modifier: Modifier = Modifier) {
    val angle = rememberHorizonAngle(enabled) ?: return
    val deviation = LevelMath.deviation(angle)
    val level = abs(deviation) < 1f
    val color by animateColorAsState(if (level) FocusGreen else Color.White, label = "levelColor")
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val half = size.minDimension * 0.16f
            val gap = 10.dp.toPx()
            val tick = 14.dp.toPx()
            fun dir(deg: Float): Offset {
                val r = Math.toRadians(deg.toDouble())
                return Offset(cos(r).toFloat(), sin(r).toFloat())
            }
            // Fixed reference ticks along the screen axis the shot is framed to.
            val ref = dir(LevelMath.snap(angle))
            for (s in listOf(-1f, 1f)) {
                val start = c + ref * (s * (half + gap))
                drawLine(color.copy(alpha = 0.8f), start, start + ref * (s * tick), 2.dp.toPx(), StrokeCap.Round)
            }
            // The horizon itself.
            val h = dir(angle)
            drawLine(color, c - h * half, c + h * half, if (level) 2.5.dp.toPx() else 1.5.dp.toPx(), StrokeCap.Round)
        }
        if (!level && abs(deviation) < 45f) {
            Text(
                "${abs(deviation).roundToInt()}°",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.offset(y = 18.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Focus reticle and manual focus bar
// ---------------------------------------------------------------------------------------------

@Composable
fun FocusReticle(cam: CameraUiState) {
    val visible = cam.focus != FocusUi.IDLE || cam.aeAfLocked
    val color by animateColorAsState(
        when {
            cam.aeAfLocked -> Accent
            cam.focus == FocusUi.FOCUSED -> FocusGreen
            cam.focus == FocusUi.FAILED -> RecordRed
            else -> Color.White
        },
        label = "reticleColor",
    )
    val size by animateDpAsState(if (cam.focus == FocusUi.FOCUSING) 84.dp else 68.dp, label = "reticleSize")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (visible) {
            val (fx, fy) = cam.focusPoint ?: (0.5f to 0.5f)
            val half = with(LocalDensity.current) { (size / 2).roundToPx() }
            val x = (constraints.maxWidth * fx).roundToInt() - half
            val y = (constraints.maxHeight * fy).roundToInt() - half
            Box(
                Modifier
                    .offset { IntOffset(x, y) }
                    .size(size)
                    .border(2.dp, color, RoundedCornerShape(6.dp)),
            )
            if (cam.aeAfLocked) {
                Text(
                    "AE/AF LOCK",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset { IntOffset(x, y - 22.dp.roundToPx()) }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        if (cam.manualFocus) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("MF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(6.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Scrim),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(160.dp * cam.manualFocusPosition)
                            .background(Accent),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Self-timer and audio meter
// ---------------------------------------------------------------------------------------------

@Composable
fun CountdownOverlay(secondsLeft: Int, rotation: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = secondsLeft,
            transitionSpec = { (scaleIn(initialScale = 1.6f) + fadeIn()) togetherWith fadeOut() },
            label = "countdown",
        ) { left ->
            if (left > 0) {
                Text(
                    "$left",
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

/** Maps an amplitude (0..1) to a 0..1 meter position on a 60 dB scale. */
fun meterLevel(amplitude: Float): Float {
    if (amplitude <= 0f) return 0f
    val db = 20f * log10(amplitude)
    return ((db + 60f) / 60f).coerceIn(0f, 1f)
}

@Composable
fun AudioMeter(amplitude: Float, modifier: Modifier = Modifier) {
    val level = meterLevel(amplitude)
    val segments = 12
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until segments) {
            val threshold = (i + 1f) / segments
            val on = level >= threshold - 0.5f / segments
            val color = when {
                i >= segments - 2 -> RecordRed
                i >= segments - 4 -> Accent
                else -> FocusGreen
            }
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(width = 4.dp, height = 10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (on) color else Color.White.copy(alpha = 0.18f)),
            )
        }
    }
}
