package com.vidbridge.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vidbridge.core.database.*
import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.api.MediaSourceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SourceRepository(
    context: Context,
    private val dao: MediaSourceDao,
    private val credentials: CredentialStore,
) {
    private val contentResolver = context.applicationContext.contentResolver

    fun observeAll(): Flow<List<MediaSourceConfig>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: String): MediaSourceConfig? = dao.get(id)?.toModel()

    suspend fun save(config: MediaSourceConfig, password: String?) {
        val previous = dao.get(config.id)
        val credentialId = when {
            !password.isNullOrEmpty() -> credentials.put(password, config.credentialId)
            config.credentialId != null -> config.credentialId
            else -> null
        }
        dao.upsert(config.copy(credentialId = credentialId).toEntity())
        previous?.credentialId?.takeIf { it != credentialId }?.let(credentials::delete)
        previous?.rootUri
            ?.takeIf { it != config.rootUri }
            ?.let { releasePermissionIfUnused(it) }
    }

    suspend fun delete(id: String) {
        val existing = dao.get(id) ?: return
        existing.credentialId?.let(credentials::delete)
        dao.delete(id)
        existing.rootUri?.let { releasePermissionIfUnused(it) }
    }

    private suspend fun releasePermissionIfUnused(rootUri: String) {
        if (dao.countByRootUri(rootUri) != 0) return
        runCatching {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(rootUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
