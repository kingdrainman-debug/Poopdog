@file:OptIn(ExperimentalMaterial3Api::class)

package com.poopdog.vr.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poopdog.vr.VrApp
import com.poopdog.vr.data.QueueState
import com.poopdog.vr.model.FormatDetector
import com.poopdog.vr.model.QueueItem
import com.poopdog.vr.model.totalPlayTimeMs
import com.poopdog.vr.vr.VrActivity

private val SLEEP_OPTIONS = listOf(0, QueueState.SLEEP_END_OF_VIDEO, 15, 30, 45, 60, 90, 120)

private fun sleepLabel(v: Int) = when (v) {
    0 -> "Off"
    QueueState.SLEEP_END_OF_VIDEO -> "After current video"
    else -> "$v min"
}

@Composable
fun QueueScreen() {
    val ctx = LocalContext.current
    val app = VrApp.from(ctx)
    val state by app.queue.state.collectAsState()
    val settings by app.settings.settings.collectAsState()
    val playlists by app.queue.playlists.collectAsState()

    var menu by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var loopsForAll by remember { mutableStateOf(false) }
    var formatFor by remember { mutableStateOf<QueueItem?>(null) }
    var subtitleFor by remember { mutableStateOf<QueueItem?>(null) }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val item = subtitleFor
        subtitleFor = null
        if (uri != null && item != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.queue.replaceItem(item.copy(subtitleUri = uri.toString()))
        }
    }

    fun play(start: Int, resume: Boolean) {
        if (state.items.isEmpty()) return
        ctx.startActivity(VrActivity.queueIntent(ctx, start, resume))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                actions = {
                    IconButton(onClick = { showPlaylists = true }) { Icon(Icons.Default.LibraryMusic, "Playlists") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Save as playlist…") }, enabled = state.items.isNotEmpty(),
                                onClick = { menu = false; saveDialog = true })
                            DropdownMenuItem(text = { Text("Set loops for all…") }, enabled = state.items.isNotEmpty(),
                                onClick = { menu = false; loopsForAll = true })
                            DropdownMenuItem(text = { Text("Clear queue") }, enabled = state.items.isNotEmpty(),
                                onClick = { menu = false; confirmClear = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                QueueHeader(
                    state = state,
                    autoAdvanceSec = settings.autoAdvanceDelaySec,
                    onPlay = { play(if (state.shuffle) state.items.indices.random() else 0, resume = false) },
                    onResume = { play(state.resumeIndex, resume = true) },
                    onShuffle = { v -> app.queue.update { it.copy(shuffle = v) } },
                    onRepeat = { v -> app.queue.update { it.copy(repeatQueue = v) } },
                    onSleep = { v -> app.queue.update { it.copy(sleepTimerMin = v) } },
                    onDelay = { v -> app.settings.update { it.copy(autoAdvanceDelaySec = v) } },
                )
            }
            if (state.items.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Your queue is empty.\nAdd videos from the Library (⋮ → Add to queue, or long-press to select several).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                QueueRow(
                    index = index,
                    item = item,
                    isLast = index == state.items.lastIndex,
                    isResumePoint = item.id == state.resumeItemId,
                    onLoops = { app.queue.replaceItem(item.copy(loops = it)) },
                    onUp = { app.queue.move(index, index - 1) },
                    onDown = { app.queue.move(index, index + 1) },
                    onRemove = { app.queue.remove(item.id) },
                    onPlayFrom = { play(index, resume = false) },
                    onFormat = { formatFor = item },
                    onSubtitle = { subtitleFor = item; subtitlePicker.launch(arrayOf("*/*")) },
                    onClearSubtitle = { app.queue.replaceItem(item.copy(subtitleUri = null)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    formatFor?.let { item ->
        FormatPickerDialog(
            current = item.format,
            detected = FormatDetector.detect(item.title + " " + (Uri.parse(item.uri).lastPathSegment ?: ""), item.width, item.height),
            onDismiss = { formatFor = null },
            onPick = { f ->
                app.queue.replaceItem(item.copy(format = f))
                app.memory.saveFormat(item.uri, f)
                formatFor = null
            },
        )
    }

    if (showPlaylists) {
        AlertDialog(
            onDismissRequest = { showPlaylists = false },
            title = { Text("Playlists") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No saved playlists yet. Build a queue, then ⋮ → Save as playlist.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(playlists, key = { _, p -> p.name }) { _, p ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("${p.items.size} videos", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row {
                                    TextButton(onClick = { app.queue.loadPlaylist(p, append = false); showPlaylists = false }) { Text("Load") }
                                    TextButton(onClick = { app.queue.loadPlaylist(p, append = true); showPlaylists = false }) { Text("Append") }
                                    TextButton(onClick = { app.queue.deletePlaylist(p.name) }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlaylists = false }) { Text("Close") } },
        )
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("Playlist ${playlists.size + 1}") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Save queue as playlist") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    app.queue.savePlaylist(name.trim(), state.items)
                    saveDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Cancel") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the queue?") },
            text = { Text("Saved playlists are not affected.") },
            confirmButton = { TextButton(onClick = { app.queue.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    if (loopsForAll) {
        var loops by remember { mutableIntStateOf(settings.defaultLoops) }
        AlertDialog(
            onDismissRequest = { loopsForAll = false },
            title = { Text("Loops for every video") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Each video plays this many times before the queue moves on.")
                    Spacer(Modifier.height(12.dp))
                    LoopStepper(loops, { loops = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.queue.update { s -> s.copy(items = s.items.map { it.copy(loops = loops) }) }
                    loopsForAll = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { loopsForAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QueueHeader(
    state: QueueState,
    autoAdvanceSec: Int,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onShuffle: (Boolean) -> Unit,
    onRepeat: (Boolean) -> Unit,
    onSleep: (Int) -> Unit,
    onDelay: (Int) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            val total = state.items.totalPlayTimeMs()
            Text(
                "${state.items.size} videos · " + (if (total == null) "loops forever" else formatDuration(total) + " total"),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Plays hands-free in the headset: each video repeats its loop count, then the next one starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, enabled = state.items.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play queue")
                }
                val ri = state.resumeIndex
                if (ri >= 0) {
                    OutlinedButton(onClick = onResume) {
                        Icon(Icons.Default.History, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Resume #${ri + 1} at ${formatDuration(state.resumePositionMs)}", maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.shuffle, onClick = { onShuffle(!state.shuffle) },
                    label = { Text("Shuffle") }, leadingIcon = { Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = state.repeatQueue, onClick = { onRepeat(!state.repeatQueue) },
                    label = { Text("Repeat queue") }, leadingIcon = { Icon(Icons.Default.Repeat, null, Modifier.size(18.dp)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var sleepMenu by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = state.sleepTimerMin != 0, onClick = { sleepMenu = true },
                        label = { Text("Sleep: ${sleepLabel(state.sleepTimerMin)}") },
                        leadingIcon = { Icon(Icons.Default.Bedtime, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        SLEEP_OPTIONS.forEach { v ->
                            DropdownMenuItem(text = { Text(sleepLabel(v)) }, onClick = { onSleep(v); sleepMenu = false })
                        }
                    }
                }
                var delayMenu by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = autoAdvanceSec > 0, onClick = { delayMenu = true },
                        label = { Text("Gap: ${autoAdvanceSec}s") },
                        leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = delayMenu, onDismissRequest = { delayMenu = false }) {
                        listOf(0, 1, 2, 3, 5, 10).forEach { v ->
                            DropdownMenuItem(
                                text = { Text(if (v == 0) "No pause between videos" else "$v s pause") },
                                onClick = { onDelay(v); delayMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    item: QueueItem,
    isLast: Boolean,
    isResumePoint: Boolean,
    onLoops: (Int) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onPlayFrom: () -> Unit,
    onFormat: () -> Unit,
    onSubtitle: () -> Unit,
    onClearSubtitle: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onPlayFrom).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isResumePoint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = if (isResumePoint) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(
                    if (item.durationMs > 0) formatDuration(item.durationMs) else null,
                    item.format?.shortLabel ?: "Auto format",
                    if (item.subtitleUri != null) "Subtitles" else null,
                ).joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Item options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Play from here") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = { menu = false; onPlayFrom() })
                    DropdownMenuItem(text = { Text("Set VR format…") }, onClick = { menu = false; onFormat() })
                    DropdownMenuItem(text = { Text("Attach subtitles (.srt/.vtt)…") },
                        leadingIcon = { Icon(Icons.Default.Subtitles, null) }, onClick = { menu = false; onSubtitle() })
                    if (item.subtitleUri != null) {
                        DropdownMenuItem(text = { Text("Remove subtitles") }, onClick = { menu = false; onClearSubtitle() })
                    }
                    DropdownMenuItem(text = { Text("Remove from queue") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Loops", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            LoopStepper(item.loops, onLoops)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onUp, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
            IconButton(onClick = onDown, enabled = !isLast) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
        }
    }
}
