@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.poopdog.vr.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poopdog.vr.VrApp
import com.poopdog.vr.convert.ConvertOutput
import com.poopdog.vr.data.Thumbnails
import com.poopdog.vr.data.VideoEntry
import com.poopdog.vr.model.Projection
import com.poopdog.vr.model.StereoLayout
import com.poopdog.vr.model.VideoFormat
import com.poopdog.vr.vr.VrActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = VrApp.from(application).media
    val device = MutableStateFlow<List<VideoEntry>>(emptyList())
    val picked = MutableStateFlow<List<VideoEntry>>(emptyList())
    val loading = MutableStateFlow(false)

    fun refresh() = viewModelScope.launch {
        loading.value = true
        device.value = repo.loadDeviceVideos()
        picked.value = repo.loadPickedVideos()
        loading.value = false
    }

    fun addPicked(uris: List<Uri>) = viewModelScope.launch {
        repo.rememberPicked(uris)
        picked.value = repo.loadPickedVideos()
    }

    fun removePicked(uri: Uri) = viewModelScope.launch {
        repo.forgetPicked(uri)
        picked.value = repo.loadPickedVideos()
    }
}

enum class SortMode(val label: String) { NEWEST("Newest first"), NAME("Name"), LONGEST("Longest"), LARGEST("Largest") }

private const val ALL = "All videos"
private const val PICKED = "Picked files"

fun videoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

