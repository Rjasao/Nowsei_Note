package com.rjasao.nowsei.domain.model

data class SyncManifest(
    val pages: Map<String, ManifestEntry> = emptyMap()
)

data class ManifestEntry(
    val hash: String,
    val updatedAt: Long
)
