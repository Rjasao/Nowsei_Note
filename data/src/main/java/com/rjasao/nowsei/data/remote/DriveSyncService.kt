package com.rjasao.nowsei.data.remote

import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.model.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed class SyncResult {
    data class Success(val cloudId: String) : SyncResult()
    data class Failure(val exception: Exception) : SyncResult()
    object NotLoggedIn : SyncResult()
}

class DriveSyncService @Inject constructor(
    private val drive: Drive?,
    private val gson: Gson
) {
    suspend fun uploadNotebook(notebook: Notebook): SyncResult {
        if (drive == null) return SyncResult.NotLoggedIn

        return withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(notebook)
                val content = ByteArrayContent.fromString("application/json", json)

                val fileMetadata = File().apply {
                    name = "notebook_${notebook.id}.json"
                }

                val request = if (notebook.cloudId.isNullOrBlank()) {
                    fileMetadata.parents = listOf("appDataFolder")
                    drive.files().create(fileMetadata, content).setFields("id")
                } else {
                    drive.files().update(notebook.cloudId, fileMetadata, content).setFields("id")
                }

                val file = request.execute()
                val id = file?.id

                if (id.isNullOrBlank()) SyncResult.Failure(Exception("ID do Drive vazio."))
                else SyncResult.Success(id)
            } catch (e: IOException) {
                SyncResult.Failure(e)
            }
        }
    }

    // ✅ NOVO: Section sem cloudId -> upsert por NOME no appDataFolder
    suspend fun uploadSection(section: Section): SyncResult {
        if (drive == null) return SyncResult.NotLoggedIn
        val name = "section_${section.id}.json"
        return upsertJsonByName(name, gson.toJson(section))
    }

    // ✅ NOVO: Page sem cloudId -> upsert por NOME no appDataFolder
    suspend fun uploadPage(page: Page): SyncResult {
        if (drive == null) return SyncResult.NotLoggedIn
        val name = "page_${page.id}.json"
        return upsertJsonByName(name, gson.toJson(page))
    }

    private suspend fun upsertJsonByName(fileName: String, json: String): SyncResult {
        if (drive == null) return SyncResult.NotLoggedIn

        return withContext(Dispatchers.IO) {
            try {
                val content = ByteArrayContent.fromString("application/json", json)

                // procura arquivo existente pelo nome dentro do appDataFolder
                val existingId = findAppDataFileIdByName(fileName)

                val meta = File().apply { name = fileName }

                val request = if (existingId == null) {
                    meta.parents = listOf("appDataFolder")
                    drive.files().create(meta, content).setFields("id")
                } else {
                    drive.files().update(existingId, meta, content).setFields("id")
                }

                val createdOrUpdated = request.execute()
                val id = createdOrUpdated?.id

                if (id.isNullOrBlank()) SyncResult.Failure(Exception("ID do Drive vazio."))
                else SyncResult.Success(id)
            } catch (e: IOException) {
                SyncResult.Failure(e)
            }
        }
    }

    private fun findAppDataFileIdByName(fileName: String): String? {
        // spaces=appDataFolder e parents='appDataFolder'
        val q = "name='${fileName.replace("'", "\\'")}' and 'appDataFolder' in parents and trashed=false"
        val result = drive!!.files()
            .list()
            .setSpaces("appDataFolder")
            .setQ(q)
            .setFields("files(id,name)")
            .execute()

        return result.files?.firstOrNull()?.id
    }
}
