package com.rjasao.nowsei.data.repository

import com.rjasao.nowsei.data.local.NotebookDao
import com.rjasao.nowsei.data.local.SectionDao
import com.rjasao.nowsei.data.local.entity.NotebookEntity
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepositoryImpl @Inject constructor(
    private val notebookDao: NotebookDao,
    private val sectionDao: SectionDao
) : NotebookRepository {

    override fun getAllNotebooks(): Flow<List<Notebook>> {
        return notebookDao.getAllNotebooks().map { entities ->
            entities.map { it.toNotebook() }
        }
    }

    override suspend fun getNotebookById(id: String): Notebook? {
        return notebookDao.getNotebookById(id)?.toNotebook()
    }

    override suspend fun upsertNotebook(notebook: Notebook): Boolean {
        notebookDao.upsertNotebook(notebook.toNotebookEntity())
        return true
    }

    override suspend fun saveNotebook(notebook: Notebook) {
        notebookDao.upsertNotebook(notebook.toNotebookEntity())
    }

    // ✅ OneNote-like: delete é tombstone
    override suspend fun deleteNotebook(notebook: Notebook) {
        notebookDao.softDeleteNotebook(notebook.id, System.currentTimeMillis())
    }

    override suspend fun getNotebookIdForSection(sectionId: String): String? {
        return sectionDao.getSectionById(sectionId)?.notebookId
    }

    private fun NotebookEntity.toNotebook() = Notebook(
        id = id,
        title = title,
        colorHex = colorHex,
        createdAt = Date(createdAt),
        lastModifiedAt = Date(lastModifiedAt),
        cloudId = cloudId,
        deletedAt = deletedAt?.let { Date(it) }
    )

    private fun Notebook.toNotebookEntity() = NotebookEntity(
        id = id,
        title = title,
        colorHex = colorHex,
        createdAt = createdAt.time,
        lastModifiedAt = lastModifiedAt.time,
        cloudId = cloudId,
        deletedAt = deletedAt?.time
    )
}
