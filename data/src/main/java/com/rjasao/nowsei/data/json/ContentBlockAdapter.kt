package com.rjasao.nowsei.data.json

import com.google.gson.*
import com.rjasao.nowsei.domain.model.ContentBlock
import java.lang.reflect.Type

/**
 * Gson adapter para sealed class ContentBlock.
 *
 * Formato persistido:
 *  - { "type":"text",  ...campos do TextBlock... }
 *  - { "type":"image", ...campos do ImageBlock... }
 *
 * ✅ Compatibilidade retroativa:
 *  - Se não existir "type", tentamos inferir:
 *      - possui campo "text"  -> TextBlock
 *      - possui campo "imageUrl" -> ImageBlock
 */
class ContentBlockAdapter : JsonSerializer<ContentBlock>, JsonDeserializer<ContentBlock> {

    override fun serialize(
        src: ContentBlock,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val obj = JsonObject()
        when (src) {
            is ContentBlock.TextBlock -> {
                obj.addProperty("type", "text")
                obj.addProperty("id", src.id)
                obj.addProperty("order", src.order)
                obj.addProperty("text", src.text)
                obj.addProperty("fontSize", src.fontSize)
                obj.addProperty("isBold", src.isBold)
                obj.addProperty("isItalic", src.isItalic)
            }

            is ContentBlock.ImageBlock -> {
                obj.addProperty("type", "image")
                obj.addProperty("id", src.id)
                obj.addProperty("order", src.order)
                obj.addProperty("imageUrl", src.imageUrl)
                if (src.thumbnailUrl != null) obj.addProperty("thumbnailUrl", src.thumbnailUrl)
                if (src.caption != null) obj.addProperty("caption", src.caption)
                if (src.width != null) obj.addProperty("width", src.width)
                if (src.height != null) obj.addProperty("height", src.height)
            }
        }
        return obj
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ContentBlock {
        if (!json.isJsonObject) {
            // fallback ultra tolerante: se vier string, vira TextBlock
            val asStr = try { json.asString } catch (_: Exception) { "" }
            return ContentBlock.TextBlock(
                id = "legacy",
                order = 0,
                text = asStr
            )
        }

        val obj = json.asJsonObject

        // ✅ Compat retroativa: inferir type se ausente
        val type = obj.get("type")?.asString?.lowercase()?.trim()
            ?: inferLegacyType(obj)

        return when (type) {
            "text" -> ContentBlock.TextBlock(
                id = obj.get("id")?.asString ?: "legacy",
                order = obj.get("order")?.asInt ?: 0,
                text = obj.get("text")?.asString ?: "",
                fontSize = obj.get("fontSize")?.asFloat ?: 16f,
                isBold = obj.get("isBold")?.asBoolean ?: false,
                isItalic = obj.get("isItalic")?.asBoolean ?: false
            )

            "image" -> ContentBlock.ImageBlock(
                id = obj.get("id")?.asString ?: "legacy",
                order = obj.get("order")?.asInt ?: 0,
                imageUrl = obj.get("imageUrl")?.asString
                    ?: obj.get("uri")?.asString  // compat antiga
                    ?: "",
                thumbnailUrl = obj.get("thumbnailUrl")?.takeIf { !it.isJsonNull }?.asString,
                caption = obj.get("caption")?.takeIf { !it.isJsonNull }?.asString,
                width = obj.get("width")?.takeIf { !it.isJsonNull }?.asInt,
                height = obj.get("height")?.takeIf { !it.isJsonNull }?.asInt
            )

            else -> {
                // último fallback: se tiver text -> TextBlock; senão -> ImageBlock
                if (obj.has("text")) {
                    ContentBlock.TextBlock(
                        id = obj.get("id")?.asString ?: "legacy",
                        order = obj.get("order")?.asInt ?: 0,
                        text = obj.get("text")?.asString ?: ""
                    )
                } else {
                    ContentBlock.ImageBlock(
                        id = obj.get("id")?.asString ?: "legacy",
                        order = obj.get("order")?.asInt ?: 0,
                        imageUrl = obj.get("imageUrl")?.asString ?: ""
                    )
                }
            }
        }
    }

    private fun inferLegacyType(obj: JsonObject): String {
        return when {
            obj.has("text") -> "text"
            obj.has("imageUrl") || obj.has("uri") -> "image"
            else -> "text"
        }
    }
}
