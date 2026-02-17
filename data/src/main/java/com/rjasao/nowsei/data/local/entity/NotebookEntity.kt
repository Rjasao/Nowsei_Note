package com.rjasao.nowsei.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey
    val id: String,

    val title: String,
    val colorHex: String,

    val createdAt: Long,
    val lastModifiedAt: Long,

    // ID no Google Drive (pode ser null)
    val cloudId: String? = null,

    // Soft delete (NULL = ativo). NÃO use @ColumnInfo(defaultValue="NULL")
    // porque isso faz o Room exigir DEFAULT NULL e quebra a migração já existente.
    val deletedAt: Long? = null
)
