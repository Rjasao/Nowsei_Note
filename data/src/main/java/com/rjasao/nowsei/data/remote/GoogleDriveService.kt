package com.rjasao.nowsei.data.remote

import android.util.Log
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.model.SyncManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class GoogleDriveService @Inject constructor(
    private val driveProvider: Provider<Drive?>,
    private val gson: Gson
) : DriveSyncDataSource {
    private val appFolderName = "NowseiApp"
    private var appFolderId: String? = null
    private var backupFolderId: String? = null

    companion object {
        private const val TAG = "GoogleDriveService"

        private const val MIME_TYPE_JSON = "application/json"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        private const val MIME_TYPE_ZIP = "application/zip"

        private const val NOTEBOOK_META_FILE = "notebook.json"
        private const val SECTION_META_FILE = "section.json"

        // ✅ Novo padrão: nome estável por ID
        private const val PAGE_STABLE_PREFIX = "page__"
        private const val PAGE_STABLE_SUFFIX = ".json"

        // ✅ Legado (título no nome) — manter compatibilidade
        private const val PAGE_LEGACY_PREFIX = "page-"

        private const val BACKUP_LATEST_ZIP = "nowsei_backup_latest.zip"
        private const val BACKUP_MANIFEST_JSON = "nowsei_manifest.json"

        // ✅ Manifest incremental (hash + updatedAt)
        private const val SYNC_MANIFEST_JSON = "sync_manifest.json"

        // ✅ Tombstones (para delete bidirecional sem ressuscitar itens)
        private const val TOMBSTONES_JSON = "tombstones.json"
    }

    /**
     * Tombstones são necessários porque, quando você remove o item do Drive (hard delete),
     * outro dispositivo não tem como saber que aquilo foi apagado.
     *
     * Armazenamos um índice simples (id -> deletedAtMillis) no root da pasta do app.
     */
    data class Tombstones(
        val notebooks: Map<String, Long> = emptyMap(),
        val sections: Map<String, Long> = emptyMap(),
        val pages: Map<String, Long> = emptyMap()
    )

    override fun isReady(): Boolean = driveProvider.get() != null

    // ---------------------------------
    // Manifest incremental
    // ---------------------------------

    /**
     * Manifest incremental (hash + updatedAt). Se não existir ainda no Drive,
     * retornamos um manifest vazio.
     */
    override suspend fun readManifest(): SyncManifest {
        val appId = getOrCreateAppFolderId()
        val file = findChildByName(appId, SYNC_MANIFEST_JSON) ?: return SyncManifest()
        return try {
            val drive = requireDrive()
            val inputStream = drive.files().get(file.id).executeMediaAsInputStream()
            val content = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).readText()
            gson.fromJson(content, SyncManifest::class.java) ?: SyncManifest()
        } catch (e: Exception) {
            Log.e(TAG, "Falha lendo manifest incremental", e)
            SyncManifest()
        }
    }

    override suspend fun writeManifest(manifest: SyncManifest) {
        val appId = getOrCreateAppFolderId()
        upsertJsonFile(appId, SYNC_MANIFEST_JSON, gson.toJson(manifest))
    }

    /**
     * Busca um arquivo/pasta filho por nome dentro de um parent.
     * Retorna null se não existir.
     */
    private suspend fun findChildByName(parentId: String, childName: String): File? {
        val drive = requireDrive()
        val safeName = childName.replace("'", "\\'")
        return withContext(Dispatchers.IO) {
            val q = "'$parentId' in parents and name='$safeName' and trashed=false"
            drive.files()
                .list()
                .setQ(q)
                .setFields("files(id,name,mimeType,modifiedTime)")
                .execute()
                .files
                ?.firstOrNull()
        }
    }


    private fun requireDrive(): Drive {
        return driveProvider.get()
            ?: throw IllegalStateException("Google Drive não autenticado (Drive == null).")
    }

    private fun sanitizeName(raw: String, maxLen: Int = 80): String {
        val cleaned = raw
            .trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
        return if (cleaned.length <= maxLen) cleaned else cleaned.substring(0, maxLen).trim()
    }

    private suspend fun getOrCreateAppFolderId(): String {
        val drive = requireDrive()
        if (appFolderId != null) return appFolderId!!

        return withContext(Dispatchers.IO) {
            val query = "mimeType='$MIME_TYPE_FOLDER' and name='$appFolderName' and trashed=false"
            val folderList = drive.files().list().setQ(query).setFields("files(id,name)").execute().files

            if (!folderList.isNullOrEmpty()) {
                appFolderId = folderList[0].id
                appFolderId!!
            } else {
                val meta = File().apply {
                    name = appFolderName
                    mimeType = MIME_TYPE_FOLDER
                }
                val newFolder = drive.files().create(meta).setFields("id").execute()
                appFolderId = newFolder.id
                appFolderId!!
            }
        }
    }

    private suspend fun getOrCreateBackupFolderId(): String {
        val drive = requireDrive()
        if (backupFolderId != null) return backupFolderId!!

        val appId = getOrCreateAppFolderId()

        return withContext(Dispatchers.IO) {
            val query = "'$appId' in parents and mimeType='$MIME_TYPE_FOLDER' and name='Backup' and trashed=false"
            val folderList = drive.files().list().setQ(query).setFields("files(id,name)").execute().files

            if (!folderList.isNullOrEmpty()) {
                backupFolderId = folderList[0].id
                backupFolderId!!
            } else {
                val meta = File().apply {
                    name = "Backup"
                    mimeType = MIME_TYPE_FOLDER
                    parents = listOf(appId)
                }
                val newFolder = drive.files().create(meta).setFields("id").execute()
                backupFolderId = newFolder.id
                backupFolderId!!
            }
        }
    }

    // ----------------------------
    // ✅ Tombstones (API)
    // ----------------------------

    override suspend fun readTombstones(): SyncTombstones {
        val appId = getOrCreateAppFolderId()
        val json = readJsonFileIfExists(appId, TOMBSTONES_JSON) ?: return SyncTombstones()
        return try {
            gson.fromJson(json, Tombstones::class.java)?.let {
                SyncTombstones(it.notebooks, it.sections, it.pages)
            } ?: SyncTombstones()
        } catch (e: Exception) {
            Log.e(TAG, "Falha lendo $TOMBSTONES_JSON (ignorando)", e)
            SyncTombstones()
        }
    }

    override suspend fun writeTombstones(tombstones: SyncTombstones) {
        val appId = getOrCreateAppFolderId()
        upsertJsonFile(
            appId,
            TOMBSTONES_JSON,
            gson.toJson(Tombstones(tombstones.notebooks, tombstones.sections, tombstones.pages))
        )
    }

    /** Atualiza o tombstone mantendo o maior timestamp. */
    fun withNotebookTombstone(current: Tombstones, id: String, deletedAtMillis: Long): Tombstones {
        val m = current.notebooks.toMutableMap()
        m[id] = maxOf(m[id] ?: Long.MIN_VALUE, deletedAtMillis)
        return current.copy(notebooks = m)
    }

    fun withSectionTombstone(current: Tombstones, id: String, deletedAtMillis: Long): Tombstones {
        val m = current.sections.toMutableMap()
        m[id] = maxOf(m[id] ?: Long.MIN_VALUE, deletedAtMillis)
        return current.copy(sections = m)
    }

    fun withPageTombstone(current: Tombstones, id: String, deletedAtMillis: Long): Tombstones {
        val m = current.pages.toMutableMap()
        m[id] = maxOf(m[id] ?: Long.MIN_VALUE, deletedAtMillis)
        return current.copy(pages = m)
    }

    // ----------------------------
    // Helpers de Drive
    // ----------------------------

    private suspend fun findFolderIdByName(parentId: String, folderName: String): String? {
        val drive = requireDrive()
        val safeName = folderName.replace("'", "\\'")

        return withContext(Dispatchers.IO) {
            val query =
                "'$parentId' in parents and mimeType='$MIME_TYPE_FOLDER' and name='$safeName' and trashed=false"
            val res = drive.files().list()
                .setQ(query)
                .setFields("files(id,name)")
                .execute()
                .files
            res.firstOrNull()?.id
        }
    }

    private suspend fun getOrCreateFolder(parentId: String, folderName: String): String {
        val drive = requireDrive()
        val name = sanitizeName(folderName)

        val existing = findFolderIdByName(parentId, name)
        if (existing != null) return existing

        return withContext(Dispatchers.IO) {
            val meta = File().apply {
                this.name = name
                mimeType = MIME_TYPE_FOLDER
                parents = listOf(parentId)
            }
            drive.files().create(meta).setFields("id").execute().id
        }
    }

    private suspend fun upsertJsonFile(parentFolderId: String, fileName: String, json: String) {
        val drive = requireDrive()
        val safeName = sanitizeName(fileName, 120).replace("'", "\\'")
        val media = ByteArrayContent.fromString(MIME_TYPE_JSON, json)

        withContext(Dispatchers.IO) {
            val query = "'$parentFolderId' in parents and name='$safeName' and trashed=false"
            val existing = drive.files().list().setQ(query).setFields("files(id,name)").execute().files

            if (!existing.isNullOrEmpty()) {
                val id = existing[0].id
                drive.files().update(id, null, media).execute()
            } else {
                val meta = File().apply {
                    name = safeName
                    parents = listOf(parentFolderId)
                }
                drive.files().create(meta, media).execute()
            }
        }
    }

    private suspend fun upsertBytesFile(parentFolderId: String, fileName: String, mimeType: String, bytes: ByteArray) {
        val drive = requireDrive()
        val safeName = sanitizeName(fileName, 120).replace("'", "\\'")
        val media = ByteArrayContent(mimeType, bytes)

        withContext(Dispatchers.IO) {
            val query = "'$parentFolderId' in parents and name='$safeName' and trashed=false"
            val existing = drive.files().list().setQ(query).setFields("files(id,name)").execute().files

            if (!existing.isNullOrEmpty()) {
                val id = existing[0].id
                drive.files().update(id, null, media).execute()
            } else {
                val meta = File().apply {
                    name = safeName
                    parents = listOf(parentFolderId)
                }
                drive.files().create(meta, media).execute()
            }
        }
    }

    private suspend fun readJsonFileIfExists(parentFolderId: String, fileName: String): String? {
        val drive = requireDrive()
        val safeName = sanitizeName(fileName, 120).replace("'", "\\'")

        return withContext(Dispatchers.IO) {
            val query = "'$parentFolderId' in parents and name='$safeName' and trashed=false"
            val files = drive.files().list().setQ(query).setFields("files(id,name)").execute().files
            val file = files.firstOrNull() ?: return@withContext null

            val inputStream = drive.files().get(file.id).executeMediaAsInputStream()
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).readText()
        }
    }

    private suspend fun listFolders(parentId: String): List<File> {
        val drive = requireDrive()
        return withContext(Dispatchers.IO) {
            val query = "'$parentId' in parents and mimeType='$MIME_TYPE_FOLDER' and trashed=false"
            drive.files().list().setQ(query).setFields("files(id,name)").execute().files ?: emptyList()
        }
    }

    private suspend fun listJsonFiles(parentId: String): List<File> {
        val drive = requireDrive()
        return withContext(Dispatchers.IO) {
            val query = "'$parentId' in parents and mimeType='$MIME_TYPE_JSON' and trashed=false"
            drive.files().list().setQ(query).setFields("files(id,name)").execute().files ?: emptyList()
        }
    }

    // ----------------------------
    // API pública (Notebooks/Sections/Pages)
    // ----------------------------

    override suspend fun getAllNotebooks(): List<Notebook> {
        val appId = getOrCreateAppFolderId()
        val notebookFolders = listFolders(appId)
        val out = mutableListOf<Notebook>()

        for (folder in notebookFolders) {
            try {
                val json = readJsonFileIfExists(folder.id, NOTEBOOK_META_FILE) ?: continue
                out.add(gson.fromJson(json, Notebook::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Falha lendo notebook em pasta '${folder.name}'", e)
            }
        }
        return out
    }

    override suspend fun saveNotebook(notebook: Notebook) {
        val appId = getOrCreateAppFolderId()
        val notebookFolderName = "${sanitizeName(notebook.title)}__${notebook.id}"
        val notebookFolderId = getOrCreateFolder(appId, notebookFolderName)
        upsertJsonFile(notebookFolderId, NOTEBOOK_META_FILE, gson.toJson(notebook))
    }

    override suspend fun getSectionsForNotebook(notebookId: String): List<Section> {
        val appId = getOrCreateAppFolderId()
        val notebookFolder = findNotebookFolderId(appId, notebookId) ?: return emptyList()

        val sectionFolders = listFolders(notebookFolder)
        val out = mutableListOf<Section>()

        for (folder in sectionFolders) {
            try {
                val json = readJsonFileIfExists(folder.id, SECTION_META_FILE) ?: continue
                val sec = gson.fromJson(json, Section::class.java)
                if (sec.notebookId == notebookId) out.add(sec)
            } catch (e: Exception) {
                Log.e(TAG, "Falha lendo section em pasta '${folder.name}'", e)
            }
        }
        return out
    }

    override suspend fun saveSection(section: Section) {
        val appId = getOrCreateAppFolderId()
        val notebookFolder = findNotebookFolderId(appId, section.notebookId)
            ?: throw IllegalStateException("Notebook remoto ausente para section ${section.id}")

        val sectionFolderName = "${sanitizeName(section.title)}__${section.id}"
        val sectionFolderId = getOrCreateFolder(notebookFolder, sectionFolderName)
        upsertJsonFile(sectionFolderId, SECTION_META_FILE, gson.toJson(section))
    }

    // ✅ Novo nome estável (por ID)
    private fun stablePageFileName(pageId: String): String = "$PAGE_STABLE_PREFIX$pageId$PAGE_STABLE_SUFFIX"

    // ✅ Remove arquivos antigos do padrão legado para evitar duplicação ao renomear
    private suspend fun deleteLegacyPageFiles(sectionFolderId: String, pageId: String) {
        val drive = requireDrive()
        withContext(Dispatchers.IO) {
            val q = "'$sectionFolderId' in parents and trashed=false"
            val files = drive.files().list().setQ(q).setFields("files(id,name)").execute().files ?: emptyList()
            val legacy = files.filter { f ->
                val n = f.name ?: return@filter false
                n.startsWith(PAGE_LEGACY_PREFIX) && n.endsWith("__${pageId}.json")
            }
            for (f in legacy) {
                try {
                    drive.files().delete(f.id).execute()
                    Log.d(TAG, "Removido legado: ${f.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao remover legado: ${f.name}", e)
                }
            }
        }
    }

    override suspend fun getPagesForSection(sectionId: String): List<Page> {
        val appId = getOrCreateAppFolderId()
        val sectionFolder = findSectionFolderId(appId, sectionId) ?: return emptyList()

        val jsonFiles = listJsonFiles(sectionFolder)
        val out = mutableListOf<Page>()
        val drive = requireDrive()

        for (f in jsonFiles) {
            val name = f.name ?: continue
            val isStable = name.startsWith(PAGE_STABLE_PREFIX) && name.endsWith(PAGE_STABLE_SUFFIX)
            val isLegacy = name.startsWith(PAGE_LEGACY_PREFIX) && name.endsWith(".json")
            if (!isStable && !isLegacy) continue
            if (name == SECTION_META_FILE) continue

            try {
                val inputStream = drive.files().get(f.id).executeMediaAsInputStream()
                val content = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).readText()
                val page = gson.fromJson(content, Page::class.java)
                if (page.sectionId == sectionId) out.add(page)
            } catch (e: Exception) {
                Log.e(TAG, "Falha lendo page '${f.name}'", e)
            }
        }
        return out
    }

    override suspend fun savePage(page: Page) {
        val appId = getOrCreateAppFolderId()
        val sectionFolder = findSectionFolderId(appId, page.sectionId)
            ?: throw IllegalStateException("Section remota ausente para page ${page.id}")

        // ✅ sempre no padrão estável por ID
        val stableName = stablePageFileName(page.id)

        // ✅ remove legado (título no nome)
        deleteLegacyPageFiles(sectionFolder, page.id)

        upsertJsonFile(sectionFolder, stableName, gson.toJson(page))
    }

    // ----------------------------
    // Backup (latest)
    // ----------------------------

    suspend fun writeLatestBackup(zipBytes: ByteArray, manifestJson: String) {
        val folderId = getOrCreateBackupFolderId()
        upsertBytesFile(folderId, BACKUP_LATEST_ZIP, MIME_TYPE_ZIP, zipBytes)
        upsertJsonFile(folderId, BACKUP_MANIFEST_JSON, manifestJson)
        Log.d(TAG, "Backup latest atualizado em NowseiApp/Backup")
    }

    // ----------------------------
    // Finders por ID
    // ----------------------------

    private suspend fun findNotebookFolderId(appFolderId: String, notebookId: String): String? {
        val folders = listFolders(appFolderId)
        return folders.firstOrNull { it.name.endsWith("__$notebookId") }?.id
    }

    private suspend fun findSectionFolderId(appFolderId: String, sectionId: String): String? {
        val notebookFolders = listFolders(appFolderId)
        for (nb in notebookFolders) {
            val sectionFolders = listFolders(nb.id)
            val found = sectionFolders.firstOrNull { it.name.endsWith("__$sectionId") }
            if (found != null) return found.id
        }
        return null
    }

    // ----------------------------
    // DELETE (Drive)
    // ----------------------------

    private suspend fun deleteById(fileOrFolderId: String) {
        val drive = requireDrive()
        withContext(Dispatchers.IO) { drive.files().delete(fileOrFolderId).execute() }
    }

    private suspend fun listChildrenIds(parentFolderId: String): List<String> {
        val drive = requireDrive()
        return withContext(Dispatchers.IO) {
            val q = "'$parentFolderId' in parents and trashed=false"
            drive.files().list()
                .setQ(q)
                .setFields("files(id,mimeType)")
                .execute()
                .files
                .mapNotNull { it.id }
        }
    }

    private suspend fun deleteFolderRecursive(folderId: String) {
        val children = listChildrenIds(folderId)
        val drive = requireDrive()
        for (childId in children) {
            val mime = withContext(Dispatchers.IO) {
                drive.files().get(childId).setFields("mimeType").execute().mimeType
            }
            if (mime == MIME_TYPE_FOLDER) deleteFolderRecursive(childId) else deleteById(childId)
        }
        deleteById(folderId)
    }

    override suspend fun deleteNotebook(notebookId: String) {
        val appId = getOrCreateAppFolderId()
        val folderId = findNotebookFolderId(appId, notebookId) ?: return
        deleteFolderRecursive(folderId)
    }

    override suspend fun deleteSection(sectionId: String) {
        val appId = getOrCreateAppFolderId()
        val folderId = findSectionFolderId(appId, sectionId) ?: return
        deleteFolderRecursive(folderId)
    }

    override suspend fun deletePage(sectionId: String, pageId: String) {
        val drive = requireDrive()
        val appId = getOrCreateAppFolderId()
        val sectionFolderId = findSectionFolderId(appId, sectionId) ?: return

        val fileName = stablePageFileName(pageId).replace("'", "\\'")

        withContext(Dispatchers.IO) {
            val q = "'$sectionFolderId' in parents and name='$fileName' and trashed=false"
            val files = drive.files().list()
                .setQ(q)
                .setFields("files(id,name)")
                .execute()
                .files

            val fileId = files.firstOrNull()?.id ?: return@withContext
            drive.files().delete(fileId).execute()
        }
    }

    /** Compatibilidade: permite chamar deletePage(Page). */
    suspend fun deletePage(page: Page) {
        deletePage(page.sectionId, page.id)
    }
}
