package com.poopdog.vr.vr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * The in-headset control panel. Drawn with Android Canvas into a bitmap that the renderer
 * uploads as a texture and places in 3D space; buttons are hit-tested with the gaze ray.
 */
class Hud {
    enum class Button(val label: String, val repeatable: Boolean = false) {
        PREV("Prev"), BACK10("-10s", true), PLAY("Play"), FWD10("+10s", true), NEXT("Next"),
        LOOP_MINUS("Loop -"), LOOP_PLUS("Loop +"), FORMAT("Format"), SPEED("Speed"),
        RECENTER("Recenter"), EXIT("Exit"), SEEKBAR("Seek");
    }

    data class State(
        val title: String = "",
        val info: String = "",
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val playing: Boolean = false,
        val hovered: Button? = null,
        val hoverSeekFraction: Float = -1f,
        val formatLabel: String = "",
        val speed: Float = 1f,
        val loopLabel: String = "",
        val banner: String? = null,
    )

    val lock = Any()
    val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    private val rects = LinkedHashMap<Button, RectF>()
    private val seekRect = RectF(40f, 132f, WIDTH - 40f, 162f)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 18, 18, 26) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 179, 71); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 42, 42, 56) }
    private val btnHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 255, 179, 71) }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val iconDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 28); style = Paint.Style.FILL }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 40f; typeface = Typeface.DEFAULT_BOLD
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 200, 215); textSize = 28f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 230, 240); textSize = 24f; textAlign = Paint.Align.CENTER
    }
    private val labelDarkPaint = Paint(labelPaint).apply { color = Color.rgb(20, 20, 28); typeface = Typeface.DEFAULT_BOLD }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 70, 70, 90) }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 179, 71) }
    private val hoverSeekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 210, 140); textSize = 28f; typeface = Typeface.DEFAULT_BOLD }

    init {
        val row1 = listOf(Button.PREV, Button.BACK10, Button.PLAY, Button.FWD10, Button.NEXT)
        val row2 = listOf(Button.LOOP_MINUS, Button.LOOP_PLUS, Button.FORMAT, Button.SPEED, Button.RECENTER, Button.EXIT)
        layoutRow(row1, top = 190f, height = 150f, gap = 18f)
        layoutRow(row2, top = 362f, height = 116f, gap = 14f)
    }

    private fun layoutRow(buttons: List<Button>, top: Float, height: Float, gap: Float) {
        val margin = 40f
        val w = (WIDTH - 2 * margin - gap * (buttons.size - 1)) / buttons.size
        buttons.forEachIndexed { i, b ->
            val left = margin + i * (w + gap)
            rects[b] = RectF(left, top, left + w, top + height)
        }
    }

    /** Hit test in bitmap-normalised coordinates (u right, v down, both 0..1). */
    fun hitTest(u: Float, v: Float): Button? {
        val x = u * WIDTH
        val y = v * HEIGHT
        val seekHit = RectF(seekRect).apply { inset(0f, -18f) }
        if (seekHit.contains(x, y)) return Button.SEEKBAR
        return rects.entries.firstOrNull { it.value.contains(x, y) }?.key
    }

    fun seekFraction(u: Float): Float = ((u * WIDTH - seekRect.left) / seekRect.width()).coerceIn(0f, 1f)

    fun draw(s: State) = synchronized(lock) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val panel = RectF(4f, 4f, WIDTH - 4f, HEIGHT - 4f)
        canvas.drawRoundRect(panel, 36f, 36f, bgPaint)
        canvas.drawRoundRect(panel, 36f, 36f, borderPaint)

        canvas.drawText(ellipsize(s.title, titlePaint, WIDTH - 80f), 40f, 62f, titlePaint)
        val info = if (s.banner != null) s.banner else s.info
        canvas.drawText(ellipsize(info, if (s.banner != null) bannerPaint else infoPaint, WIDTH - 80f), 40f, 108f,
            if (s.banner != null) bannerPaint else infoPaint)

        // Seek bar.
        canvas.drawRoundRect(seekRect, 15f, 15f, trackPaint)
        if (s.durationMs > 0) {
            val f = (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f)
            val r = RectF(seekRect.left, seekRect.top, seekRect.left + seekRect.width() * f, seekRect.bottom)
            canvas.drawRoundRect(r, 15f, 15f, progressPaint)
        }
        if (s.hovered == Button.SEEKBAR && s.hoverSeekFraction >= 0f) {
            val x = seekRect.left + seekRect.width() * s.hoverSeekFraction
            canvas.drawRect(x - 3f, seekRect.top - 12f, x + 3f, seekRect.bottom + 12f, hoverSeekPaint)
        }
        val timeText = "${fmt(s.positionMs)} / ${if (s.durationMs > 0) fmt(s.durationMs) else "--:--"}"
        infoPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeText, WIDTH - 40f, 62f, infoPaint)
        infoPaint.textAlign = Paint.Align.LEFT

        for ((b, r) in rects) {
            val hovered = s.hovered == b
            canvas.drawRoundRect(r, 22f, 22f, if (hovered) btnHoverPaint else btnPaint)
            val ip = if (hovered) iconDarkPaint else iconPaint
            val lp = if (hovered) labelDarkPaint else labelPaint
            val label = when (b) {
                Button.PLAY -> if (s.playing) "Pause" else "Play"
                Button.FORMAT -> s.formatLabel.ifEmpty { "Format" }
                Button.SPEED -> "Speed ${trimSpeed(s.speed)}x"
                Button.LOOP_PLUS -> "Loop +"
                else -> b.label
            }
            val iconCy = r.top + r.height() * 0.40f
            drawIcon(b, s, r.centerX(), iconCy, r.height() * 0.22f, ip, lp)
            canvas.drawText(ellipsize(label, lp, r.width() - 12f), r.centerX(), r.bottom - 16f, lp)
        }
    }

    private fun drawIcon(b: Button, s: State, cx: Float, cy: Float, size: Float, p: Paint, lp: Paint) {
        val path = Path()
        when (b) {
            Button.PLAY -> if (s.playing) {
                canvas.drawRect(cx - size * 0.8f, cy - size, cx - size * 0.25f, cy + size, p)
                canvas.drawRect(cx + size * 0.25f, cy - size, cx + size * 0.8f, cy + size, p)
            } else {
                triangle(path, cx - size * 0.6f, cy, size, right = true); canvas.drawPath(path, p)
            }
            Button.NEXT -> {
                triangle(path, cx - size * 0.9f, cy, size * 0.9f, right = true); canvas.drawPath(path, p)
                canvas.drawRect(cx + size * 0.55f, cy - size * 0.9f, cx + size * 0.85f, cy + size * 0.9f, p)
            }
            Button.PREV -> {
                triangle(path, cx + size * 0.9f, cy, size * 0.9f, right = false); canvas.drawPath(path, p)
                canvas.drawRect(cx - size * 0.85f, cy - size * 0.9f, cx - size * 0.55f, cy + size * 0.9f, p)
            }
            Button.FWD10 -> {
                triangle(path, cx - size * 1.0f, cy, size * 0.75f, right = true)
                triangle(path, cx - size * 0.1f, cy, size * 0.75f, right = true); canvas.drawPath(path, p)
            }
            Button.BACK10 -> {
                triangle(path, cx + size * 1.0f, cy, size * 0.75f, right = false)
                triangle(path, cx + size * 0.1f, cy, size * 0.75f, right = false); canvas.drawPath(path, p)
            }
            Button.RECENTER -> {
                val ring = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = size * 0.22f }
                canvas.drawCircle(cx, cy, size * 0.95f, ring)
                canvas.drawCircle(cx, cy, size * 0.3f, p)
            }
            Button.EXIT -> {
                val line = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = size * 0.28f; strokeCap = Paint.Cap.ROUND }
                canvas.drawLine(cx - size * 0.75f, cy - size * 0.75f, cx + size * 0.75f, cy + size * 0.75f, line)
                canvas.drawLine(cx - size * 0.75f, cy + size * 0.75f, cx + size * 0.75f, cy - size * 0.75f, line)
            }
            else -> {
                val text = when (b) {
                    Button.LOOP_MINUS, Button.LOOP_PLUS -> s.loopLabel
                    Button.FORMAT -> "VR"
                    Button.SPEED -> "»"
                    else -> ""
                }
                val big = Paint(lp).apply { textSize = size * 1.5f; typeface = Typeface.DEFAULT_BOLD }
                canvas.drawText(text, cx, cy + size * 0.5f, big)
            }
        }
    }

    private fun triangle(path: Path, x: Float, cy: Float, size: Float, right: Boolean) {
        val w = size * 1.5f
        path.moveTo(x, cy - size)
        path.lineTo(if (right) x + w else x - w, cy)
        path.lineTo(x, cy + size)
        path.close()
    }

    companion object {
        const val WIDTH = 1024
        const val HEIGHT = 512
        /** Panel size in metres when placed in the scene. */
        const val WORLD_WIDTH = 1.6f
        val WORLD_HEIGHT = WORLD_WIDTH * HEIGHT / WIDTH
        const val DISTANCE = 2.4f

        fun fmt(ms: Long): String {
            val t = (ms / 1000).coerceAtLeast(0)
            val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }

        fun trimSpeed(s: Float): String =
            if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString().trimEnd('0')

        fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }
    }
}

