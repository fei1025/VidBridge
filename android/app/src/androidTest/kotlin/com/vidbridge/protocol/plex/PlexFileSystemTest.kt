package com.vidbridge.protocol.plex

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlexFileSystemTest {
    @Test
    fun parsesMovieAndEpisodeMetadata() {
        val rows = parsePlexMetadata(
            JSONArray(
                """[{"ratingKey":"m1","type":"movie","title":"电影","Media":[{"container":"mp4","Part":[{"size":99}]}]},{"ratingKey":"e1","type":"episode","title":"开端","grandparentTitle":"测试剧","parentIndex":1,"index":2,"Media":[{"container":"mkv"}]}]""",
            ),
        )
        assertEquals("item/m1.mp4", rows[0].path.value)
        assertEquals("测试剧 - S01E02 - 开端.mkv", rows[1].name)
    }
}
