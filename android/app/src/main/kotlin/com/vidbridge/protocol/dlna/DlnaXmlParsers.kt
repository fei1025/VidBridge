package com.vidbridge.protocol.dlna

import java.net.URI
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class DlnaContentDirectory(
    val friendlyName: String,
    val controlUrl: String,
)

data class DlnaContentEntry(
    val id: String,
    val parentId: String?,
    val title: String,
    val isContainer: Boolean,
    val resourceUrl: String?,
    val size: Long?,
    val durationSeconds: Long?,
)

data class DlnaBrowsePage(
    val entries: List<DlnaContentEntry>,
    val totalMatches: Int,
)

internal object DlnaXmlParsers {
    fun parseDeviceDescription(xml: String, location: String): DlnaContentDirectory? = runCatching {
        val base = URI(location)
        var friendlyName: String? = null
        var inContentDirectoryService = false
        var controlPath: String? = null
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(xml))
        }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (localName(parser.name)) {
                    "friendlyname" -> friendlyName = parser.nextText().trim().ifBlank { null }
                    "service" -> inContentDirectoryService = false
                    "servicetype" -> {
                        inContentDirectoryService = parser.nextText().trim()
                            .contains("contentdirectory", ignoreCase = true)
                    }
                    "controlurl" -> {
                        if (inContentDirectoryService) {
                            controlPath = parser.nextText().trim().ifBlank { null }
                        }
                    }
                }
            }
            parser.next()
        }
        val control = controlPath?.let { base.resolve(it).toString() } ?: return@runCatching null
        val controlUri = URI(control)
        if (friendlyName.isNullOrBlank() || controlUri.host != base.host) return@runCatching null
        DlnaContentDirectory(friendlyName, control)
    }.getOrNull()

    fun parseDidl(xml: String): List<DlnaContentEntry> = buildList {
        parse(xml) { parser ->
            if (parser.eventType != XmlPullParser.START_TAG) return@parse
            val name = localName(parser.name)
            if (name != "container" && name != "item") return@parse
            val id = parser.getAttributeValue(null, "id") ?: return@parse
            val parentId = parser.getAttributeValue(null, "parentID")
            val isContainer = name == "container"
            var title = id
            var resourceUrl: String? = null
            var size: Long? = null
            var durationSeconds: Long? = null
            val depth = parser.depth
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (localName(parser.name)) {
                    "title" -> title = parser.nextText().trim().ifBlank { id }
                    "res" -> {
                        size = parser.getAttributeValue(null, "size")?.toLongOrNull()
                        durationSeconds = parseDuration(parser.getAttributeValue(null, "duration"))
                        resourceUrl = parser.nextText().trim().ifBlank { null }
                    }
                }
            }
            add(DlnaContentEntry(id, parentId, title, isContainer, resourceUrl, size, durationSeconds))
        }
    }

    fun parseBrowseResponse(xml: String): DlnaBrowsePage? = runCatching {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(xml))
        }
        var result: String? = null
        var totalMatches: Int? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (localName(parser.name)) {
                    "result" -> result = parser.nextText()
                    "totalmatches" -> totalMatches = parser.nextText().trim().toIntOrNull()
                }
            }
            parser.next()
        }
        val didl = result ?: return@runCatching null
        DlnaBrowsePage(parseDidl(didl), totalMatches ?: parseDidl(didl).size)
    }.getOrNull()

    private fun parseDuration(value: String?): Long? {
        val parts = value?.split(':') ?: return null
        if (parts.size != 3) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull()?.toLong() ?: return null
        return hours * 3600L + minutes * 60L + seconds
    }

    private fun parse(xml: String, block: (XmlPullParser) -> Unit) {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(xml))
        }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            block(parser)
            parser.next()
        }
    }

    private fun localName(name: String): String = name.substringAfter(':').lowercase()
}
