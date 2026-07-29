package com.vidbridge.library

import java.security.MessageDigest

data class ParsedMediaName(
    val title: String,
    val kind: MediaKind,
    val season: Int? = null,
    val episode: Int? = null,
)

enum class MediaKind { MOVIE, EPISODE, VIDEO }

object MediaFileNameParser {
    private val episodePattern = Regex(
        """(?i)^(.*?)[\s._-]+S(\d{1,2})E(\d{1,3})(?:[\s._-]+(.*))?$""",
    )
    private val trailingQuality = Regex(
        """(?i)(?:^|[\s._-]+)(?:2160p|1080p|720p|480p|4k|uhd|bluray|web[-_. ]?dl|webrip|hdtv|x26[45]|hevc|av1)(?:(?:[\s._-]+)(?:2160p|1080p|720p|480p|4k|uhd|bluray|web[-_. ]?dl|webrip|hdtv|x26[45]|hevc|av1))*$""",
    )
    private val yearSuffix = Regex("""[\s._-]*\((19|20)\d{2}\)$""")

    fun parse(fileName: String): ParsedMediaName {
        val stem = fileName.substringBeforeLast('.', fileName)
        val match = episodePattern.matchEntire(stem)
        if (match != null) {
            val show = clean(match.groupValues[1])
            val season = match.groupValues[2].toInt()
            val episode = match.groupValues[3].toInt()
            val episodeTitle = clean(match.groupValues.getOrElse(4) { "" })
            val title = if (episodeTitle.isBlank()) show else "$show · $episodeTitle"
            return ParsedMediaName(title.ifBlank { stem }, MediaKind.EPISODE, season, episode)
        }
        val cleaned = clean(stem)
        val kind = if (Regex("""\b(19|20)\d{2}\b""").containsMatchIn(stem)) MediaKind.MOVIE else MediaKind.VIDEO
        return ParsedMediaName(cleaned.ifBlank { stem }, kind)
    }

    private fun clean(value: String): String = value
        .replace(trailingQuality, "")
        .replace(yearSuffix, "")
        .replace(Regex("""[._]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '_', '.')
}

object MediaFormats {
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "ts", "m2ts", "webm", "mpeg", "mpg", "m4v", "wmv", "3gp",
    )

    fun isVideo(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in videoExtensions

    fun mimeType(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts" -> "video/mp2t"
        "mpeg", "mpg" -> "video/mpeg"
        "3gp" -> "video/3gpp"
        "wmv" -> "video/x-ms-wmv"
        else -> null
    }
}
object MediaIdentity {
    fun fingerprint(path: String, size: Long?, modifiedAtEpochMs: Long?): String =
        sha256("$path\u0000${size ?: -1}\u0000${modifiedAtEpochMs ?: -1}")

    fun mediaKey(sourceId: String, path: String, size: Long?, modifiedAtEpochMs: Long?): String =
        entryKey(sourceId, path, false, size, modifiedAtEpochMs)

    fun groupKey(sourceId: String, title: String, kind: MediaKind): String =
        sha256("$sourceId\u0000${kind.name}\u0000${title.trim().lowercase()}")

    fun entryKey(sourceId: String, path: String, isDirectory: Boolean, size: Long?, modifiedAtEpochMs: Long?): String =
        if (isDirectory) sha256("$sourceId\u0000$path\u0000directory")
        else sha256("$sourceId\u0000$path\u0000${size ?: -1}\u0000${modifiedAtEpochMs ?: -1}")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
