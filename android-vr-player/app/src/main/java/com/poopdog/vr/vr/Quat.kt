package com.poopdog.vr.vr

import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimal immutable unit quaternion for head-pose maths (no Android dependencies). */
data class Quat(val w: Float, val x: Float, val y: Float, val z: Float) {

    operator fun times(o: Quat): Quat = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun conjugate(): Quat = Quat(w, -x, -y, -z)

    fun normalized(): Quat {
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n < 1e-9f) IDENTITY else Quat(w / n, x / n, y / n, z / n)
    }

    /** Rotate vector v by this quaternion. */
    fun rotate(v: FloatArray): FloatArray {
        val p = Quat(0f, v[0], v[1], v[2])
        val r = this * p * conjugate()
        return floatArrayOf(r.x, r.y, r.z)
    }

    /** Column-major 4x4 rotation matrix (OpenGL layout). */
    fun toMatrix(out: FloatArray = FloatArray(16)): FloatArray {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[0] = 1 - 2 * (yy + zz); out[1] = 2 * (xy + wz); out[2] = 2 * (xz - wy); out[3] = 0f
        out[4] = 2 * (xy - wz); out[5] = 1 - 2 * (xx + zz); out[6] = 2 * (yz + wx); out[7] = 0f
        out[8] = 2 * (xz + wy); out[9] = 2 * (yz - wx); out[10] = 1 - 2 * (xx + yy); out[11] = 0f
        out[12] = 0f; out[13] = 0f; out[14] = 0f; out[15] = 1f
        return out
    }

    /** Direction the camera looks (camera -Z) in world space. */
    fun forward(): FloatArray = rotate(floatArrayOf(0f, 0f, -1f))

    /** Yaw (radians) of the forward vector around world +Y; 0 = looking down -Z. */
    fun yaw(): Float {
        val f = forward()
        return atan2(-f[0], -f[2])
    }

    /** Pitch (radians) of the forward vector; positive = looking up. */
    fun pitch(): Float {
        val f = forward()
        return asin(f[1].coerceIn(-1f, 1f))
    }

    companion object {
        val IDENTITY = Quat(1f, 0f, 0f, 0f)

        fun axisAngle(ax: Float, ay: Float, az: Float, radians: Float): Quat {
            val n = sqrt(ax * ax + ay * ay + az * az)
            val s = sin(radians / 2f) / n
            return Quat(cos(radians / 2f), ax * s, ay * s, az * s)
        }

        fun rotY(radians: Float) = axisAngle(0f, 1f, 0f, radians)
        fun rotX(radians: Float) = axisAngle(1f, 0f, 0f, radians)
        fun rotZ(radians: Float) = axisAngle(0f, 0f, 1f, radians)

        fun slerp(a: Quat, b0: Quat, t: Float): Quat {
            var b = b0
            var d = a.w * b.w + a.x * b.x + a.y * b.y + a.z * b.z
            if (d < 0f) { b = Quat(-b.w, -b.x, -b.y, -b.z); d = -d }
            if (d > 0.9995f) {
                return Quat(
                    a.w + t * (b.w - a.w), a.x + t * (b.x - a.x),
                    a.y + t * (b.y - a.y), a.z + t * (b.z - a.z),
                ).normalized()
            }
            val theta0 = kotlin.math.acos(d)
            val theta = theta0 * t
            val s0 = cos(theta) - d * sin(theta) / sin(theta0)
            val s1 = sin(theta) / sin(theta0)
            return Quat(
                s0 * a.w + s1 * b.w, s0 * a.x + s1 * b.x,
                s0 * a.y + s1 * b.y, s0 * a.z + s1 * b.z,
            )
        }
    }
}

/**
 * Converts the phone's rotation-vector sensor output into a camera orientation in the
 * renderer's world (OpenGL: +Y up, -Z forward/north at rest).
 */
object HeadPose {
    /** Rotation taking Android's ENU world (Z up, Y north) into GL world (Y up, -Z north). */
    private val ENU_TO_GL = Quat.rotX((-Math.PI / 2).toFloat())

    /**
     * @param device device→ENU rotation from SensorManager.getQuaternionFromVector.
     * @param displayRotationDeg 0/90/180/270 from Display.getRotation().
     * @return camera→world rotation; the camera looks down its own -Z through the screen.
     */
    fun cameraFromDevice(device: Quat, displayRotationDeg: Int): Quat {
        val screenToDevice = Quat.rotZ(Math.toRadians(-displayRotationDeg.toDouble()).toFloat())
        return (ENU_TO_GL * device * screenToDevice).normalized()
    }

    /** Yaw-only recenter: returns the correction to pre-multiply so [head] faces -Z. */
    fun yawRecenter(head: Quat): Quat = Quat.rotY(-head.yaw())

    /** Full recenter (for lying down): the correction makes [head] the identity. */
    fun fullRecenter(head: Quat): Quat = head.conjugate()
}
