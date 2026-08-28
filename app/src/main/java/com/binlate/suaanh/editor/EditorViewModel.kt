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
import com.binlate.suaanh.editor.model.CoverMode
import com.binlate.suaanh.editor.model.EditorTool
import com.binlate.suaanh.editor.model.Layer
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    // ---------- image / preview ----------
    var imageUri by mutableStateOf<Uri?>(null)
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var blurBitmap by mutableStateOf<Bitmap?>(null)
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

    // ---------- cover settings ----------
    var coverMode by mutableStateOf(CoverMode.BLUR)
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

    /** The currently selected text layer, or null when none. */
    val selectedText: Layer.Text?
        get() = layers.lastOrNull { it is Layer.Text && it.id == selectedTextId } as Layer.Text?

    fun findText(id: Long): Layer.Text? =
        layers.lastOrNull { it is Layer.Text && it.id == id } as Layer.Text?

    // ---------------- Image load ----------------
    fun setImage(uri: Uri) {
        clear()
        imageUri = uri
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val bmp = EditorProcessor.decodePreview(resolver, uri)
            preview = bmp
            blurBitmap = EditorProcessor.blur(bmp)
            pixelBitmap = EditorProcessor.pixelate(bmp)
            originalWidth = bmp.width
            originalHeight = bmp.height
        }.onFailure {
            imageUri = null
        }
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
        preview?.recycle()
        preview = null
        blurBitmap?.recycle()
        blurBitmap = null
        pixelBitmap?.recycle()
        pixelBitmap = null
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

    // ---- property editing of the selected text (size / color), with undo session ----

    /** Open an undo session so a sequence of live edits commits as one history entry. */
    fun beginTextPropertySession() {
        if (selectedTextId != null && !textSessionOn) {
            textSessionBase = layers
            textSessionOn = true
            future.clear()
        }
    }

    /** Live-preview font size; when a text is selected it updates that layer immediately. */
    fun previewTextSize(fraction: Float) {
        beginTextPropertySession()
        textSizeFraction = fraction
        val id = selectedTextId ?: return
        layers = layers.map {
            if (it is Layer.Text && it.id == id) it.copy(sizeFraction = fraction) else it
        }
    }

    /** Live-preview color; when a text is selected it updates that layer immediately. */
    fun previewTextColor(color: Color) {
        beginTextPropertySession()
        textColor = color
        val id = selectedTextId ?: return
        layers = layers.map {
            if (it is Layer.Text && it.id == id) it.copy(color = color.toArgb()) else it
        }
    }

    /** Commit the open text property session as a single undo step. */
    fun commitTextPropertySession() {
        if (!textSessionOn) return
        textSessionOn = false
        val base = textSessionBase
        textSessionBase = null
        if (base != null && base != layers) history.add(base)
        updateUndoFlags()
    }

    /** Discard the open session and restore the state before it (used by Cancel). */
    fun cancelTextPropertySession() {
        if (!textSessionOn) return
        textSessionOn = false
        layers = textSessionBase ?: layers
        textSessionBase = null
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
        blurBitmap?.recycle()
        pixelBitmap?.recycle()
        super.onCleared()
    }
}