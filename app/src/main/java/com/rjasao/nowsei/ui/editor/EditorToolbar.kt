package com.rjasao.nowsei.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditorToolbar(
    onInsertText: () -> Unit,
    onInsertImage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        Button(onClick = onInsertText) {
            Text("Texto")
        }

        Button(onClick = onInsertImage) {
            Text("Imagem")
        }
    }
}
