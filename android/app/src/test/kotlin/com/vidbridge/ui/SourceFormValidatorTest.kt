package com.vidbridge.ui

import com.vidbridge.protocol.api.MediaSourceType
import org.junit.Assert.*
import org.junit.Test

class SourceFormValidatorTest {
    @Test
    fun webDavDoesNotRequireSmbShare() {
        val draft = SourceDraft(
            type = MediaSourceType.WEBDAV,
            displayName = "远程媒体",
            host = "dav.example.com",
            port = "443",
            tls = true,
            rootPath = "remote.php/dav/files/alice",
            username = "alice",
            password = "secret",
        )
        assertNull(SourceFormValidator.validate(draft))
    }

    @Test
    fun smbShareNameIsOptional() {
        val draft = SourceDraft(
            type = MediaSourceType.SMB,
            displayName = "NAS",
            host = "192.168.1.20",
            port = "445",
            username = "media",
        )
        assertNull(SourceFormValidator.validate(draft))
    }

    @Test
    fun hostRejectsSchemeAndPathForEveryProtocol() {
        val draft = SourceDraft(
            type = MediaSourceType.WEBDAV,
            displayName = "DAV",
            host = "https://dav.example.com/root",
            port = "443",
            username = "alice",
        )
        assertEquals("主机只填写 IP 地址或主机名，不要包含协议或路径", SourceFormValidator.validate(draft))
    }
}
