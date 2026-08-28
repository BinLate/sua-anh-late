package com.binlate.suaanh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binlate.suaanh.editor.EditorViewModel
import com.binlate.suaanh.ui.EditorScreen

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B6CB0),
    onPrimary = Color.White,
    secondary = Color(0xFF7B1FA2),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFBFBFB),
)

@Composable
fun SuaAnhTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuaAnhTheme {
                val vm: EditorViewModel = viewModel()
                EditorScreen(vm)
            }
        }
    }
}