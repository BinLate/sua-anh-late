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
import androidx.compose.material.icons.filled.Delete
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
import com.binlate.suaanh.editor.model.ShapeKind
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
            val curRotation = sel?.rotation ?: 0f
            val curScale = sel?.scale ?: 1f

            ColorControls(curColor, { color ->
                state.previewTextColor(color)
                state.commitLayerPropertySession()
            }, {
                openColorPicker(
                    ColorPickerRequest(
                        initial = curColor,
                        onLive = { state.previewTextColor(it) },
                        onApply = {
                            state.previewTextColor(it)
                            state.commitLayerPropertySession()
                        },
                        onCancel = { state.cancelLayerPropertySession() },
                    )
                )
            }, modifier)

            SizeControl(
                "Kích thước chữ",
                curSize,
                0.02f..0.3f,
                onChange = { state.previewTextSize(it) },
                onValueChangeFinished = { state.commitLayerPropertySession() },
            )

            NumberSlider(
                "Xoay (°)",
                curRotation,
                -180f..180f,
                onChange = { state.previewTextRotation(it) },
                onValueChangeFinished = { state.commitLayerPropertySession() },
            )

            NumberSlider(
                "Tỷ lệ",
                curScale,
                0.5f..3f,
                onChange = { state.previewTextScale(it) },
                onValueChangeFinished = { state.commitLayerPropertySession() },
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
                    TextButton(onClick = { state.deleteSelectedLayer() }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xóa")
                    }
                }
            }
        }

        EditorTool.SHAPE -> {
            val sel = state.selectedShape
            val curColor = sel?.let { Color(it.color) } ?: state.shapeColor
            val curStroke = sel?.strokeFraction ?: state.shapeStrokeFraction

            // Shape kind chips
            Row(
                modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.shapeKind == ShapeKind.ELLIPSE,
                    onClick = { state.shapeKind = ShapeKind.ELLIPSE },
                    label = { Text("Ellipse") },
                )
                FilterChip(
                    selected = state.shapeKind == ShapeKind.RECT,
                    onClick = { state.shapeKind = ShapeKind.RECT },
                    label = { Text("Chữ nhật") },
                )
            }

            // Stroke color edits the selected shape live
            ColorControls(curColor, { color ->
                state.previewShapeColor(color)
                state.commitLayerPropertySession()
            }, {
                openColorPicker(
                    ColorPickerRequest(
                        initial = curColor,
                        onLive = { state.previewShapeColor(it) },
                        onApply = {
                            state.previewShapeColor(it)
                            state.commitLayerPropertySession()
                        },
                        onCancel = { state.cancelLayerPropertySession() },
                    )
                )
            }, modifier)

            // Stroke width edits the selected shape live (one undo step per drag)
            SizeControl(
                "Độ dày viền",
                curStroke,
                0.0005f..0.02f,
                onChange = { state.previewShapeStroke(it) },
                onValueChangeFinished = { state.commitLayerPropertySession() },
            )

            if (sel != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { state.deleteSelectedLayer() }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xóa")
                    }
                }
            }
        }

        EditorTool.BLUR -> {
            // Brush size: where/how large the blur stroke is.
            SizeControl("Kích thước cọ", state.blurBrushFraction, 0.005f..0.08f) {
                state.blurBrushFraction = it
            }
            // Blur strength: how strong the blur is (real blur radius).
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(
                    "Độ mạnh làm mờ: ${state.blurStrength}/10",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Start,
                )
                Slider(
                    value = state.blurStrength.toFloat(),
                    onValueChange = { state.updateBlurStrength(it.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }
        }

        EditorTool.ARROW -> {
            val sel = state.selectedArrow
            val curColor = sel?.let { Color(it.color) } ?: state.arrowColor
            val curStroke = sel?.strokeFraction ?: state.arrowStrokeFraction
            val curHead = sel?.headScale ?: state.arrowHeadScale

            // Arrow color edits the selected arrow live
            ColorControls(curColor, { color ->
                state.previewArrowColor(color)
                state.commitLayerPropertySession()
            }, {
                openColorPicker(
                    ColorPickerRequest(
                        initial = curColor,
                        onLive = { state.previewArrowColor(it) },
                        onApply = {
                            state.previewArrowColor(it)
                            state.commitLayerPropertySession()
                        },
                        onCancel = { state.cancelLayerPropertySession() },
                    )
                )
            }, modifier)

            // Shaft thickness edits the selected arrow live (one undo step per drag)
            SizeControl(
                "Độ dày thân mũi tên",
                curStroke,
                0.002f..0.03f,
                onChange = { state.previewArrowStroke(it) },
                onValueChangeFinished = { state.commitLayerPropertySession() },
            )

            // Arrowhead size (also scales proportionally with thickness)
            NumberSlider(
                "Kích thước đầu mũi tên",
                curHead,
                0.5f..3f,
                onChange = { state.previewArrowHead(it) },
                onValueChangeFinished = { state.commitLayerPropertySession() },
            )

            if (sel != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { state.deleteSelectedLayer() }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xóa")
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

/** Slider that displays the raw numeric value (used for rotation and scale). */
@Composable
private fun NumberSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    val display = ((value * 100).roundToInt()) / 100f
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "$label: $display",
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
