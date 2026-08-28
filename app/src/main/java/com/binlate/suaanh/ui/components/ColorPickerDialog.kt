package com.binlate.suaanh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Color request holder passed to [ColorPickerDialog]; when non-null the dialog is shown. */
data class ColorPickerRequest(val initial: Color, val onApply: (Color) -> Unit)

@Composable
fun ColorPickerDialog(request: ColorPickerRequest, onDismiss: () -> Unit) {
    val (initH, initS, initV) = remember(request) { toHsv(request.initial) }
    var h by remember(request.initial) { mutableStateOf(initH) }
    var s by remember(request.initial) { mutableStateOf(initS) }
    var v by remember(request.initial) { mutableStateOf(initV) }

    val preview = remember(h, s, v) { Color.hsv(h, s, v) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn màu") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(preview)
                    )
                    Spacer(Modifier.width(12.dp))
                    PaletteRow(selected = preview) { c ->
                        val (ph, ps, pv) = toHsv(c)
                        h = ph; s = ps; v = pv
                    }
                }
                Spacer(Modifier.height(16.dp))

                LabeledSlider("Độ màu (Hue)", h, 0f..360f) { h = it }
                LabeledSlider("Độ bão hòa (Saturation)", s, 0f..1f) { s = it }
                LabeledSlider("Độ sáng (Value)", v, 0f..1f) { v = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { request.onApply(preview); onDismiss() }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
        val display = if (range.endInclusive <= 1f) {
            "%.0f%%".format(value * 100f)
        } else {
            "%.0f°".format(value)
        }
        Text(
            display,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** The default palette swatches with the currently selected colour highlighted. */
@Composable
fun PaletteRow(selected: Color, onPick: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { color ->
            val boxSize = if (color == selected) 30.dp else 26.dp
            Box(
                Modifier
                    .size(boxSize)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onPick(color) }
            )
        }
    }
}

/** A horizontal row of common colors; emits [onPick] when tapped. */
@Composable
fun CompactPalette(current: Color, onPick: (Color) -> Unit) {
    PaletteRow(selected = current, onPick = onPick)
}

val PALETTE = listOf(
    Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFD32F2F), Color(0xFFF57C00),
    Color(0xFFFBC02D), Color(0xFF388E3C), Color(0xFF1976D2), Color(0xFF7B1FA2),
    Color(0xFFE91E63), Color(0xFF00BCD4), Color(0xFF8D6E63), Color(0xFF9E9E9E),
)

/** Convert a Compose [Color] to (hue, saturation, value) using android.graphics.Color. */
internal fun toHsv(color: Color): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    val argb = color.toArgb()
    android.graphics.Color.colorToHSV(argb, hsv)
    return Triple(hsv[0], hsv[1], hsv[2])
}