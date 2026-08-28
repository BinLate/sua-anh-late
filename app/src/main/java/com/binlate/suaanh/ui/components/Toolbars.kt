package com.binlate.suaanh.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.binlate.suaanh.editor.EditorViewModel
import com.binlate.suaanh.editor.model.EditorTool

@Composable
fun ToolSelector(state: EditorViewModel, modifier: Modifier = Modifier) {
    // Horizontally scrollable so six tools never overflow narrow screens.
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolChoice("Bút vẽ", Icons.Filled.Edit, EditorTool.PEN, state)
        ToolChoice("Highlight", Icons.Filled.Brush, EditorTool.HIGHLIGHT, state)
        ToolChoice("Chữ", Icons.Filled.TextFields, EditorTool.TEXT, state)
        ToolChoice("Che", Icons.Filled.Layers, EditorTool.COVER, state)
        ToolChoice("Hình", Icons.Filled.Category, EditorTool.SHAPE, state)
        ToolChoice("Làm mờ", Icons.Filled.BlurOn, EditorTool.BLUR, state)
        ToolChoice("Mũi tên", Icons.Filled.NorthEast, EditorTool.ARROW, state)
    }
}

@Composable
private fun ToolChoice(label: String, icon: ImageVector, tool: EditorTool, state: EditorViewModel) {
    val selected = state.tool == tool
    FilterChip(
        selected = selected,
        onClick = { state.selectTool(tool) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
fun UndoRedoBar(state: EditorViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { state.undo() }, enabled = state.canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Hoàn tác")
        }
        IconButton(onClick = { state.redo() }, enabled = state.canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Làm lại")
        }
    }
}