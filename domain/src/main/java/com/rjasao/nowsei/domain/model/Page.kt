package com.rjasao.nowsei.domain.model

import java.util.Date
import java.util.UUID

data class Page(
    val id: String = UUID.randomUUID().toString(),
    val sectionId: String,
    val title: String,
    val content: String,
    val lastModifiedAt: Date,

    val position: Int = -1,

    // ✅ Tombstone
    val deletedAt: Date? = null
)
