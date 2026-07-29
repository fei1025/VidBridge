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

    @Test
    fun webDavRejectsCleartextTransport() {
        val draft = SourceDraft(
            type = MediaSourceType.WEBDAV,
            displayName = "DAV",
            host = "dav.example.com",
            port = "80",
            tls = false,
            username = "alice",
        )
        assertEquals("WebDAV 必须使用 HTTPS", SourceFormValidator.validate(draft))
    }

    @Test
    fun sftpRequiresPasswordForNewSource() {
        val draft = SourceDraft(
            type = MediaSourceType.SFTP,
            displayName = "SFTP NAS",
            host = "nas.example.com",
            port = "22",
            username = "media",
        )
        assertEquals("请输入 SFTP 密码", SourceFormValidator.validate(draft, passwordRequired = true))
        assertNull(SourceFormValidator.validate(draft.copy(password = "secret"), passwordRequired = true))
    }

    @Test
    fun nfsRequiresExportPath() {
        val draft = SourceDraft(
            type = MediaSourceType.NFS,
            displayName = "NFS NAS",
            host = "nas.example.com",
            port = "2049",
            anonymous = true,
        )
        assertEquals("请输入 NFS 导出路径", SourceFormValidator.validate(draft))
        assertNull(SourceFormValidator.validate(draft.copy(rootPath = "/volume1/video")))
    }
}
