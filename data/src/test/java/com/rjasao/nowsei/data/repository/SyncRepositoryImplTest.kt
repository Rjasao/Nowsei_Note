package com.rjasao.nowsei.data.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.rjasao.nowsei.data.json.ContentBlockAdapter
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
import com.rjasao.nowsei.domain.model.Notebook
import com.rjasao.nowsei.domain.model.Page
import com.rjasao.nowsei.domain.model.Section
import com.rjasao.nowsei.domain.model.SyncManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SyncRepositoryImplTest {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ContentBlock::class.java, ContentBlockAdapter())
        .create()

    @Test
    fun syncAll_mergesRemotePagesIntoLocalAndManifest() = runBlocking {
        val notebook = notebook("n1", updatedAt = 100)
        val section = section("s1", notebook.id, updatedAt = 100)
        val localPage = page("p-local", section.id, updatedAt = 100)
        val remotePage = page("p-remote", section.id, updatedAt = 200, text = "remoto")

        val notebookDao = FakeNotebookDao(mutableMapOf(notebook.id to notebook.toEntity()))
        val sectionDao = FakeSectionDao(mutableMapOf(section.id to section.toEntity()))
        val pageDao = FakePageDao(mutableMapOf(localPage.id to localPage.toEntity(gson)))
        val drive = FakeDriveSyncDataSource(
            notebooks = mutableMapOf(notebook.id to notebook),
            sections = mutableMapOf(section.id to section),
            pages = mutableMapOf(remotePage.id to remotePage)
        )
        val pageTombstones = FakePageTombstoneStore()

        val repository = SyncRepositoryImpl(notebookDao, sectionDao, pageDao, pageTombstones, drive, gson)

        repository.syncAll()

        assertEquals(setOf("p-local", "p-remote"), pageDao.pages.keys)
        assertEquals(setOf("p-local", "p-remote"), drive.pages.keys)
        assertEquals(setOf("p-local", "p-remote"), drive.manifest.pages.keys)
        assertEquals("remoto", pageDao.pages.getValue("p-remote").toDomain(gson).textBlockText())
    }

    @Test
    fun syncAll_uploadsNotebookAndSectionBeforePage() = runBlocking {
        val notebook = notebook("n1", updatedAt = 100)
        val section = section("s1", notebook.id, updatedAt = 100)
        val localPage = page("p1", section.id, updatedAt = 100)
        val drive = FakeDriveSyncDataSource()
        val pageTombstones = FakePageTombstoneStore()

        val repository = SyncRepositoryImpl(
            FakeNotebookDao(mutableMapOf(notebook.id to notebook.toEntity())),
            FakeSectionDao(mutableMapOf(section.id to section.toEntity())),
            FakePageDao(mutableMapOf(localPage.id to localPage.toEntity(gson))),
            pageTombstones,
            drive,
            gson
        )

        repository.syncAll()

        assertTrue(drive.calls.indexOf("saveNotebook:n1") < drive.calls.indexOf("saveSection:s1"))
        assertTrue(drive.calls.indexOf("saveSection:s1") < drive.calls.indexOf("savePage:p1"))
    }

    @Test
    fun syncAll_propagatesPageTombstoneAndDeletesRemoteCopy() = runBlocking {
        val notebook = notebook("n1", updatedAt = 100)
        val section = section("s1", notebook.id, updatedAt = 100)
        val deletedAt = 300L
        val remotePage = page("p1", section.id, updatedAt = 100)
        val drive = FakeDriveSyncDataSource(
            notebooks = mutableMapOf(notebook.id to notebook),
            sections = mutableMapOf(section.id to section),
            pages = mutableMapOf(remotePage.id to remotePage)
        )
        val pageTombstones = FakePageTombstoneStore(mutableMapOf("p1" to deletedAt))

        val repository = SyncRepositoryImpl(
            FakeNotebookDao(mutableMapOf(notebook.id to notebook.toEntity())),
            FakeSectionDao(mutableMapOf(section.id to section.toEntity())),
            FakePageDao(),
            pageTombstones,
            drive,
            gson
        )

        repository.syncAll()

        assertTrue("deletePage:s1:p1" in drive.calls)
        assertEquals(deletedAt, drive.tombstones.pages["p1"])
        assertTrue("p1" !in drive.manifest.pages)
    }

    private fun notebook(id: String, updatedAt: Long): Notebook {
        return Notebook(
            id = id,
            title = "Notebook $id",
            colorHex = "#ffffff",
            createdAt = Date(updatedAt),
            lastModifiedAt = Date(updatedAt)
        )
    }

    private fun section(id: String, notebookId: String, updatedAt: Long): Section {
        return Section(
            id = id,
            notebookId = notebookId,
            title = "Section $id",
            content = "",
            lastModifiedAt = Date(updatedAt)
        )
    }

    private fun page(id: String, sectionId: String, updatedAt: Long, text: String = "local"): Page {
        return Page(
            id = id,
            sectionId = sectionId,
            title = "Page $id",
            contentBlocks = listOf(
                ContentBlock.TextBlock(
                    id = "tb-$id",
                    order = 0,
                    text = text
                )
            ),
            createdAt = updatedAt,
            updatedAt = updatedAt
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

    private fun Page.toEntity(gson: Gson): PageEntity {
        return PageEntity(
            id = id,
            sectionId = sectionId,
            title = title,
            contentBlocksJson = gson.toJson(contentBlocks),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun PageEntity.toDomain(gson: Gson): Page {
        val type = object : TypeToken<List<ContentBlock>>() {}.type
        val blocks: List<ContentBlock> = gson.fromJson(contentBlocksJson, type)
        return Page(
            id = id,
            sectionId = sectionId,
            title = title,
            contentBlocks = blocks,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Page.textBlockText(): String {
        return (contentBlocks.first() as ContentBlock.TextBlock).text
    }

    private class FakeNotebookDao(
        val notebooks: MutableMap<String, NotebookEntity> = mutableMapOf()
    ) : NotebookDao {
        override fun getAllNotebooks(): Flow<List<NotebookEntity>> = flowOf(notebooks.values.toList())
        override suspend fun getAllNotebooksForSync(): List<NotebookEntity> = notebooks.values.toList()
        override suspend fun getNotebookById(id: String): NotebookEntity? = notebooks[id]
        override suspend fun upsertNotebook(notebook: NotebookEntity) {
            notebooks[notebook.id] = notebook
        }
        override suspend fun softDeleteNotebook(id: String, ts: Long) {
            notebooks[id] = notebooks.getValue(id).copy(deletedAt = ts, lastModifiedAt = ts)
        }
        override suspend fun undeleteNotebook(id: String) {
            notebooks[id] = notebooks.getValue(id).copy(deletedAt = null)
        }
    }

    private class FakeSectionDao(
        val sections: MutableMap<String, SectionEntity> = mutableMapOf()
    ) : SectionDao {
        override suspend fun upsertSection(section: SectionEntity) {
            sections[section.id] = section
        }
        override fun getSectionsForNotebook(notebookId: String): Flow<List<SectionEntity>> {
            return flowOf(sections.values.filter { it.notebookId == notebookId })
        }
        override suspend fun getSectionById(sectionId: String): SectionEntity? = sections[sectionId]
        override suspend fun getAllSectionsForSync(): List<SectionEntity> = sections.values.toList()
        override suspend fun getSectionsForNotebookForSync(notebookId: String): List<SectionEntity> {
            return sections.values.filter { it.notebookId == notebookId }
        }
        override suspend fun softDeleteSection(id: String, ts: Long) {
            sections[id] = sections.getValue(id).copy(deletedAt = ts, lastModifiedAt = ts)
        }
        override suspend fun undeleteSection(id: String) {
            sections[id] = sections.getValue(id).copy(deletedAt = null)
        }
    }

    private class FakePageDao(
        val pages: MutableMap<String, PageEntity> = mutableMapOf()
    ) : PageDao {
        override fun getPagesForSection(sectionId: String): Flow<List<PageEntity>> {
            return flowOf(pages.values.filter { it.sectionId == sectionId })
        }
        override fun getPageById(id: String): Flow<PageEntity?> = flowOf(pages[id])
        override suspend fun getPageEntityById(id: String): PageEntity? = pages[id]
        override suspend fun upsertPage(page: PageEntity) {
            pages[page.id] = page
        }
        override suspend fun deletePage(page: PageEntity) {
            pages.remove(page.id)
        }
        override suspend fun deletePageById(id: String) {
            pages.remove(id)
        }
        override suspend fun getAllPages(): List<PageEntity> = pages.values.toList()
    }

    private class FakePageTombstoneStore(
        private val tombstones: MutableMap<String, Long> = mutableMapOf()
    ) : PageTombstoneStore {
        override suspend fun readAll(): Map<String, Long> = tombstones.toMap()
        override suspend fun writeAll(tombstones: Map<String, Long>) {
            this.tombstones.clear()
            this.tombstones.putAll(tombstones)
        }
        override suspend fun markDeleted(pageId: String, deletedAt: Long) {
            tombstones[pageId] = deletedAt
        }
        override suspend fun clear(pageId: String) {
            tombstones.remove(pageId)
        }
    }

    private class FakeDriveSyncDataSource(
        val notebooks: MutableMap<String, Notebook> = mutableMapOf(),
        val sections: MutableMap<String, Section> = mutableMapOf(),
        val pages: MutableMap<String, Page> = mutableMapOf(),
        var manifest: SyncManifest = SyncManifest(),
        var tombstones: SyncTombstones = SyncTombstones(),
        val calls: MutableList<String> = mutableListOf()
    ) : DriveSyncDataSource {
        override fun isReady(): Boolean = true
        override suspend fun readManifest(): SyncManifest = manifest
        override suspend fun writeManifest(manifest: SyncManifest) {
            calls += "writeManifest"
            this.manifest = manifest
        }
        override suspend fun readTombstones(): SyncTombstones = tombstones
        override suspend fun writeTombstones(tombstones: SyncTombstones) {
            calls += "writeTombstones"
            this.tombstones = tombstones
        }
        override suspend fun getAllNotebooks(): List<Notebook> = notebooks.values.toList()
        override suspend fun saveNotebook(notebook: Notebook) {
            calls += "saveNotebook:${notebook.id}"
            notebooks[notebook.id] = notebook
        }
        override suspend fun deleteNotebook(notebookId: String) {
            calls += "deleteNotebook:$notebookId"
            notebooks.remove(notebookId)
        }
        override suspend fun getSectionsForNotebook(notebookId: String): List<Section> {
            return sections.values.filter { it.notebookId == notebookId }
        }
        override suspend fun saveSection(section: Section) {
            calls += "saveSection:${section.id}"
            sections[section.id] = section
        }
        override suspend fun deleteSection(sectionId: String) {
            calls += "deleteSection:$sectionId"
            sections.remove(sectionId)
        }
        override suspend fun getPagesForSection(sectionId: String): List<Page> {
            return pages.values.filter { it.sectionId == sectionId }
        }
        override suspend fun savePage(page: Page) {
            calls += "savePage:${page.id}"
            pages[page.id] = page
        }
        override suspend fun deletePage(sectionId: String, pageId: String) {
            calls += "deletePage:$sectionId:$pageId"
            pages.remove(pageId)
        }
    }
}
