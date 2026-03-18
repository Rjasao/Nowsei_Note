package com.rjasao.nowsei.data.local

interface PageTombstoneStore {
    suspend fun readAll(): Map<String, Long>

    suspend fun writeAll(tombstones: Map<String, Long>)

    suspend fun markDeleted(pageId: String, deletedAt: Long)

    suspend fun clear(pageId: String)
}
