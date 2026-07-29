package com.vidbridge.playback

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vidbridge.core.security.CredentialStore
import com.vidbridge.core.settings.BufferPreset
import com.vidbridge.core.settings.PlayerPreferences
import com.vidbridge.protocol.api.MediaSourceConfig
import com.vidbridge.protocol.api.MediaSourceType

/** Resolves logical source paths into libVLC media without leaking protocol details into UI. */
class PlaybackSourceResolver(
    context: Context,
    private val credentials: CredentialStore,
) {
    private val appContext = context.applicationContext

    fun uri(config: MediaSourceConfig, path: String, localPath: String? = null): Uri {
        localPath?.let { return Uri.fromFile(java.io.File(it)) }
        if (config.type == MediaSourceType.LOCAL) {
            val tree = config.rootUri?.let(Uri::parse) ?: error("本地来源授权已失效")
            var document = DocumentFile.fromTreeUri(appContext, tree) ?: error("无法打开本地来源")
            path.trim('/').split('/').filter(String::isNotEmpty).forEach { segment ->
                document = document.findFile(segment) ?: error("找不到文件：$path")
            }
            return document.uri
        }
        if (config.type == MediaSourceType.WEBDAV) {
            return endpointUri(if (config.endpoint.tls) "https" else "http", config, path)
        }
        if (config.type == MediaSourceType.SFTP) return endpointUri("sftp", config, path)
        if (config.type == MediaSourceType.NFS) {
            return Uri.Builder().scheme("nfs").authority(config.endpoint.host)
                .apply { appendSegments(path) }.build()
        }
        if (config.type == MediaSourceType.DLNA) {
            val encoded = path.trim('/').removePrefix("dlna-item/")
            return android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                .toString(Charsets.UTF_8)
                .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                ?.let(Uri::parse)
                ?: error("DLNA 媒体地址无效")
        }
        if (config.type == MediaSourceType.JELLYFIN || config.type == MediaSourceType.EMBY) {
            val token = config.credentialId?.let(credentials::get)?.password?.takeIf(String::isNotBlank)
                ?: error("媒体服务器 API Token 已失效")
            val itemId = path.trim('/').removePrefix("item/").substringBefore('/').substringBeforeLast('.')
            return Uri.Builder()
                .scheme(if (config.endpoint.tls) "https" else "http")
                .authority("${config.endpoint.host}:${config.endpoint.port}")
                .apply { appendSegments(config.rootPath) }
                .appendPath("Videos").appendPath(itemId).appendPath("stream")
                .appendQueryParameter("Static", "true").appendQueryParameter("api_key", token)
                .build()
        }
        if (config.type == MediaSourceType.PLEX) {
            val token = config.credentialId?.let(credentials::get)?.password?.takeIf(String::isNotBlank)
                ?: error("Plex API Token 已失效")
            val itemId = path.trim('/').removePrefix("item/").substringBefore('/').substringBeforeLast('.')
            return Uri.Builder()
                .scheme(if (config.endpoint.tls) "https" else "http")
                .authority("${config.endpoint.host}:${config.endpoint.port}")
                .apply { appendSegments(config.rootPath) }
                .appendPath("library").appendPath("metadata").appendPath(itemId)
                .appendQueryParameter("download", "1").appendQueryParameter("X-Plex-Token", token)
                .build()
        }
        val configuredShare = config.shareName?.trim()?.trim('/', '\\')?.takeIf(String::isNotEmpty)
        val normalized = path.trim('/').replace('\\', '/')
        val share = configuredShare ?: normalized.substringBefore('/')
        val relative = if (configuredShare != null) normalized else normalized.substringAfter('/', "")
        return Uri.Builder().scheme("smb").authority(config.endpoint.host).appendPath(share)
            .apply { relative.split('/').filter(String::isNotEmpty).forEach(::appendPath) }.build()
    }

    fun options(config: MediaSourceConfig, preferences: PlayerPreferences): List<String> = buildList {
        val credential = config.credentialId?.let(credentials::get)
        when (config.type) {
            MediaSourceType.SMB -> {
                config.username?.takeIf(String::isNotBlank)?.let { add(":smb-user=$it") }
                credential?.password?.let { add(":smb-pwd=$it") }
            }
            MediaSourceType.WEBDAV -> {
                config.username?.takeIf(String::isNotBlank)?.let { add(":http-user=$it") }
                credential?.password?.let { add(":http-pwd=$it") }
            }
            MediaSourceType.SFTP -> {
                config.username?.takeIf(String::isNotBlank)?.let { add(":sftp-user=$it") }
                credential?.password?.let { add(":sftp-pwd=$it") }
            }
            else -> Unit
        }
        val caching = when (preferences.bufferPreset) {
            BufferPreset.LOW_LATENCY -> 300
            BufferPreset.BALANCED -> 1000
            BufferPreset.STABLE -> 3000
        }
        add(":network-caching=$caching")
        add(":file-caching=$caching")
        preferences.preferredAudioLanguage
            .takeIf(String::isNotBlank)
            ?.let { add(":audio-track-lang=$it") }
        preferences.preferredSubtitleLanguage
            .takeIf(String::isNotBlank)
            ?.let { add(":sub-track-lang=$it") }
    }

    private fun endpointUri(scheme: String, config: MediaSourceConfig, path: String): Uri =
        Uri.Builder().scheme(scheme).authority("${config.endpoint.host}:${config.endpoint.port}")
            .apply { appendSegments(path) }.build()

    private fun Uri.Builder.appendSegments(value: String) {
        value.trim('/').split('/').filter(String::isNotEmpty).forEach(::appendPath)
    }
}
