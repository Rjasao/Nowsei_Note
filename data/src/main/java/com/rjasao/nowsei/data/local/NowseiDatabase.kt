package com.rjasao.nowsei.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.GsonBuilder
import com.rjasao.nowsei.data.json.ContentBlockAdapter
import com.rjasao.nowsei.data.local.entity.NotebookEntity
import com.rjasao.nowsei.data.local.entity.PageEntity
import com.rjasao.nowsei.data.local.entity.SectionEntity
import com.rjasao.nowsei.domain.model.ContentBlock
import java.util.UUID

@Database(
    entities = [NotebookEntity::class, SectionEntity::class, PageEntity::class],
    version = 8,
    exportSchema = true
)
abstract class NowseiDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun sectionDao(): SectionDao
    abstract fun pageDao(): PageDao

    companion object {

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebooks ADD COLUMN cloudId TEXT")
                db.execSQL("ALTER TABLE notebooks ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebooks ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE sections ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE pages ADD COLUMN deletedAt INTEGER")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notebooks_new` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastModifiedAt` INTEGER NOT NULL,
                        `cloudId` TEXT,
                        `deletedAt` INTEGER DEFAULT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `notebooks_new`(id,title,colorHex,createdAt,lastModifiedAt,cloudId,deletedAt)
                    SELECT id,title,colorHex,createdAt,lastModifiedAt,cloudId,deletedAt
                    FROM `notebooks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `notebooks`")
                db.execSQL("ALTER TABLE `notebooks_new` RENAME TO `notebooks`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sections_new` (
                        `id` TEXT NOT NULL,
                        `notebookId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastModifiedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER DEFAULT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`notebookId`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `sections_new`(id,notebookId,title,content,createdAt,lastModifiedAt,deletedAt)
                    SELECT id,notebookId,title,content,createdAt,lastModifiedAt,deletedAt
                    FROM `sections`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `sections`")
                db.execSQL("ALTER TABLE `sections_new` RENAME TO `sections`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sections_notebookId` ON `sections` (`notebookId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pages_new` (
                        `id` TEXT NOT NULL,
                        `sectionId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastModifiedAt` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL DEFAULT 0,
                        `deletedAt` INTEGER DEFAULT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sectionId`) REFERENCES `sections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `pages_new`(id,sectionId,title,content,createdAt,lastModifiedAt,position,deletedAt)
                    SELECT id,sectionId,title,content,createdAt,lastModifiedAt,position,deletedAt
                    FROM `pages`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `pages`")
                db.execSQL("ALTER TABLE `pages_new` RENAME TO `pages`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_sectionId` ON `pages` (`sectionId`)")
            }
        }
    }
}

private val migrationGson = GsonBuilder()
    .registerTypeAdapter(ContentBlock::class.java, ContentBlockAdapter())
    .create()

internal fun legacyPageContentToContentBlocksJson(content: String): String {
    val normalized = content.replace("\u0000", "")
    val block = ContentBlock.TextBlock(
        id = "legacy-${UUID.nameUUIDFromBytes(normalized.toByteArray())}",
        order = 0,
        text = normalized
    )
    return migrationGson.toJson(listOf(block))
}

val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val legacyPages = mutableListOf<Pair<String, String>>()
        db.query("SELECT id, content FROM pages").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val contentIndex = cursor.getColumnIndexOrThrow("content")
            while (cursor.moveToNext()) {
                legacyPages += cursor.getString(idIndex) to
                    legacyPageContentToContentBlocksJson(cursor.getString(contentIndex) ?: "")
            }
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pages_new` (
                `id` TEXT NOT NULL,
                `sectionId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `contentBlocksJson` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`sectionId`) REFERENCES `sections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT OR IGNORE INTO `pages_new`(id, sectionId, title, contentBlocksJson, createdAt, updatedAt)
            SELECT id, sectionId, title,
                   '' as contentBlocksJson,
                   COALESCE(createdAt, 0) as createdAt,
                   COALESCE(lastModifiedAt, 0) as updatedAt
            FROM `pages`
            """.trimIndent()
        )

        legacyPages.forEach { (id, blocksJson) ->
            db.execSQL(
                "UPDATE `pages_new` SET `contentBlocksJson` = ? WHERE `id` = ?",
                arrayOf(blocksJson, id)
            )
        }

        db.execSQL("DROP TABLE `pages`")
        db.execSQL("ALTER TABLE `pages_new` RENAME TO `pages`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_sectionId` ON `pages` (`sectionId`)")
    }
}
