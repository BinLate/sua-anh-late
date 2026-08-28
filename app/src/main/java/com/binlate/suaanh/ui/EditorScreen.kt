package com.binlate.suaanh.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binlate.suaanh.R
import com.binlate.suaanh.editor.CanvasOverlay
import com.binlate.suaanh.editor.EditorViewModel
import com.binlate.suaanh.ui.components.ColorPickerRequest
import com.binlate.suaanh.ui.components.ColorPickerDialog
import com.binlate.suaanh.ui.components.TextContentDialog
import com.binlate.suaanh.ui.components.ToolControls
import com.binlate.suaanh.ui.components.ToolSelector
import com.binlate.suaanh.ui.components.UndoRedoBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: EditorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var colorPicker by remember { mutableStateOf<ColorPickerRequest?>(null) }
    var addTextOpen by remember { mutableStateOf(false) }
    var editTextOpen by remember { mutableStateOf(false) }
    var editTargetId by remember { mutableStateOf<Long?>(null) }

    // When the view model requests editing an existing text layer, open the editor.
    LaunchedEffect(state.editRequestId) {
        val id = state.editRequestId
        if (id != null) {
            editTargetId = id
            editTextOpen = true
            state.clearEditRequest()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { state.setImage(it) } }

    fun saveAndShare(share: Boolean) {
        scope.launch(Dispatchers.IO) {
            val uri = state.exportAndGetUri()
            withContext(Dispatchers.Main) {
                if (uri == null) {
                    Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                Toast.makeText(context, R.string.saved, Toast.LENGTH_SHORT).show()
                if (share) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { saveAndShare(false) }) {
                        Icon(Icons.Filled.Save, contentDescription = context.getString(R.string.save))
                    }
                    IconButton(onClick = { saveAndShare(true) }) {
                        Icon(Icons.Filled.Share, contentDescription = context.getString(R.string.share))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Tool selection
            ToolSelector(state, Modifier.fillMaxWidth())

            // Per-tool controls (color / size / cover mode)
            ToolControls(
                state = state,
                openColorPicker = { request -> colorPicker = request },
                onAddText = { addTextOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )

            // Canvas
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.preview != null) {
                    CanvasOverlay(state, Modifier.fillMaxSize())
                } else {
                    EmptyState(onPick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            }

            // Action bar
            UndoRedoBar(state, Modifier.fillMaxWidth())
        }
    }

    colorPicker?.let { request ->
        ColorPickerDialog(request = request, onDismiss = { colorPicker = null })
    }

    if (addTextOpen) {
        TextContentDialog(
            title = "Thêm chữ",
            onConfirm = { content ->
                addTextOpen = false
                state.addText(content)
            },
            onDismiss = { addTextOpen = false },
        )
    }

    if (editTextOpen) {
        val target = state.findText(editTargetId ?: -1)
        TextContentDialog(
            title = "Sửa chữ",
            initialText = target?.content.orEmpty(),
            onConfirm = { content ->
                editTextOpen = false
                editTargetId?.let { id -> state.applyTextEdit(id, content) }
            },
            onDismiss = { editTextOpen = false },
        )
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Chưa có ảnh nào",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onPick) {
                Text("Chọn ảnh từ máy")
            }
        }
    }
}