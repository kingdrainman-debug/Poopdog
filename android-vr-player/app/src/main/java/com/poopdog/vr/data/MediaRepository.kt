package com.poopdog.vr.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import com.poopdog.vr.model.FormatDetector
import com.poopdog.vr.model.QueueItem
import com.poopdog.vr.model.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class VideoEntry(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedSec: Long,
    val folder: String,
    val mediaId: Long? = null,
    val isStream: Boolean = false,
) {
    val title: String get() = name.substringBeforeLast('.', name)
    val detectedFormat: VideoFormat get() = FormatDetector.detect(name, width, height)

    fun toQueueItem(loops: Int, format: VideoFormat? = null) = QueueItem(
        uri = uri.toString(), title = title, loops = loops, format = format,
        durationMs = durationMs, width = width, height = height,
    )
}

/** Device videos (MediaStore), files picked with the system picker, and stream URLs. */
class MediaRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("picked", Context.MODE_PRIVATE)

    suspend fun loadDeviceVideos(): List<VideoEntry> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val out = ArrayList<VideoEntry>()
        try {
            context.contentResolver.query(collection, projection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out += VideoEntry(
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        name = c.getString(iName) ?: "Video $id",
                        durationMs = c.getLong(iDur),
                        sizeBytes = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        dateAddedSec = c.getLong(iDate),
                        folder = c.getString(iBucket) ?: "Other",
                        mediaId = id,
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted (yet).
        }
        out
    }

    /** Files opened through the system picker; persisted so they survive restarts. */
    suspend fun loadPickedVideos(): List<VideoEntry> = withContext(Dispatchers.IO) {
        pickedUris().mapNotNull { runCatching { describe(Uri.parse(it)) }.getOrNull() }
    }

    fun rememberPicked(uris: List<Uri>) {
        for (u in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
        }
        val all = (pickedUris() + uris.map { it.toString() }).distinct()
        prefs.edit().putString("uris", JSONArray(all).toString()).apply()
    }

    fun forgetPicked(uri: Uri) {
        val all = pickedUris().filterNot { it == uri.toString() }
        prefs.edit().putString("uris", JSONArray(all).toString()).apply()
        try {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    private fun pickedUris(): List<String> = try {
        val arr = JSONArray(prefs.getString("uris", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    /** Reads name/size from the provider and duration/dimensions from the file itself. */
    suspend fun describe(uri: Uri): VideoEntry = withContext(Dispatchers.IO) {
        if (uri.scheme == "http" || uri.scheme == "https") {
            val name = uri.lastPathSegment ?: uri.host ?: "Stream"
            return@withContext VideoEntry(uri, name, 0, 0, 0, 0, System.currentTimeMillis() / 1000,
                "Streams", isStream = true)
        }
        var name = uri.lastPathSegment ?: "Video"
        var size = 0L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        var duration = 0L
        var w = 0
        var h = 0
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        } catch (_: Exception) {
        } finally {
            runCatching { r.release() }
        }
        VideoEntry(uri, name, duration, size, w, h, System.currentTimeMillis() / 1000, "Picked files")
    }
}

/** Small in-memory thumbnail cache. */
object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(uri: Uri): Bitmap? = cache.get(uri.toString())

    suspend fun load(context: Context, entry: VideoEntry): Bitmap? = withContext(Dispatchers.IO) {
        if (entry.isStream) return@withContext null
        cache.get(entry.uri.toString())?.let { return@withContext it }
        val bmp: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(entry.uri, Size(320, 180), null)
            } else if (entry.mediaId != null) {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, entry.mediaId,
                    MediaStore.Video.Thumbnails.MINI_KIND, null)
            } else frameOf(context, entry.uri)
        } catch (_: Exception) {
            runCatching { frameOf(context, entry.uri) }.getOrNull()
        }
        if (bmp != null) cache.put(entry.uri.toString(), bmp)
        bmp
    }

    private fun frameOf(context: Context, uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val f = r.getFrameAtTime(2_000_000) ?: return null
            val scale = 320f / maxOf(f.width, 1)
            Bitmap.createScaledBitmap(f, 320, (f.height * scale).toInt().coerceAtLeast(1), true)
        } finally {
            runCatching { r.release() }
        }
    }
}
