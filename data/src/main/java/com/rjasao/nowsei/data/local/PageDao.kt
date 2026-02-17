package com.rjasao.nowsei.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.rjasao.nowsei.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Upsert
    suspend fun upsertPage(page: PageEntity)

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    // ✅ UI: só ativos
    @Query("""
        SELECT * FROM pages 
        WHERE sectionId = :sectionId AND deletedAt IS NULL
        ORDER BY position ASC, lastModifiedAt DESC
    """)
    fun getPagesForSection(sectionId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun getPageById(pageId: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageByIdBlocking(pageId: String): PageEntity?

    // ✅ Sync: inclui deletados
    @Query("SELECT * FROM pages")
    suspend fun getAllPagesForSync(): List<PageEntity>

    @Query("SELECT * FROM pages WHERE sectionId = :sectionId")
    suspend fun getPagesForSectionForSync(sectionId: String): List<PageEntity>

    // ✅ Soft delete
    @Query("UPDATE pages SET deletedAt = :ts, lastModifiedAt = :ts WHERE id = :id")
    suspend fun softDeletePage(id: String, ts: Long)

    @Query("UPDATE pages SET deletedAt = NULL WHERE id = :id")
    suspend fun undeletePage(id: String)

    // Mantém utilitários existentes
    @Query("DELETE FROM pages WHERE sectionId = :sectionId")
    suspend fun deletePagesFromSection(sectionId: String)

    @Query("SELECT COUNT(id) FROM pages WHERE sectionId = :sectionId AND deletedAt IS NULL")
    suspend fun getPageCountInSection(sectionId: String): Int
}
