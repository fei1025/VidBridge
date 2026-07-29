package com.vidbridge.protocol.nfs

import org.junit.Assert.assertEquals
import org.junit.Test

class NfsPathTest {
    @Test
    fun stripsExportPrefixForClientRelativePath() {
        assertEquals("/", nfsRelativePath("/volume1/video", "/volume1/video"))
        assertEquals("/Movies/a.mkv", nfsRelativePath("/volume1/video", "/volume1/video/Movies/a.mkv"))
        assertEquals("/Movies/a.mkv", nfsRelativePath("volume1/video", "volume1/video/Movies/a.mkv"))
    }
}
