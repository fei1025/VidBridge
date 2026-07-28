package com.vidbridge.protocol.smb

import com.vidbridge.protocol.api.PageRequest
import com.vidbridge.protocol.api.RemoteEntry
import com.vidbridge.protocol.api.RemotePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbPathRouterTest {
    @Test
    fun configuredShareKeepsWholePathRelativeToShare() {
        assertEquals(
            SmbLocation("Videos", RemotePath("movies/demo.mkv")),
            SmbPathRouter.resolve("Videos", RemotePath("movies/demo.mkv")),
        )
    }

    @Test
    fun emptyPathWithoutConfiguredShareIsServerRoot() {
        assertNull(SmbPathRouter.resolve(null, RemotePath("")))
    }

    @Test
    fun firstPathSegmentSelectsDiscoveredShare() {
        assertEquals(
            SmbLocation("家庭视频", RemotePath("2026/demo.mkv")),
            SmbPathRouter.resolve(null, RemotePath("家庭视频/2026/demo.mkv")),
        )
    }

    @Test
    fun shareEntriesCanBePaged() {
        val entries = listOf("Movies", "TV", "家庭视频").map {
            RemoteEntry(it, RemotePath("").child(it), true, null, null)
        }
        val page = paginate(entries, PageRequest(offset = 1, limit = 1))

        assertEquals(listOf("TV"), page.items.map { it.name })
        assertEquals(PageRequest(offset = 2, limit = 1), page.next)
    }
}
