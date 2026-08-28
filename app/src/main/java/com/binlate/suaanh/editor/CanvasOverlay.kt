package com.binlate.suaanh.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.binlate.suaanh.editor.model.ArrowEnd
import com.binlate.suaanh.editor.model.CoverMode
import com.binlate.suaanh.editor.model.EditorTool
import com.binlate.suaanh.editor.model.Handle
import com.binlate.suaanh.editor.model.Layer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val SelectionColor = Color(0xFF2196F3)
private const val HandleDrawRadiusPx = 7f
private const val HandleSlopPx = 30f
private const val OutlineSlopPx = 16f

/**
 * The editable canvas: draws the fitted image, applies the committed + draft
 * layers and handles gestures for every tool (draw/highlight, cover, text,
 * shapes with move/resize handles, and brush-based blur).
 */
@Composable
fun CanvasOverlay(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val preview = vm.preview ?: return
    val imageBitmap = remember(preview) { preview.asImageBitmap() }
    val pixelImage = remember(vm.pixelBitmap) { vm.pixelBitmap?.asImageBitmap() }
    val textMeasurer = rememberTextMeasurer()
    // Subscribe so newly computed blur bitmaps trigger a redraw.
    val previewBlurVersion = vm.previewBlurVersion

    var fitRect by remember { mutableStateOf<Rect?>(null) }

    val toNorm: (Offset) -> Offset = { pos ->
        val r = fitRect
        if (r == null) {
            Offset.Zero
        } else {
            Offset((pos.x - r.left) / r.width, (pos.y - r.top) / r.height)
        }
    }

    // Hit-test the topmost text layer that contains a screen point.
    val hitText: (Offset) -> Layer.Text? = { pos ->
        val r = fitRect
        if (r == null) {
            null
        } else {
            vm.layers.asReversed().firstNotNullOfOrNull { layer ->
                if (layer is Layer.Text && textContains(textMeasurer, layer, r, pos)) {
                    layer
                } else {
                    null
                }
            }
        }
    }

    // Hit-test the topmost outline shape under a screen point.
    val hitShape: (Offset) -> Layer.Shape? = { pos ->
        val r = fitRect
        if (r == null) {
            null
        } else {
            vm.layers.asReversed().firstNotNullOfOrNull { layer ->
                if (layer is Layer.Shape && shapeOutlineContains(layer, r, pos, OutlineSlopPx)) {
                    layer
                } else {
                    null
                }
            }
        }
    }

    // Resize handle under a screen point (of the currently selected shape).
    val hitHandle: (Offset) -> Handle? = { pos ->
        val sel = vm.selectedShape
        val r = fitRect
        if (sel == null || r == null) {
            null
        } else {
            handlePositions(shapeRect(sel, r)).firstOrNull { (_, p) ->
                (pos - p).getDistance() <= HandleSlopPx
            }?.first
        }
    }

    // Hit-test the topmost arrow whose shaft or arrowhead is near a screen point.
    val hitArrow: (Offset) -> Layer.Arrow? = { pos ->
        val r = fitRect
        if (r == null) {
            null
        } else {
            vm.layers.asReversed().firstNotNullOfOrNull { layer ->
                if (layer is Layer.Arrow && arrowContains(layer, r, pos, OutlineSlopPx)) {
                    layer
                } else {
                    null
                }
            }
        }
    }

    // Which endpoint of the currently selected arrow is under a screen point.
    val hitArrowEnd: (Offset) -> ArrowEnd? = { pos ->
        val sel = vm.selectedArrow
        val r = fitRect
        if (sel == null || r == null) {
            null
        } else {
            val start = arrowPoint(sel.start, r)
            val end = arrowPoint(sel.end, r)
            when {
                (pos - start).getDistance() <= HandleSlopPx -> ArrowEnd.START
                (pos - end).getDistance() <= HandleSlopPx -> ArrowEnd.END
                else -> null
            }
        }
    }

    Canvas(
        modifier = modifier
            .background(Color(0xFF1B1B1B))
            .pointerInput(preview) {
                detectTapGestures(
                    onTap = { pos ->
                        when (vm.tool) {
                            EditorTool.TEXT -> vm.handleTextTap(hitText(pos))
                            EditorTool.SHAPE -> vm.handleShapeTap(hitShape(pos))
                            EditorTool.ARROW -> vm.handleArrowTap(hitArrow(pos))
                            else -> Unit
                        }
                    },
                )
            }
            .pointerInput(preview) {
                detectDragGestures(
                    onDragStart = { position ->
                        val norm = toNorm(position)
                        when (vm.tool) {
                            EditorTool.PEN, EditorTool.HIGHLIGHT -> vm.beginStroke(norm)
                            EditorTool.COVER -> vm.beginCover(norm)
                            EditorTool.TEXT -> vm.beginTextDrag(hitText(position), norm)
                            EditorTool.SHAPE -> {
                                val handle = hitHandle(position)
                                val shape = if (handle != null) vm.selectedShape else hitShape(position)
                                val started = vm.beginShapeInteraction(shape, handle, norm)
                                if (!started) vm.beginShapeCreation(vm.shapeKind, norm)
                            }
                            EditorTool.BLUR -> vm.beginBlurStroke(norm)
                            EditorTool.ARROW -> {
                                val end = hitArrowEnd(position)
                                val arrow = if (end != null) vm.selectedArrow else hitArrow(position)
                                val started = vm.beginArrowInteraction(arrow, end, norm)
                                if (!started) vm.beginArrowCreation(norm)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val norm = toNorm(change.position)
                        when (vm.tool) {
                            EditorTool.PEN, EditorTool.HIGHLIGHT -> vm.continueStroke(norm)
                            EditorTool.COVER -> vm.continueCover(norm)
                            EditorTool.TEXT -> vm.continueTextDrag(norm)
                            EditorTool.SHAPE -> if (vm.draftShape != null) {
                                vm.continueShapeCreation(norm)
                            } else {
                                vm.continueShapeDrag(norm)
                            }
                            EditorTool.BLUR -> vm.continueBlurStroke(norm)
                            EditorTool.ARROW -> if (vm.draftArrowEnd != null) {
                                vm.continueArrowCreation(norm)
                            } else {
                                vm.continueArrowDrag(norm)
                            }
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
            val selectedText = layer is Layer.Text && layer.id == vm.selectedTextId
            val selectedShape = layer is Layer.Shape && layer.id == vm.selectedShapeId
            val selectedArrow = layer is Layer.Arrow && layer.id == vm.selectedArrowId
            drawLayer(
                layer = layer,
                rect = rect,
                sourceW = sourceW,
                sourceH = sourceH,
                pixelImage = pixelImage,
                textMeasurer = textMeasurer,
                isTextSelected = selectedText,
                isShapeSelected = selectedShape,
                isArrowSelected = selectedArrow,
                blurredFor = { strength -> vm.previewBlurredBitmap(strength)?.asImageBitmap() },
            )
        }
    }
}

private fun endAction(vm: EditorViewModel) {
    when (vm.tool) {
        EditorTool.PEN, EditorTool.HIGHLIGHT -> vm.endStroke()
        EditorTool.COVER -> vm.endCover()
        EditorTool.TEXT -> vm.endTextDrag()
        EditorTool.SHAPE -> {
            vm.endShapeDrag()
            vm.endShapeCreation()
        }
        EditorTool.BLUR -> vm.endBlurStroke()
        EditorTool.ARROW -> {
            vm.endArrowDrag()
            vm.endArrowCreation()
        }
    }
}

/** Returns the on-screen bounds of a text layer (used for hit-test and selection box). */
private fun textBounds(textMeasurer: TextMeasurer, layer: Layer.Text, rect: Rect): Rect {
    val layout = measureTextLayout(textMeasurer, layer, rect)
    val cx = rect.left + layer.position.x * rect.width
    val cy = rect.top + layer.position.y * rect.height
    val pad = 4f
    return Rect(
        cx - layout.size.width / 2f - pad,
        cy - layout.size.height / 2f - pad,
        cx + layout.size.width / 2f + pad,
        cy + layout.size.height / 2f + pad,
    )
}

/** Whether a screen point falls inside a (possibly rotated) text layer. */
private fun textContains(
    textMeasurer: TextMeasurer,
    layer: Layer.Text,
    rect: Rect,
    pos: Offset,
): Boolean {
    if (layer.rotation == 0f) return textBounds(textMeasurer, layer, rect).contains(pos)
    val b = textBounds(textMeasurer, layer, rect)
    val cx = (b.left + b.right) / 2f
    val cy = (b.top + b.bottom) / 2f
    val rad = (-layer.rotation) * (PI.toFloat() / 180f)
    val dx = pos.x - cx
    val dy = pos.y - cy
    val rx = dx * cos(rad) - dy * sin(rad)
    val ry = dx * sin(rad) + dy * cos(rad)
    val local = Offset(cx + rx, cy + ry)
    return b.contains(local)
}

private fun measureTextLayout(
    textMeasurer: TextMeasurer,
    layer: Layer.Text,
    rect: Rect,
) = textMeasurer.measure(
    AnnotatedString(layer.content),
    style = TextStyle(
        color = Color(layer.color),
        fontSize = TextUnit(layer.sizeFraction * layer.scale * rect.width, TextUnitType.Sp),
        fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Normal,
    ),
)

/** Screen-space bounding rect of a shape layer. */
private fun shapeRect(shape: Layer.Shape, rect: Rect): Rect = Rect(
    rect.left + shape.left * rect.width,
    rect.top + shape.top * rect.height,
    rect.left + shape.right * rect.width,
    rect.top + shape.bottom * rect.height,
)

private fun handlePositions(b: Rect): List<Pair<Handle, Offset>> = listOf(
    Handle.TL to Offset(b.left, b.top),
    Handle.TR to Offset(b.right, b.top),
    Handle.BL to Offset(b.left, b.bottom),
    Handle.BR to Offset(b.right, b.bottom),
    Handle.TC to Offset((b.left + b.right) / 2f, b.top),
    Handle.BC to Offset((b.left + b.right) / 2f, b.bottom),
    Handle.LC to Offset(b.left, (b.top + b.bottom) / 2f),
    Handle.RC to Offset(b.right, (b.top + b.bottom) / 2f),
)

/** True when [pos] lies close to the outline of the shape (within [tol] px). */
private fun shapeOutlineContains(
    shape: Layer.Shape,
    rect: Rect,
    pos: Offset,
    tol: Float,
): Boolean {
    val b = shapeRect(shape, rect)
    if (b.width < 1f || b.height < 1f) return false
    return when (shape.kind) {
        com.binlate.suaanh.editor.model.ShapeKind.RECT -> {
            val d = minOf(
                segDistance(pos, Offset(b.left, b.top), Offset(b.right, b.top)),
                segDistance(pos, Offset(b.right, b.top), Offset(b.right, b.bottom)),
                segDistance(pos, Offset(b.right, b.bottom), Offset(b.left, b.bottom)),
                segDistance(pos, Offset(b.left, b.bottom), Offset(b.left, b.top)),
            )
            d <= tol
        }
        com.binlate.suaanh.editor.model.ShapeKind.ELLIPSE -> {
            val a = b.width / 2f
            val bb = b.height / 2f
            if (a <= 0f || bb <= 0f) return false
            val cx = (b.left + b.right) / 2f
            val cy = (b.top + b.bottom) / 2f
            val dx = pos.x - cx
            val dy = pos.y - cy
            val fx = dx / a
            val fy = dy / bb
            val f = fx * fx + fy * fy
            val gx = 2f * dx / (a * a)
            val gy = 2f * dy / (bb * bb)
            val g = sqrt(gx * gx + gy * gy)
            if (g == 0f) false else abs(f - 1f) / g <= tol
        }
    }
}

private fun segDistance(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lenSq = abx * abx + aby * aby
    if (lenSq == 0f) return (p - a).getDistance()
    var t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq
    t = t.coerceIn(0f, 1f)
    val cx = a.x + abx * t
    val cy = a.y + aby * t
    return (p - Offset(cx, cy)).getDistance()
}

private fun DrawScope.drawLayer(
    layer: Layer,
    rect: Rect,
    sourceW: Float,
    sourceH: Float,
    pixelImage: ImageBitmap?,
    textMeasurer: TextMeasurer,
    isTextSelected: Boolean,
    isShapeSelected: Boolean,
    isArrowSelected: Boolean,
    blurredFor: (Int) -> ImageBitmap?,
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
                style = Stroke(
                    width = layer.widthFraction * min(rect.width, rect.height),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        is Layer.Text -> {
            val layout = measureTextLayout(textMeasurer, layer, rect)
            val cx = rect.left + layer.position.x * rect.width
            val cy = rect.top + layer.position.y * rect.height
            rotate(degrees = layer.rotation, pivot = Offset(cx, cy)) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f),
                )
                if (isTextSelected) {
                    drawSelectionBox(textBounds(textMeasurer, layer, rect))
                }
            }
        }

        is Layer.Shape -> {
            val b = shapeRect(layer, rect)
            val stroke = Stroke(
                width = layer.strokeFraction * min(rect.width, rect.height),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            when (layer.kind) {
                com.binlate.suaanh.editor.model.ShapeKind.ELLIPSE -> drawOval(
                    color = Color(layer.color),
                    topLeft = Offset(b.left, b.top),
                    size = Size(b.width, b.height),
                    style = stroke,
                )
                com.binlate.suaanh.editor.model.ShapeKind.RECT -> drawRect(
                    color = Color(layer.color),
                    topLeft = Offset(b.left, b.top),
                    size = Size(b.width, b.height),
                    style = stroke,
                )
            }
            if (isShapeSelected) {
                drawSelectionBox(b)
                handlePositions(b).forEach { (_, p) ->
                    drawCircle(SelectionColor, HandleDrawRadiusPx, p)
                    drawCircle(Color.White, HandleDrawRadiusPx - 2.5f, p)
                }
            }
        }

        is Layer.BlurStroke -> {
            val blurred = blurredFor(layer.strength) ?: return
            if (layer.points.isEmpty()) return
            val stroke = android.graphics.Path()
            layer.points.forEachIndexed { i, p ->
                val x = rect.left + p.x * rect.width
                val y = rect.top + p.y * rect.height
                if (i == 0) stroke.moveTo(x, y) else stroke.lineTo(x, y)
            }
            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = layer.brushFraction * 2f * min(rect.width, rect.height)
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            val fill = android.graphics.Path()
            strokePaint.getFillPath(stroke, fill)
            clipPath(fill.asComposePath()) {
                drawImage(
                    image = blurred,
                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                    dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                )
            }
        }

        is Layer.Arrow -> {
            val a = arrowPoint(layer.start, rect)
            val b = arrowPoint(layer.end, rect)
            val shaftPx = layer.strokeFraction * min(rect.width, rect.height)
            val headLen = shaftPx * 4f * layer.headScale
            val head = arrowHeadGeometry(a, b, headLen)
            if (head != null) {
                drawLine(
                    color = Color(layer.color),
                    start = a,
                    end = head.shaftEnd,
                    strokeWidth = shaftPx,
                    cap = StrokeCap.Round,
                )
                val headPath = androidx.compose.ui.graphics.Path()
                headPath.moveTo(head.tip.x, head.tip.y)
                headPath.lineTo(head.left.x, head.left.y)
                headPath.lineTo(head.right.x, head.right.y)
                headPath.close()
                drawPath(headPath, Color(layer.color))
            }
            if (isArrowSelected) {
                drawSelectionBox(arrowBounds(a, b, headLen))
                listOf(a, b).forEach { p ->
                    drawCircle(SelectionColor, HandleDrawRadiusPx, p)
                    drawCircle(Color.White, HandleDrawRadiusPx - 2.5f, p)
                }
            }
        }

        is Layer.Cover -> {
            val x = rect.left + layer.left * rect.width
            val y = rect.top + layer.top * rect.height
            val w = layer.width * rect.width
            val h = layer.height * rect.height
            when (layer.mode) {
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
                    size = Size(w, h),
                )
            }
        }
    }
}

private fun DrawScope.drawSelectionBox(b: Rect) {
    drawRoundRect(
        color = SelectionColor,
        topLeft = Offset(b.left, b.top),
        size = Size(b.width, b.height),
        cornerRadius = CornerRadius(4f),
        style = Stroke(width = 2f),
    )
}

/** Converts a normalized arrow point into a screen point inside [rect]. */
private fun arrowPoint(norm: Offset, rect: Rect) = Offset(
    rect.left + norm.x * rect.width,
    rect.top + norm.y * rect.height,
)

/** Arrowhead geometry: shaft ends at the head base, triangle points at the tip. */
private class ArrowHead(
    val tip: Offset,
    val left: Offset,
    val right: Offset,
    val shaftEnd: Offset,
)

/**
 * Computes arrowhead points for a shaft [start]->[end]. Returns null for
 * near-zero-length arrows so degenerate geometry is never drawn. The head is
 * clamped to 80% of the shaft length so very short arrows still render cleanly.
 */
private fun arrowHeadGeometry(start: Offset, end: Offset, headLenRaw: Float): ArrowHead? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 0.01f) return null
    val ux = dx / len
    val uy = dy / len
    val headLen = min(headLenRaw, len * 0.8f)
    val shaftEnd = Offset(end.x - ux * headLen, end.y - uy * headLen)
    val halfW = headLen * 0.5f
    val left = Offset(shaftEnd.x - uy * halfW, shaftEnd.y + ux * halfW)
    val right = Offset(shaftEnd.x + uy * halfW, shaftEnd.y - ux * halfW)
    return ArrowHead(tip = end, left = left, right = right, shaftEnd = shaftEnd)
}

