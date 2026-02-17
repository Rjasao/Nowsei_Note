package com.rjasao.nowsei.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rjasao.nowsei.data.local.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {

    // ✅ UI: só ativos
    @Query("SELECT * FROM notebooks WHERE deletedAt IS NULL ORDER BY lastModifiedAt DESC")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    // ✅ Sync: inclui deletados (tombstones)
    @Query("SELECT * FROM notebooks")
    suspend fun getAllNotebooksForSync(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: String): NotebookEntity?

    @Upsert
    suspend fun upsertNotebook(notebook: NotebookEntity)

    // ✅ Soft delete (tombstone)
    @Query("UPDATE notebooks SET deletedAt = :ts, lastModifiedAt = :ts WHERE id = :id")
    suspend fun softDeleteNotebook(id: String, ts: Long)

    // ✅ Undelete (se merge trouxer versão ativa mais recente)
    @Query("UPDATE notebooks SET deletedAt = NULL WHERE id = :id")
    suspend fun undeleteNotebook(id: String)
}
