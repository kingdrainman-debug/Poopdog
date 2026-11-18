package com.poopdog.vr.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.poopdog.vr.VrApp
import com.poopdog.vr.convert.ConvertState
import com.poopdog.vr.vr.VrActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Tab requested from outside Compose (e.g. after a share intent). */
    private val requestedTab = MutableStateFlow(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoopdogTheme { AppRoot(requestedTab) }
        }
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Videos shared from a gallery or file manager are appended to the queue. */
    private fun handleShare(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableListExtra(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        val app = VrApp.from(this)
        // Keep access to the shared files if the sending app allows it, and list them
        // under "Picked files" so they can be re-queued later.
        app.media.rememberPicked(uris)
        lifecycleScope.launch {
            val entries = uris.map { app.media.describe(it) }
            val loops = app.settings.current.defaultLoops
            app.queue.add(entries.map { it.toQueueItem(loops, app.memory.format(it.uri.toString())) })
            requestedTab.value = 1
            Toast.makeText(this@MainActivity, "Added ${entries.size} to queue", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(key: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Uri::class.java) else getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private fun Intent.parcelableListExtra(key: String): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, Uri::class.java)
        else getParcelableArrayListExtra(key)) ?: emptyList()
}

@Composable
private fun AppRoot(requestedTab: MutableStateFlow<Int>) {
    val app = VrApp.from(LocalContext.current)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val requested by requestedTab.collectAsState()
    LaunchedEffect(requested) {
        if (requested >= 0) {
            tab = requested
            requestedTab.value = -1
        }
    }
    val queue by app.queue.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = {
                        BadgedBox(badge = { if (queue.items.isNotEmpty()) Badge { Text("${queue.items.size}") } }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
                        }
                    },
                    label = { Text("Queue") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(bottom = pad.calculateBottomPadding()).fillMaxSize()) {
            when (tab) {
                0 -> LibraryScreen(onQueueChanged = {})
                1 -> QueueScreen()
                else -> SettingsScreen()
            }
        }
    }
    ConvertProgress()
}

@Composable
private fun ConvertProgress() {
    val ctx = LocalContext.current
    val app = VrApp.from(ctx)
    val state by app.converter.state.collectAsState()
    when (val s = state) {
        is ConvertState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Converting to VR") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(s.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { s.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("${s.progress}% · keep the app open", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { app.converter.cancel() }) { Text("Cancel") } },
        )
        is ConvertState.Done -> AlertDialog(
            onDismissRequest = { app.converter.dismiss() },
            title = { Text("VR file ready") },
            text = { Text("Saved ${s.name} to Movies/PoopdogVR.") },
            confirmButton = {
                TextButton(onClick = {
                    app.converter.dismiss()
                    ctx.startActivity(VrActivity.singleIntent(ctx, s.uri, s.name.substringBeforeLast('.'), null,
                        app.settings.current.defaultLoops))
                }) { Text("Play now") }
            },
            dismissButton = { TextButton(onClick = { app.converter.dismiss() }) { Text("Close") } },
        )
        is ConvertState.Failed -> AlertDialog(
            onDismissRequest = { app.converter.dismiss() },
            title = { Text("Conversion failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { app.converter.dismiss() }) { Text("OK") } },
        )
        ConvertState.Idle -> Unit
    }
}
