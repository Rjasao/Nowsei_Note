package com.rjasao.nowsei.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
// 1. IMPORTAÇÕES CORRIGIDAS PARA OS DAOs
import com.rjasao.nowsei.data.local.NotebookDao
import com.rjasao.nowsei.data.local.PageDao
import com.rjasao.nowsei.data.local.SectionDao
import com.rjasao.nowsei.data.local.entity.NotebookEntity
import com.rjasao.nowsei.data.local.entity.PageEntity
import com.rjasao.nowsei.data.local.entity.SectionEntity

@Database(
    entities = [
        NotebookEntity::class,
        SectionEntity::class,
        PageEntity::class
    ],
    version = 2, // A versão está correta para a mudança de esquema
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notebookDao(): NotebookDao
    abstract fun sectionDao(): SectionDao
    abstract fun pageDao(): PageDao

}
