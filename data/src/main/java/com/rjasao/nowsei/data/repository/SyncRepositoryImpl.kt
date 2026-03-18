package com.rjasao.nowsei.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rjasao.nowsei.data.local.NotebookDao
import com.rjasao.nowsei.data.local.PageDao
import com.rjasao.nowsei.data.local.PageTombstoneStore
import com.rjasao.nowsei.data.local.SectionDao
import com.rjasao.nowsei.data.local.entity.NotebookEntity
import com.rjasao.nowsei.data.local.entity.PageEntity
import com.rjasao.nowsei.data.local.entity.SectionEntity
import com.rjasao.nowsei.data.remote.DriveSyncDataSource
import com.rjasao.nowsei.data.remote.SyncTombstones
import com.rjasao.nowsei.domain.model.ContentBlock
import com.rjasao.nowsei.domain.model.ManifestEntry
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.model.SyncManifest
import com.rjasao.nowsei.domain.repository.SyncRepository
import java.security.MessageDigest
import java.util.Date
import javax.inject.Inject

class SyncRepositoryImpl @Inject constructor(
    private val notebookDao: NotebookDao,
    private val sectionDao: SectionDao,
    private val pageDao: PageDao,
    private val pageTombstoneStore: PageTombstoneStore,
    private val driveService: DriveSyncDataSource,
    private val gson: Gson
) : SyncRepository {

    override suspend fun syncAll() {
        if (!driveService.isReady()) {
            throw IllegalStateException("Google Drive não autenticado")
        }

        val localNotebooks = notebookDao.getAllNotebooksForSync().map { it.toDomain() }
        val localSections = sectionDao.getAllSectionsForSync().map { it.toDomain() }
        val localPages = pageDao.getAllPages().map { it.toDomain() }

        val remoteManifest = driveService.readManifest()
        val remoteTombstones = driveService.readTombstones()
        val localPageTombstones = pageTombstoneStore.readAll()
        val remoteNotebooks = driveService.getAllNotebooks()
        val remoteSections = remoteNotebooks.flatMap { driveService.getSectionsForNotebook(it.id) }
        val remotePages = remoteSections.flatMap { driveService.getPagesForSection(it.id) }

        val mergedTombstones = SyncTombstones(
            notebooks = mergeTimestampMaps(
                localNotebooks.mapNotNull { it.deletedAt?.time?.let { ts -> it.id to ts } }.toMap(),
                remoteTombstones.notebooks
            ),
            sections = mergeTimestampMaps(
                localSections.mapNotNull { it.deletedAt?.time?.let { ts -> it.id to ts } }.toMap(),
                remoteTombstones.sections
            ),
            pages = mergeTimestampMaps(localPageTombstones, remoteTombstones.pages)
        )

        val mergedNotebooks = mergeNotebooks(localNotebooks, remoteNotebooks)
            .mapValues { (_, notebook) -> applyNotebookTombstone(notebook, mergedTombstones.notebooks[notebook.id]) }
        val effectiveNotebookTombstones = mergedTombstones.notebooks.filter { (id, deletedAt) ->
            val notebook = mergedNotebooks[id]
            notebook == null || notebook.deletedAt?.time == deletedAt
        }
        mergedNotebooks.values
            .sortedBy { it.createdAt.time }
            .forEach { notebook ->
                notebookDao.upsertNotebook(notebook.toEntity())
                if (notebook.deletedAt == null) {
                    driveService.saveNotebook(notebook)
                } else {
                    driveService.deleteNotebook(notebook.id)
                }
            }

        val mergedSections = mergeSections(localSections, remoteSections)
            .mapValues { (_, section) -> applySectionTombstone(section, mergedTombstones.sections[section.id]) }
            .filterValues { section -> mergedNotebooks[section.notebookId]?.deletedAt == null }
        val effectiveSectionTombstones = mergedTombstones.sections.filter { (id, deletedAt) ->
            val section = mergedSections[id]
            section == null || section.deletedAt?.time == deletedAt
        }
        mergedSections.values
            .sortedBy { it.lastModifiedAt.time }
            .forEach { section ->
                sectionDao.upsertSection(section.toEntity())
                if (section.deletedAt == null) {
                    driveService.saveSection(section)
                } else {
                    driveService.deleteSection(section.id)
                }
            }

        val mergedPages = mergePages(localPages, remotePages, remoteManifest)
            .filterValues { page -> mergedSections[page.sectionId]?.deletedAt == null }
            .filterValues { page ->
                val deletedAt = mergedTombstones.pages[page.id]
                deletedAt == null || page.updatedAt > deletedAt
            }
        val effectivePageTombstones = mergedTombstones.pages.filterKeys { it !in mergedPages.keys }
        mergedPages.values
            .sortedBy { it.createdAt }
            .forEach { page ->
                pageDao.upsertPage(page.toEntity())
                pageTombstoneStore.clear(page.id)
                driveService.savePage(page)
            }

        effectivePageTombstones.forEach { (pageId, deletedAt) ->
            val localPage = pageDao.getPageEntityById(pageId)
            if (localPage != null && localPage.updatedAt <= deletedAt) {
                pageDao.deletePageById(pageId)
            }
        }

        val activePageIds = mergedPages.keys
        effectivePageTombstones
            .filterKeys { it !in activePageIds }
            .forEach { (pageId, deletedAt) ->
                pageTombstoneStore.markDeleted(pageId, deletedAt)
                val remotePage = remotePages.firstOrNull { it.id == pageId }
                driveService.deletePage(remotePage?.sectionId ?: "", pageId)
            }

        driveService.writeManifest(
            SyncManifest(
                pages = mergedPages.values.associate { page ->
                    page.id to ManifestEntry(
                        hash = generateHash(page),
                        updatedAt = page.updatedAt
                    )
                }
            )
        )
        driveService.writeTombstones(
            SyncTombstones(
                notebooks = effectiveNotebookTombstones,
                sections = effectiveSectionTombstones,
                pages = effectivePageTombstones
            )
        )
    }

    private fun mergeTimestampMaps(
        local: Map<String, Long>,
        remote: Map<String, Long>
    ): Map<String, Long> {
        return (local.keys + remote.keys).associateWith { id ->
            maxOf(local[id] ?: Long.MIN_VALUE, remote[id] ?: Long.MIN_VALUE)
        }.filterValues { it != Long.MIN_VALUE }
    }

    private fun applyNotebookTombstone(notebook: Notebook, deletedAt: Long?): Notebook {
        if (deletedAt == null || notebook.lastModifiedAt.time > deletedAt) return notebook
        return notebook.copy(
            lastModifiedAt = Date(deletedAt),
            deletedAt = Date(deletedAt)
        )
    }

    private fun applySectionTombstone(section: Section, deletedAt: Long?): Section {
        if (deletedAt == null || section.lastModifiedAt.time > deletedAt) return section
        return section.copy(
            lastModifiedAt = Date(deletedAt),
            deletedAt = Date(deletedAt)
        )
    }

    private fun mergeNotebooks(
        local: List<Notebook>,
        remote: List<Notebook>
    ): Map<String, Notebook> {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        return (localById.keys + remoteById.keys).associateWith { id ->
            val localItem = localById[id]
            val remoteItem = remoteById[id]
            when {
                localItem == null -> remoteItem!!
                remoteItem == null -> localItem
                localItem.lastModifiedAt.time >= remoteItem.lastModifiedAt.time -> localItem
                else -> remoteItem
            }
        }
    }

    private fun mergeSections(
        local: List<Section>,
        remote: List<Section>
    ): Map<String, Section> {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        return (localById.keys + remoteById.keys).associateWith { id ->
            val localItem = localById[id]
            val remoteItem = remoteById[id]
            when {
                localItem == null -> remoteItem!!
                remoteItem == null -> localItem
                localItem.lastModifiedAt.time >= remoteItem.lastModifiedAt.time -> localItem
                else -> remoteItem
            }
        }
    }

    private fun mergePages(
        local: List<Page>,
        remote: List<Page>,
        remoteManifest: SyncManifest
    ): Map<String, Page> {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        return (localById.keys + remoteById.keys).associateWith { id ->
            val localItem = localById[id]
            val remoteItem = remoteById[id]
            when {
                localItem == null -> remoteItem!!
                remoteItem == null -> localItem
                else -> resolvePageConflict(localItem, remoteItem, remoteManifest.pages[id])
            }
        }
    }

    private fun resolvePageConflict(
        local: Page,
        remote: Page,
        remoteEntry: ManifestEntry?
    ): Page {
        if (local.updatedAt != remote.updatedAt) {
            return if (local.updatedAt >= remote.updatedAt) local else remote
        }

        val localHash = generateHash(local)
        val remoteHash = generateHash(remote)
        if (localHash == remoteHash) {
            return local
        }

        return if (remoteEntry?.hash == remoteHash) remote else local
    }

    private fun generateHash(page: Page): String {
        val json = gson.toJson(page)
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(json.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun NotebookEntity.toDomain(): Notebook {
        return Notebook(
            id = id,
            title = title,
            colorHex = colorHex,
            createdAt = Date(createdAt),
            lastModifiedAt = Date(lastModifiedAt),
            cloudId = cloudId,
            deletedAt = deletedAt?.let(::Date)
        )
    }

    private fun Notebook.toEntity(): NotebookEntity {
        return NotebookEntity(
            id = id,
            title = title,
            colorHex = colorHex,
            createdAt = createdAt.time,
            lastModifiedAt = lastModifiedAt.time,
            cloudId = cloudId,
            deletedAt = deletedAt?.time
        )
    }

    private fun SectionEntity.toDomain(): Section {
        return Section(
            id = id,
            notebookId = notebookId,
            title = title,
            content = content,
            lastModifiedAt = Date(lastModifiedAt),
            deletedAt = deletedAt?.let(::Date)
        )
    }

    private fun Section.toEntity(): SectionEntity {
        return SectionEntity(
            id = id,
            notebookId = notebookId,
            title = title,
            content = content,
            createdAt = lastModifiedAt.time,
            lastModifiedAt = lastModifiedAt.time,
            deletedAt = deletedAt?.time
        )
    }

    private fun PageEntity.toDomain(): Page {
        val type = object : TypeToken<List<ContentBlock>>() {}.type
        val blocks: List<ContentBlock> =
            if (contentBlocksJson.isBlank()) emptyList()
            else gson.fromJson(contentBlocksJson, type)

        return Page(
            id = id,
            sectionId = sectionId,
            title = title,
            contentBlocks = blocks,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Page.toEntity(): PageEntity {
        return PageEntity(
            id = id,
            sectionId = sectionId,
            title = title,
            contentBlocksJson = gson.toJson(contentBlocks),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
