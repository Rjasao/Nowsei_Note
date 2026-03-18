package com.rjasao.nowsei.data.local

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.rjasao.nowsei.data.json.ContentBlockAdapter
import com.rjasao.nowsei.domain.model.ContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowseiDatabaseMigrationTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ContentBlock::class.java, ContentBlockAdapter())
        .create()

    @Test
    fun legacyPageContentToContentBlocksJson_preservesLegacyText() {
        val legacyContent = "Primeira linha\nSegunda linha"

        val json = legacyPageContentToContentBlocksJson(legacyContent)
        val type = object : TypeToken<List<ContentBlock>>() {}.type
        val blocks: List<ContentBlock> = gson.fromJson(json, type)

        assertEquals(1, blocks.size)
        assertTrue(blocks.first() is ContentBlock.TextBlock)
        assertEquals(legacyContent, (blocks.first() as ContentBlock.TextBlock).text)
    }
}
