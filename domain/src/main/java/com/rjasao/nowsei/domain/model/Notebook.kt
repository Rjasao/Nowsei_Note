package com.rjasao.nowsei.domain.model

import java.io.Serializable
import java.util.Date

data class Notebook(
    val id: String,
    val title: String,
    val colorHex: String,
    val createdAt: Date,
    val lastModifiedAt: Date,
    val cloudId: String? = null,

    // ✅ Tombstone (delete “profissional” e recuperável)
    val deletedAt: Date? = null
) : Serializable
