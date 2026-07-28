package com.vidbridge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibraryRulesTest {
    @Test
    fun parsesEpisodeWithUnicodeAndQualitySuffix() {
        val parsed = MediaFileNameParser.parse("三体.S02E03.宇宙闪烁.1080p.WEB-DL.mkv")

        assertEquals(MediaKind.EPISODE, parsed.kind)
        assertEquals("三体 · 宇宙闪烁", parsed.title)
        assertEquals(2, parsed.season)
        assertEquals(3, parsed.episode)
    }

    @Test
    fun parsesMovieTitleAndRemovesYearAndQuality() {
        val parsed = MediaFileNameParser.parse("Arrival (2016).2160p.mkv")

        assertEquals(MediaKind.MOVIE, parsed.kind)
        assertEquals("Arrival", parsed.title)
    }

    @Test
    fun recognizesSupportedVideoExtensionsCaseInsensitively() {
        assertTrue(MediaFormats.isVideo("电影.MKV"))
        assertEquals("video/mp2t", MediaFormats.mimeType("sample.m2ts"))
        assertFalse(MediaFormats.isVideo("movie.nfo"))
    }

    @Test
    fun stableIdentityChangesWhenFileVersionChanges() {
        val first = MediaIdentity.mediaKey("source", "电影/movie.mkv", 100, 10)
        val same = MediaIdentity.mediaKey("source", "电影/movie.mkv", 100, 10)
        val changed = MediaIdentity.mediaKey("source", "电影/movie.mkv", 101, 10)

        assertEquals(first, same)
        assertNotEquals(first, changed)
        assertEquals(64, first.length)
    }

    @Test
    fun filtersHiddenAndNasServiceDirectories() {
        assertTrue(ScanRules.isHidden(".cache"))
        assertTrue(ScanRules.isExcludedDirectory("@eaDir"))
        assertTrue(ScanRules.isExcludedDirectory("#recycle"))
        assertFalse(ScanRules.isExcludedDirectory("Movies"))
    }

    @Test
    fun parsesKodiStyleNfoMetadata() {
        val metadata = NfoParser.parse(
            """<movie><title>降临</title><originaltitle>Arrival</originaltitle><year>2016</year><rating>7.9</rating><plot>语言改变时间。</plot></movie>""",
        )

        requireNotNull(metadata)
        assertEquals("降临", metadata.title)
        assertEquals("Arrival", metadata.originalTitle)
        assertEquals(2016, metadata.year)
        assertEquals(7.9f, metadata.rating)
        assertEquals("语言改变时间。", metadata.plot)
    }

    @Test
    fun groupsVersionsByParsedContentAndDetectsQuality() {
        val parsed = MediaFileNameParser.parse("Show.S01E02.1080p.mkv")

        assertEquals("show:s1:e2", MediaVersionRules.contentKey(parsed))
        assertEquals("1080p", MediaVersionRules.qualityLabel("Show.S01E02.1080p.mkv"))
        assertEquals("4K", MediaVersionRules.qualityLabel("Movie.UHD.HEVC.mkv"))
    }}
