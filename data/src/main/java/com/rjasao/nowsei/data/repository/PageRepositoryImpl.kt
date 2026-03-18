package com.rjasao.nowsei.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rjasao.nowsei.data.local.PageDao
import com.rjasao.nowsei.data.local.PageTombstoneStore
import com.rjasao.nowsei.data.local.entity.PageEntity
import com.rjasao.nowsei.domain.model.ContentBlock
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PageRepositoryImpl @Inject constructor(
    private val pageDao: PageDao,
    private val pageTombstoneStore: PageTombstoneStore,
    private val gson: Gson
) : PageRepository {

    override fun getPagesForSection(sectionId: String): Flow<List<Page>> {
        return pageDao.getPagesForSection(sectionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getPageById(id: String): Flow<Page?> {
        return pageDao.getPageById(id).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun upsertPage(page: Page) {
        pageDao.upsertPage(page.toEntity())
        pageTombstoneStore.clear(page.id)
    }

    override suspend fun deletePage(page: Page) {
        pageTombstoneStore.markDeleted(page.id, System.currentTimeMillis())
        pageDao.deletePage(page.toEntity())
    }

    override suspend fun getAllPages(): List<Page> {
        return pageDao.getAllPages().map { it.toDomain() }
    }

    // -----------------------------
    // MAPPERS
    // -----------------------------

    private fun PageEntity.toDomain(): Page {
        val type = object : TypeToken<List<ContentBlock>>() {}.type
        val blocks: List<ContentBlock> =
            if (contentBlocksJson.isBlank()) emptyList()
            else gson.fromJson(contentBlocksJson, type)

        return Page(
            id = id,
            sectionId = sectionId,
            title = title,
            contentBlocks = blocks,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Page.toEntity(): PageEntity {
        return PageEntity(
            id = id,
            sectionId = sectionId,
            title = title,
            contentBlocksJson = gson.toJson(contentBlocks),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
