package com.poopdog.vr

import com.poopdog.vr.convert.ConvertOutput
import com.poopdog.vr.convert.convertOutputSize
import com.poopdog.vr.model.Projection
import com.poopdog.vr.model.StereoLayout
import com.poopdog.vr.model.VideoFormat
import com.poopdog.vr.vr.DistortionMesh
import com.poopdog.vr.vr.GazeController
import com.poopdog.vr.vr.HeadPose
import com.poopdog.vr.vr.Meshes
import com.poopdog.vr.vr.Quat
import com.poopdog.vr.vr.ScreenGeometry
import com.poopdog.vr.vr.ViewerProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class VrMathTest {
    private val eps = 1e-4f

    private fun assertVec(expected: FloatArray, actual: FloatArray, tol: Float = 1e-3f) =
        assertArrayEquals(expected, actual, tol)

    /** Quaternion from an orthonormal basis given as the columns' images (device axes in world). */
    private fun quatFromBasis(x: FloatArray, y: FloatArray, z: FloatArray): Quat {
        val m00 = x[0]; val m10 = x[1]; val m20 = x[2]
        val m01 = y[0]; val m11 = y[1]; val m21 = y[2]
        val m02 = z[0]; val m12 = z[1]; val m22 = z[2]
        val tr = m00 + m11 + m22
        return if (tr > 0) {
            val s = sqrt(tr + 1f) * 2f
            Quat(0.25f * s, (m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s)
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            Quat((m21 - m12) / s, 0.25f * s, (m01 + m10) / s, (m02 + m20) / s)
        } else if (m11 > m22) {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            Quat((m02 - m20) / s, (m01 + m10) / s, 0.25f * s, (m12 + m21) / s)
        } else {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            Quat((m10 - m01) / s, (m02 + m20) / s, (m12 + m21) / s, 0.25f * s)
        }.normalized()
    }

    @Test
    fun quaternionRotationBasics() {
        val q = Quat.rotY((PI / 2).toFloat())
        assertVec(floatArrayOf(0f, 0f, -1f), q.rotate(floatArrayOf(1f, 0f, 0f)))
        assertEquals((PI / 2).toFloat(), q.yaw(), eps)
        val m = q.toMatrix()
        // Column-major: first column is the image of +X.
        assertVec(floatArrayOf(0f, 0f, -1f), floatArrayOf(m[0], m[1], m[2]))
        val p = Quat.rotX(0.5f)
        assertEquals(0.5f, p.pitch(), eps)
    }

    @Test
    fun phoneFlatOnTableLooksDown() {
        // Device frame == ENU world (screen up), portrait: camera looks through the back = down.
        val head = HeadPose.cameraFromDevice(Quat.IDENTITY, 0)
        assertVec(floatArrayOf(0f, -1f, 0f), head.forward())
    }

    @Test
    fun landscapeHeadsetFacingNorthLooksForwardAndUpright() {
        // Phone in a headset, landscape (ROTATION_90: top edge of the phone points left),
        // user facing north. Device axes expressed in ENU:
        //   device +X (right edge in portrait) -> up, device +Y (top) -> west, device +Z -> south.
        val device = quatFromBasis(
            x = floatArrayOf(0f, 0f, 1f),
            y = floatArrayOf(-1f, 0f, 0f),
            z = floatArrayOf(0f, -1f, 0f),
        )
        val head = HeadPose.cameraFromDevice(device, 90)
        assertVec(floatArrayOf(0f, 0f, -1f), head.forward())                       // north = -Z
        assertVec(floatArrayOf(0f, 1f, 0f), head.rotate(floatArrayOf(0f, 1f, 0f)))  // screen up = world up
        assertVec(floatArrayOf(1f, 0f, 0f), head.rotate(floatArrayOf(1f, 0f, 0f)))  // screen right = east

        // Turning to face east must yaw the view to +X.
        val turn = Quat.axisAngle(0f, 0f, 1f, (-PI / 2).toFloat()) // ENU: rotate clockwise seen from above
        val headEast = HeadPose.cameraFromDevice((turn * device).normalized(), 90)
        assertVec(floatArrayOf(1f, 0f, 0f), headEast.forward())
    }

    @Test
    fun recenterYawOnlyKeepsPitch() {
        val head = (Quat.rotY(1.1f) * Quat.rotX(-0.3f)).normalized()
        val fixed = HeadPose.yawRecenter(head) * head
        assertEquals(0f, fixed.yaw(), 1e-3f)
        assertEquals(-0.3f, fixed.pitch(), 1e-3f)
        val full = HeadPose.fullRecenter(head) * head
        assertVec(floatArrayOf(0f, 0f, -1f), full.forward())
    }

    @Test
    fun distortionMeshIsIdentityAtLensCentreAndBarrelsOutward() {
        val profile = ViewerProfile.DEFAULT
        val screen = ScreenGeometry(2400, 1080, 0.150f, 0.0675f)
        val mesh = DistortionMesh.build(profile, screen, rightEye = false)
        val n = DistortionMesh.GRID
        fun uv(i: Int, j: Int): FloatArray { val k = (j * (n + 1) + i) * 4; return floatArrayOf(mesh.vertices[k + 2], mesh.vertices[k + 3]) }
        // Texture coordinates must grow monotonically left->right and bottom->top (no fold-over).
        for (j in 0..n) for (i in 1..n) assertTrue(uv(i, j)[0] > uv(i - 1, j)[0])
        for (i in 0..n) for (j in 1..n) assertTrue(uv(i, j)[1] > uv(i, j - 1)[1])
        // Vertical centre row maps to v = 0.5.
        assertEquals(0.5f, uv(n / 2, n / 2)[1], 0.02f)
        // Undistorted vs distorted: the corrected mesh reaches further into the texture at the edges.
        val plain = DistortionMesh.build(profile, screen, rightEye = false, correct = false)
        val kEdge = (n / 2 * (n + 1) + 0) * 4
        assertTrue(abs(mesh.vertices[kEdge + 2] - 0.5f) > abs(plain.vertices[kEdge + 2] - 0.5f))
        assertEquals(n * n * 6, mesh.indices.size)
    }

    @Test
    fun rightEyeMeshMirrorsLeft() {
        val profile = ViewerProfile.DEFAULT
        val screen = ScreenGeometry(2400, 1080, 0.150f, 0.0675f)
        val l = DistortionMesh.build(profile, screen, rightEye = false)
        val r = DistortionMesh.build(profile, screen, rightEye = true)
        val n = DistortionMesh.GRID
        val j = n / 3
        for (i in 0..n) {
            val kl = (j * (n + 1) + i) * 4
            val kr = (j * (n + 1) + (n - i)) * 4
            assertEquals(l.vertices[kl + 2], 1f - r.vertices[kr + 2], 1e-4f)
            assertEquals(l.vertices[kl + 3], r.vertices[kr + 3], 1e-4f)
        }
    }

    @Test
    fun screenGeometryRejectsBogusDpi() {
        val g = ScreenGeometry.fromDpi(2400, 1080, xdpi = 160f, ydpi = 160f, densityDpi = 440)
        assertEquals(2400 / 440f * 0.0254f, g.widthM, 1e-4f)
        val ok = ScreenGeometry.fromDpi(2400, 1080, xdpi = 401f, ydpi = 403f, densityDpi = 420)
        assertEquals(2400 / 401f * 0.0254f, ok.widthM, 1e-4f)
    }

    @Test
    fun equirectSphereCentreFacesForward() {
        val m = Meshes.equirect(360f, 8, 16)
        // Middle vertex (u=0.5, v=0.5) must sit straight ahead at -Z.
        val k = (4 * 17 + 8) * 5
        assertVec(floatArrayOf(0f, 0f, -Meshes.SPHERE_RADIUS, 0.5f, 0.5f), m.vertices.copyOfRange(k, k + 5))
        // u = 0.75 is 90° to the right (+X).
        val k2 = (4 * 17 + 12) * 5
        assertVec(floatArrayOf(Meshes.SPHERE_RADIUS, 0f, 0f), m.vertices.copyOfRange(k2, k2 + 3))
    }

    @Test
    fun fisheyeMeshMatchesProjectionFormula() {
        val m = Meshes.fisheye(190f, 10, 24)
        for (v in 0 until m.vertices.size / 5) {
            val k = v * 5
            val uv = Meshes.fisheyeUv(m.vertices[k], m.vertices[k + 1], m.vertices[k + 2], 190f)
            assertEquals(uv[0], m.vertices[k + 3], 1e-3f)
            assertEquals(uv[1], m.vertices[k + 4], 1e-3f)
        }
    }

    @Test
    fun curvedScreenEdgesComeTowardsViewer() {
        val m = Meshes.quad(4f, 2f, 60f, distance = 4f, cols = 8)
        val zLeft = m.vertices[2]
        val zMid = m.vertices[4 * 5 + 2]
        assertTrue(zLeft > zMid)
        assertEquals(0f, zMid, 1e-5f)
    }

    @Test
    fun gazeDwellFiresOnceForNonRepeatable() {
        val g = GazeController(1000)
        assertNull(g.update(3, 0))
        assertNull(g.update(3, 500))
        assertEquals(0.5f, g.progress, 0.01f)
        assertEquals(3, g.update(3, 1000))
        assertNull(g.update(3, 2500)) // still staring: no second click
        assertNull(g.update(GazeController.NONE, 2600))
        assertNull(g.update(3, 2700))
        assertEquals(3, g.update(3, 3700))
    }

    @Test
    fun gazeDwellRepeatsForRepeatable() {
        val g = GazeController(1000)
        g.update(1, 0)
        assertEquals(1, g.update(1, 1000) { true })
        assertNull(g.update(1, 1500) { true })
        assertEquals(1, g.update(1, 2000) { true })
    }

    @Test
    fun convertOutputSizes() {
        val mono = VideoFormat.FLAT_2D
        assertEquals(3840 to 1080, convertOutputSize(1920, 1080, mono, ConvertOutput.FULL_SBS))
        assertEquals(1920 to 1080, convertOutputSize(1920, 1080, mono, ConvertOutput.HALF_SBS))
        // 4K source is capped to a 3840-wide encode.
        assertEquals(3840 to 1080, convertOutputSize(3840, 2160, mono, ConvertOutput.FULL_SBS))
        val tb = VideoFormat(Projection.FLAT, StereoLayout.TB)
        assertEquals(3840 to 1080, convertOutputSize(1920, 2160, tb, ConvertOutput.FULL_SBS))
        assertEquals(1920 to 1080, convertOutputSize(1920, 1080, tb, ConvertOutput.HALF_SBS))
        val (w, h) = convertOutputSize(1279, 721, mono, ConvertOutput.FULL_SBS)
        assertEquals(0, w % 2); assertEquals(0, h % 2)
    }
}
