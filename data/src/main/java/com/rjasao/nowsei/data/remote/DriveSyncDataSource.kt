package com.rjasao.nowsei.data.remote

import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.model.SyncManifest

data class SyncTombstones(
    val notebooks: Map<String, Long> = emptyMap(),
    val sections: Map<String, Long> = emptyMap(),
    val pages: Map<String, Long> = emptyMap()
)

interface DriveSyncDataSource {
    fun isReady(): Boolean

    suspend fun readManifest(): SyncManifest

    suspend fun writeManifest(manifest: SyncManifest)

    suspend fun readTombstones(): SyncTombstones

    suspend fun writeTombstones(tombstones: SyncTombstones)

    suspend fun getAllNotebooks(): List<Notebook>

    suspend fun saveNotebook(notebook: Notebook)

    suspend fun deleteNotebook(notebookId: String)

    suspend fun getSectionsForNotebook(notebookId: String): List<Section>

    suspend fun saveSection(section: Section)

    suspend fun deleteSection(sectionId: String)

    suspend fun getPagesForSection(sectionId: String): List<Page>

    suspend fun savePage(page: Page)

    suspend fun deletePage(sectionId: String, pageId: String)
}
