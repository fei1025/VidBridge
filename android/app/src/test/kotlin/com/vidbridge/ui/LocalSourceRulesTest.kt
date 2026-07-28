package com.vidbridge.ui

import com.vidbridge.protocol.api.MediaSourceType
import org.junit.Assert.*
import org.junit.Test

class LocalSourceRulesTest {
    @Test
    fun localSourceRequiresTreeUriButNoNetworkFields() {
        val valid = SourceDraft(
            type = MediaSourceType.LOCAL,
            displayName = "手机视频",
            port = "0",
            rootUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies",
        )
        assertNull(SourceFormValidator.validate(valid))
        assertEquals(
            "请选择本地文件夹",
            SourceFormValidator.validate(valid.copy(rootUri = null)),
        )
    }
}
