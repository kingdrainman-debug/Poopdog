package com.poopdog.vr.vr

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.poopdog.vr.VrApp
import com.poopdog.vr.data.QueueState
import com.poopdog.vr.data.VrSettings
import com.poopdog.vr.model.FormatDetector
import com.poopdog.vr.model.QueueItem
import com.poopdog.vr.model.StereoLayout
import com.poopdog.vr.model.Projection
import com.poopdog.vr.model.VideoFormat
import com.poopdog.vr.playback.QueueEngine
import java.util.Locale
import kotlin.math.abs

/**
 * Full-screen headset player. Plays a single video or the whole queue hands-free:
 * per-item loop counts, auto-advance with an "Up next" countdown, gaze/dwell controls,
 * Bluetooth remote / gamepad / media keys, look-down menu, sleep timer and resume.
 */
@OptIn(UnstableApi::class)
class VrActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val MODE_QUEUE = "queue"
        private const val MODE_SINGLE = "single"
        private const val EXTRA_START = "start"
        private const val EXTRA_RESUME = "resume"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_FORMAT = "format"
        private const val EXTRA_LOOPS = "loops"

        fun queueIntent(ctx: Context, startIndex: Int, resume: Boolean): Intent =
            Intent(ctx, VrActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_QUEUE)
                .putExtra(EXTRA_START, startIndex)
                .putExtra(EXTRA_RESUME, resume)

        fun singleIntent(ctx: Context, uri: Uri, title: String, format: VideoFormat?, loops: Int): Intent =
            Intent(ctx, VrActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_SINGLE)
                .putExtra(EXTRA_URI, uri.toString())
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_FORMAT, format?.key())
                .putExtra(EXTRA_LOOPS, loops)

        private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        private const val TICK_MS = 33L
    }

    private val app by lazy { VrApp.from(this) }
    private lateinit var settings: VrSettings

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: VrRenderer
    private val headState = HeadState()
    private lateinit var tracker: HeadTracker
    private val hud = Hud()
    private val subtitlePanel = TextPanel(1024, 256, 46f, withBackground = false)
    private val toastPanel = TextPanel(1024, 200, 42f, withBackground = true)
    private val gaze = GazeController()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var player: ExoPlayer
    private lateinit var engine: QueueEngine
    private var queueMode = false

    private var currentFormat = VideoFormat.FLAT_2D
    private var formatIsAuto = true
    private var detectName = ""
    private var detectW = 0
    private var detectH = 0
    private var speedIndex = 2

    private var pendingIndex: Int? = null
    private var countdownEndsAt = 0L
    private var sleepDeadline = 0L
    private var sleepAtEnd = false
    private var lastHudInteraction = 0L
    private var lookDownSince = 0L
    private var toastUntil = 0L
    private var lastHudState: Hud.State? = null
    private var hudBanner: String? = null
    private var lastGazeU = 0f
    private var finishedQueue = false
    /** Step deferred by the "stop after this video" sleep timer; runs on the next Play. */
    private var sleepPendingStep: QueueEngine.Step? = null
    private var videoSurface: Surface? = null
    private var resumedBefore = false

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = app.settings.current
        setupWindow()

        val items = buildItems()
        if (items.isEmpty()) {
            finish()
            return
        }

        tracker = HeadTracker(this, headState)
        headState.trackingEnabled = settings.headTracking && tracker.available
        tracker.onFirstSample = { handler.post { headState.recenterNow(settings.recenterFull) } }
        gaze.dwellMs = settings.dwellMs.toLong()

        val dm = resources.displayMetrics
        renderer = VrRenderer(headState, hud, subtitlePanel, toastPanel, dm.xdpi, dm.ydpi, dm.densityDpi) { surface ->
            videoSurface = surface
            if (::player.isInitialized) player.setVideoSurface(surface)
        }
        renderer.gazeEnabled = settings.gazeControl
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            keepScreenOn = true
        }
        setContentView(glView)
        setupTouch()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(Locale.getDefault().language)
            .setSelectUndeterminedTextLanguage(true)
            .build()
        player.addListener(playerListener)
        videoSurface?.let { player.setVideoSurface(it) }

        val qs = app.queue.state.value
        val start = intent.getIntExtra(EXTRA_START, 0)
        engine = QueueEngine(
            items,
            startIndex = start,
            repeatQueue = queueMode && qs.repeatQueue,
            shuffle = queueMode && qs.shuffle,
        )
        if (queueMode) {
            when {
                qs.sleepTimerMin == QueueState.SLEEP_END_OF_VIDEO -> sleepAtEnd = true
                qs.sleepTimerMin > 0 -> sleepDeadline = SystemClock.elapsedRealtime() + qs.sleepTimerMin * 60_000L
            }
        }

        var startPos = 0L
        if (queueMode && intent.getBooleanExtra(EXTRA_RESUME, false) && engine.current.id == qs.resumeItemId) {
            startPos = qs.resumePositionMs
        } else if (!queueMode && settings.resumePlayback) {
            startPos = app.memory.position(engine.current.uri)
        }
        startItem(engine.currentIndex, startPos)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (renderer.hudVisible) hideHud() else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (::glView.isInitialized) {
            glView.onResume()
            tracker.displayRotationDeg = displayRotation()
            tracker.start()
            handler.post(tick)
            // Coming back to a paused video (screen off, app switch): offer the controls.
            if (resumedBefore && !player.playWhenReady) handler.postDelayed({ showHud(fromLookDown = false) }, 300)
            resumedBefore = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (::glView.isInitialized) {
            handler.removeCallbacks(tick)
            tracker.stop()
            glView.onPause()
            player.pause()
            saveResume()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) player.release()
        if (::renderer.isInitialized) renderer.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (settings.maxBrightness) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
    }

    private fun hideSystemUi() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        val r = if (Build.VERSION.SDK_INT >= 30) display?.rotation ?: Surface.ROTATION_90
        else windowManager.defaultDisplay.rotation
        return when (r) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    // ------------------------------------------------------------------ items

    private fun buildItems(): List<QueueItem> {
        val i = intent
        if (i.action == Intent.ACTION_VIEW && i.data != null) {
            val uri = i.data!!
            return listOf(QueueItem(uri.toString(), displayName(uri), loops = settings.defaultLoops))
        }
        return when (i.getStringExtra(EXTRA_MODE)) {
            MODE_QUEUE -> {
                queueMode = true
                app.queue.state.value.items
            }
            MODE_SINGLE -> {
                val uri = i.getStringExtra(EXTRA_URI) ?: return emptyList()
                listOf(
                    QueueItem(
                        uri = uri,
                        title = i.getStringExtra(EXTRA_TITLE) ?: "Video",
                        loops = i.getIntExtra(EXTRA_LOOPS, settings.defaultLoops),
                        format = VideoFormat.fromKey(i.getStringExtra(EXTRA_FORMAT)),
                    ),
                )
            }
            else -> emptyList()
        }
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment ?: "Video"
    }

    // ------------------------------------------------------------------ playback

    private fun startItem(index: Int, startPositionMs: Long = 0L) {
        pendingIndex = null
        sleepPendingStep = null
        finishedQueue = false
        hudBanner = null
        if (engine.currentIndex != index) engine.jumpTo(index)
        val item = engine.current

        detectName = item.title + " " + (Uri.parse(item.uri).lastPathSegment ?: "")
        detectW = item.width
        detectH = item.height
        val remembered = app.memory.format(item.uri)
        val forced = item.format ?: remembered
        formatIsAuto = forced == null
        currentFormat = forced ?: autoFormat(FormatDetector.MetaStereo.NONE, false)
        if (item.width > 0 && item.height > 0) renderer.frameAspect = item.width.toFloat() / item.height
        applyRenderConfig()
        subtitlePanel.draw(emptyList()); renderer.subtitlesVisible = false

        val builder = MediaItem.Builder().setUri(Uri.parse(item.uri)).setMediaId(item.id)
        item.subtitleUri?.let { sub ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub))
                        .setMimeType(subtitleMime(sub))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        player.setMediaItem(builder.build(), startPositionMs.coerceAtLeast(0L))
        player.setPlaybackSpeed(SPEEDS[speedIndex])
        updateRepeatMode()
        player.prepare()
        player.play()

        // No "now playing" pop-up: a new video starts with a clean view for immersion
        // (title and queue position stay available in the control panel). Also clear any
        // leftover message, e.g. the last second of the "Up next" countdown.
        renderer.toastVisible = false
        if (queueMode) app.queue.saveResume(item.id, startPositionMs)
    }

    private fun autoFormat(meta: FormatDetector.MetaStereo, spherical: Boolean): VideoFormat {
        if (!settings.autoDetectFormat) {
            return if (settings.auto2dTo3d) VideoFormat.FLAT_2D.copy(depth3d = true) else VideoFormat.FLAT_2D
        }
        val f = FormatDetector.detect(detectName, detectW, detectH, meta, spherical)
        return if (settings.auto2dTo3d && f.projection == Projection.FLAT && f.layout == StereoLayout.MONO) {
            f.copy(depth3d = true)
        } else f
    }

    private fun subtitleMime(uri: String): String {
        val lower = (displayName(Uri.parse(uri))).lowercase()
        return when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun applyRenderConfig() {
        val s = settings
        renderer.config = RenderConfig(
            format = currentFormat,
            viewMode = s.viewMode,
            profile = s.profile,
            distortion = s.distortionCorrection,
            swapEyes = s.swapEyes,
            screenFovDeg = s.screenSizeDeg,
            screenDistance = s.screenDistanceM,
            curvedScreen = s.curvedScreen,
            headLockedScreen = s.headLockedScreen,
            depthStrength = s.depthStrength,
            aspectMode = s.aspectMode,
            showReticle = s.showReticle,
        )
        lastHudState = null
    }

    private fun updateRepeatMode() {
        player.repeatMode = if (engine.shouldRepeatCurrent()) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) onPassFinished(fromRepeat = true)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED && pendingIndex == null && !finishedQueue && sleepPendingStep == null) {
                onPassFinished(fromRepeat = false)
            }
            lastHudState = null
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            lastHudState = null
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            renderer.frameAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            if (formatIsAuto && (detectW != videoSize.width || detectH != videoSize.height)) {
                detectW = videoSize.width
                detectH = videoSize.height
                val f = autoFormat(FormatDetector.MetaStereo.NONE, false)
                if (f != currentFormat) { currentFormat = f; applyRenderConfig() }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (!formatIsAuto) return
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_VIDEO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSelected(i)) continue
                    val f = group.getTrackFormat(i)
                    val meta = when (f.stereoMode) {
                        C.STEREO_MODE_LEFT_RIGHT -> FormatDetector.MetaStereo.LEFT_RIGHT
                        C.STEREO_MODE_TOP_BOTTOM -> FormatDetector.MetaStereo.TOP_BOTTOM
                        C.STEREO_MODE_MONO -> FormatDetector.MetaStereo.MONO
                        else -> FormatDetector.MetaStereo.NONE
                    }
                    val spherical = f.projectionData != null
                    if (meta != FormatDetector.MetaStereo.NONE || spherical) {
                        if (f.width > 0 && f.height > 0) { detectW = f.width; detectH = f.height }
                        val nf = autoFormat(meta, spherical)
                        if (nf != currentFormat) { currentFormat = nf; applyRenderConfig() }
                    }
                }
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            val lines = if (settings.showSubtitles) cueGroup.cues.mapNotNull { it.text?.toString()?.trim() }
                .filter { it.isNotEmpty() } else emptyList()
            val visible = subtitlePanel.draw(lines)
            renderer.subtitlesDirty.set(true)
            renderer.subtitlesVisible = visible
        }

        override fun onPlayerError(error: PlaybackException) {
            showToast(listOf("Can't play ${engine.current.title}", error.errorCodeName), 4000)
            val failedId = engine.current.id
            if (settings.skipOnError && engine.size > 1) {
                handler.postDelayed({
                    // Only skip if the user hasn't already moved on.
                    if (engine.current.id != failedId) return@postDelayed
                    when (val step = engine.next()) {
                        is QueueEngine.Step.Play -> startItem(step.index)
                        else -> onQueueFinished()
                    }
                }, 3000)
            } else {
                showHud(fromLookDown = false)
            }
        }
    }

    /** One pass of the current video ended (naturally or by looping around). */
    private fun onPassFinished(fromRepeat: Boolean) {
        if (!queueMode) app.memory.savePosition(engine.current.uri, 0)
        val step = engine.onItemFinished()
        if (sleepAtEnd) {
            sleepAtEnd = false
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.pause()
            sleepPendingStep = step
            showToast(listOf("Sleep timer", "Stopped after this video"), 6000)
            return
        }
        runStep(step, fromRepeat)
    }

    private fun runStep(step: QueueEngine.Step, fromRepeat: Boolean) {
        when (step) {
            is QueueEngine.Step.Repeat -> {
                updateRepeatMode()
                if (!fromRepeat) {
                    player.seekTo(0)
                    player.play()
                }
                showToast(listOf(engine.current.title, "Loop ${step.pass} of ${loopsText(engine.loopsTotal)}"), 2500)
            }
            is QueueEngine.Step.Play -> {
                if (fromRepeat) {
                    // Player already wrapped around; stop it while we switch.
                    player.pause()
                }
                scheduleAdvance(step.index)
            }
            QueueEngine.Step.Finished -> onQueueFinished()
        }
    }

    private fun loopsText(total: Int) = if (total == QueueItem.LOOP_FOREVER) "∞" else total.toString()

    private fun onQueueFinished() {
        finishedQueue = true
        player.pause()
        if (queueMode) app.queue.saveResume(null, 0)
        showToast(listOf(if (queueMode) "End of queue" else "Finished", "Look at Play to start again"), 6000)
        showHud(fromLookDown = false)
    }

    private fun scheduleAdvance(index: Int) {
        val delay = settings.autoAdvanceDelaySec
        if (delay <= 0) {
            startItem(index)
            return
        }
        pendingIndex = index
        countdownEndsAt = SystemClock.uptimeMillis() + delay * 1000L
    }

    private fun startPendingNow(): Boolean {
        val p = pendingIndex ?: return false
        startItem(p)
        return true
    }

    // ------------------------------------------------------------------ actions

    private fun togglePlay() {
        if (startPendingNow()) return
        sleepPendingStep?.let { step ->
            sleepPendingStep = null
            runStep(step, fromRepeat = false)
            return
        }
        if (finishedQueue) {
            val step = engine.restart() as QueueEngine.Step.Play
            startItem(step.index)
            return
        }
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
        lastHudState = null
    }

    private fun next() {
        if (startPendingNow()) return
        when (val step = engine.next()) {
            is QueueEngine.Step.Play -> startItem(step.index)
            else -> showToast(listOf("End of queue"), 2000)
        }
    }

    private fun previous() {
        pendingIndex = null
        if (player.currentPosition > 5000 && !finishedQueue) {
            player.seekTo(0)
            return
        }
        when (val step = engine.previous()) {
            is QueueEngine.Step.Play -> startItem(step.index)
            else -> Unit
        }
    }

    private fun seekBy(deltaMs: Long) {
        if (pendingIndex != null) return
        val d = player.duration
        var target = player.currentPosition + deltaMs
        if (d != C.TIME_UNSET) target = target.coerceAtMost(d - 500)
        player.seekTo(target.coerceAtLeast(0))
        lastHudState = null
    }

    private fun adjustLoops(delta: Int) {
        val loops = engine.adjustCurrentLoops(delta)
        updateRepeatMode()
        if (queueMode) app.queue.replaceItem(engine.current)
        showToast(listOf("Loops: ${loopsText(loops)}", "Now on pass ${engine.pass}"), 2000)
        lastHudState = null
    }

    private fun cycleFormat() {
        currentFormat = VideoFormat.nextInCycle(currentFormat)
        formatIsAuto = false
        val item = engine.current
        app.memory.saveFormat(item.uri, currentFormat)
        engine.replaceCurrent(item.copy(format = currentFormat))
        if (queueMode) app.queue.replaceItem(engine.current)
        applyRenderConfig()
        showToast(listOf("Format", currentFormat.shortLabel), 2000)
    }

    private fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.size
        player.setPlaybackSpeed(SPEEDS[speedIndex])
        showToast(listOf("Speed ${Hud.trimSpeed(SPEEDS[speedIndex])}x"), 1500)
        lastHudState = null
    }

    private fun recenter() {
        headState.recenterNow(settings.recenterFull)
        if (renderer.hudVisible) placeHud(fromLookDown = false)
        showToast(listOf("Recentered"), 1200)
    }

    private fun perform(b: Hud.Button) {
        lastHudInteraction = SystemClock.uptimeMillis()
        when (b) {
            Hud.Button.PREV -> previous()
            Hud.Button.BACK10 -> seekBy(-10_000)
            Hud.Button.PLAY -> togglePlay()
            Hud.Button.FWD10 -> seekBy(10_000)
            Hud.Button.NEXT -> next()
            Hud.Button.LOOP_MINUS -> adjustLoops(-1)
            Hud.Button.LOOP_PLUS -> adjustLoops(+1)
            Hud.Button.FORMAT -> cycleFormat()
            Hud.Button.SPEED -> cycleSpeed()
            Hud.Button.RECENTER -> recenter()
            Hud.Button.EXIT -> finish()
            Hud.Button.SEEKBAR -> {
                val d = player.duration
                if (d != C.TIME_UNSET && d > 0 && pendingIndex == null) player.seekTo((hud.seekFraction(lastGazeU) * d).toLong())
            }
        }
        lastHudState = null
    }

    // ------------------------------------------------------------------ HUD

    private fun showHud(fromLookDown: Boolean) {
        placeHud(fromLookDown)
        renderer.hudVisible = true
        gaze.reset()
        lastHudInteraction = SystemClock.uptimeMillis()
        lastHudState = null
    }

    private fun placeHud(fromLookDown: Boolean) {
        val q = renderer.lastHead
        renderer.hudYaw = q.yaw()
        val pitchDeg = Math.toDegrees(q.pitch().toDouble()).toFloat()
        val target = if (fromLookDown) -28f else (pitchDeg - 16f).coerceIn(-55f, 25f)
        renderer.hudPitch = Math.toRadians(target.toDouble()).toFloat()
    }

    private fun hideHud() {
        renderer.hudVisible = false
        renderer.dwellProgress = 0f
        gaze.reset()
    }

    private fun toggleHud() = if (renderer.hudVisible) hideHud() else showHud(fromLookDown = false)

    private fun hoveredButton(): Hud.Button? {
        if (!renderer.hudVisible) return null
        val g = renderer.gazeOnHud ?: return null
        lastGazeU = g[0]
        return hud.hitTest(g[0], g[1])
    }

    private fun showToast(lines: List<String>, durationMs: Long) {
        if (toastPanel.draw(lines)) {
            renderer.toastDirty.set(true)
            renderer.toastVisible = true
            toastUntil = SystemClock.uptimeMillis() + durationMs
        }
    }

    private fun onTick() {
        val now = SystemClock.uptimeMillis()
        tracker.displayRotationDeg = displayRotation()

        // Gaze dwell.
        val hovered = hoveredButton()
        if (hovered != null) lastHudInteraction = now
        if (settings.gazeControl && renderer.hudVisible) {
            val fired = gaze.update(hovered?.ordinal ?: GazeController.NONE, now) { Hud.Button.entries[it].repeatable }
            renderer.dwellProgress = gaze.progress
            if (fired != null) perform(Hud.Button.entries[fired])
        }

        // Auto-hide while playing.
        if (renderer.hudVisible && player.isPlaying && pendingIndex == null &&
            now - lastHudInteraction > settings.hudTimeoutSec * 1000L) hideHud()

        // Look down to open the menu.
        if (settings.lookDownMenu && !renderer.hudVisible && headState.trackingEnabled) {
            val pitch = Math.toDegrees(renderer.lastHead.pitch().toDouble())
            if (pitch < -50) {
                if (lookDownSince == 0L) lookDownSince = now
                else if (now - lookDownSince > 700) { showHud(fromLookDown = true); lookDownSince = 0L }
            } else lookDownSince = 0L
        }

        // Auto-advance countdown.
        pendingIndex?.let { idx ->
            val left = ((countdownEndsAt - now + 999) / 1000).toInt()
            if (left <= 0) {
                startItem(idx)
            } else {
                hudBanner = "Up next in ${left}s: ${engine.item(idx).title}"
                showToast(listOf("Up next in ${left}s", engine.item(idx).title + loopsNote(idx)), 1500)
            }
        }

        // Sleep timer.
        if (sleepDeadline > 0 && SystemClock.elapsedRealtime() >= sleepDeadline) {
            sleepDeadline = 0
            player.pause()
            pendingIndex?.let { idx ->
                // Don't auto-start the next video after the timer fires.
                pendingIndex = null
                sleepPendingStep = QueueEngine.Step.Play(idx)
            }
            showToast(listOf("Sleep timer", "Playback stopped"), 8000)
        }

        if (renderer.toastVisible && now > toastUntil) renderer.toastVisible = false

        if (renderer.hudVisible) redrawHud(hovered)
    }

    private fun loopsNote(idx: Int): String {
        val l = engine.item(idx).loops
        return if (l == 1) "" else "  (${QueueItem.loopsLabel(l)})"
    }

    private fun redrawHud(hovered: Hud.Button?) {
        val item = engine.current
        val info = buildString {
            if (queueMode) append("Video ${engine.positionInOrder + 1} of ${engine.size}  ·  ")
            append("Loop ${engine.pass} of ${loopsText(engine.loopsTotal)}")
            if (sleepDeadline > 0) {
                val min = ((sleepDeadline - SystemClock.elapsedRealtime()) / 60_000L) + 1
                append("  ·  Sleep in ${min}m")
            } else if (sleepAtEnd) append("  ·  Sleep after this video")
        }
        val d = player.duration
        val state = Hud.State(
            title = item.title,
            info = info,
            positionMs = (player.currentPosition / 1000) * 1000,
            durationMs = if (d == C.TIME_UNSET) 0 else d,
            playing = player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            hovered = hovered,
            hoverSeekFraction = if (hovered == Hud.Button.SEEKBAR) (hud.seekFraction(lastGazeU) * 100).toInt() / 100f else -1f,
            formatLabel = currentFormat.shortLabel,
            speed = SPEEDS[speedIndex],
            loopLabel = "${engine.pass}/${loopsText(engine.loopsTotal)}",
            banner = if (pendingIndex != null) hudBanner else null,
        )
        if (state != lastHudState) {
            hud.draw(state)
            renderer.hudDirty.set(true)
            lastHudState = state
        }
    }

    // ------------------------------------------------------------------ input

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onTap()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                // Touch look-around: always for yaw, pitch only when sensors are off.
                val k = 0.004f
                headState.dragYaw += dx * k
                if (!headState.trackingEnabled) {
                    headState.dragPitch = (headState.dragPitch + dy * k).coerceIn(-1.4f, 1.4f)
                }
                return true
            }
        })
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                renderer.monoFovDeg = (renderer.monoFovDeg / d.scaleFactor).coerceIn(35f, 110f)
                return true
            }
        })
        glView.setOnTouchListener { _, ev ->
            scale.onTouchEvent(ev)
            if (!scale.isInProgress) gestures.onTouchEvent(ev)
            true
        }
    }

    /** Screen tap — also the Cardboard trigger button and BT remotes in mouse mode. */
    private fun onTap() {
        if (renderer.hudVisible) {
            val b = hoveredButton()
            if (b != null) perform(b) else hideHud()
        } else {
            showHud(fromLookDown = false)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0 && keyCode !in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_START -> togglePlay()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (!player.isPlaying) togglePlay()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val b = hoveredButton()
                if (b != null) perform(b) else togglePlay()
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_N -> next()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_P -> previous()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_BUTTON_R2 -> seekBy(10_000)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_BUTTON_L2 -> seekBy(-10_000)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_M -> toggleHud()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_R -> recenter()
            KeyEvent.KEYCODE_BUTTON_B -> if (renderer.hudVisible) hideHud()
            KeyEvent.KEYCODE_L -> adjustLoops(+1)
            KeyEvent.KEYCODE_F -> cycleFormat()
            KeyEvent.KEYCODE_VOLUME_UP -> if (settings.volumeKeysControl) next() else return super.onKeyDown(keyCode, event)
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (settings.volumeKeysControl) togglePlay() else return super.onKeyDown(keyCode, event)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ------------------------------------------------------------------ persistence

    private fun saveResume() {
        if (!::engine.isInitialized) return
        val pos = player.currentPosition
        val d = player.duration
        if (queueMode) {
            if (!finishedQueue) app.queue.saveResume(engine.current.id, if (pendingIndex != null) 0 else pos)
        } else {
            val keep = pos > 5000 && (d == C.TIME_UNSET || abs(d - pos) > 15_000)
            app.memory.savePosition(engine.current.uri, if (keep) pos else 0)
        }
    }
}
