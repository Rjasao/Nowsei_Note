package com.rjasao.nowsei.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["notebookId"])]
)
data class SectionEntity(
    @PrimaryKey
    val id: String,

    val notebookId: String,
    val title: String,
    val content: String,

    val createdAt: Long,
    val lastModifiedAt: Long,

    // Soft delete (NULL = ativo). NÃO use @ColumnInfo(defaultValue="NULL")
    val deletedAt: Long? = null
)
