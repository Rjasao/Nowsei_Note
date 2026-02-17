package com.rjasao.nowsei.data.repository

import com.rjasao.nowsei.data.local.SectionDao
import com.rjasao.nowsei.data.local.entity.SectionEntity
import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SectionRepositoryImpl @Inject constructor(
    private val sectionDao: SectionDao
) : SectionRepository {

    override fun getSectionsForNotebook(notebookId: String): Flow<List<Section>> {
        return sectionDao.getSectionsForNotebook(notebookId).map { entities ->
            entities.map { it.toSection() }
        }
    }

    override suspend fun upsertSection(section: Section) {
        sectionDao.upsertSection(section.toSectionEntity())
    }

    override suspend fun saveSection(section: Section) {
        sectionDao.upsertSection(section.toSectionEntity())
    }

    // ✅ OneNote-like: delete é tombstone
    override suspend fun deleteSection(section: Section) {
        sectionDao.softDeleteSection(section.id, System.currentTimeMillis())
    }

    private fun SectionEntity.toSection() = Section(
        id = id,
        notebookId = notebookId,
        title = title,
        content = content,
        lastModifiedAt = Date(lastModifiedAt),
        deletedAt = deletedAt?.let { Date(it) }
    )

    private fun Section.toSectionEntity() = SectionEntity(
        id = id,
        notebookId = notebookId,
        title = title,
        content = content,
        // mantém seu padrão atual
        createdAt = lastModifiedAt.time,
        lastModifiedAt = lastModifiedAt.time,
        deletedAt = deletedAt?.time
    )
}
