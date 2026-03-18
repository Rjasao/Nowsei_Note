package com.rjasao.nowsei.domain.repository

/** Contrato de sincronização (Drive / backup / etc). */
interface SyncRepository {
    /** Executa sincronização completa/incremental, conforme implementação. */
    suspend fun syncAll()
}
