package com.binlate.suaanh.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.binlate.suaanh.editor.model.CoverMode
import com.binlate.suaanh.editor.model.Layer
import com.binlate.suaanh.editor.model.ShapeKind

/**
 * Rasterizes layers onto a bitmap. Used for the final export so the saved
 * file matches the on-screen preview exactly (same maths, same order).
 */
object EditorRenderer {

    fun render(base: Bitmap, layers: List<Layer>): Bitmap {
        val shorter = EditorProcessor.shorter(base)
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(base, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        for (layer in layers) {
            when (layer) {
                is Layer.Stroke -> drawStroke(canvas, layer, base.width, base.height, shorter.toFloat())
                is Layer.Text -> drawText(canvas, layer, base.width, base.height)
                is Layer.Cover -> drawCover(canvas, base, layer)
                is Layer.Shape -> drawShape(canvas, layer, base.width, base.height, shorter.toFloat())
                is Layer.BlurStroke -> drawBlurStroke(canvas, base, layer, shorter.toFloat())
                is Layer.Arrow -> drawArrow(canvas, layer, base.width, base.height, shorter.toFloat())
            }
        }
        return out
    }

    private fun drawStroke(
        canvas: Canvas,
        layer: Layer.Stroke,
        w: Int,
        h: Int,
        shorter: Float,
    ) {
        if (layer.points.size < 2) return
        val path = Path()
        layer.points.forEachIndexed { i, p ->
            val x = p.x * w
            val y = p.y * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color
            alpha = (layer.alpha * 255).toInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeWidth = layer.widthFraction * shorter
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, paint)
    }

    private fun drawText(canvas: Canvas, layer: Layer.Text, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color
            textAlign = Paint.Align.CENTER
            textSize = layer.sizeFraction * layer.scale * w
            typeface = if (layer.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val x = layer.position.x * w
        val y = layer.position.y * h
        val baseline = y - (paint.descent() + paint.ascent()) / 2f
        if (layer.rotation != 0f) {
            canvas.save()
            canvas.rotate(layer.rotation, x, y)
            canvas.drawText(layer.content, x, baseline, paint)
            canvas.restore()
        } else {
            canvas.drawText(layer.content, x, baseline, paint)
        }
    }

    private fun drawCover(canvas: Canvas, base: Bitmap, layer: Layer.Cover) {
        val rect = RectF(
            layer.left * base.width,
            layer.top * base.height,
            layer.right * base.width,
            layer.bottom * base.height,
        )
        when (layer.mode) {
            CoverMode.PIXELATE ->
                canvas.drawBitmap(EditorProcessor.pixelate(base), null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            CoverMode.SOLID ->
                canvas.drawRect(rect, Paint().apply {
                    color = layer.color
                    style = Paint.Style.FILL
                })
        }
    }

    private fun drawShape(
        canvas: Canvas,
        layer: Layer.Shape,
        w: Int,
        h: Int,
        shorter: Float,
    ) {
        val rect = RectF(
            layer.left * w,
            layer.top * h,
            layer.right * w,
            layer.bottom * h,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color
            style = Paint.Style.STROKE
            strokeWidth = layer.strokeFraction * shorter
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when (layer.kind) {
            ShapeKind.ELLIPSE -> canvas.drawOval(rect, paint)
            ShapeKind.RECT -> canvas.drawRect(rect, paint)
        }
    }

    /**
     * Applies a real blur to the region covered by the blur stroke: the blurred
     * source is drawn only where the stroke path (inflated by the brush size) is.
     */
    private fun drawBlurStroke(
        canvas: Canvas,
        base: Bitmap,
        layer: Layer.BlurStroke,
        shorter: Float,
    ) {
        if (layer.points.isEmpty()) return
        val stroke = android.graphics.Path()
        layer.points.forEachIndexed { i, p ->
            val x = p.x * base.width
            val y = p.y * base.height
            if (i == 0) stroke.moveTo(x, y) else stroke.lineTo(x, y)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = layer.brushFraction * 2f * shorter
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = android.graphics.Path()
        strokePaint.getFillPath(stroke, fill)

        val blurred = EditorProcessor.blurredFor(base, layer.strength)
        val save = canvas.save()
        canvas.clipPath(fill)
        canvas.drawBitmap(blurred, 0f, 0f, null)
        canvas.restoreToCount(save)
    }

    /**
     * Draws an arrow annotation. The arrowhead geometry mirrors the preview
     * (CanvasOverlay.arrowHeadGeometry) so export matches on-screen rendering:
     * the shaft stops at the head base and the head is clamped to 80% of the
     * shaft length so very short arrows still render cleanly.
     */
    private fun drawArrow(
        canvas: Canvas,
        layer: Layer.Arrow,
        w: Int,
        h: Int,
        shorter: Float,
    ) {
        val ax = layer.start.x * w
        val ay = layer.start.y * h
        val bx = layer.end.x * w
        val by = layer.end.y * h
        val dx = bx - ax
        val dy = by - ay
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 0.01f) return
        val ux = dx / len
        val uy = dy / len

        val shaftPx = layer.strokeFraction * shorter
        val headLen = minOf(shaftPx * 4f * layer.headScale, len * 0.8f)
        val shaftEndX = bx - ux * headLen
        val shaftEndY = by - uy * headLen

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color
            style = Paint.Style.STROKE
            strokeWidth = shaftPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(ax, ay, shaftEndX, shaftEndY, paint)

        val halfW = headLen * 0.5f
        val head = Path().apply {
            moveTo(bx, by)
            lineTo(shaftEndX - uy * halfW, shaftEndY + ux * halfW)
            lineTo(shaftEndX + uy * halfW, shaftEndY - ux * halfW)
            close()
        }
        canvas.drawPath(head, paint.apply { style = Paint.Style.FILL })
    }
}