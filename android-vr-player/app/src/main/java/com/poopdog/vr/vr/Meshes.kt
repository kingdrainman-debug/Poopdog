package com.poopdog.vr.vr

import com.poopdog.vr.model.Projection
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry onto which the video is projected. Texture coordinates are in "eye space":
 * (0,0) = bottom-left of one eye's picture, (1,1) = top-right; the shader maps that to the
 * correct half/quarter of the frame and through the SurfaceTexture transform.
 */
class Mesh(
    /** Interleaved x, y, z, u, v. */
    val vertices: FloatArray,
    val indices: ShortArray,
)

object Meshes {
    const val SPHERE_RADIUS = 10f

    fun forProjection(p: Projection, stacks: Int = 48, slices: Int = 96): Mesh = when (p) {
        Projection.FLAT -> quad(1f, 1f, 0f)
        Projection.EQUIRECT_360 -> equirect(360f, stacks, slices)
        Projection.EQUIRECT_180 -> equirect(180f, stacks, slices / 2)
        Projection.FISHEYE_180, Projection.FISHEYE_190, Projection.FISHEYE_200 -> fisheye(p.fovDeg, 40, 96)
    }

    /**
     * Cinema screen: unit-size grid centred on the origin in the XY plane, facing +Z.
     * With [curveDeg] > 0 the grid bends around the viewer as a cylinder arc whose
     * radius is [distance] (so the centre stays at z = 0 relative to its placement).
     */
    fun quad(width: Float, height: Float, curveDeg: Float, distance: Float = 1f, cols: Int = 32): Mesh {
        val rows = 1
        val verts = FloatArray((cols + 1) * (rows + 1) * 5)
        var k = 0
        val arc = Math.toRadians(curveDeg.toDouble()).toFloat()
        for (j in 0..rows) {
            val v = j.toFloat() / rows
            for (i in 0..cols) {
                val u = i.toFloat() / cols
                var x = (u - 0.5f) * width
                var z = 0f
                if (arc > 0.001f) {
                    // Arc length == width, radius == distance.
                    val r = distance
                    val a = (u - 0.5f) * (width / r)
                    x = r * sin(a)
                    z = r - r * cos(a)
                }
                verts[k++] = x
                verts[k++] = (v - 0.5f) * height
                verts[k++] = z
                verts[k++] = u
                verts[k++] = v
            }
        }
        return Mesh(verts, gridIndices(cols, rows))
    }

    /** Inward-facing sphere (or front hemisphere for 180°) with equirectangular UVs. */
    fun equirect(fovDeg: Float, stacks: Int, slices: Int): Mesh {
        val r = SPHERE_RADIUS
        val lonSpan = Math.toRadians(fovDeg.toDouble())
        val verts = FloatArray((stacks + 1) * (slices + 1) * 5)
        var k = 0
        for (j in 0..stacks) {
            val v = j.toDouble() / stacks
            val lat = (v - 0.5) * PI // -90° (bottom) .. +90° (top)
            for (i in 0..slices) {
                val u = i.toDouble() / slices
                // u = 0 is the left edge; the centre of the picture (u = 0.5) faces -Z.
                val lon = (u - 0.5) * lonSpan
                verts[k++] = (r * cos(lat) * sin(lon)).toFloat()
                verts[k++] = (r * sin(lat)).toFloat()
                verts[k++] = (-r * cos(lat) * cos(lon)).toFloat()
                verts[k++] = u.toFloat()
                verts[k++] = v.toFloat()
            }
        }
        return Mesh(verts, gridIndices(slices, stacks))
    }

    /**
     * Equidistant fisheye: a direction at angle θ from the lens axis lands at radius
     * θ / (fov/2) · 0.5 from the centre of the (square) eye image.
     */
    fun fisheye(fovDeg: Float, rings: Int, segments: Int): Mesh {
        val r = SPHERE_RADIUS
        val half = Math.toRadians(fovDeg / 2.0)
        val verts = FloatArray((rings + 1) * (segments + 1) * 5)
        var k = 0
        for (j in 0..rings) {
            val theta = half * j / rings // angle from forward axis
            for (i in 0..segments) {
                val phi = 2 * PI * i / segments // around the axis, 0 = +X (right)
                val dx = sin(theta) * cos(phi)
                val dy = sin(theta) * sin(phi)
                val dz = -cos(theta)
                verts[k++] = (r * dx).toFloat()
                verts[k++] = (r * dy).toFloat()
                verts[k++] = (r * dz).toFloat()
                val rad = theta / half * 0.5
                verts[k++] = (0.5 + rad * cos(phi)).toFloat()
                verts[k++] = (0.5 + rad * sin(phi)).toFloat()
            }
        }
        return Mesh(verts, gridIndices(segments, rings))
    }

    /** Fisheye UV for a world direction (used by tests to cross-check the mesh). */
    fun fisheyeUv(dx: Float, dy: Float, dz: Float, fovDeg: Float): FloatArray {
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        val theta = acos((-dz / len).coerceIn(-1f, 1f))
        val phi = atan2(dy, dx)
        val rad = theta / Math.toRadians(fovDeg / 2.0).toFloat() * 0.5f
        return floatArrayOf(0.5f + rad * cos(phi), 0.5f + rad * sin(phi))
    }

    private fun gridIndices(cols: Int, rows: Int): ShortArray {
        val out = ShortArray(cols * rows * 6)
        var m = 0
        for (j in 0 until rows) for (i in 0 until cols) {
            val a = j * (cols + 1) + i
            val b = a + 1
            val c = a + (cols + 1)
            val d = c + 1
            out[m++] = a.toShort(); out[m++] = c.toShort(); out[m++] = b.toShort()
            out[m++] = b.toShort(); out[m++] = c.toShort(); out[m++] = d.toShort()
        }
        return out
    }
}
