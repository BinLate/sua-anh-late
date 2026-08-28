package com.binlate.suaanh.editor

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binlate.suaanh.editor.model.ArrowEnd
import com.binlate.suaanh.editor.model.CoverMode
import com.binlate.suaanh.editor.model.EditorTool
import com.binlate.suaanh.editor.model.Handle
import com.binlate.suaanh.editor.model.Layer
import com.binlate.suaanh.editor.model.ShapeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    // ---------- image / preview ----------
    var imageUri by mutableStateOf<Uri?>(null)
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var pixelBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var originalWidth by mutableStateOf(0)
        private set
    var originalHeight by mutableStateOf(0)
        private set

    // ---------- layers + undo/redo ----------
    var layers by mutableStateOf<List<Layer>>(emptyList())
        private set
    private val history = mutableListOf<List<Layer>>()
    private val future = mutableListOf<List<Layer>>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var baseBeforeAction: List<Layer>? = null

    // ---------- dirty state (for the "choose another image" flow) ----------
    private var savedUri: Uri? = null
    private var savedLayers: List<Layer> = emptyList()

    /** True only after a real modification that is not saved yet. */
    val isDirty: Boolean
        get() = imageUri != null && (layers != savedLayers || imageUri != savedUri)

    // ---------- active tool ----------
    var tool by mutableStateOf(EditorTool.PEN)
        private set

    // ---------- pen settings ----------
    var penColor by mutableStateOf(Color(0xFFD32F2F))
    var penWidthFraction by mutableStateOf(0.004f)   // of shorter edge

    // ---------- highlight settings ----------
    var highlightColor by mutableStateOf(Color(0xFFFFEB3B))
    var highlightWidthFraction by mutableStateOf(0.01f)
    val highlightAlpha = 0.45f

    // ---------- text settings ----------
    var textColor by mutableStateOf(Color(0xFFFFFFFF))
    var textSizeFraction by mutableStateOf(0.06f)    // of image width

    // ---------- shape settings ----------
    var shapeKind by mutableStateOf(ShapeKind.ELLIPSE)
    var shapeColor by mutableStateOf(Color(0xFFFFEB3B))
    var shapeStrokeFraction by mutableStateOf(0.004f) // of shorter edge

    // ---------- blur settings ----------
    var blurBrushFraction by mutableStateOf(0.02f)    // brush radius, of shorter edge
    var blurStrength by mutableStateOf(5)             // 1..10, maps to a real blur radius

    // ---------- arrow settings ----------
    var arrowColor by mutableStateOf(Color(0xFFE53935))
    var arrowStrokeFraction by mutableStateOf(0.006f) // shaft width, of shorter edge
    var arrowHeadScale by mutableStateOf(1f)          // arrowhead size multiplier

    // Precomputed blurred preview bitmap for the current strength (background task).
    var blurStrengthBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // LRU cache of preview blurred bitmaps per strength (for rendering strokes).
    private val previewBlurCache = object : LinkedHashMap<Int, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean = size > 3
    }
    var previewBlurVersion by mutableStateOf(0)
        private set

    /**
     * Returns the preview bitmap blurred at [strength], or null while it is
     * being computed in the background (recomposition happens via
     * [previewBlurVersion]).
     */
    fun previewBlurredBitmap(strength: Int): Bitmap? {
        val src = preview ?: return null
        val s = strength.coerceIn(1, 10)
        previewBlurCache[s]?.let { return it }
        viewModelScope.launch(Dispatchers.Default) {
            val b = EditorProcessor.blurredFor(src, s)
            previewBlurCache[s] = b
            previewBlurVersion++
        }
        return null
    }

    // ---------- cover settings ----------
    var coverMode by mutableStateOf(CoverMode.SOLID)
    var coverColor by mutableStateOf(Color(0xFF000000))

    private val coverArgb: Int
        get() = if (coverMode == CoverMode.SOLID) coverColor.toArgb() else 0

    // ---------- transient drawing state ----------
    private var draftStroke: Layer.Stroke? = null
    private var draftPoints = mutableListOf<Offset>()
    private var coverStart: Offset? = null
    private var coverEnd: Offset? = null
    private var movingTextId: Long? = null
    private var movingOriginalPos: Offset = Offset.Zero
    private var dragStartOffset: Offset = Offset.Zero

    // ---------- text selection / editing ----------
    var selectedTextId by mutableStateOf<Long?>(null)
        private set
    var editRequestId by mutableStateOf<Long?>(null)
        private set
    private var nextTextId = 1L
    private var textSessionBase: List<Layer>? = null
    private var textSessionOn = false

    // ---------- shape selection / editing ----------
    var selectedShapeId by mutableStateOf<Long?>(null)
        private set
    private var movingShapeId: Long? = null
    private var shapeMoveOriginal: Layer.Shape? = null
    private var resizingHandle: Handle? = null
    private var resizeOriginal: Layer.Shape? = null
    private var resizeStartOffset: Offset = Offset.Zero
    var draftShape by mutableStateOf<Layer.Shape?>(null)
        private set
    private var draftShapeStart: Offset? = null
    private var nextShapeId = 1L

    // ---------- blur stroke drafting ----------
    var draftBlur by mutableStateOf<Layer.BlurStroke?>(null)
        private set
    private var draftBlurPoints = mutableListOf<Offset>()

    // ---------- arrow selection / editing ----------
    var selectedArrowId by mutableStateOf<Long?>(null)
        private set
    private var movingArrowId: Long? = null
    private var arrowMoveOriginal: Layer.Arrow? = null
    private var arrowEndDrag: ArrowEnd? = null
    private var draftArrow: Offset? = null       // normalized start while creating
    var draftArrowEnd by mutableStateOf<Offset?>(null) // normalized live end while creating
    private var nextArrowId = 1L
    private var draftArrowId = 1L

    /** The currently selected arrow layer, or null when none. */
    val selectedArrow: Layer.Arrow?
        get() = layers.lastOrNull { it is Layer.Arrow && it.id == selectedArrowId } as Layer.Arrow?

    fun findArrow(id: Long): Layer.Arrow? =
        layers.lastOrNull { it is Layer.Arrow && it.id == id } as Layer.Arrow?

    /** The currently selected text layer, or null when none. */
    val selectedText: Layer.Text?
        get() = layers.lastOrNull { it is Layer.Text && it.id == selectedTextId } as Layer.Text?

    fun findText(id: Long): Layer.Text? =
        layers.lastOrNull { it is Layer.Text && it.id == id } as Layer.Text?

    /** The currently selected shape layer, or null when none. */
    val selectedShape: Layer.Shape?
        get() = layers.lastOrNull { it is Layer.Shape && it.id == selectedShapeId } as Layer.Shape?

    fun findShape(id: Long): Layer.Shape? =
        layers.lastOrNull { it is Layer.Shape && it.id == id } as Layer.Shape?

    // ---------------- Image load ----------------
    fun setImage(uri: Uri) {
        clear()
        imageUri = uri
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val bmp = EditorProcessor.decodePreview(resolver, uri)
            preview = bmp
            pixelBitmap = EditorProcessor.pixelate(bmp)
            originalWidth = bmp.width
            originalHeight = bmp.height
            refreshBlurStrengthBitmap()
            savedUri = uri
            savedLayers = emptyList()
        }.onFailure {
            imageUri = null
        }
    }

    /** Recomputes the blurred preview bitmap for the current strength off the main thread. */
    private fun refreshBlurStrengthBitmap() {
        val bmp = preview ?: return
        val strength = blurStrength
        viewModelScope.launch(Dispatchers.Default) {
            val blurred = EditorProcessor.blurredFor(bmp, strength)
            blurStrengthBitmap = blurred
        }
    }

    fun updateBlurStrength(strength: Int) {
        val clamped = strength.coerceIn(1, 10)
        if (clamped == blurStrength) return
        blurStrength = clamped
        refreshBlurStrengthBitmap()
    }

    /** Marks the current state as saved (after a successful export). */
    fun markSaved() {
        savedUri = imageUri
        savedLayers = layers
    }

    private fun clear() {
        layers = emptyList()
        history.clear()
        future.clear()
        canUndo = false
        canRedo = false
        selectedTextId = null
        editRequestId = null
        movingTextId = null
        textSessionBase = null
        textSessionOn = false
        nextTextId = 1L
        selectedShapeId = null
        movingShapeId = null
        resizingHandle = null
        draftShape = null
        draftBlur = null
        selectedArrowId = null
        movingArrowId = null
        arrowMoveOriginal = null
        arrowEndDrag = null
        draftArrow = null
        draftArrowEnd = null
        nextArrowId = 1L
        EditorProcessor.clearBlurCache()
        previewBlurCache.clear()
        previewBlurVersion++
        preview?.recycle()
        preview = null
        pixelBitmap?.recycle()
        pixelBitmap = null
        blurStrengthBitmap = null
    }

    // ---------------- tool ----------------
    fun selectTool(t: EditorTool) {
        tool = t
    }

    // ---------------- undo / redo ----------------
    private fun beginAction() {
        baseBeforeAction = layers
        future.clear()
    }

    private fun finalize(newLayers: List<Layer>) {
        val base = baseBeforeAction
        if (base != null && newLayers != base) history.add(base)
        layers = newLayers
        baseBeforeAction = null
        updateUndoFlags()
    }

    fun undo() {
        if (history.isEmpty()) return
        future.add(layers)
        layers = history.removeAt(history.size - 1)
        updateUndoFlags()
    }

    fun redo() {
        if (future.isEmpty()) return
        history.add(layers)
        layers = future.removeAt(future.size - 1)
        updateUndoFlags()
    }

    private fun updateUndoFlags() {
        canUndo = history.isNotEmpty()
        canRedo = future.isNotEmpty()
    }

    // ---------------- strokes (pen / highlight) ----------------
    fun beginStroke(norm: Offset) {
        beginAction()
        draftPoints = mutableListOf()
        draftStroke = when (tool) {
            EditorTool.PEN -> Layer.Stroke(emptyList(), penColor.toArgb(), penWidthFraction, 1f)
            EditorTool.HIGHLIGHT ->
                Layer.Stroke(emptyList(), highlightColor.toArgb(), highlightWidthFraction, highlightAlpha)
            else -> null
        }
        if (draftStroke != null) draftPoints.add(norm)
    }

    fun continueStroke(norm: Offset) {
        val stroke = draftStroke ?: return
        draftPoints.add(norm)
        draftStroke = stroke.copy(points = draftPoints.toList())
        layers = baseBeforeAction.orEmpty() + draftStroke!!
    }

    fun endStroke() {
        val stroke = draftStroke ?: return
        if (draftPoints.size >= 2) {
            finalize(baseBeforeAction.orEmpty() + stroke.copy(points = draftPoints.toList()))
        } else {
            layers = baseBeforeAction.orEmpty()
            baseBeforeAction = null
        }
        draftStroke = null
        draftPoints.clear()
    }

    // ---------------- cover ----------------
    fun beginCover(norm: Offset) {
        beginAction()
        coverStart = norm
        coverEnd = norm
        layers = baseBeforeAction.orEmpty()
    }

    fun continueCover(norm: Offset) {
        coverEnd = norm
        val start = coverStart ?: return
        val rect = coverDesc(start, norm) ?: return
        layers = baseBeforeAction.orEmpty() +
            Layer.Cover(rect.left, rect.top, rect.right, rect.bottom, coverMode, coverArgb)
    }

    fun endCover() {
        val start = coverStart ?: return
        val end = coverEnd ?: return
        coverStart = null
        coverEnd = null
        val rect = coverDesc(start, end) ?: run {
            layers = baseBeforeAction.orEmpty()
            baseBeforeAction = null
            return
        }
        if (rect.width > 0.001f && rect.height > 0.001f) {
            finalize(baseBeforeAction.orEmpty() + Layer.Cover(rect.left, rect.top, rect.right, rect.bottom, coverMode, coverArgb))
        } else {
            layers = baseBeforeAction.orEmpty()
            baseBeforeAction = null
        }
    }

    private class RectDesc(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    private fun coverDesc(a: Offset, b: Offset): RectDesc? {
        val left = min(a.x, b.x).coerceIn(0f, 1f)
        val right = max(a.x, b.x).coerceIn(0f, 1f)
        val top = min(a.y, b.y).coerceIn(0f, 1f)
        val bottom = max(a.y, b.y).coerceIn(0f, 1f)
        return RectDesc(left, top, right, bottom)
    }

    // ---------------- text ----------------
    /** Add a brand-new text layer and auto-select it. */
    fun addText(content: String) {
        if (content.isBlank()) return
        beginAction()
        val id = nextTextId++
        finalize(
            layers + Layer.Text(
                id = id,
                position = Offset(0.5f, 0.5f),
                content = content,
                color = textColor.toArgb(),
                sizeFraction = textSizeFraction,
            )
        )
        selectedTextId = id
    }

    /** Handle a tap on the canvas: select a text layer or open the editor for the selected one. */
    fun handleTextTap(tapped: Layer.Text?) {
        when {
            tapped == null -> selectedTextId = null
            tapped.id == selectedTextId -> editRequestId = tapped.id
            else -> selectedTextId = tapped.id
        }
    }

    /** Begin a drag on a (possibly null) text layer. Returns true when dragging a text. */
    fun beginTextDrag(textOrNull: Layer.Text?, norm: Offset): Boolean {
        if (textOrNull == null) {
            selectedTextId = null
            return false
        }
        beginAction()
        selectedTextId = textOrNull.id
        movingTextId = textOrNull.id
        movingOriginalPos = textOrNull.position
        dragStartOffset = norm
        return true
    }

    fun continueTextDrag(norm: Offset) {
        val id = movingTextId ?: return
        val dx = norm.x - dragStartOffset.x
        val dy = norm.y - dragStartOffset.y
        layers = layers.map {
            if (it is Layer.Text && it.id == id) {
                it.copy(position = Offset(movingOriginalPos.x + dx, movingOriginalPos.y + dy))
            } else {
                it
            }
        }
    }

    fun endTextDrag() {
        if (movingTextId != null) finalize(layers)
        movingTextId = null
    }

    fun requestEditSelected() {
        editRequestId = selectedTextId
    }

    fun clearEditRequest() {
        editRequestId = null
    }

    /** Edit the content of an existing text layer (no new layer is created). */
    fun applyTextEdit(id: Long, content: String) {
        val text = findText(id) ?: return
        if (content == text.content) return
        beginAction()
        finalize(
            layers.map {
                if (it is Layer.Text && it.id == id) it.copy(content = content) else it
            }
        )
        selectedTextId = id
    }

    // ---- property editing of the selected layer (text or shape), with undo session ----

    private val selectedLayerId: Long?
        get() = selectedTextId ?: selectedShapeId ?: selectedArrowId

    /** Open an undo session so a sequence of live edits commits as one history entry. */
    fun beginLayerPropertySession() {
        if (selectedLayerId != null && !textSessionOn) {
            textSessionBase = layers
            textSessionOn = true
            future.clear()
        }
    }

    /** Live-preview font size; when a text is selected it updates that layer immediately. */
    fun previewTextSize(fraction: Float) {
        beginLayerPropertySession()
        textSizeFraction = fraction
        val id = selectedTextId ?: return
        layers = layers.map {
            if (it is Layer.Text && it.id == id) it.copy(sizeFraction = fraction) else it
        }
    }

    /** Live-preview color; when a text is selected it updates that layer immediately. */
    fun previewTextColor(color: Color) {
        beginLayerPropertySession()
        textColor = color
        val id = selectedTextId ?: return
        layers = layers.map {
            if (it is Layer.Text && it.id == id) it.copy(color = color.toArgb()) else it
        }
    }

    /** Live-preview stroke color; when a shape is selected it updates that layer immediately. */
    fun previewShapeColor(color: Color) {
        beginLayerPropertySession()
        shapeColor = color
        val id = selectedShapeId ?: return
        layers = layers.map {
            if (it is Layer.Shape && it.id == id) it.copy(color = color.toArgb()) else it
        }
    }

    /** Live-preview text rotation; edits the selected text layer in place. */
    fun previewTextRotation(degrees: Float) {
        beginLayerPropertySession()
        val id = selectedTextId ?: return
        layers = layers.map {
            if (it is Layer.Text && it.id == id) it.copy(rotation = degrees) else it
        }
    }

    /** Live-preview text scale; edits the selected text layer in place. */
    fun previewTextScale(scale: Float) {
        beginLayerPropertySession()
        val id = selectedTextId ?: return
        layers = layers.map {
            if (it is Layer.Text && it.id == id) it.copy(scale = scale) else it
        }
    }

    /** Delete the currently selected text, shape, or arrow layer (participates in undo/redo). */
    fun deleteSelectedLayer() {
        val textId = selectedTextId
        val shapeId = selectedShapeId
        val arrowId = selectedArrowId
        when {
            textId != null -> {
                beginAction()
                layers = layers.filterNot { it is Layer.Text && it.id == textId }
                selectedTextId = null
                editRequestId = null
                finalize(layers)
            }
            shapeId != null -> {
                beginAction()
                layers = layers.filterNot { it is Layer.Shape && it.id == shapeId }
                selectedShapeId = null
                finalize(layers)
            }
            arrowId != null -> {
                beginAction()
                layers = layers.filterNot { it is Layer.Arrow && it.id == arrowId }
                selectedArrowId = null
                finalize(layers)
            }
        }
    }

    /** Live-preview stroke width; when a shape is selected it updates that layer immediately. */
    fun previewShapeStroke(fraction: Float) {
        beginLayerPropertySession()
        shapeStrokeFraction = fraction
        val id = selectedShapeId ?: return
        layers = layers.map {
            if (it is Layer.Shape && it.id == id) it.copy(strokeFraction = fraction) else it
        }
    }

    /** Commit the open property session as a single undo step. */
    fun commitLayerPropertySession() {
        if (!textSessionOn) return
        textSessionOn = false
        val base = textSessionBase
        textSessionBase = null
        if (base != null && base != layers) history.add(base)
        updateUndoFlags()
    }

    /** Discard the open session and restore the state before it (used by Cancel). */
    fun cancelLayerPropertySession() {
        if (!textSessionOn) return
        textSessionOn = false
        layers = textSessionBase ?: layers
        textSessionBase = null
    }

    // ---------------- shapes ----------------

    /** Handle a tap on the canvas in the SHAPE tool: select a shape or deselect. */
    fun handleShapeTap(tapped: Layer.Shape?) {
        selectedShapeId = tapped?.id
    }

    /**
     * Begin a drag on a shape. [handle] != null means resizing that handle,
     * otherwise the whole shape is moved (when [tapped] != null).
     */
    fun beginShapeInteraction(tapped: Layer.Shape?, handle: Handle?, norm: Offset): Boolean {
        if (tapped == null) {
            selectedShapeId = null
            return false
        }
        beginAction()
        selectedShapeId = tapped.id
        if (handle != null) {
            resizingHandle = handle
            resizeOriginal = tapped
            resizeStartOffset = norm
        } else {
            movingShapeId = tapped.id
            shapeMoveOriginal = tapped
            dragStartOffset = norm
        }
        return true
    }

    fun continueShapeDrag(norm: Offset) {
        val handle = resizingHandle
        val resizeBase = resizeOriginal
        if (handle != null && resizeBase != null) {
            val dx = norm.x - resizeStartOffset.x
            val dy = norm.y - resizeStartOffset.y
            var l = resizeBase.left
            var t = resizeBase.top
            var r = resizeBase.right
            var b = resizeBase.bottom
            if (handle == Handle.TL || handle == Handle.BL || handle == Handle.LC) l = (resizeBase.left + dx).coerceIn(0f, 1f)
            if (handle == Handle.TR || handle == Handle.BR || handle == Handle.RC) r = (resizeBase.right + dx).coerceIn(0f, 1f)
            if (handle == Handle.TL || handle == Handle.TR || handle == Handle.TC) t = (resizeBase.top + dy).coerceIn(0f, 1f)
            if (handle == Handle.BL || handle == Handle.BR || handle == Handle.BC) b = (resizeBase.bottom + dy).coerceIn(0f, 1f)
            layers = layers.map {
                if (it is Layer.Shape && it.id == resizeBase.id) {
                    it.copy(left = min(l, r), top = min(t, b), right = max(l, r), bottom = max(t, b))
                } else {
                    it
                }
            }
            return
        }
        val id = movingShapeId
        val moved = shapeMoveOriginal
        if (id != null && moved != null) {
            val dx = (norm.x - dragStartOffset.x).coerceIn(-moved.left, 1f - moved.right)
            val dy = (norm.y - dragStartOffset.y).coerceIn(-moved.top, 1f - moved.bottom)
            layers = layers.map {
                if (it is Layer.Shape && it.id == id) {
                    it.copy(
                        left = moved.left + dx,
                        top = moved.top + dy,
                        right = moved.right + dx,
                        bottom = moved.bottom + dy,
                    )
                } else {
                    it
                }
            }
        }
    }

    fun endShapeDrag() {
        if (movingShapeId != null || resizingHandle != null) finalize(layers)
        movingShapeId = null
        shapeMoveOriginal = null
        resizingHandle = null
        resizeOriginal = null
    }

    /** Drag-create a new shape of [kind]. */
    fun beginShapeCreation(kind: ShapeKind, norm: Offset) {
        beginAction()
        draftShapeStart = norm
        draftShape = Layer.Shape(
            id = nextShapeId++,
            kind = kind,
            left = norm.x,
            top = norm.y,
            right = norm.x,
            bottom = norm.y,
            color = shapeColor.toArgb(),
            strokeFraction = shapeStrokeFraction,
        )
        layers = baseBeforeAction.orEmpty() + draftShape!!
    }

    fun continueShapeCreation(norm: Offset) {
        val start = draftShapeStart ?: return
        val draft = draftShape ?: return
        draftShape = draft.copy(
            left = min(start.x, norm.x),
            top = min(start.y, norm.y),
            right = max(start.x, norm.x),
            bottom = max(start.y, norm.y),
        )
        layers = baseBeforeAction.orEmpty() + draftShape!!
    }

    fun endShapeCreation() {
        val draft = draftShape ?: return
        draftShape = null
        draftShapeStart = null
        if (draft.width > 0.01f || draft.height > 0.01f) {
            finalize(baseBeforeAction.orEmpty() + draft)
            selectedShapeId = draft.id
        } else {
            layers = baseBeforeAction.orEmpty()
            baseBeforeAction = null
        }
    }

    // ---------------- blur strokes ----------------

    fun beginBlurStroke(norm: Offset) {
        beginAction()
        draftBlurPoints = mutableListOf(norm)
        draftBlur = Layer.BlurStroke(draftBlurPoints.toList(), blurBrushFraction, blurStrength)
        layers = baseBeforeAction.orEmpty() + draftBlur!!
    }

    fun continueBlurStroke(norm: Offset) {
        val draft = draftBlur ?: return
        val last = draftBlurPoints.lastOrNull()
        if (last != null) {
            val dx = norm.x - last.x
            val dy = norm.y - last.y
            // Skip micro movements to keep the path light.
            if (dx * dx + dy * dy < 0.000004f) return
        }
        draftBlurPoints.add(norm)
        draftBlur = draft.copy(points = draftBlurPoints.toList())
        layers = baseBeforeAction.orEmpty() + draftBlur!!
    }

    fun endBlurStroke() {
        val draft = draftBlur ?: return
        draftBlur = null
        if (draftBlurPoints.isNotEmpty()) {
            finalize(baseBeforeAction.orEmpty() + draft.copy(points = draftBlurPoints.toList()))
        } else {
            layers = baseBeforeAction.orEmpty()
            baseBeforeAction = null
        }
        draftBlurPoints.clear()
    }

    // ---------------- arrows ----------------

    /** Handle a tap: select an arrow or deselect when tapping empty space. */
    fun handleArrowTap(tapped: Layer.Arrow?) {
        selectedArrowId = tapped?.id
    }

    /**
     * Begin a drag on an arrow. [end] != null means dragging a specific endpoint,
     * otherwise ([tapped] != null) the whole arrow is moved.
     */
    fun beginArrowInteraction(tapped: Layer.Arrow?, end: ArrowEnd?, norm: Offset): Boolean {
        if (tapped == null) {
            selectedArrowId = null
            return false
        }
        beginAction()
        selectedArrowId = tapped.id
        if (end != null) {
            arrowEndDrag = end
        } else {
            movingArrowId = tapped.id
            arrowMoveOriginal = tapped
            dragStartOffset = norm
        }
        return true
    }

    fun continueArrowDrag(norm: Offset) {
        val moved = arrowMoveOriginal
        if (arrowEndDrag == null && moved != null) {
            val id = movingArrowId ?: return
            val dx = norm.x - dragStartOffset.x
            val dy = norm.y - dragStartOffset.y
            layers = layers.map {
                if (it is Layer.Arrow && it.id == id) {
                    it.copy(start = Offset(moved.start.x + dx, moved.start.y + dy),
                            end = Offset(moved.end.x + dx, moved.end.y + dy))
                } else {
                    it
                }
            }
            return
        }
        val id = movingArrowId ?: selectedArrowId ?: return
        when (arrowEndDrag) {
            ArrowEnd.START -> layers = layers.map {
                if (it is Layer.Arrow && it.id == id) {
                    it.copy(start = Offset(norm.x.coerceIn(0f, 1f), norm.y.coerceIn(0f, 1f)))
                } else {
                    it
                }
            }
            ArrowEnd.END -> layers = layers.map {
                if (it is Layer.Arrow && it.id == id) {
                    it.copy(end = Offset(norm.x.coerceIn(0f, 1f), norm.y.coerceIn(0f, 1f)))
                } else {
                    it
                }
            }
            null -> Unit
        }
    }

    fun endArrowDrag() {
        if (movingArrowId != null || arrowEndDrag != null) finalize(layers)
        movingArrowId = null
        arrowMoveOriginal = null
        arrowEndDrag = null
    }

    /** Drag-create a new arrow from [norm] (start and end at the same point). */
    fun beginArrowCreation(norm: Offset) {
        beginAction()
        draftArrow = norm
        draftArrowEnd = norm
        draftArrowId = nextArrowId++
    }

    fun continueArrowCreation(norm: Offset) {
        draftArrowEnd = norm
        val start = draftArrow ?: return
        val end = norm
        layers = baseBeforeAction.orEmpty() +
            Layer.Arrow(
                id = draftArrowId,
                start = Offset(start.x.coerceIn(0f, 1f), start.y.coerceIn(0f, 1f)),
                end = Offset(end.x.coerceIn(0f, 1f), end.y.coerceIn(0f, 1f)),
                color = arrowColor.toArgb(),
                strokeFraction = arrowStrokeFraction,
                headScale = arrowHeadScale,
            )
    }

    fun endArrowCreation() {
        draftArrowEnd = null
        val start = draftArrow ?: run { draftArrow = null; return }
        draftArrow = null
        val dx = layers.mapNotNull { (it as? Layer.Arrow)?.let { a -> a } }
            .firstOrNull { it.id == draftArrowId }
        if (dx != null && (start - dx.end).getDistance() > 0.001f) {
            finalize(layers)
            selectedArrowId = dx.id
        } else {
            layers = baseBeforeAction.orEmpty()
            baseBeforeAction = null
        }
    }

    /** Live-preview arrow color; edits the selected arrow in place. */
    fun previewArrowColor(color: Color) {
        beginLayerPropertySession()
        arrowColor = color
        val id = selectedArrowId ?: return
        layers = layers.map {
            if (it is Layer.Arrow && it.id == id) it.copy(color = color.toArgb()) else it
        }
    }

    /** Live-preview arrow shaft width; edits the selected arrow in place. */
    fun previewArrowStroke(fraction: Float) {
        beginLayerPropertySession()
        arrowStrokeFraction = fraction
        val id = selectedArrowId ?: return
        layers = layers.map {
            if (it is Layer.Arrow && it.id == id) it.copy(strokeFraction = fraction) else it
        }
    }

    /** Live-preview arrowhead size; edits the selected arrow in place. */
    fun previewArrowHead(scale: Float) {
        beginLayerPropertySession()
        arrowHeadScale = scale
        val id = selectedArrowId ?: return
        layers = layers.map {
            if (it is Layer.Arrow && it.id == id) it.copy(headScale = scale) else it
        }
    }

    // ---------------- export ----------------
    fun exportAndGetUri(): Uri? {
        val uri = imageUri ?: return null
        val app = getApplication<Application>()
        return runCatching {
            val resolver = app.contentResolver
            val full = EditorProcessor.decodeFull(resolver, uri)
            val out = EditorRenderer.render(full, layers)
            full.recycle()
            val saved = saveToMediaStore(app, out)
            out.recycle()
            saved
        }.getOrNull()
    }

    private fun saveToMediaStore(context: Application, bitmap: Bitmap): Uri? {
        val name = "SuaAnh_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SuaAnh")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver
        val insertUri = resolver.insert(collection, values) ?: return null
        return runCatchingInsert(resolver, insertUri, values, bitmap)
    }

    private fun runCatchingInsert(
        resolver: android.content.ContentResolver,
        insertUri: Uri,
        values: ContentValues,
        bitmap: Bitmap,
    ): Uri? {
        return try {
            val stream = resolver.openOutputStream(insertUri) ?: return null
            stream.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(insertUri, values, null, null)
            }
            insertUri
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        preview?.recycle()
        blurStrengthBitmap?.recycle()
        previewBlurCache.values.forEach { it.recycle() }
        previewBlurCache.clear()
        pixelBitmap?.recycle()
        super.onCleared()
    }
}