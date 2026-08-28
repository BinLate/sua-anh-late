package com.binlate.suaanh.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.binlate.suaanh.editor.model.CoverMode
import com.binlate.suaanh.editor.model.EditorTool
import com.binlate.suaanh.editor.model.Layer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The editable canvas: draws the fitted image, applies the committed + draft
 * layers and turns touch gestures into editor actions (strokes, covers, text drag).
 */
@Composable
fun CanvasOverlay(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val preview = vm.preview ?: return
    val imageBitmap = remember(preview) { preview.asImageBitmap() }
    val blurImage = remember(vm.blurBitmap) { vm.blurBitmap?.asImageBitmap() }
    val pixelImage = remember(vm.pixelBitmap) { vm.pixelBitmap?.asImageBitmap() }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var fitRect by remember { mutableStateOf<Rect?>(null) }

    val toNorm: (Offset) -> Offset = { pos ->
        val r = fitRect
        if (r == null) {
            Offset.Zero
        } else {
            Offset((pos.x - r.left) / r.width, (pos.y - r.top) / r.height)
        }
    }

    Canvas(
        modifier = modifier
            .background(Color(0xFF1B1B1B))
            .pointerInput(preview) {
                detectDragGestures(
                    onDragStart = { position ->
                        val norm = toNorm(position)
                        when (vm.tool) {
                            EditorTool.PEN, EditorTool.HIGHLIGHT -> vm.beginStroke(norm)
                            EditorTool.COVER -> vm.beginCover(norm)
                            EditorTool.TEXT -> vm.beginTextDrag(norm)
                        }
                    },
                    onDrag = { change, _ ->
                        val norm = toNorm(change.position)
                        when (vm.tool) {
                            EditorTool.PEN, EditorTool.HIGHLIGHT -> vm.continueStroke(norm)
                            EditorTool.COVER -> vm.continueCover(norm)
                            EditorTool.TEXT -> vm.continueTextDrag(norm)
                        }
                        change.consume()
                    },
                    onDragEnd = { endAction(vm) },
                    onDragCancel = { endAction(vm) },
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val iw = imageBitmap.width.toFloat()
        val ih = imageBitmap.height.toFloat()
        val scale = min(w / iw, h / ih)
        val dw = iw * scale
        val dh = ih * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        val rect = Rect(left, top, left + dw, top + dh)
        fitRect = rect

        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        )

        val sourceW = imageBitmap.width.toFloat()
        val sourceH = imageBitmap.height.toFloat()
        vm.layers.forEach { layer ->
            drawLayer(layer, rect, sourceW, sourceH, blurImage, pixelImage, textMeasurer)
        }
    }
}

private fun endAction(vm: EditorViewModel) {
    when (vm.tool) {
        EditorTool.PEN, EditorTool.HIGHLIGHT -> vm.endStroke()
        EditorTool.COVER -> vm.endCover()
        EditorTool.TEXT -> vm.endTextDrag()
    }
}

private fun DrawScope.drawLayer(
    layer: Layer,
    rect: Rect,
    sourceW: Float,
    sourceH: Float,
    blurImage: ImageBitmap?,
    pixelImage: ImageBitmap?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    when (layer) {
        is Layer.Stroke -> {
            if (layer.points.size < 2) return
            val path = androidx.compose.ui.graphics.Path()
            layer.points.forEachIndexed { i, p ->
                val x = rect.left + p.x * rect.width
                val y = rect.top + p.y * rect.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(layer.color).copy(alpha = layer.alpha),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = layer.widthFraction * min(rect.width, rect.height),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }

        is Layer.Text -> {
            val fontSize = TextUnit(layer.sizeFraction * rect.width, TextUnitType.Sp)
            val layout = textMeasurer.measure(
                androidx.compose.ui.text.AnnotatedString(layer.content),
                style = androidx.compose.ui.text.TextStyle(
                    color = Color(layer.color),
                    fontSize = fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
            )
            val cx = rect.left + layer.position.x * rect.width
            val cy = rect.top + layer.position.y * rect.height
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f),
            )
        }

        is Layer.Cover -> {
            val x = rect.left + layer.left * rect.width
            val y = rect.top + layer.top * rect.height
            val w = layer.width * rect.width
            val h = layer.height * rect.height
            when (layer.mode) {
                CoverMode.BLUR -> blurImage?.let {
                    drawImage(
                        image = it,
                        srcOffset = IntOffset((layer.left * sourceW).roundToInt(), (layer.top * sourceH).roundToInt()),
                        srcSize = IntSize((layer.width * sourceW).roundToInt(), (layer.height * sourceH).roundToInt()),
                        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    )
                }
                CoverMode.PIXELATE -> pixelImage?.let {
                    drawImage(
                        image = it,
                        srcOffset = IntOffset((layer.left * sourceW).roundToInt(), (layer.top * sourceH).roundToInt()),
                        srcSize = IntSize((layer.width * sourceW).roundToInt(), (layer.height * sourceH).roundToInt()),
                        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    )
                }
                CoverMode.SOLID -> drawRect(
                    color = Color(layer.color),
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(w, h),
                )
            }
        }
    }
}