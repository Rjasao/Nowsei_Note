package com.rjasao.nowsei.data.repository

import com.rjasao.nowsei.data.local.PageDao
import com.rjasao.nowsei.data.local.entity.PageEntity
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageRepositoryImpl @Inject constructor(
    private val pageDao: PageDao
) : PageRepository {

    override fun getPagesForSection(sectionId: String): Flow<List<Page>> {
        return pageDao.getPagesForSection(sectionId).map { entities ->
            entities.map { it.toPage() }
        }
    }

    override fun getPageById(pageId: String): Flow<Page?> {
        return pageDao.getPageById(pageId).map { entity -> entity?.toPage() }
    }

    override suspend fun upsertPage(page: Page) {
        pageDao.upsertPage(page.toPageEntity())
    }

    override suspend fun savePage(page: Page) {
        upsertPage(page)
    }

    // ✅ OneNote-like: delete é tombstone
    override suspend fun deletePage(page: Page) {
        pageDao.softDeletePage(page.id, System.currentTimeMillis())
    }

    private fun PageEntity.toPage() = Page(
        id = id,
        sectionId = sectionId,
        title = title,
        content = content,
        lastModifiedAt = Date(lastModifiedAt),
        position = position,
        deletedAt = deletedAt?.let { Date(it) }
    )

    private fun Page.toPageEntity() = PageEntity(
        id = id,
        sectionId = sectionId,
        title = title,
        content = content,
        createdAt = lastModifiedAt.time,
        lastModifiedAt = lastModifiedAt.time,
        position = position,
        deletedAt = deletedAt?.time
    )
}
