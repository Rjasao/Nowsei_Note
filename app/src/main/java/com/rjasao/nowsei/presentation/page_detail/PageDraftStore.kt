package com.rjasao.nowsei.presentation.page_detail

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PageDraftSnapshot(
    val pageId: String,
    val title: String,
    val html: String,
    val updatedAt: Long
)

@Singleton
class PageDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val draftsDir: File by lazy {
        File(context.filesDir, "page_drafts").apply { mkdirs() }
    }

    suspend fun save(snapshot: PageDraftSnapshot) = withContext(Dispatchers.IO) {
        val file = fileFor(snapshot.pageId)
        file.writeText(gson.toJson(snapshot))
    }

    suspend fun load(pageId: String): PageDraftSnapshot? = withContext(Dispatchers.IO) {
        val file = fileFor(pageId)
        if (!file.exists()) return@withContext null
        runCatching {
            gson.fromJson(file.readText(), PageDraftSnapshot::class.java)
        }.getOrNull()
    }

    suspend fun clear(pageId: String) = withContext(Dispatchers.IO) {
        fileFor(pageId).delete()
    }

    private fun fileFor(pageId: String): File {
        return File(draftsDir, "page_$pageId.json")
    }
}

