package com.rjasao.nowsei.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rjasao.nowsei.domain.model.ContentBlock
import com.rjasao.nowsei.domain.model.Page
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    page: Page,
    onPageChange: (Page) -> Unit,
    onAddImage: (String) -> Unit
) {

    var blocks by remember { mutableStateOf(page.contentBlocks) }

    fun updatePage(newBlocks: List<ContentBlock>) {
        blocks = newBlocks
        onPageChange(page.copy(contentBlocks = newBlocks))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page.title) }
            )
        },
        bottomBar = {
            EditorToolbar(
                onInsertText = {
                    val newBlock = ContentBlock.TextBlock(
                        id = UUID.randomUUID().toString(),
                        order = blocks.size,
                        text = ""
                    )
                    updatePage(blocks + newBlock)
                },
                onInsertImage = {
                    onAddImage(blocks.lastOrNull()?.id ?: "")
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->

                when (block) {

                    is ContentBlock.TextBlock -> {
                        EditableTextBlock(
                            block = block,
                            onTextChange = { newText ->
                                val updated = blocks.toMutableList()
                                updated[index] = block.copy(text = newText)
                                updatePage(updated)
                            }
                        )
                    }

                    is ContentBlock.ImageBlock -> {
                        ImageBlockView(block)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
