package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPagesUseCase @Inject constructor(
    private val repository: PageRepository
) {
    operator fun invoke(sectionId: String): Flow<List<Page>> {
        return repository.getPagesForSection(sectionId)
    }
}
