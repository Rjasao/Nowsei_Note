package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.ContentBlock
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.repository.PageRepository
import java.util.UUID
import javax.inject.Inject

class AddPageUseCase @Inject constructor(
    private val repository: PageRepository
) {

    suspend operator fun invoke(
        sectionId: String,
        title: String
    ): Page {

        val newPage = Page(
            id = UUID.randomUUID().toString(),
            sectionId = sectionId,
            title = title,
            contentBlocks = listOf(
                ContentBlock.TextBlock(
                    id = UUID.randomUUID().toString(),
                    order = 0,
                    text = ""
                )
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        repository.upsertPage(newPage)

        return newPage
    }
}
