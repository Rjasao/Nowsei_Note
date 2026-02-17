package com.rjasao.nowsei.backup

import android.content.Context
import com.google.gson.Gson
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.model.Section
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class BackupManifest(
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val notebooks: Int,
    val sections: Int,
    val pages: Int,
    val sha256: String
)

class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    fun createSnapshotZip(
        notebooks: List<Notebook>,
        sections: List<Section>,
        pages: List<Page>
    ): File {
        val dir = File(context.cacheDir, "nowsei_backup").apply { mkdirs() }
        val zipFile = File(dir, "nowsei_backup_latest.zip")

        // monta JSONs
        val notebooksJson = gson.toJson(notebooks)
        val sectionsJson = gson.toJson(sections)
        val pagesJson = gson.toJson(pages)

        // hash do conteúdo (simples e útil)
        val sha = sha256(notebooksJson + sectionsJson + pagesJson)

        val manifest = BackupManifest(
            createdAt = System.currentTimeMillis(),
            notebooks = notebooks.size,
            sections = sections.size,
            pages = pages.size,
            sha256 = sha
        )
        val manifestJson = gson.toJson(manifest)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            putText(zos, "manifest.json", manifestJson)
            putText(zos, "notebooks.json", notebooksJson)
            putText(zos, "sections.json", sectionsJson)
            putText(zos, "pages.json", pagesJson)
        }

        return zipFile
    }

    private fun putText(zos: ZipOutputStream, name: String, text: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