/** Head-locked text card (subtitles). */
class TextPanel(val widthPx: Int, val heightPx: Int, private val fontPx: Float, private val withBackground: Boolean) {
    val lock = Any()
    val bitmap: Bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = fontPx; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val stroke = Paint(text).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = fontPx / 7f
    }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 18, 18, 26) }

    /** Draws [lines] centred at the bottom of the bitmap. Returns false if empty. */
    fun draw(lines: List<String>): Boolean = synchronized(lock) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val wrapped = lines.flatMap { wrap(it, widthPx - 60f) }.takeLast(4)
        if (wrapped.isEmpty()) return@synchronized false
        val lineH = fontPx * 1.25f
        val blockH = lineH * wrapped.size
        val top = heightPx - blockH - 12f
        if (withBackground) {
            val w = wrapped.maxOf { text.measureText(it) } + 60f
            canvas.drawRoundRect(RectF((widthPx - w) / 2f, top - 10f, (widthPx + w) / 2f, heightPx - 4f), 28f, 28f, bg)
        }
        wrapped.forEachIndexed { i, l ->
            val y = top + lineH * (i + 1) - fontPx * 0.25f
            if (!withBackground) canvas.drawText(l, widthPx / 2f, y, stroke)
            canvas.drawText(l, widthPx / 2f, y, text)
        }
        true
    }

    private fun wrap(s: String, max: Float): List<String> {
        val out = ArrayList<String>()
        for (para in s.split('\n')) {
            var line = ""
            for (word in para.split(' ')) {
                val cand = if (line.isEmpty()) word else "$line $word"
                if (text.measureText(cand) <= max || line.isEmpty()) line = cand else { out += line; line = word }
            }
            if (line.isNotBlank()) out += line
        }
        return out
    }
}
