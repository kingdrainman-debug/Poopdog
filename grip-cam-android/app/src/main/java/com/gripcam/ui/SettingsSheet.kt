@file:OptIn(ExperimentalMaterial3Api::class)

package com.gripcam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gripcam.grip.C1Action
import com.gripcam.settings.AppSettings
import java.util.Locale

@Composable
fun SettingsSheet(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onRestartBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            Section("Grip buttons")
            ButtonMapTable()
            Spacer(Modifier.height(12.dp))
            Text("C1 button", style = MaterialTheme.typography.titleSmall)
            C1Action.entries.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.c1Action == action,
                            role = Role.RadioButton,
                            onClick = { onChange { it.copy(c1Action = action) } },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.c1Action == action, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(action.label)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Zoom speed: ${String.format(Locale.US, "%.2g", settings.zoomSpeed)}×",
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = settings.zoomSpeed,
                onValueChange = { v -> onChange { it.copy(zoomSpeed = v) } },
                valueRange = 0.25f..2f,
            )
            ToggleRow(
                title = "Reverse zoom direction",
                subtitle = "Swap T and W if the lever zooms the wrong way.",
                checked = settings.invertZoom,
                onCheckedChange = { v -> onChange { it.copy(invertZoom = v) } },
            )
            ToggleRow(
                title = "Treat focus codes as zoom",
                subtitle = "Recommended for the GP-VPT2BT. Sources disagree on which code family the zoom " +
                    "lever sends, so this makes either one zoom. Turn off for a remote with Focus +/– " +
                    "buttons to get manual focus instead.",
                checked = settings.focusJogsAsZoom,
                onCheckedChange = { v -> onChange { it.copy(focusJogsAsZoom = v) } },
            )

            Section("Feedback")
            ToggleRow(
                title = "Shutter and record sounds",
                subtitle = "Handy when the phone is out of reach on the grip.",
                checked = settings.sounds,
                onCheckedChange = { v -> onChange { it.copy(sounds = v) } },
            )
            ToggleRow(
                title = "Show control log",
                subtitle = "Lists every command the grip sends, to check what each button does.",
                checked = settings.showLog,
                onCheckedChange = { v -> onChange { it.copy(showLog = v) } },
            )

            Section("Connection")
            ToggleRow(
                title = "Accept new grip pairings",
                subtitle = "Advertise as a camera in pairing mode. Turn off once paired if another remote " +
                    "nearby keeps grabbing the phone.",
                checked = settings.pairingMode,
                onCheckedChange = { v -> onChange { it.copy(pairingMode = v) } },
            )
            Spacer(Modifier.height(8.dp))
            Text("Advanced: camera identity", style = MaterialTheme.typography.titleSmall)
            Text(
                "What the phone claims to be. Only change these if the grip won't connect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Text("Model code", style = MaterialTheme.typography.labelLarge)
            Choice(
                options = AppSettings.MODEL_CODES,
                selected = settings.modelCode,
                label = { it },
                onSelect = { v -> onChange { it.copy(modelCode = v) } },
            )
            Spacer(Modifier.height(8.dp))
            Text("Protocol version", style = MaterialTheme.typography.labelLarge)
            Choice(
                options = AppSettings.PROTOCOL_VERSIONS,
                selected = settings.protocolVersion,
                label = { "0x%02X".format(it) },
                onSelect = { v -> onChange { it.copy(protocolVersion = v) } },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRestartBluetooth) { Text("Restart Bluetooth service") }

            Section("About")
            Text(
                "GripCam makes your phone look like a Sony camera to a Bluetooth shooting grip or " +
                    "remote, using the community-documented remote protocol. It is not made by or " +
                    "affiliated with Sony. To forget a grip, unpair it in Android's Bluetooth settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun ButtonMapTable() {
    val rows = listOf(
        "PHOTO, half press" to "Autofocus and hold",
        "PHOTO, full press" to "Take a photo",
        "MOVIE" to "Start / stop video",
        "Zoom lever T / W" to "Zoom in / out (speed follows the lever)",
        "C1" to "Your choice below",
    )
    Column {
        rows.forEach { (button, action) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(button, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1.3f),
                )
            }
        }
    }
}
