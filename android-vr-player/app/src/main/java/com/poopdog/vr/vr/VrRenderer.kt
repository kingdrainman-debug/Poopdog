package com.poopdog.vr.vr

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.poopdog.vr.model.AspectMode
import com.poopdog.vr.model.Projection
import com.poopdog.vr.model.StereoLayout
import com.poopdog.vr.model.VideoFormat
import com.poopdog.vr.model.flatEyeAspect
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

enum class ViewMode { VR, MONO }

/** Everything that affects how a frame is drawn; swapped atomically from the UI thread. */
data class RenderConfig(
    val format: VideoFormat = VideoFormat.FLAT_2D,
    val viewMode: ViewMode = ViewMode.VR,
    val profile: ViewerProfile = ViewerProfile.DEFAULT,
    val distortion: Boolean = true,
    val swapEyes: Boolean = false,
    val screenFovDeg: Float = 80f,
    val screenDistance: Float = 4f,
    val curvedScreen: Boolean = false,
    val headLockedScreen: Boolean = false,
    val depthStrength: Float = 0.5f,
    val aspectMode: AspectMode = AspectMode.AUTO,
    val showReticle: Boolean = true,
)

/**
 * Thread-safe head orientation: sensor pose × recenter correction × touch-drag offset.
 * Written from the sensor/UI threads, read by the GL thread every frame.
 */
class HeadState {
    @Volatile var sensorPose: Quat = Quat.IDENTITY
    @Volatile var recenter: Quat = Quat.IDENTITY
    @Volatile var dragYaw = 0f
    @Volatile var dragPitch = 0f
    @Volatile var trackingEnabled = true

    fun current(): Quat {
        val drag = Quat.rotY(dragYaw) * Quat.rotX(dragPitch)
        return if (trackingEnabled) (Quat.rotY(dragYaw) * recenter * sensorPose).normalized() else drag
    }

    fun recenterNow(full: Boolean) {
        dragYaw = 0f
        dragPitch = 0f
        val pose = sensorPose
        recenter = if (full) HeadPose.fullRecenter(pose) else HeadPose.yawRecenter(pose)
    }
}

/**
 * Converts any decoded video frame into a headset-ready image: projects it onto a flat /
 * curved cinema screen, 180°/360° sphere or fisheye dome, renders each eye (choosing that
 * eye's half of SBS/TB frames or synthesising depth for 2D), then barrel-warps both eyes
 * for the viewer's lenses.
 */
