package com.binlate.suaanh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binlate.suaanh.editor.EditorViewModel
import com.binlate.suaanh.editor.model.CoverMode
import com.binlate.suaanh.editor.model.EditorTool
import kotlin.math.roundToInt

/**
 * Per-tool control row: colour palette + size slider (and cover mode selector).
 * When a text layer is selected, the controls edit that layer instead of just
 * configuring the "next add" defaults.
 */
@Composable
fun ToolControls(
    state: EditorViewModel,
    openColorPicker: (ColorPickerRequest) -> Unit,
    onAddText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.tool) {
        EditorTool.PEN -> {
            ColorControls(state.penColor, { state.penColor = it }, {
                openColorPicker(
                    ColorPickerRequest(state.penColor, onApply = { state.penColor = it })
                )
            }, modifier)
            SizeControl("Kích thước bút", state.penWidthFraction, 0.0001f..0.008f) {
                state.penWidthFraction = it
            }
        }

        EditorTool.HIGHLIGHT -> {
            ColorControls(state.highlightColor, { state.highlightColor = it }, {
                openColorPicker(
                    ColorPickerRequest(state.highlightColor, onApply = { state.highlightColor = it })
                )
            }, modifier)
            SizeControl("Ngòi highlight", state.highlightWidthFraction, 0.004f..0.05f) {
                state.highlightWidthFraction = it
            }
        }

        EditorTool.TEXT -> {
            val sel = state.selectedText
            val curColor = sel?.let { Color(it.color) } ?: state.textColor
            val curSize = sel?.sizeFraction ?: state.textSizeFraction

            ColorControls(curColor, { color ->
                state.previewTextColor(color)
                state.commitTextPropertySession()
            }, {
                openColorPicker(
                    ColorPickerRequest(
                        initial = curColor,
                        onLive = { state.previewTextColor(it) },
                        onApply = {
                            state.previewTextColor(it)
                            state.commitTextPropertySession()
                        },
                        onCancel = { state.cancelTextPropertySession() },
                    )
                )
            }, modifier)

            SizeControl(
                "Kích thước chữ",
                curSize,
                0.02f..0.3f,
                onChange = { state.previewTextSize(it) },
                onValueChangeFinished = { state.commitTextPropertySession() },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onAddText) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Thêm chữ")
                }
                if (sel != null) {
                    TextButton(onClick = { state.requestEditSelected() }) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sửa chữ")
                    }
                }
            }
        }

        EditorTool.COVER -> {
            CoverModeSelector(state, modifier)
            if (state.coverMode == CoverMode.SOLID) {
                ColorControls(
                    state.coverColor,
                    { state.coverColor = it },
                    { openColorPicker(ColorPickerRequest(state.coverColor, onApply = { state.coverColor = it })) },
                    modifier,
                )
            }
        }
    }
}

@Composable
private fun CoverModeSelector(state: EditorViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.coverMode == CoverMode.BLUR,
            onClick = { state.coverMode = CoverMode.BLUR },
            label = { Text("Làm mờ") },
        )
        FilterChip(
            selected = state.coverMode == CoverMode.PIXELATE,
            onClick = { state.coverMode = CoverMode.PIXELATE },
            label = { Text("Mosaic") },
        )
        FilterChip(
            selected = state.coverMode == CoverMode.SOLID,
            onClick = { state.coverMode = CoverMode.SOLID },
            label = { Text("Khối màu") },
        )
    }
}

@Composable
private fun ColorControls(
    current: Color,
    onPick: (Color) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(current)
                .clickable { onCustom() }
        )
        PALETTE.forEach { color ->
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onPick(color) }
            )
        }
    }
}

@Composable
private fun SizeControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    val percent = ((value - range.start) / (range.endInclusive - range.start) * 100)
        .roundToInt().coerceIn(0, 100)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "$label: $percent",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Start,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
        )
    }
}
