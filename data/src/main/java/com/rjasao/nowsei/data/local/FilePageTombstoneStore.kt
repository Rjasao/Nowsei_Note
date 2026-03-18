package com.rjasao.nowsei.data.local

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilePageTombstoneStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : PageTombstoneStore {
    private val storageFile: File by lazy {
        File(context.filesDir, "sync/page_tombstones.json").apply {
            parentFile?.mkdirs()
        }
    }

    override suspend fun readAll(): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) return@withContext emptyMap()
        runCatching {
            gson.fromJson(storageFile.readText(), TombstoneMap::class.java)?.pages.orEmpty()
        }.getOrDefault(emptyMap())
    }

    override suspend fun writeAll(tombstones: Map<String, Long>) = withContext(Dispatchers.IO) {
        storageFile.writeText(gson.toJson(TombstoneMap(pages = tombstones)))
    }

    override suspend fun markDeleted(pageId: String, deletedAt: Long) = withContext(Dispatchers.IO) {
        val current = readAll().toMutableMap()
        current[pageId] = maxOf(current[pageId] ?: Long.MIN_VALUE, deletedAt)
        writeAll(current)
    }

    override suspend fun clear(pageId: String) = withContext(Dispatchers.IO) {
        val current = readAll().toMutableMap()
        if (current.remove(pageId) != null) {
            writeAll(current)
        }
    }

    private data class TombstoneMap(
        val pages: Map<String, Long> = emptyMap()
    )
}
