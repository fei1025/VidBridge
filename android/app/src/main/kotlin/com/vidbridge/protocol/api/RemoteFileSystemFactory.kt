package com.vidbridge.protocol.api

import android.content.Context
import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.local.LocalFileSystem
import com.vidbridge.protocol.smb.SmbFileSystem
import com.vidbridge.protocol.webdav.WebDavFileSystem
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
        return when (config.type) {
            MediaSourceType.LOCAL -> LocalFileSystem(context, config)
            MediaSourceType.SMB -> SmbFileSystem(config, credentials)
            MediaSourceType.WEBDAV -> WebDavFileSystem(config, credentials, httpClient)
            else -> throw SourceFailure.UnsupportedOperation()
        }
    }
}
