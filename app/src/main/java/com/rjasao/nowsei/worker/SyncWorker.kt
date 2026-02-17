package com.rjasao.nowsei.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.rjasao.nowsei.data.local.NotebookDao
import com.rjasao.nowsei.data.local.PageDao
import com.rjasao.nowsei.data.local.SectionDao
import com.rjasao.nowsei.data.local.entity.NotebookEntity
import com.rjasao.nowsei.data.local.entity.PageEntity
import com.rjasao.nowsei.data.local.entity.SectionEntity
import com.rjasao.nowsei.data.remote.GoogleDriveService
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.model.Section
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveService: GoogleDriveService,

    private val notebookDao: NotebookDao,
    private val sectionDao: SectionDao,
    private val pageDao: PageDao,

    private val gson: Gson
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORKER_NAME = "SyncWorker"
        private const val TAG = "SyncWorker"
        private const val VISUAL_TICKS = 20

        const val PROGRESS_STEP = "progress_step"
        const val PROGRESS_TOTAL_STEPS = "progress_total_steps"
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_MESSAGE = "progress_message"
        const val PROGRESS_FRACTION = "progress_fraction"

        private const val TOTAL_STEPS = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!driveService.isReady()) {
                return@withContext Result.failure(
                    Data.Builder().putString(PROGRESS_MESSAGE, "Google Drive não autenticado.").build()
                )
            }

            setProgressCompat(1, 0, 1, 0f, "Lendo dados locais...")
            val localNotebooks = notebookDao.getAllNotebooksForSync()
            val localSections = sectionDao.getAllSectionsForSync()
            val localPages = pageDao.getAllPagesForSync()

            setProgressCompat(1, 0, 1, 0f, "Lendo dados do Google Drive...")
            val driveNotebooks = driveService.getAllNotebooks()
            val driveSections = mutableListOf<Section>()
            val drivePages = mutableListOf<Page>()
            for (nb in driveNotebooks) {
                val secs = driveService.getSectionsForNotebook(nb.id)
                driveSections += secs
                for (sec in secs) {
                    drivePages += driveService.getPagesForSection(sec.id)
                }
            }

            // ✅ Tombstones do Drive (para deletados não ressuscitarem)
            val driveTombstones = driveService.readTombstones()

            // -------------------------
            // ETAPA 1: MERGE NOTEBOOKS
            // -------------------------
            setProgressCompat(1, 0, 1, 0f, "Mesclando cadernos...")
            var mergedNotebooks = mergeNotebooks(
                local = localNotebooks.map { it.toDomain() },
                remote = driveNotebooks
            )
            mergedNotebooks = applyNotebookTombstones(mergedNotebooks, driveTombstones)

            val deletedNotebookIds = mergedNotebooks.filter { it.deletedAt != null }.map { it.id }.toSet()

            // -------------------------
            // ETAPA 2: MERGE SECTIONS
            // -------------------------
            setProgressCompat(2, 0, 1, 0f, "Mesclando seções...")
            var mergedSections = mergeSections(
                local = localSections.map { it.toDomain() },
                remote = driveSections
            ).map { sec ->
                if (deletedNotebookIds.contains(sec.notebookId)) {
                    val ts = maxOf(sec.lastModifiedAt.time, sec.deletedAt?.time ?: 0L)
                    sec.copy(deletedAt = Date(ts))
                } else sec
            }
            mergedSections = applySectionTombstones(mergedSections, driveTombstones)

            val deletedSectionIds = mergedSections.filter { it.deletedAt != null }.map { it.id }.toSet()

            // -------------------------
            // ETAPA 3: MERGE PAGES
            // -------------------------
            setProgressCompat(3, 0, 1, 0f, "Mesclando páginas...")
            var mergedPages = mergePages(
                local = localPages.map { it.toDomain() },
                remote = drivePages
            ).map { p ->
                if (deletedSectionIds.contains(p.sectionId)) {
                    val ts = maxOf(p.lastModifiedAt.time, p.deletedAt?.time ?: 0L)
                    p.copy(deletedAt = Date(ts))
                } else p
            }
            mergedPages = applyPageTombstones(mergedPages, driveTombstones)

            // -------------------------
            // APLICA NO LOCAL (offline-first)
            // -------------------------
            setProgressCompat(1, 0, 1, 0f, "Aplicando no celular...")
            applyToLocal(mergedNotebooks, mergedSections, mergedPages)

            // -------------------------
            // APLICA NO DRIVE + grava tombstones
            // -------------------------
            syncToDrive(mergedNotebooks, mergedSections, mergedPages)

            // -------------------------
            // BACKUP (latest)
            // -------------------------
            setProgressCompat(3, 1, 1, 1f, "Gerando backup...")
            writeBackupLatest(mergedNotebooks, mergedSections, mergedPages)

            setProgressCompat(3, 1, 1, 1f, "Sincronização concluída.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Falha na sincronização", e)
            Result.failure(
                Data.Builder().putString(PROGRESS_MESSAGE, e.message ?: "Falha na sincronização.").build()
            )
        }
    }

    // -----------------------------------------
    // Merge (mais recente vence) considerando delete
    // -----------------------------------------

    private fun eventTime(lastModifiedAt: Long, deletedAt: Long?): Long =
        max(lastModifiedAt, deletedAt ?: Long.MIN_VALUE)

    private fun mergeNotebooks(local: List<Notebook>, remote: List<Notebook>): List<Notebook> {
        val map = LinkedHashMap<String, Notebook>()
        for (n in local) map[n.id] = n
        for (r in remote) {
            val l = map[r.id]
            map[r.id] = if (l == null) r else {
                val lt = eventTime(l.lastModifiedAt.time, l.deletedAt?.time)
                val rt = eventTime(r.lastModifiedAt.time, r.deletedAt?.time)
                if (rt >= lt) r else l
            }
        }
        return map.values.toList()
    }

    private fun mergeSections(local: List<Section>, remote: List<Section>): List<Section> {
        val map = LinkedHashMap<String, Section>()
        for (s in local) map[s.id] = s
        for (r in remote) {
            val l = map[r.id]
            map[r.id] = if (l == null) r else {
                val lt = eventTime(l.lastModifiedAt.time, l.deletedAt?.time)
                val rt = eventTime(r.lastModifiedAt.time, r.deletedAt?.time)
                if (rt >= lt) r else l
            }
        }
        return map.values.toList()
    }

    private fun mergePages(local: List<Page>, remote: List<Page>): List<Page> {
        val map = LinkedHashMap<String, Page>()
        for (p in local) map[p.id] = p
        for (r in remote) {
            val l = map[r.id]
            map[r.id] = if (l == null) r else {
                val lt = eventTime(l.lastModifiedAt.time, l.deletedAt?.time)
                val rt = eventTime(r.lastModifiedAt.time, r.deletedAt?.time)
                if (rt >= lt) r else l
            }
        }
        return map.values.toList()
    }

    private fun applyNotebookTombstones(
        list: List<Notebook>,
        t: GoogleDriveService.Tombstones
    ): List<Notebook> {
        if (t.notebooks.isEmpty()) return list
        return list.map { n ->
            val ts = t.notebooks[n.id] ?: return@map n
            val cur = eventTime(n.lastModifiedAt.time, n.deletedAt?.time)
            if (ts >= cur) n.copy(deletedAt = Date(ts)) else n
        }
    }

    private fun applySectionTombstones(
        list: List<Section>,
        t: GoogleDriveService.Tombstones
    ): List<Section> {
        if (t.sections.isEmpty()) return list
        return list.map { s ->
            val ts = t.sections[s.id] ?: return@map s
            val cur = eventTime(s.lastModifiedAt.time, s.deletedAt?.time)
            if (ts >= cur) s.copy(deletedAt = Date(ts)) else s
        }
    }

    private fun applyPageTombstones(
        list: List<Page>,
        t: GoogleDriveService.Tombstones
    ): List<Page> {
        if (t.pages.isEmpty()) return list
        return list.map { p ->
            val ts = t.pages[p.id] ?: return@map p
            val cur = eventTime(p.lastModifiedAt.time, p.deletedAt?.time)
            if (ts >= cur) p.copy(deletedAt = Date(ts)) else p
        }
    }

    // -----------------------------------------
    // Apply local
    // -----------------------------------------

    private suspend fun applyToLocal(
        notebooks: List<Notebook>,
        sections: List<Section>,
        pages: List<Page>
    ) {
        for (n in notebooks) {
            val delTs = n.deletedAt?.time
            if (delTs != null) notebookDao.softDeleteNotebook(n.id, delTs)
            else {
                notebookDao.upsertNotebook(n.toEntity())
                notebookDao.undeleteNotebook(n.id)
            }
        }

        for (s in sections) {
            val delTs = s.deletedAt?.time
            if (delTs != null) sectionDao.softDeleteSection(s.id, delTs)
            else {
                sectionDao.upsertSection(s.toEntity())
                sectionDao.undeleteSection(s.id)
            }
        }

        for (p in pages) {
            val delTs = p.deletedAt?.time
            if (delTs != null) pageDao.softDeletePage(p.id, delTs)
            else {
                pageDao.upsertPage(p.toEntity())
                pageDao.undeletePage(p.id)
            }
        }
    }

    // -----------------------------------------
    // Apply drive + tombstones
    // -----------------------------------------

    private suspend fun syncToDrive(
        notebooks: List<Notebook>,
        sections: List<Section>,
        pages: List<Page>
    ) {
        var tombstones = driveService.readTombstones()

        syncStepItems(step = 1, total = notebooks.size, messageBase = "Sincronizando cadernos") { idx ->
            val n = notebooks[idx]
            val del = n.deletedAt
            if (del != null) {
                tombstones = driveService.withNotebookTombstone(tombstones, n.id, del.time)
                driveService.deleteNotebook(n.id)
            } else {
                driveService.saveNotebook(n)
            }
        }

        syncStepItems(step = 2, total = sections.size, messageBase = "Sincronizando seções") { idx ->
            val s = sections[idx]
            val del = s.deletedAt
            if (del != null) {
                tombstones = driveService.withSectionTombstone(tombstones, s.id, del.time)
                driveService.deleteSection(s.id)
            } else {
                driveService.saveSection(s)
            }
        }

        syncStepItems(step = 3, total = pages.size, messageBase = "Sincronizando páginas") { idx ->
            val p = pages[idx]
            val del = p.deletedAt
            if (del != null) {
                tombstones = driveService.withPageTombstone(tombstones, p.id, del.time)
                driveService.deletePage(p.sectionId, p.id)
            } else {
                driveService.savePage(p)
            }
        }

        driveService.writeTombstones(tombstones)
        tickToFull(step = 3, message = "Finalizando...")
    }

    private suspend fun syncStepItems(step: Int, total: Int, messageBase: String, action: suspend (Int) -> Unit) {
        val safeTotal = max(1, total)
        setProgressCompat(step, 0, safeTotal, 0f, "$messageBase...")
        if (total <= 0) {
            tickToFull(step, "Sem itens para sincronizar.")
            return
        }
        for (i in 0 until total) {
            if (isStopped) throw IllegalStateException("Sincronização interrompida.")
            val cur = i + 1
            val frac = cur.toFloat() / safeTotal.toFloat()
            setProgressCompat(step, cur, safeTotal, frac, "$messageBase ($cur/$safeTotal)...")
            action(i)
        }
        tickToFull(step, "Finalizando...")
    }

    // -----------------------------------------
    // Backup latest
    // -----------------------------------------

    private suspend fun writeBackupLatest(notebooks: List<Notebook>, sections: List<Section>, pages: List<Page>) {
        val zipBytes = buildZipBackup(notebooks, sections, pages)
        val manifest = buildManifestJson(notebooks, sections, pages)
        driveService.writeLatestBackup(zipBytes, manifest)
    }

    private fun buildZipBackup(notebooks: List<Notebook>, sections: List<Section>, pages: List<Page>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(name: String, json: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("notebooks.json", gson.toJson(notebooks))
            put("sections.json", gson.toJson(sections))
            put("pages.json", gson.toJson(pages))
        }
        return bos.toByteArray()
    }

    private fun buildManifestJson(notebooks: List<Notebook>, sections: List<Section>, pages: List<Page>): String {
        val now = System.currentTimeMillis()
        val obj = mapOf(
            "generatedAt" to now,
            "counts" to mapOf(
                "notebooks" to notebooks.size,
                "sections" to sections.size,
                "pages" to pages.size
            )
        )
        return gson.toJson(obj)
    }

    // -----------------------------------------
    // Mappers (sem createdAt no Domain de Section/Page)
    // -----------------------------------------

    private fun NotebookEntity.toDomain(): Notebook = Notebook(
        id = id,
        title = title,
        colorHex = colorHex,
        createdAt = Date(createdAt),
        lastModifiedAt = Date(lastModifiedAt),
        cloudId = cloudId,
        deletedAt = deletedAt?.let { Date(it) }
    )

    private fun Notebook.toEntity(): NotebookEntity = NotebookEntity(
        id = id,
        title = title,
        colorHex = colorHex,
        createdAt = createdAt.time,
        lastModifiedAt = lastModifiedAt.time,
        cloudId = cloudId,
        deletedAt = deletedAt?.time
    )

    private fun SectionEntity.toDomain(): Section = Section(
        id = id,
        notebookId = notebookId,
        title = title,
        content = content,
        lastModifiedAt = Date(lastModifiedAt),
        deletedAt = deletedAt?.let { Date(it) }
    )

    private fun Section.toEntity(): SectionEntity = SectionEntity(
        id = id,
        notebookId = notebookId,
        title = title,
        content = content,
        createdAt = lastModifiedAt.time,      // mantém coluna sem depender de Domain.createdAt
        lastModifiedAt = lastModifiedAt.time,
        deletedAt = deletedAt?.time
    )

    private fun PageEntity.toDomain(): Page = Page(
        id = id,
        sectionId = sectionId,
        title = title,
        content = content,
        lastModifiedAt = Date(lastModifiedAt),
        position = position,
        deletedAt = deletedAt?.let { Date(it) }
    )

    private fun Page.toEntity(): PageEntity = PageEntity(
        id = id,
        sectionId = sectionId,
        title = title,
        content = content,
        createdAt = lastModifiedAt.time,      // mantém coluna sem depender de Domain.createdAt
        lastModifiedAt = lastModifiedAt.time,
        position = position,
        deletedAt = deletedAt?.time
    )

    // -----------------------------------------
    // Animação
    // -----------------------------------------

    private suspend fun tickToFull(step: Int, message: String) {
        for (i in 1..VISUAL_TICKS) {
            val f = i.toFloat() / VISUAL_TICKS.toFloat()
            setProgressCompat(step, i, VISUAL_TICKS, f, message)
            delay(40)
        }
    }

    private suspend fun setProgressCompat(step: Int, current: Int, total: Int, fraction: Float, message: String) {
        val safeTotal = max(1, total)
        val safeCurrent = current.coerceIn(0, safeTotal)
        val safeFraction = fraction.coerceIn(0f, 1f)

        val data = Data.Builder()
            .putInt(PROGRESS_STEP, step)
            .putInt(PROGRESS_TOTAL_STEPS, TOTAL_STEPS)
            .putInt(PROGRESS_CURRENT, safeCurrent)
            .putInt(PROGRESS_TOTAL, safeTotal)
            .putFloat(PROGRESS_FRACTION, safeFraction)
            .putString(PROGRESS_MESSAGE, message)
            .build()

        setProgress(data)
    }
}
