package com.binlate.suaanh.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.binlate.suaanh.editor.model.CoverMode
import com.binlate.suaanh.editor.model.Layer

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
            textSize = layer.sizeFraction * w
            typeface = if (layer.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val x = layer.position.x * w
        val y = layer.position.y * h
        val baseline = y - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(layer.content, x, baseline, paint)
    }

    private fun drawCover(canvas: Canvas, base: Bitmap, layer: Layer.Cover) {
        val rect = RectF(
            layer.left * base.width,
            layer.top * base.height,
            layer.right * base.width,
            layer.bottom * base.height,
        )
        when (layer.mode) {
            CoverMode.BLUR ->
                canvas.drawBitmap(EditorProcessor.blur(base), null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            CoverMode.PIXELATE ->
                canvas.drawBitmap(EditorProcessor.pixelate(base), null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            CoverMode.SOLID ->
                canvas.drawRect(rect, Paint().apply {
                    color = layer.color
                    style = Paint.Style.FILL
                })
        }
    }
}