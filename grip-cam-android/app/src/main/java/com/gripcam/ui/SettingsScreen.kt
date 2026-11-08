package com.gripcam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gripcam.MainViewModel
import com.gripcam.camera.CameraCapabilities
import com.gripcam.settings.AppSettings
import com.gripcam.settings.ChoiceSetting
import com.gripcam.settings.DriveMode
import com.gripcam.settings.GripButtonAction
import com.gripcam.settings.Labeled
import com.gripcam.settings.Prefs
import com.gripcam.settings.SectionId
import com.gripcam.settings.Setting
import com.gripcam.settings.SettingsCatalog
import com.gripcam.settings.SliderSetting
import com.gripcam.settings.ToggleSetting

/**
 * Full-screen settings, generated from [SettingsCatalog]: a new setting shows up here as soon
 * as it's added to a section. Options the current camera can't do are hidden.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, capabilities: CameraCapabilities, onClose: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var openChoice by remember { mutableStateOf<ChoiceSetting<*>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    DisposableEffect(Unit) {
        viewModel.settingsOpen = true
        onDispose { viewModel.settingsOpen = false }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmReset = true }) { Text("Reset") }
            }
            HorizontalDivider()
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                for (section in SettingsCatalog.sections) {
                    item(key = "header_${section.id}") { SectionHeader(sectionIcon(section.id), section.title) }
                    if (section.id == SectionId.GRIP) {
                        item(key = "grip_map") { ButtonMap(settings) }
                    }
                    items(section.settings.filter { it.visibleWhen(capabilities) }, key = { it.key }) { setting ->
                        SettingRow(
                            setting = setting,
                            settings = settings,
                            capabilities = capabilities,
                            onSet = viewModel::setAny,
                            onOpenChoice = { openChoice = it },
                        )
                    }
                    if (section.id == SectionId.CONNECTION) {
                        item(key = "restart_bt") {
                            OutlinedButton(onClick = viewModel::restartGrip, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Text("Restart Bluetooth service")
                            }
                        }
                    }
                }
                item(key = "about") { About() }
            }
        }
    }

    openChoice?.let { setting ->
        ChoiceDialog(
            setting = setting,
            current = settings.valueOf(setting),
            capabilities = capabilities,
            onSelect = {
                viewModel.setAny(setting, it)
                openChoice = null
            },
            onDismiss = { openChoice = null },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Every setting goes back to its default. Your grip stays paired.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSettings()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private fun sectionIcon(id: SectionId): ImageVector = when (id) {
    SectionId.VIDEO -> Icons.Filled.Videocam
    SectionId.PHOTO -> Icons.Filled.PhotoCamera
    SectionId.VIEWFINDER -> Icons.Filled.CropFree
    SectionId.GRIP -> Icons.Filled.TouchApp
    SectionId.CONNECTION -> Icons.Filled.Bluetooth
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingRow(
    setting: Setting<*>,
    settings: AppSettings,
    capabilities: CameraCapabilities,
    onSet: (Setting<*>, Any) -> Unit,
    onOpenChoice: (ChoiceSetting<*>) -> Unit,
) {
    when (setting) {
        is ToggleSetting -> {
            val checked = settings[setting]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onSet(setting, !checked) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowText(setting.title, setting.summary, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Switch(checked = checked, onCheckedChange = null)
            }
        }

        is SliderSetting -> {
            val value = settings[setting]
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RowText(setting.title, setting.summary, Modifier.weight(1f))
                    Text(setting.format(value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = value,
                    onValueChange = { onSet(setting, it) },
                    valueRange = setting.range,
                    steps = setting.steps,
                )
            }
        }

        is ChoiceSetting<*> -> {
            val current = settings.valueOf(setting)
            val supported = setting.available(capabilities).any { it == current }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpenChoice(setting) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val note = if (supported) setting.summary else "Not available on this camera, so a fallback is used."
                RowText(setting.title, note, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    (current as Labeled).label,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RowText(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!summary.isNullOrBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ChoiceDialog(
    setting: ChoiceSetting<*>,
    current: Any,
    capabilities: CameraCapabilities,
    onSelect: (Any) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = setting.available(capabilities)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(setting.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                setting.summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                for (option in setting.options) {
                    val isSupported = option in available
                    if (!isSupported && option != current) continue
                    val labeled = option as Labeled
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == current,
                                enabled = isSupported,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = null, enabled = isSupported)
                        Spacer(Modifier.width(10.dp))
                        RowText(
                            labeled.label,
                            if (isSupported) labeled.detail else "Not available on this camera",
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ButtonMap(settings: AppSettings) {
    val c1 = settings[Prefs.C1_ACTION]
    val c1Double = settings[Prefs.C1_DOUBLE]
    val rows = buildList {
        add("PHOTO half press" to "Autofocus and hold")
        add("PHOTO full press" to if (settings[Prefs.DRIVE] == DriveMode.BURST) "Burst while held" else "Take a photo")
        add("MOVIE" to "Start / stop video")
        add("Zoom T / W (hold)" to "Smooth zoom")
        if (settings[Prefs.ZOOM_SNAP]) add("Zoom T / W double press" to "Jump to next / previous zoom stop")
        add("C1" to c1.label)
        if (c1Double != GripButtonAction.NONE) add("C1 double press" to c1Double.label)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            rows.forEach { (button, action) ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(button, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        action,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun About() {
    Column(Modifier.padding(20.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("About GripCam", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "GripCam makes your phone look like a Sony camera to a Bluetooth shooting grip or remote, " +
                "using the community-documented remote protocol. It is not made by or affiliated with " +
                "Sony. To forget a grip, unpair it in Android's Bluetooth settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(24.dp))
    }
}
