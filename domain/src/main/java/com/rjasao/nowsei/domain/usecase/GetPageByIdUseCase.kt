package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPageByIdUseCase @Inject constructor(
    private val repository: PageRepository
) {

    operator fun invoke(id: String): Flow<com.rjasao.nowsei.domain.model.Page?> {
        return repository.getPageById(id)
    }
}
