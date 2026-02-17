package com.rjasao.nowsei.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sectionId"])]
)
data class PageEntity(
    @PrimaryKey
    val id: String,

    val sectionId: String,
    val position: Int = 0,

    val title: String,
    val content: String,

    val createdAt: Long,
    val lastModifiedAt: Long,

    // Soft delete (NULL = ativo). NÃO use @ColumnInfo(defaultValue="NULL")
    val deletedAt: Long? = null
)
