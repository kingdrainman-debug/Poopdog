package com.poopdog.vr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.poopdog.vr.model.QueueItem
import com.poopdog.vr.model.VideoFormat

val Accent = Color(0xFFFFB347)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1B1530),
    secondary = Color(0xFF9F8CFF),
    onSecondary = Color(0xFF14102A),
    tertiary = Color(0xFF6FD3C1),
    background = Color(0xFF0E0F14),
    surface = Color(0xFF0E0F14),
    surfaceVariant = Color(0xFF1E2030),
    surfaceContainer = Color(0xFF181A26),
    surfaceContainerHigh = Color(0xFF20222F),
    onSurface = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFFB4B6C8),
    primaryContainer = Color(0xFF4A3410),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondaryContainer = Color(0xFF2C2650),
    onSecondaryContainer = Color(0xFFE2DCFF),
)

@Composable
fun PoopdogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

fun formatDuration(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    else -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    subtitle: String? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(label(selected), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 220.dp))
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(label(o)) }, onClick = { onSelect(o); open = false })
                }
            }
        }
    }
}

/** "−  ×3  +" control; steps 1..99 then ∞. */
@Composable
fun LoopStepper(loops: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange(QueueItem.stepLoops(loops, -1)) }) {
            Icon(Icons.Default.Remove, contentDescription = "Fewer loops")
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(min = 52.dp),
        ) {
            Text(
                QueueItem.loopsLabel(loops),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        FilledTonalIconButton(onClick = { onChange(QueueItem.stepLoops(loops, +1)) }) {
            Icon(Icons.Default.Add, contentDescription = "More loops")
        }
    }
}

/**
 * Choose how a video is converted for the headset. [current] null means auto-detect;
 * [detected] is what auto-detect picks.
 */
@Composable
fun FormatPickerDialog(
    current: VideoFormat?,
    detected: VideoFormat,
    onDismiss: () -> Unit,
    onPick: (VideoFormat?) -> Unit,
) {
    var choice by remember { mutableStateOf(current?.copy(swapEyes = false)) }
    var swap by remember { mutableStateOf(current?.swapEyes ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video format") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    FormatOption("Auto-detect (${detected.shortLabel})", choice == null) { choice = null }
                }
                items(VideoFormat.CYCLE) { f ->
                    FormatOption(f.shortLabel, choice == f) { choice = f }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { swap = !swap }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = swap, onCheckedChange = { swap = it })
                        Text("Swap left/right eyes (RL / BT files)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(choice?.copy(swapEyes = swap && choice!!.isStereo)) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FormatOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
