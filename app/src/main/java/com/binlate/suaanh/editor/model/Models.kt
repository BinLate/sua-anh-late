package com.binlate.suaanh.editor.model

import androidx.compose.ui.geometry.Offset

enum class EditorTool { PEN, HIGHLIGHT, TEXT, COVER }
enum class CoverMode { BLUR, PIXELATE, SOLID }

/**
 * A single editing layer placed over the source image.
 * All positional/length values are NORMALIZED (0..1) relative to the image
 * so that editing stays resolution independent and exports to the original
 * image pixel-perfectly.
 */
sealed class Layer {
    /** Free-hand stroke drawn with a pen or a highlight/highlighter. */
    data class Stroke(
        val points: List<Offset>,   // normalized local offsets along the path
        val color: Int,               // ARGB
        val widthFraction: Float,     // width as fraction of the shorter image edge
        val alpha: Float,             // 1f for pen, ~0.45f for highlight
    ) : Layer()

    /** Text pasted onto the photo; kept editable as its own layer with a unique id. */
    data class Text(
        val id: Long,                 // unique id so the layer can be edited/selected
        val position: Offset,         // normalized center of the text
        val content: String,
        val color: Int,               // ARGB
        val sizeFraction: Float,      // text paint size as fraction of image width
        val bold: Boolean = true,
    ) : Layer()

    /** Rectangular region used to hide/highlight parts of the photo. */
    data class Cover(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,             // normalized bounds
        val mode: CoverMode,
        val color: Int,               // used when mode == SOLID
    ) : Layer() {
        val width: Float get() = right - left
        val height: Float get() = top - bottom
    }
}

/** Immutable snapshot of the full editing stack (used for undo/redo). */
data class EditorLayerState(
    val layers: List<Layer> = emptyList(),
)