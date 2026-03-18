package com.rjasao.nowsei.data.local

import androidx.room.*
import com.rjasao.nowsei.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE sectionId = :sectionId ORDER BY createdAt ASC")
    fun getPagesForSection(sectionId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :id")
    fun getPageById(id: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageEntityById(id: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePageById(id: String)

    @Query("SELECT * FROM pages")
    suspend fun getAllPages(): List<PageEntity>
}