class VrRenderer(
    private val head: HeadState,
    private val hud: Hud,
    private val subtitles: TextPanel,
    private val toast: TextPanel,
    private val xdpi: Float,
    private val ydpi: Float,
    private val densityDpi: Int,
    private val onSurfaceReady: (Surface) -> Unit,
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    @Volatile var config = RenderConfig()
    /** Display aspect ratio (width/height) of the whole decoded frame. */
    @Volatile var frameAspect = 16f / 9f
    @Volatile var monoFovDeg = 80f

    @Volatile var hudVisible = false
    @Volatile var hudYaw = 0f
    @Volatile var hudPitch = 0f
    @Volatile var subtitlesVisible = false
    @Volatile var toastVisible = false
    @Volatile var dwellProgress = 0f
    @Volatile var gazeEnabled = true

    val hudDirty = AtomicBoolean(true)
    val subtitlesDirty = AtomicBoolean(false)
    val toastDirty = AtomicBoolean(false)

    /** Gaze position on the HUD in bitmap coordinates (u right, v down), or null. */
    @Volatile var gazeOnHud: FloatArray? = null
        private set
    /** Latest head orientation used for drawing (read by the UI thread). */
    @Volatile var lastHead: Quat = Quat.IDENTITY
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameAvailable = AtomicBoolean(false)
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private var videoProgram = 0
    private var panelProgram = 0
    private var reticleProgram = 0
    private var distortProgram = 0
    private var videoTex = 0
    private var hudTex = 0
    private var subTex = 0
    private var toastTex = 0

    private var videoMesh: GlMesh? = null
    private var videoMeshKey: Any? = null
    private val unitQuad by lazy { Meshes.quad(1f, 1f, 0f, cols = 1).let { GlMesh(5, it.vertices, it.indices) } }
    private var distortMeshes: Array<GlMesh>? = null
    private var distortKey: Any? = null
    private var eyeFbos: Array<IntArray>? = null
    private var eyeSize = 0

    private var width = 1
    private var height = 1

    private val texMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val headRot = FloatArray(16)
    private val tmp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val eyeT = FloatArray(16)

    // Uniform/attribute locations.
    private var vMvp = 0; private var vPos = 0; private var vUv = 0; private var vTexM = 0
    private var vEye = 0; private var vDepth = 0; private var vBright = 0; private var vTex = 0
    private var pMvp = 0; private var pPos = 0; private var pUv = 0; private var pAlpha = 0; private var pTex = 0
    private var rMvp = 0; private var rPos = 0; private var rUv = 0; private var rProg = 0
    private var dPos = 0; private var dUv = 0; private var dTex = 0

    override fun onSurfaceCreated(gl: GL10?, cfg: EGLConfig?) {
        videoProgram = GlUtil.program(Shaders.MESH_VERTEX, Shaders.VIDEO_FRAGMENT_OES)
        panelProgram = GlUtil.program(Shaders.MESH_VERTEX, Shaders.PANEL_FRAGMENT)
        reticleProgram = GlUtil.program(Shaders.MESH_VERTEX, Shaders.RETICLE_FRAGMENT)
        distortProgram = GlUtil.program(Shaders.DISTORT_VERTEX, Shaders.DISTORT_FRAGMENT)

        vMvp = GLES20.glGetUniformLocation(videoProgram, "uMvp")
        vPos = GLES20.glGetAttribLocation(videoProgram, "aPosition")
        vUv = GLES20.glGetAttribLocation(videoProgram, "aTexCoord")
        vTexM = GLES20.glGetUniformLocation(videoProgram, "uTexMatrix")
        vEye = GLES20.glGetUniformLocation(videoProgram, "uEyeRect")
        vDepth = GLES20.glGetUniformLocation(videoProgram, "uDepth")
        vBright = GLES20.glGetUniformLocation(videoProgram, "uBrightness")
        vTex = GLES20.glGetUniformLocation(videoProgram, "uTex")
        pMvp = GLES20.glGetUniformLocation(panelProgram, "uMvp")
        pPos = GLES20.glGetAttribLocation(panelProgram, "aPosition")
        pUv = GLES20.glGetAttribLocation(panelProgram, "aTexCoord")
        pAlpha = GLES20.glGetUniformLocation(panelProgram, "uAlpha")
        pTex = GLES20.glGetUniformLocation(panelProgram, "uTex")
        rMvp = GLES20.glGetUniformLocation(reticleProgram, "uMvp")
        rPos = GLES20.glGetAttribLocation(reticleProgram, "aPosition")
        rUv = GLES20.glGetAttribLocation(reticleProgram, "aTexCoord")
        rProg = GLES20.glGetUniformLocation(reticleProgram, "uProgress")
        dPos = GLES20.glGetAttribLocation(distortProgram, "aPosition")
        dUv = GLES20.glGetAttribLocation(distortProgram, "aTexCoord")
        dTex = GLES20.glGetUniformLocation(distortProgram, "uTex")

        videoTex = GlUtil.externalTexture()
        hudTex = GlUtil.texture2d()
        subTex = GlUtil.texture2d()
        toastTex = GlUtil.texture2d()
        hudDirty.set(true); subtitlesDirty.set(true); toastDirty.set(true)

        // A new GL context means all GL objects are gone.
        videoMesh = null; videoMeshKey = null; distortMeshes = null; distortKey = null; eyeFbos = null

        surfaceTexture?.release()
        surface?.release()
        val st = SurfaceTexture(videoTex).also { it.setOnFrameAvailableListener(this) }
        val s = Surface(st)
        surfaceTexture = st
        surface = s
        mainHandler.post { onSurfaceReady(s) }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        distortKey = null
        eyeFbos?.forEach { GlUtil.deleteFramebuffer(it) }
        eyeFbos = null
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        frameAvailable.set(true)
    }

    fun release() {
        surfaceTexture?.release()
        surface?.release()
        surfaceTexture = null
        surface = null
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        if (frameAvailable.getAndSet(false)) {
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
            } catch (_: RuntimeException) {
                // Surface abandoned during teardown.
            }
        }
        uploadPanels()

        val cfg = config
        val q = head.current()
        lastHead = q
        q.conjugate().toMatrix(headRot)
        updateGaze(q)
        ensureVideoMesh(cfg)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        if (cfg.viewMode == ViewMode.MONO) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            Matrix.perspectiveM(proj, 0, monoFovDeg, width.toFloat() / height, 0.1f, 100f)
            drawScene(cfg, eye = 0)
            return
        }

        val halfW = width / 2
        if (cfg.distortion) {
            ensureDistortion(cfg)
            val fbos = eyeFbos!!
            val t = cfg.profile.tanHalfFov * 0.1f
            Matrix.frustumM(proj, 0, -t, t, -t, t, 0.1f, 100f)
            for (eye in 1..2) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[eye - 1][0])
                GLES20.glViewport(0, 0, eyeSize, eyeSize)
                drawScene(cfg, eye)
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUseProgram(distortProgram)
            val meshes = distortMeshes!!
            for (eye in 1..2) {
                GLES20.glViewport(if (eye == 1) 0 else halfW, 0, halfW, height)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbos[eye - 1][1])
                GLES20.glUniform1i(dTex, 0)
                meshes[eye - 1].draw(dPos, dUv, 2)
            }
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            Matrix.perspectiveM(proj, 0, cfg.profile.fovHalfDeg * 2f, halfW.toFloat() / height, 0.1f, 100f)
            for (eye in 1..2) {
                GLES20.glViewport(if (eye == 1) 0 else halfW, 0, halfW, height)
                drawScene(cfg, eye)
            }
        }
    }

    /** @param eye 0 = mono, 1 = left, 2 = right. */
    private fun drawScene(cfg: RenderConfig, eye: Int) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val ipd = cfg.profile.interLensMm / 1000f
        val eyeShift = when (eye) { 1 -> ipd / 2f; 2 -> -ipd / 2f; else -> 0f }
        Matrix.setIdentityM(eyeT, 0)
        Matrix.translateM(eyeT, 0, eyeShift, 0f, 0f)

        // ---- Video ----
        val fmt = cfg.format
        val mesh = videoMesh ?: return
        Matrix.setIdentityM(model, 0)
        if (fmt.projection == Projection.FLAT) {
            Matrix.translateM(model, 0, 0f, 0f, -cfg.screenDistance)
            if (cfg.headLockedScreen) {
                System.arraycopy(eyeT, 0, view, 0, 16)
            } else {
                Matrix.multiplyMM(view, 0, eyeT, 0, headRot, 0)
            }
        } else {
            // Spherical content carries its own stereo; translating the eyes inside a
            // 10 m sphere would only add false parallax.
            System.arraycopy(headRot, 0, view, 0, 16)
        }
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(videoProgram)
        GLES20.glUniformMatrix4fv(vMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(vTexM, 1, false, texMatrix, 0)
        val swap = fmt.swapEyes != cfg.swapEyes
        val sampleEye = if (eye == 0) 1 else if (swap) 3 - eye else eye
        when (fmt.layout) {
            StereoLayout.MONO -> GLES20.glUniform4f(vEye, 0f, 0f, 1f, 1f)
            StereoLayout.SBS -> GLES20.glUniform4f(vEye, if (sampleEye == 1) 0f else 0.5f, 0f, 0.5f, 1f)
            StereoLayout.TB -> GLES20.glUniform4f(vEye, 0f, if (sampleEye == 1) 0.5f else 0f, 1f, 0.5f)
        }
        val depth = if (fmt.depth3d && fmt.layout == StereoLayout.MONO && eye != 0) {
            val s = 0.035f * cfg.depthStrength
            if (eye == 1) -s else s
        } else 0f
        GLES20.glUniform1f(vDepth, depth)
        GLES20.glUniform1f(vBright, 1f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTex)
        GLES20.glUniform1i(vTex, 0)
        mesh.draw(vPos, vUv, 3)

        // ---- Overlays ----
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        if (subtitlesVisible) {
            drawPanel(subTex, eyeT, null, 0f, -0.62f, Hud.DISTANCE, 1.8f, 1.8f * 256f / 1024f)
        }
        if (toastVisible) {
            drawPanel(toastTex, eyeT, null, 0f, 0.42f, Hud.DISTANCE, 1.6f, 1.6f * 200f / 1024f)
        }
        if (hudVisible) {
            Matrix.multiplyMM(view, 0, eyeT, 0, headRot, 0)
            val hudQ = Quat.rotY(hudYaw) * Quat.rotX(hudPitch)
            drawPanel(hudTex, view, hudQ, 0f, 0f, Hud.DISTANCE, Hud.WORLD_WIDTH, Hud.WORLD_HEIGHT)
            if (gazeEnabled && cfg.showReticle) drawReticle()
        }
    }

    private fun drawPanel(tex: Int, viewM: FloatArray, orient: Quat?, x: Float, y: Float, dist: Float, w: Float, h: Float) {
        Matrix.setIdentityM(model, 0)
        if (orient != null) orient.toMatrix(model)
        Matrix.translateM(model, 0, x, y, -dist)
        Matrix.scaleM(model, 0, w, h, 1f)
        Matrix.multiplyMM(tmp, 0, viewM, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)
        GLES20.glUseProgram(panelProgram)
        GLES20.glUniformMatrix4fv(pMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(pAlpha, 1f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(pTex, 0)
        unitQuad.draw(pPos, pUv, 3)
    }

    private fun drawReticle() {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, 0f, -Hud.DISTANCE + 0.02f)
        val size = 0.09f
        Matrix.scaleM(model, 0, size, size, 1f)
        Matrix.multiplyMM(tmp, 0, eyeT, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)
        GLES20.glUseProgram(reticleProgram)
        GLES20.glUniformMatrix4fv(rMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(rProg, dwellProgress)
        unitQuad.draw(rPos, rUv, 3)
    }

    private fun uploadPanels() {
        if (hudDirty.getAndSet(false)) synchronized(hud.lock) { GlUtil.upload(hudTex, hud.bitmap) }
        if (subtitlesDirty.getAndSet(false)) synchronized(subtitles.lock) { GlUtil.upload(subTex, subtitles.bitmap) }
        if (toastDirty.getAndSet(false)) synchronized(toast.lock) { GlUtil.upload(toastTex, toast.bitmap) }
    }

    /** Intersect the gaze ray (camera forward) with the HUD plane. */
    private fun updateGaze(q: Quat) {
        if (!hudVisible) { gazeOnHud = null; return }
        val hudQ = Quat.rotY(hudYaw) * Quat.rotX(hudPitch)
        val c = hudQ.rotate(floatArrayOf(0f, 0f, -Hud.DISTANCE))
        val r = hudQ.rotate(floatArrayOf(1f, 0f, 0f))
        val u = hudQ.rotate(floatArrayOf(0f, 1f, 0f))
        val n = hudQ.rotate(floatArrayOf(0f, 0f, 1f))
        val f = q.forward()
        val denom = f[0] * n[0] + f[1] * n[1] + f[2] * n[2]
        if (denom >= -1e-4f) { gazeOnHud = null; return }
        val t = (c[0] * n[0] + c[1] * n[1] + c[2] * n[2]) / denom
        if (t <= 0f) { gazeOnHud = null; return }
        val px = f[0] * t - c[0]; val py = f[1] * t - c[1]; val pz = f[2] * t - c[2]
        val lx = (px * r[0] + py * r[1] + pz * r[2]) / Hud.WORLD_WIDTH + 0.5f
        val ly = 0.5f - (px * u[0] + py * u[1] + pz * u[2]) / Hud.WORLD_HEIGHT
        gazeOnHud = if (lx in 0f..1f && ly in 0f..1f) floatArrayOf(lx, ly) else null
    }

    private fun ensureVideoMesh(cfg: RenderConfig) {
        val fmt = cfg.format
        val key: Any = if (fmt.projection == Projection.FLAT) {
            val aspect = flatEyeAspect(fmt, frameAspect, cfg.aspectMode)
            listOf(fmt.projection, aspect, cfg.screenFovDeg, cfg.screenDistance, cfg.curvedScreen)
        } else fmt.projection
        if (key == videoMeshKey && videoMesh != null) return
        val m = if (fmt.projection == Projection.FLAT) {
            val aspect = flatEyeAspect(fmt, frameAspect, cfg.aspectMode)
            val fovRad = Math.toRadians(cfg.screenFovDeg.toDouble()).toFloat()
            val w = if (cfg.curvedScreen) cfg.screenDistance * fovRad
            else 2f * cfg.screenDistance * tan(fovRad / 2f)
            Meshes.quad(w, w / aspect, if (cfg.curvedScreen) cfg.screenFovDeg else 0f, cfg.screenDistance)
        } else {
            Meshes.forProjection(fmt.projection)
        }
        videoMesh = GlMesh(5, m.vertices, m.indices)
        videoMeshKey = key
    }

    private fun ensureDistortion(cfg: RenderConfig) {
        val screen = ScreenGeometry.fromDpi(width, height, xdpi, ydpi, densityDpi)
        val key = listOf(cfg.profile, screen)
        if (key != distortKey || distortMeshes == null) {
            distortMeshes = arrayOf(
                DistortionMesh.build(cfg.profile, screen, rightEye = false).let { GlMesh(4, it.vertices, it.indices) },
                DistortionMesh.build(cfg.profile, screen, rightEye = true).let { GlMesh(4, it.vertices, it.indices) },
            )
            distortKey = key
        }
        val size = (min(2048, max(1024, (height * 1.4f).toInt())) / 16) * 16
        if (eyeFbos == null || size != eyeSize) {
            eyeFbos?.forEach { GlUtil.deleteFramebuffer(it) }
            eyeFbos = arrayOf(GlUtil.framebuffer(size, size), GlUtil.framebuffer(size, size))
            eyeSize = size
        }
    }
}
