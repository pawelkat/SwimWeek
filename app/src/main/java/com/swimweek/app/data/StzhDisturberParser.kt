package com.swimweek.app.data

import com.swimweek.app.domain.PoolNotice
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Parses Stadt Zürich `stzh-show` + `stzh-disturber` banners from pool HTML.
 *
 * Primary structure (per-pool pages):
 * ```
 * <stzh-show slot="disturber" show-from-date="..." hide-from-date="...">
 *   <stzh-disturber>
 *     <stzh-clamp slot="label">Geschlossen bis 16. August (Revision)</stzh-clamp>
 *   </stzh-disturber>
 * </stzh-show>
 * ```
 */
object StzhDisturberParser {

    private val SHOW_BLOCK = Regex(
        """<stzh-show\b([^>]*)>([\s\S]*?)</stzh-show>""",
        RegexOption.IGNORE_CASE,
    )
    private val DISTURBER_BLOCK = Regex(
        """<stzh-disturber\b[^>]*>([\s\S]*?)</stzh-disturber>""",
        RegexOption.IGNORE_CASE,
    )
    private val LABEL_CLAMP = Regex(
        """slot\s*=\s*["']label["'][^>]*>([^<]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val SHOW_FROM = Regex(
        """show-from-date\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val HIDE_FROM = Regex(
        """hide-from-date\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val CLOSURE_KEYWORDS = Regex(
        """geschlossen|revision|gesperrt|unterbrochen|wartung|revisionsarbeit""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(html: String): List<PoolNotice> {
        if (html.isBlank()) return emptyList()
        val out = mutableListOf<PoolNotice>()
        for (showMatch in SHOW_BLOCK.findAll(html)) {
            val attrs = showMatch.groupValues[1]
            val body = showMatch.groupValues[2]
            if (!DISTURBER_BLOCK.containsMatchIn(body)) continue
            val disturberBody = DISTURBER_BLOCK.find(body)?.groupValues?.get(1) ?: body
            val label = extractLabel(disturberBody) ?: continue
            if (label.length < 3) continue
            val showFrom = parseInstant(SHOW_FROM.find(attrs)?.groupValues?.get(1))
            val hideFrom = parseInstant(HIDE_FROM.find(attrs)?.groupValues?.get(1))
            out += PoolNotice(
                label = decodeBasicEntities(label.trim()),
                showFrom = showFrom,
                hideFrom = hideFrom,
                isClosureLike = CLOSURE_KEYWORDS.containsMatchIn(label),
            )
        }
        // Dedupe identical labels
        return out.distinctBy { it.label to it.showFrom to it.hideFrom }
    }

    private fun extractLabel(disturberInnerHtml: String): String? {
        LABEL_CLAMP.find(disturberInnerHtml)?.groupValues?.get(1)?.let { return it }
        // Fallback: first non-empty text chunk
        val plain = disturberInnerHtml
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return plain.takeIf { it.isNotBlank() && it.length <= 200 }
    }

    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(raw.trim()).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(raw.trim())
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun decodeBasicEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&nbsp;", " ")
            .replace('\u00a0', ' ')
}