fun hasVideoPermission(ctx: Context): Boolean {
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

@Composable
fun LibraryScreen(onQueueChanged: (String) -> Unit, vm: LibraryViewModel = viewModel()) {
    val ctx = LocalContext.current
    val app = VrApp.from(ctx)
    val settings by app.settings.settings.collectAsState()
    val device by vm.device.collectAsState()
    val picked by vm.picked.collectAsState()
    val loading by vm.loading.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var permitted by remember { mutableStateOf(hasVideoPermission(ctx)) }
    var folder by remember { mutableStateOf(ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortMode.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Uri>() }
    var formatFor by remember { mutableStateOf<VideoEntry?>(null) }
    var convertFor by remember { mutableStateOf<VideoEntry?>(null) }
    var showStream by remember { mutableStateOf(false) }
    var formatVersion by remember { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasVideoPermission(ctx)
        vm.refresh()
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            vm.addPicked(uris)
            folder = PICKED
        }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    fun notify(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun addToQueue(entries: List<VideoEntry>, playNext: Boolean = false) {
        val items = entries.map { it.toQueueItem(settings.defaultLoops, app.memory.format(it.uri.toString())) }
        app.queue.add(items, playNext)
        val msg = if (entries.size == 1) "Added \"${entries[0].title}\" to queue" else "Added ${entries.size} videos to queue"
        onQueueChanged(msg)
        notify(msg)
    }

    fun play(e: VideoEntry) {
        ctx.startActivity(
            VrActivity.singleIntent(ctx, e.uri, e.title, app.memory.format(e.uri.toString()), settings.defaultLoops),
        )
    }

    val all = remember(device, picked) { (picked + device).distinctBy { it.uri.toString() } }
    val folders = remember(device) { device.map { it.folder }.distinct().sortedBy { it.lowercase() } }
    val shown = remember(all, folder, query, sort) {
        all.asSequence()
            .filter {
                when (folder) {
                    ALL -> true
                    PICKED -> it.folder == PICKED || it.isStream
                    else -> it.folder == folder
                }
            }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .let { s ->
                when (sort) {
                    SortMode.NEWEST -> s.sortedByDescending { it.dateAddedSec }
                    SortMode.NAME -> s.sortedBy { it.name.lowercase() }
                    SortMode.LONGEST -> s.sortedByDescending { it.durationMs }
                    SortMode.LARGEST -> s.sortedByDescending { it.sizeBytes }
                }
            }
            .toList()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, "Clear selection") }
                    },
                    actions = {
                        IconButton(onClick = {
                            shown.forEach { if (it.uri !in selected) selected.add(it.uri) }
                        }) { Icon(Icons.Default.SelectAll, "Select all") }
                        IconButton(onClick = {
                            addToQueue(shown.filter { it.uri in selected })
                            selected.clear()
                        }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue") }
                    },
                )
            } else if (searching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Search videos") }, modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, "Close search") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Library") },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search") }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.label) },
                                        leadingIcon = { RadioButton(selected = m == sort, onClick = null) },
                                        onClick = { sort = m; sortMenu = false },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showStream = true }) { Icon(Icons.Default.Link, "Open stream URL") }
                        IconButton(onClick = { pickLauncher.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Default.FolderOpen, "Open files")
                        }
                        IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!permitted) {
                PermissionCard(
                    onAllow = { permLauncher.launch(videoPermissions()) },
                    onPick = { pickLauncher.launch(arrayOf("video/*")) },
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chips = buildList {
                    add(ALL)
                    if (picked.isNotEmpty()) add(PICKED)
                    addAll(folders)
                }
                items(chips) { c ->
                    FilterChip(selected = folder == c, onClick = { folder = c }, label = { Text(c) })
                }
            }
            if (shown.isEmpty() && !loading) {
                EmptyLibrary(permitted)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.uri.toString() }) { e ->
                    val remembered = remember(e.uri, formatVersion) { app.memory.format(e.uri.toString()) }
                    VideoRow(
                        entry = e,
                        format = remembered ?: e.detectedFormat,
                        formatForced = remembered != null,
                        selected = e.uri in selected,
                        selectionMode = selected.isNotEmpty(),
                        onClick = {
                            if (selected.isNotEmpty()) {
                                if (e.uri in selected) selected.remove(e.uri) else selected.add(e.uri)
                            } else play(e)
                        },
                        onLongClick = { if (e.uri in selected) selected.remove(e.uri) else selected.add(e.uri) },
                        onPlay = { play(e) },
                        onAdd = { addToQueue(listOf(e)) },
                        onPlayNext = { addToQueue(listOf(e), playNext = true) },
                        onFormat = { formatFor = e },
                        onConvert = { convertFor = e },
                        onRemovePicked = if (e.folder == PICKED || e.isStream) ({ vm.removePicked(e.uri) }) else null,
                    )
                }
            }
        }
    }

    formatFor?.let { e ->
        FormatPickerDialog(
            current = app.memory.format(e.uri.toString()),
            detected = e.detectedFormat,
            onDismiss = { formatFor = null },
            onPick = { f ->
                app.memory.saveFormat(e.uri.toString(), f)
                formatVersion++
                formatFor = null
            },
        )
    }

    convertFor?.let { e ->
        ConvertDialog(e, onDismiss = { convertFor = null }) { input, output, depth ->
            app.converter.start(e.uri, e.title, input, output, depth)
            convertFor = null
        }
    }

    if (showStream) {
        StreamDialog(
            onDismiss = { showStream = false },
            onPlay = { url, title ->
                showStream = false
                ctx.startActivity(VrActivity.singleIntent(ctx, Uri.parse(url), title, null, settings.defaultLoops))
            },
            onQueue = { url, title ->
                showStream = false
                vm.addPicked(listOf(Uri.parse(url)))
                val e = VideoEntry(Uri.parse(url), title, 0, 0, 0, 0, System.currentTimeMillis() / 1000, PICKED, isStream = true)
                addToQueue(listOf(e))
            },
        )
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit, onPick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("See your videos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Allow access to list the videos on this phone, or pick individual files without granting access.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("Allow access") }
                OutlinedButton(onClick = onPick) { Text("Pick files") }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(permitted: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Movie, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permitted) "No videos found. Use the folder button to pick files, or the link button for a stream URL."
            else "No videos yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoRow(
    entry: VideoEntry,
    format: VideoFormat,
    formatForced: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    onPlayNext: () -> Unit,
    onFormat: () -> Unit,
    onConvert: () -> Unit,
    onRemovePicked: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    val thumb by produceState<Bitmap?>(Thumbnails.cached(entry.uri), entry.uri) {
        if (value == null) value = Thumbnails.load(ctx, entry)
    }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val b = thumb
            if (b != null) {
                Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(if (entry.isStream) Icons.Default.Link else Icons.Default.Movie, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.durationMs > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                ) {
                    Text(formatDuration(entry.durationMs), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val info = listOfNotNull(
                if (entry.width > 0) "${entry.width}×${entry.height}" else null,
                formatSize(entry.sizeBytes).ifEmpty { null },
                if (entry.isStream) "Stream" else null,
            ).joinToString(" · ")
            if (info.isNotEmpty()) {
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (formatForced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    (if (formatForced) "" else "Auto: ") + format.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (!selectionMode) {
            FilledIconButton(onClick = onPlay) { Icon(Icons.Default.ViewInAr, "Play in headset") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; onAdd() })
                    DropdownMenuItem(text = { Text("Play next in queue") }, onClick = { menu = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("Set VR format…") }, onClick = { menu = false; onFormat() })
                    if (!entry.isStream) {
                        DropdownMenuItem(text = { Text("Convert to VR file…") }, onClick = { menu = false; onConvert() })
                    }
                    if (onRemovePicked != null) {
                        DropdownMenuItem(text = { Text("Remove from list") }, onClick = { menu = false; onRemovePicked() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvertDialog(entry: VideoEntry, onDismiss: () -> Unit, onStart: (VideoFormat, ConvertOutput, Float) -> Unit) {
    val detected = entry.detectedFormat
    val inputs = listOf(
        "2D video → 3D (synthesised depth)" to VideoFormat(Projection.FLAT, StereoLayout.MONO, depth3d = true),
        "2D video → VR (same image both eyes)" to VideoFormat(Projection.FLAT, StereoLayout.MONO),
        "3D Top/Bottom → Side-by-side" to VideoFormat(Projection.FLAT, StereoLayout.TB),
    )
    var input by remember {
        mutableStateOf(if (detected.layout == StereoLayout.TB) inputs[2].second else inputs[0].second)
    }
    var output by remember { mutableStateOf(ConvertOutput.FULL_SBS) }
    var depth by remember { mutableFloatStateOf(0.6f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert to VR file") },
        text = {
            Column {
                Text("Creates a side-by-side 3D copy in Movies/PoopdogVR that plays in any VR player.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Source", style = MaterialTheme.typography.labelLarge)
                inputs.forEach { (label, f) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = input == f, onClick = { input = f })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (input.depth3d) {
                    Text("3D depth: ${(depth * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = depth, onValueChange = { depth = it }, valueRange = 0.1f..1f)
                }
                Text("Output", style = MaterialTheme.typography.labelLarge)
                ConvertOutput.entries.forEach { o ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = output == o, onClick = { output = o })
                        Text(o.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (detected.projection != Projection.FLAT) {
                    Text("This looks like a ${detected.shortLabel} video, which is already a VR format — just play it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(input, output, if (input.depth3d) depth else 0f) }) { Text("Convert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StreamDialog(onDismiss: () -> Unit, onPlay: (String, String) -> Unit, onQueue: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val valid = url.startsWith("http://") || url.startsWith("https://")
    fun name() = title.ifBlank { Uri.parse(url).lastPathSegment ?: "Stream" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play from URL") },
        text = {
            Column {
                Text("MP4, MKV, WebM, HLS (.m3u8) or DASH (.mpd). Add format tags like _180_LR to the name for auto-detect.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it.trim() }, label = { Text("URL") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Name (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = valid, onClick = { onQueue(url, name()) }) { Text("Add to queue") }
                TextButton(enabled = valid, onClick = { onPlay(url, name()) }) { Text("Play") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
