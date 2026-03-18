package com.rjasao.nowsei.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey val id: String,
    val sectionId: String,
    val title: String,
    val contentBlocksJson: String,
    val createdAt: Long,
    val updatedAt: Long
)
