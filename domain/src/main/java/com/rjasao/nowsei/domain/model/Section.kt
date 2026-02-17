package com.rjasao.nowsei.domain.model

import java.util.Date
import java.util.UUID

data class Section(
    val id: String = UUID.randomUUID().toString(),
    val notebookId: String,
    val title: String,
    val content: String,
    val lastModifiedAt: Date,

    // ✅ Tombstone
    val deletedAt: Date? = null
)
