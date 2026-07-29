package com.vidbridge.protocol.api

import android.content.Context
import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.local.LocalFileSystem
import com.vidbridge.protocol.smb.SmbFileSystem
import com.vidbridge.protocol.webdav.WebDavFileSystem
import com.vidbridge.protocol.mediaserver.MediaServerFileSystem
import com.vidbridge.protocol.plex.PlexFileSystem
import com.vidbridge.protocol.sftp.SftpFileSystem
import com.vidbridge.protocol.nfs.NfsFileSystem
import com.vidbridge.protocol.dlna.DlnaFileSystem
import com.vidbridge.sources.SourceRepository
import okhttp3.OkHttpClient

class RemoteFileSystemFactory(
    private val context: Context,
    private val sources: SourceRepository,
    private val credentials: CredentialStore,
    private val httpClient: OkHttpClient,
) {
    suspend fun create(sourceId: String): RemoteFileSystem {
        val config = sources.get(sourceId) ?: throw SourceFailure.NotFound()
        val fileSystem = when (config.type) {
            MediaSourceType.LOCAL -> LocalFileSystem(context, config)
            MediaSourceType.SMB -> SmbFileSystem(config, credentials)
            MediaSourceType.WEBDAV -> WebDavFileSystem(config, credentials, httpClient)
            MediaSourceType.SFTP -> SftpFileSystem(context, config, credentials)
            MediaSourceType.NFS -> NfsFileSystem(config)
            MediaSourceType.JELLYFIN, MediaSourceType.EMBY -> MediaServerFileSystem(sourceId, config, credentials, httpClient)
            MediaSourceType.PLEX -> PlexFileSystem(sourceId, config, credentials, httpClient)
            MediaSourceType.DLNA -> DlnaFileSystem(sourceId, config, httpClient)
        }
        // Every network-backed source gets the same bounded reconnect contract.
        // Local SAF access must stay untouched because reconnecting it cannot repair
        // a revoked document permission and would only hide the real failure.
        return if (config.type != MediaSourceType.LOCAL) RetryingRemoteFileSystem(fileSystem) else fileSystem
    }
}
