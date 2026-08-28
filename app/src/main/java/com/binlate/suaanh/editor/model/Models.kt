package com.binlate.suaanh.editor.model

import androidx.compose.ui.geometry.Offset

enum class EditorTool { PEN, HIGHLIGHT, TEXT, COVER, SHAPE, BLUR, ARROW }
enum class CoverMode { SOLID, PIXELATE }

/** Kind of outline shape that can be drawn and edited. */
enum class ShapeKind { ELLIPSE, RECT }

/** Resize handles of a selected shape (corners resize both axes, edges one axis). */
enum class Handle { TL, TR, BL, BR, TC, BC, LC, RC }

/** Which endpoint of an arrow is being dragged. */
enum class ArrowEnd { START, END }

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
        val rotation: Float = 0f,     // rotation in degrees (0..360)
        val scale: Float = 1f,        // extra size multiplier on top of sizeFraction
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
        val height: Float get() = bottom - top
    }

    /** Editable outline shape (ellipse or rectangle) with its own stroke settings. */
    data class Shape(
        val id: Long,                  // unique id for selection/editing
        val kind: ShapeKind,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,             // normalized bounds
        val color: Int,                // stroke ARGB
        val strokeFraction: Float,     // stroke width as fraction of the shorter image edge
    ) : Layer() {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** Editable arrow annotation with distinct start/end points. */
    data class Arrow(
        val id: Long,                  // unique id for selection/editing
        val start: Offset,             // normalized start point
        val end: Offset,               // normalized end point (arrow head)
        val color: Int,                // ARGB
        val strokeFraction: Float,     // shaft width as fraction of the shorter image edge
        val headScale: Float = 1f,     // extra arrowhead size multiplier
    ) : Layer()

    /**
     * A painted blur stroke. The affected region is the stroke path inflated by
     * the brush radius; [strength] selects the actual blur radius (1..10).
     */
    data class BlurStroke(
        val points: List<Offset>,      // normalized stroke path
        val brushFraction: Float,      // brush radius as fraction of the shorter image edge
        val strength: Int,             // 1..10, maps to a real blur radius
    ) : Layer()
}

/** Immutable snapshot of the full editing stack (used for undo/redo). */
data class EditorLayerState(
    val layers: List<Layer> = emptyList(),
)