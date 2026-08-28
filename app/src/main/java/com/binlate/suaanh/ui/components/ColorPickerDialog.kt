package com.binlate.suaanh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Color request holder passed to [ColorPickerDialog]. [onLive] fires as the user
 * previews a color (real-time), [onApply] fires on OK, and [onCancel] restores.
 */
data class ColorPickerRequest(
    val initial: Color,
    val onLive: (Color) -> Unit = {},
    val onApply: (Color) -> Unit,
    val onCancel: () -> Unit = {},
)

/** Advanced, responsive HSV color picker (no hardcoded overflowing layout). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(request: ColorPickerRequest, onDismiss: () -> Unit) {
    val (initH, initS, initV) = remember(request.initial) { toHsv(request.initial) }
    var h by remember(request.initial) { mutableStateOf(initH) }
    var s by remember(request.initial) { mutableStateOf(initS) }
    var v by remember(request.initial) { mutableStateOf(initV) }
    var a by remember(request.initial) { mutableStateOf(request.initial.alpha) }

    var hexText by remember(request.initial) { mutableStateOf(toHexString(request.initial)) }
    var rText by remember(request.initial) {
        mutableStateOf(((request.initial.red * 255).toInt().coerceIn(0, 255)).toString())
    }
    var gText by remember(request.initial) {
        mutableStateOf(((request.initial.green * 255).toInt().coerceIn(0, 255)).toString())
    }
    var bText by remember(request.initial) {
        mutableStateOf(((request.initial.blue * 255).toInt().coerceIn(0, 255)).toString())
    }

    val preview = remember(h, s, v, a) { Color.hsv(h, s, v).copy(alpha = a) }

    // Keep the hex/RGB fields in sync whenever the colour is changed via sliders/area.
    LaunchedEffect(h, s, v) {
        val argb = Color.hsv(h, s, v).toArgb()
        hexText = toHexString(Color.hsv(h, s, v))
        rText = ((argb shr 16) and 0xFF).toString()
        gText = ((argb shr 8) and 0xFF).toString()
        bText = (argb and 0xFF).toString()
    }
    // Live preview on the currently edited object.
    LaunchedEffect(preview) { request.onLive(preview) }

    fun applyHex(value: String) {
        val hex = value.removePrefix("#")
        if (hex.length == 6) {
            val argb = hex.toLongOrNull(16) ?: return
            val c = Color(0xFF000000L or argb)
            val (hh, ss, vv) = toHsv(c)
            h = hh; s = ss; v = vv
        }
    }

    fun applyRgb() {
        val r = rText.toIntOrNull() ?: return
        val g = gText.toIntOrNull() ?: return
        val b = bText.toIntOrNull() ?: return
        if (r in 0..255 && g in 0..255 && b in 0..255) {
            val argb = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            val c = Color(argb)
            val (hh, ss, vv) = toHsv(c)
            h = hh; s = ss; v = vv
        }
    }

    AlertDialog(
        onDismissRequest = { request.onCancel(); onDismiss() },
        title = { Text("Chọn màu") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // New vs original preview
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatch(preview, 40.dp)
                    Text("  →  ", style = MaterialTheme.typography.labelLarge)
                    ColorSwatch(request.initial, 40.dp)
                }
                Spacer(Modifier.height(12.dp))

                // 2D Saturation / Value area
                SvArea(h = h, s = s, v = v, onSv = { ns, nv -> s = ns; v = nv })

                Spacer(Modifier.height(12.dp))

                // Hue slider
                HueSlider(h = h, onHue = { h = it })

                Spacer(Modifier.height(12.dp))

                Spacer(Modifier.height(8.dp))

                // Hex input
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { value ->
                        hexText = value
                        applyHex(value)
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // RGB inputs (each flexes; no fixed width so nothing overflows)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(value = rText, onValueChange = { rText = it.filter { c -> c.isDigit() }.take(3); applyRgb() }, label = { Text("R") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = gText, onValueChange = { gText = it.filter { c -> c.isDigit() }.take(3); applyRgb() }, label = { Text("G") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = bText, onValueChange = { bText = it.filter { c -> c.isDigit() }.take(3); applyRgb() }, label = { Text("B") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(8.dp))

                // Alpha / transparency control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Độ mờ", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = a,
                        onValueChange = { a = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Wrapping palette (no horizontal overflow on narrow screens)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PALETTE.forEach { color ->
                        Box(
                            Modifier
                                .size(if (color == preview) 32.dp else 28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    val (hh, ss, vv) = toHsv(color)
                                    h = hh; s = ss; v = vv
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { request.onApply(preview); onDismiss() }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = { request.onCancel(); onDismiss() }) { Text("Hủy") }
        },
    )
}

@Composable
private fun ColorSwatch(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** 2D Saturation / Value selection area with a hue base colour. */
@Composable
private fun SvArea(h: Float, s: Float, v: Float, onSv: (Float, Float) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(h) {
                fun update(pos: Offset) {
                    val d = size
                    val nx = (pos.x / d.width).coerceIn(0f, 1f)
                    val ny = (pos.y / d.height).coerceIn(0f, 1f)
                    onSv(nx, 1f - ny)
                }
                detectTapGestures(onTap = ::update)
            }
            .pointerInput(h) {
                fun update(pos: Offset) {
                    val d = size
                    val nx = (pos.x / d.width).coerceIn(0f, 1f)
                    val ny = (pos.y / d.height).coerceIn(0f, 1f)
                    onSv(nx, 1f - ny)
                }
                detectDragGestures { change, _ -> change.consume(); update(change.position) }
            }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val hueColor = Color.hsv(h, 1f, 1f)
            drawRect(Brush.linearGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val mx = s * size.width
            val my = (1f - v) * size.height
            drawCircle(Color.White, 10f, Offset(mx, my), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            drawCircle(Color(0xFFFF2222), 6f, Offset(mx, my))
        }
    }
}

/** Horizontal rainbow hue slider. */
@Composable
private fun HueSlider(h: Float, onHue: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                fun update(pos: Offset) {
                    onHue(((pos.x / size.width).coerceIn(0f, 1f) * 360f).coerceIn(0f, 360f))
                }
                detectTapGestures(onTap = ::update)
            }
            .pointerInput(Unit) {
                fun update(pos: Offset) {
                    onHue(((pos.x / size.width).coerceIn(0f, 1f) * 360f).coerceIn(0f, 360f))
                }
                detectDragGestures { change, _ -> change.consume(); update(change.position) }
            }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
                ),
                cornerRadius = CornerRadius(6f),
            )
            val tx = (h / 360f) * size.width
            drawLine(Color.White, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = 4f)
        }
    }
}

/** The default palette swatches (also reused by the tool control row). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteRow(selected: Color, onPick: (Color) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { color ->
            Box(
                Modifier
                    .size(if (color == selected) 30.dp else 26.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onPick(color) }
            )
        }
    }
}

/** A compact row of common colors; emits [onPick] when tapped. */
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

internal fun toHexString(color: Color): String {
    val argb = color.toArgb()
    val rgb = argb and 0xFFFFFF
    return String.format("#%06X", rgb)
}

