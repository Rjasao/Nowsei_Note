package com.rjasao.nowsei.domain.repository

import com.rjasao.nowsei.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface PageRepository {

    fun getPagesForSection(sectionId: String): Flow<List<Page>>

    fun getPageById(id: String): Flow<Page?>

    suspend fun upsertPage(page: Page)

    suspend fun deletePage(page: Page)

    // ✅ ADICIONE ISTO
    suspend fun getAllPages(): List<Page>
}
