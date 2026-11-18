package com.poopdog.vr.data

import android.content.Context
import com.poopdog.vr.model.QueueItem
import com.poopdog.vr.model.VideoFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val shuffle: Boolean = false,
    val repeatQueue: Boolean = false,
    /** 0 = off, [SLEEP_END_OF_VIDEO] = stop when the current video ends, else minutes. */
    val sleepTimerMin: Int = 0,
    /** Where hands-free playback stopped last time, for "Resume queue". */
    val resumeItemId: String? = null,
    val resumePositionMs: Long = 0L,
) {
    val resumeIndex: Int get() = items.indexOfFirst { it.id == resumeItemId }

    companion object {
        const val SLEEP_END_OF_VIDEO = -1
    }
}

data class Playlist(val name: String, val items: List<QueueItem>)

/** The play queue plus saved playlists, persisted as JSON in SharedPreferences. */
class QueueRepository(context: Context) {
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    private val _playlists = MutableStateFlow(loadPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun update(transform: (QueueState) -> QueueState) {
        val next = transform(_state.value)
        _state.value = next
        saveState(next)
    }

    fun add(items: List<QueueItem>, playNext: Boolean = false) = update { s ->
        if (playNext) {
            val at = (s.resumeIndex + 1).coerceIn(0, s.items.size)
            s.copy(items = s.items.subList(0, at) + items + s.items.subList(at, s.items.size))
        } else {
            s.copy(items = s.items + items)
        }
    }

    fun remove(id: String) = update { s -> s.copy(items = s.items.filterNot { it.id == id }) }

    fun move(from: Int, to: Int) = update { s ->
        if (from !in s.items.indices || to !in s.items.indices) return@update s
        val list = s.items.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        s.copy(items = list)
    }

    fun replaceItem(item: QueueItem) = update { s ->
        s.copy(items = s.items.map { if (it.id == item.id) item else it })
    }

    fun clear() = update { it.copy(items = emptyList(), resumeItemId = null, resumePositionMs = 0) }

    fun saveResume(itemId: String?, positionMs: Long) =
        update { it.copy(resumeItemId = itemId, resumePositionMs = positionMs) }

    // ---- Playlists ----

    fun savePlaylist(name: String, items: List<QueueItem>) {
        val list = _playlists.value.filterNot { it.name == name } + Playlist(name, items)
        _playlists.value = list.sortedBy { it.name.lowercase() }
        savePlaylists(_playlists.value)
    }

    fun deletePlaylist(name: String) {
        _playlists.value = _playlists.value.filterNot { it.name == name }
        savePlaylists(_playlists.value)
    }

    fun loadPlaylist(p: Playlist, append: Boolean) = update { s ->
        // Fresh ids so the same playlist can be appended twice.
        val fresh = p.items.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
        if (append) s.copy(items = s.items + fresh)
        else s.copy(items = fresh, resumeItemId = null, resumePositionMs = 0)
    }

    // ---- Persistence ----

    private fun loadState(): QueueState = try {
        QueueState(
            items = itemsFromJson(prefs.getString("items", "[]")!!),
            shuffle = prefs.getBoolean("shuffle", false),
            repeatQueue = prefs.getBoolean("repeat", false),
            sleepTimerMin = prefs.getInt("sleep", 0),
            resumeItemId = prefs.getString("resumeId", null),
            resumePositionMs = prefs.getLong("resumePos", 0L),
        )
    } catch (_: Exception) {
        QueueState()
    }

    private fun saveState(s: QueueState) {
        prefs.edit()
            .putString("items", itemsToJson(s.items).toString())
            .putBoolean("shuffle", s.shuffle)
            .putBoolean("repeat", s.repeatQueue)
            .putInt("sleep", s.sleepTimerMin)
            .putString("resumeId", s.resumeItemId)
            .putLong("resumePos", s.resumePositionMs)
            .apply()
    }

    private fun loadPlaylists(): List<Playlist> = try {
        val arr = JSONArray(prefs.getString("playlists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(o.getString("name"), itemsFromJson(o.getJSONArray("items").toString()))
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p -> arr.put(JSONObject().put("name", p.name).put("items", itemsToJson(p.items))) }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    companion object {
        fun itemsToJson(items: List<QueueItem>): JSONArray {
            val arr = JSONArray()
            items.forEach { arr.put(itemToJson(it)) }
            return arr
        }

        fun itemToJson(it: QueueItem): JSONObject = JSONObject()
            .put("id", it.id)
            .put("uri", it.uri)
            .put("title", it.title)
            .put("loops", it.loops)
            .put("format", it.format?.key() ?: "")
            .put("durationMs", it.durationMs)
            .put("width", it.width)
            .put("height", it.height)
            .put("subtitleUri", it.subtitleUri ?: "")

        fun itemsFromJson(json: String): List<QueueItem> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i -> itemFromJson(arr.getJSONObject(i)) }
        }

        fun itemFromJson(o: JSONObject): QueueItem = QueueItem(
            uri = o.getString("uri"),
            title = o.optString("title", "Video"),
            loops = o.optInt("loops", 1),
            format = VideoFormat.fromKey(o.optString("format", "")),
            durationMs = o.optLong("durationMs", 0L),
            width = o.optInt("width", 0),
            height = o.optInt("height", 0),
            subtitleUri = o.optString("subtitleUri", "").ifEmpty { null },
            id = o.optString("id", java.util.UUID.randomUUID().toString()),
        )
    }
}

/** Per-video memory: last playback position and the user's chosen format. */
class PlaybackMemory(context: Context) {
    private val prefs = context.getSharedPreferences("memory", Context.MODE_PRIVATE)

    fun position(uri: String): Long = prefs.getLong("pos:$uri", 0L)
    fun savePosition(uri: String, ms: Long) {
        prefs.edit().apply { if (ms <= 0) remove("pos:$uri") else putLong("pos:$uri", ms) }.apply()
    }

    fun format(uri: String): VideoFormat? = VideoFormat.fromKey(prefs.getString("fmt:$uri", null))
    fun saveFormat(uri: String, format: VideoFormat?) {
        prefs.edit().apply { if (format == null) remove("fmt:$uri") else putString("fmt:$uri", format.key()) }.apply()
    }
}
