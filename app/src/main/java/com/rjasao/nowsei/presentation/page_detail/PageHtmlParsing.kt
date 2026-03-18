package com.rjasao.nowsei.presentation.page_detail

enum class PageHtmlTextKind {
    PARAGRAPH,
    HEADING_2,
    HEADING_3,
    LIST_ITEM
}

sealed interface PageHtmlToken {
    data class Text(
        val kind: PageHtmlTextKind,
        val text: String,
        val html: String
    ) : PageHtmlToken

    data class Image(
        val src: String,
        val caption: String
    ) : PageHtmlToken
}

private val htmlTokenRegex = Regex(
    """(?is)<figure\b[^>]*class\s*=\s*["'][^"']*image-card[^"']*["'][^>]*>.*?</figure>|<p\b[^>]*>.*?</p>|<h2\b[^>]*>.*?</h2>|<h3\b[^>]*>.*?</h3>|<li\b[^>]*>.*?</li>"""
)

private val htmlImageRegex = Regex(
    """(?is)<figure\b[^>]*class\s*=\s*["'][^"']*image-card[^"']*["'][^>]*>.*?</figure>|<p>\s*<img\b[^>]*>\s*</p>(?:\s*<p>.*?</p>)?"""
)

fun parsePageHtmlTokens(html: String): List<PageHtmlToken> {
    if (html.isBlank()) return emptyList()

    val tokens = mutableListOf<PageHtmlToken>()
    var cursor = 0

    htmlTokenRegex.findAll(html).forEach { match ->
        val before = html.substring(cursor, match.range.first)
        appendPlainParagraphToken(tokens, before)

        val normalized = match.value.trim()
        when {
            normalized.startsWith("<figure", ignoreCase = true) -> {
                parsePageHtmlImageToken(normalized)?.let(tokens::add)
            }

            normalized.startsWith("<h2", ignoreCase = true) -> {
                appendTaggedTextToken(tokens, normalized, PageHtmlTextKind.HEADING_2)
            }

            normalized.startsWith("<h3", ignoreCase = true) -> {
                appendTaggedTextToken(tokens, normalized, PageHtmlTextKind.HEADING_3)
            }

            normalized.startsWith("<li", ignoreCase = true) -> {
                appendTaggedTextToken(tokens, normalized, PageHtmlTextKind.LIST_ITEM)
            }

            normalized.startsWith("<p", ignoreCase = true) -> {
                val paragraphImage = parseLegacyParagraphImageToken(normalized)
                if (paragraphImage != null) {
                    tokens += paragraphImage
                } else {
                    appendTaggedTextToken(tokens, normalized, PageHtmlTextKind.PARAGRAPH)
                }
            }
        }

        cursor = match.range.last + 1
    }

    appendPlainParagraphToken(tokens, html.substring(cursor))
    return tokens
}

fun pageHtmlImageRegex(): Regex = htmlImageRegex

fun extractPlainTextFromHtml(html: String): String {
    return html
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""</h2>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""</h3>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

fun stripImageCaptionPrefix(value: String): String {
    return value.replace(Regex("""^img\s+\d+\s*-\s*""", RegexOption.IGNORE_CASE), "").trim()
}

private fun appendPlainParagraphToken(tokens: MutableList<PageHtmlToken>, fragment: String) {
    val plain = extractPlainTextFromHtml(fragment).trim()
    if (plain.isNotBlank()) {
        tokens += PageHtmlToken.Text(
            kind = PageHtmlTextKind.PARAGRAPH,
            text = plain,
            html = "<p>${escapeHtmlText(plain).replace("\n", "<br>")}</p>"
        )
    }
}

private fun appendTaggedTextToken(
    tokens: MutableList<PageHtmlToken>,
    html: String,
    kind: PageHtmlTextKind
) {
    val plain = extractPlainTextFromHtml(html).trim()
    if (plain.isNotBlank()) {
        tokens += PageHtmlToken.Text(kind = kind, text = plain, html = html)
    }
}

private fun escapeHtmlText(value: String): String {
    return buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}

private fun parsePageHtmlImageToken(html: String): PageHtmlToken.Image? {
    val src = Regex("""<img\b[^>]*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val captionHtml = Regex(
        """<figcaption\b[^>]*>(.*?)</figcaption>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()

    val caption = stripImageCaptionPrefix(extractPlainTextFromHtml(captionHtml))
        .ifBlank { "toque para descrever" }

    return PageHtmlToken.Image(src = src, caption = caption)
}

private fun parseLegacyParagraphImageToken(html: String): PageHtmlToken.Image? {
    val src = Regex("""<img\b[^>]*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return PageHtmlToken.Image(src = src, caption = "toque para descrever")
}
