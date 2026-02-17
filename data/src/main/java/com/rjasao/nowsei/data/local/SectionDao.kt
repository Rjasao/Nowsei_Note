package com.rjasao.nowsei.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rjasao.nowsei.data.local.entity.SectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {

    @Upsert
    suspend fun upsertSection(section: SectionEntity)

    // ✅ UI: só ativos
    @Query("SELECT * FROM sections WHERE notebookId = :notebookId AND deletedAt IS NULL ORDER BY lastModifiedAt DESC")
    fun getSectionsForNotebook(notebookId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE id = :sectionId")
    suspend fun getSectionById(sectionId: String): SectionEntity?

    // ✅ Sync: inclui deletados
    @Query("SELECT * FROM sections")
    suspend fun getAllSectionsForSync(): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE notebookId = :notebookId")
    suspend fun getSectionsForNotebookForSync(notebookId: String): List<SectionEntity>

    // ✅ Soft delete
    @Query("UPDATE sections SET deletedAt = :ts, lastModifiedAt = :ts WHERE id = :id")
    suspend fun softDeleteSection(id: String, ts: Long)

    @Query("UPDATE sections SET deletedAt = NULL WHERE id = :id")
    suspend fun undeleteSection(id: String)
}
