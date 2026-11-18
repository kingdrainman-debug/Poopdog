package com.poopdog.vr

import com.poopdog.vr.convert.SbsShaderProgram
import com.poopdog.vr.model.Projection
import com.poopdog.vr.vr.DistortionMesh
import com.poopdog.vr.vr.Meshes
import com.poopdog.vr.vr.ScreenGeometry
import com.poopdog.vr.vr.Shaders
import com.poopdog.vr.vr.ViewerProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sanity checks on the GLSL sources, and a dump of shaders + meshes into build/shader-dump
 * so tools/shader-harness.html can compile and render them with WebGL (GLSL ES 1.00).
 */
class ShaderDumpTest {
    private val sources = mapOf(
        "mesh.vert" to Shaders.MESH_VERTEX,
        "video_oes.frag" to Shaders.VIDEO_FRAGMENT_OES,
        "video_2d.frag" to Shaders.VIDEO_FRAGMENT_2D,
        "panel.frag" to Shaders.PANEL_FRAGMENT,
        "reticle.frag" to Shaders.RETICLE_FRAGMENT,
        "distort.vert" to Shaders.DISTORT_VERTEX,
        "distort.frag" to Shaders.DISTORT_FRAGMENT,
        "export.vert" to SbsShaderProgram.VERTEX,
        "export.frag" to SbsShaderProgram.FRAGMENT,
    )

    @Test
    fun shadersAreWellFormed() {
        for ((name, src) in sources) {
            assertTrue("$name has main()", src.contains("void main()"))
            assertFalse("$name uses ES3 syntax", Regex("\\b(in|out|texture)\\s*\\(").containsMatchIn(src.replace("texture2D", "")))
            if (name.endsWith(".frag")) assertTrue("$name sets precision", src.contains("precision"))
        }
        assertTrue(Shaders.VIDEO_FRAGMENT_OES.trimStart().startsWith("#extension GL_OES_EGL_image_external"))
    }

    @Test
    fun dumpForHarness() {
        val dir = File("build/shader-dump").apply { mkdirs() }
        for ((name, src) in sources) File(dir, name).writeText(src)
        val screen = ScreenGeometry(2400, 1080, 0.1524f, 0.0686f)
        val sb = StringBuilder("{")
        fun arr(a: FloatArray) = a.joinToString(",", "[", "]") { "%.5f".format(java.util.Locale.US, it) }
        fun arrS(a: ShortArray) = a.joinToString(",", "[", "]")
        val p = ViewerProfile.DEFAULT
        val l = DistortionMesh.build(p, screen, false)
        val r = DistortionMesh.build(p, screen, true)
        sb.append("\"tanHalfFov\":${p.tanHalfFov},")
        sb.append("\"ipd\":${p.interLensMm / 1000f},")
        sb.append("\"distortL\":{\"v\":${arr(l.vertices)},\"i\":${arrS(l.indices)}},")
        sb.append("\"distortR\":{\"v\":${arr(r.vertices)},\"i\":${arrS(r.indices)}},")
        val s180 = Meshes.forProjection(Projection.EQUIRECT_180)
        sb.append("\"sphere180\":{\"v\":${arr(s180.vertices)},\"i\":${arrS(s180.indices)}},")
        val fish = Meshes.forProjection(Projection.FISHEYE_190)
        sb.append("\"fisheye190\":{\"v\":${arr(fish.vertices)},\"i\":${arrS(fish.indices)}},")
        val quad = Meshes.quad(1f, 1f, 0f, cols = 1)
        sb.append("\"quad\":{\"v\":${arr(quad.vertices)},\"i\":${arrS(quad.indices)}}")
        sb.append("}")
        File(dir, "meshes.json").writeText(sb.toString())
    }
}
