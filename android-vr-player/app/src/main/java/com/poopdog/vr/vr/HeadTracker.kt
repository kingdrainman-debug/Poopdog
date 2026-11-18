package com.poopdog.vr.vr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Feeds the phone's fused orientation sensor into [HeadState]. Prefers the game rotation
 * vector (gyro + accelerometer, no magnetometer jumps), falling back to the regular
 * rotation vector. Without either sensor, touch-drag look-around is used instead.
 */
class HeadTracker(context: Context, private val state: HeadState) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = sensor != null

    @Volatile var displayRotationDeg: Int = 90

    /** Invoked once, on the first reading after [start], so the view can be recentred. */
    var onFirstSample: (() -> Unit)? = null
    private var gotSample = false
    private val qBuf = FloatArray(4)

    fun start() {
        gotSample = false
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getQuaternionFromVector(qBuf, event.values)
        val device = Quat(qBuf[0], qBuf[1], qBuf[2], qBuf[3])
        state.sensorPose = HeadPose.cameraFromDevice(device, displayRotationDeg)
        if (!gotSample) {
            gotSample = true
            onFirstSample?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
