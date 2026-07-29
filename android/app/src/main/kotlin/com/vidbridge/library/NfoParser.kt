package com.vidbridge.library

import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class LocalMetadata(
    val title: String? = null,
    val originalTitle: String? = null,
    val plot: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val director: String? = null,
    val castMembers: List<String> = emptyList(),
)

object NfoParser {
    fun parse(bytes: ByteArray): LocalMetadata? = parse(bytes.toString(Charsets.UTF_8))

    fun parse(xml: String): LocalMetadata? {
        if (xml.isBlank()) return null
        return runCatching {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(StringReader(xml))
            }
            var title: String? = null
            var originalTitle: String? = null
            var plot: String? = null
            var year: Int? = null
            var rating: Float? = null
            var director: String? = null
            val castMembers = linkedSetOf<String>()
            var insideActor = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "title" -> if (title == null) title = parser.nextText().trim().ifBlank { null }
                        "originaltitle" -> originalTitle = parser.nextText().trim().ifBlank { null }
                        "plot", "outline" -> if (plot == null) plot = parser.nextText().trim().ifBlank { null }
                        "year" -> year = parser.nextText().trim().toIntOrNull()
                        "rating" -> rating = parser.nextText().trim().toFloatOrNull()
                        "director" -> director = parser.nextText().trim().ifBlank { null }
                        "actor" -> insideActor = true
                        "name" -> if (insideActor) parser.nextText().trim().takeIf { it.isNotBlank() }?.let(castMembers::add)
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name.equals("actor", true)) {
                    insideActor = false
                }
                parser.next()
            }
            LocalMetadata(title, originalTitle, plot, year, rating, director, castMembers.toList())
                .takeIf { it.title != null || it.plot != null || it.year != null || it.director != null || it.castMembers.isNotEmpty() }
        }.getOrNull()
    }
}

object MediaVersionRules {
    fun contentKey(parsed: ParsedMediaName): String = buildString {
        append(parsed.title.lowercase())
        parsed.season?.let { append(":s").append(it) }
        parsed.episode?.let { append(":e").append(it) }
    }

    fun qualityLabel(fileName: String): String? = when {
        Regex("(?i)(2160p|4k|uhd)").containsMatchIn(fileName) -> "4K"
        Regex("(?i)1080p").containsMatchIn(fileName) -> "1080p"
        Regex("(?i)720p").containsMatchIn(fileName) -> "720p"
        Regex("(?i)480p").containsMatchIn(fileName) -> "480p"
        else -> null
    }
}
