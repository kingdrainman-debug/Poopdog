package com.poopdog.vr.convert

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.poopdog.vr.model.VideoFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ConvertState {
    data object Idle : ConvertState
    data class Running(val name: String, val progress: Int) : ConvertState
    data class Done(val name: String, val uri: Uri) : ConvertState
    data class Failed(val name: String, val message: String) : ConvertState
}

/**
 * Converts a regular (2D or top/bottom 3D) video into a side-by-side VR file saved to
 * Movies/PoopdogVR, so it can be watched in this or any other headset player.
 * Uses Media3 Transformer with hardware encoding; runs on the main looper.
 */
@OptIn(UnstableApi::class)
class VrConverter(private val context: Context) {
    private val _state = MutableStateFlow<ConvertState>(ConvertState.Idle)
    val state: StateFlow<ConvertState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transformer: Transformer? = null
    private var tmpFile: File? = null

    fun start(source: Uri, title: String, input: VideoFormat, output: ConvertOutput, depth: Float) {
        if (_state.value is ConvertState.Running) return
        val outName = "${sanitize(title)}_${output.tag}.mp4"
        val dir = context.externalCacheDir ?: context.cacheDir
        val tmp = File(dir, "convert-${System.currentTimeMillis()}.mp4")
        tmpFile = tmp
        _state.value = ConvertState.Running(outName, 0)

        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    io.launch {
                        val uri = runCatching { publish(tmp, outName) }.getOrNull()
                        tmp.delete()
                        _state.value = if (uri != null) ConvertState.Done(outName, uri)
                        else ConvertState.Failed(outName, "Could not save to Movies/PoopdogVR")
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    transformer = null
                    tmp.delete()
                    _state.value = ConvertState.Failed(outName, exportException.errorCodeName + ": " + (exportException.message ?: ""))
                }
            })
            .build()
        transformer = t

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(Effects(emptyList(), listOf(SbsEffect(input, output, depth))))
            .build()
        try {
            t.start(edited, tmp.absolutePath)
        } catch (e: Exception) {
            transformer = null
            _state.value = ConvertState.Failed(outName, e.message ?: e.toString())
            return
        }
        pollProgress(outName)
    }

    private fun pollProgress(name: String) {
        val holder = ProgressHolder()
        handler.postDelayed(object : Runnable {
            override fun run() {
                val t = transformer ?: return
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _state.value = ConvertState.Running(name, holder.progress)
                }
                handler.postDelayed(this, 500)
            }
        }, 500)
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
        tmpFile?.delete()
        _state.value = ConvertState.Idle
    }

    fun dismiss() {
        if (_state.value !is ConvertState.Running) _state.value = ConvertState.Idle
    }

    private fun publish(file: File, name: String): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PoopdogVR")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "PoopdogVR")
        dir.mkdirs()
        val dest = File(dir, name)
        file.copyTo(dest, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
        return Uri.fromFile(dest)
    }

    private fun sanitize(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "video" }
}