/** Screen bounds used for the selection box of a selected arrow. */
private fun arrowBounds(a: Offset, b: Offset, headLen: Float): Rect {
    val pad = max(headLen, 12f)
    return Rect(
        min(a.x, b.x) - pad,
        min(a.y, b.y) - pad,
        max(a.x, b.x) + pad,
        max(a.y, b.y) + pad,
    )
}

/** Whether a screen point is on the arrow shaft or inside the arrowhead. */
private fun arrowContains(layer: Layer.Arrow, rect: Rect, pos: Offset, tol: Float): Boolean {
    val a = arrowPoint(layer.start, rect)
    val b = arrowPoint(layer.end, rect)
    val shaftPx = layer.strokeFraction * min(rect.width, rect.height)
    val head = arrowHeadGeometry(a, b, shaftPx * 4f * layer.headScale) ?: return false
    return segDistance(pos, a, head.shaftEnd) <= tol ||
        pointInTriangle(pos, head.tip, head.left, head.right)
}

private fun pointInTriangle(p: Offset, a: Offset, b: Offset, c: Offset): Boolean {
    val d1 = triangleSign(p, a, b)
    val d2 = triangleSign(p, b, c)
    val d3 = triangleSign(p, c, a)
    val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
    val hasPos = d1 > 0 || d2 > 0 || d3 > 0
    return !(hasNeg && hasPos)
}

private fun triangleSign(p: Offset, a: Offset, b: Offset) =
    (p.x - b.x) * (a.y - b.y) - (a.x - b.x) * (p.y - b.y)


