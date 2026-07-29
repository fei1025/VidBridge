package com.vidbridge.protocol.mediaserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vidbridge.protocol.api.PageRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaServerFileSystemTest {
    @Test
    fun parsesMoviesAndEpisodesAsPlayableVirtualPaths() {
        val page = parseMediaServerResponse(
            """{"Items":[{"Id":"movie-1","Type":"Movie","Name":"电影","Container":"mp4","MediaSources":[{"Size":1234}]},{"Id":"episode-1","Type":"Episode","Name":"开端","SeriesName":"测试剧","ParentIndexNumber":1,"IndexNumber":2,"Container":"mkv"}],"TotalRecordCount":2}""",
            PageRequest(),
        )

        assertEquals(2, page.items.size)
        assertEquals("item/movie-1.mp4", page.items[0].path.value)
        assertEquals("测试剧 - S01E02 - 开端.mkv", page.items[1].name)
        assertEquals(1234L, page.items[0].size)
    }
}
