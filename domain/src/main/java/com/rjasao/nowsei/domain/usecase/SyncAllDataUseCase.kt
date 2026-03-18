package com.rjasao.nowsei.domain.usecase

import com.rjasao.nowsei.domain.repository.SyncRepository
import javax.inject.Inject

class SyncAllDataUseCase @Inject constructor(
    private val repository: SyncRepository
) {
    suspend operator fun invoke() {
        repository.syncAll()
    }
}
