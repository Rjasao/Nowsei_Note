package com.rjasao.nowsei.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rjasao.nowsei.domain.model.ContentBlock

@Composable
fun ImageBlockView(block: ContentBlock.ImageBlock) {

    AsyncImage(
        model = block.imageUrl,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentScale = ContentScale.Crop
    )
}
