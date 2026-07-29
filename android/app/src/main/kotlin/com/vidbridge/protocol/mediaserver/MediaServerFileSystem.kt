package com.vidbridge.protocol.mediaserver

import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.api.MediaSourceConfig
import com.vidbridge.protocol.api.MediaSourceType
import com.vidbridge.protocol.api.Page
import com.vidbridge.protocol.api.PageRequest
import com.vidbridge.protocol.api.RemoteEntry
import com.vidbridge.protocol.api.RemoteFileInfo
import com.vidbridge.protocol.api.RemoteFileSystem
import com.vidbridge.protocol.api.RemotePath
import com.vidbridge.protocol.api.RemoteReadHandle
import com.vidbridge.protocol.api.SourceCapabilities
import com.vidbridge.protocol.api.SourceFailure
import com.vidbridge.protocol.api.LocalNetworkPolicy
import com.vidbridge.protocol.api.HttpRangeReadHandle
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/** Read-only Jellyfin/Emby library adapter using a server API token. */
class MediaServerFileSystem(
    override val sourceId: String,
    private val config: MediaSourceConfig,
    private val credentials: CredentialStore,
    private val client: OkHttpClient,
) : RemoteFileSystem {
    override val capabilities = SourceCapabilities(
        canList = true,
        canStat = false,
        canSeekRead = true,
        canStreamRead = true,
        canSearch = true,
        supportsServerSideMetadata = true,
    )
    private var connected = false
    private val token: String
        get() = config.credentialId?.let(credentials::get)?.password.orEmpty()

    private fun baseUrl(): HttpUrl = HttpUrl.Builder()
        .scheme(if (config.endpoint.tls) "https" else "http")
        .host(config.endpoint.host)
        .port(config.endpoint.port)
        .apply {
            config.rootPath.trim('/').takeIf(String::isNotBlank)?.split('/')?.forEach(::addPathSegment)
        }
        .build()

    override suspend fun connect() {
        if (token.isBlank()) throw SourceFailure.AuthenticationRequired()
        if (!config.endpoint.tls && !LocalNetworkPolicy.isPrivateHost(config.endpoint.host)) {
            throw SourceFailure.InsecurePublicHttp()
        }
        val request = requestBuilder(baseUrl()).url(baseUrl().newBuilder().addPathSegment("System").addPathSegment("Info").build()).get().build()
        return client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> connected = true
                response.code == 401 || response.code == 403 -> throw SourceFailure.AuthenticationRejected()
                response.code in 500..599 -> throw SourceFailure.HostUnreachable()
                else -> throw SourceFailure.ProtocolMismatch()
            }
        }
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> {
        checkConnected()
        val requestUrl = baseUrl().newBuilder()
            .addPathSegment("Items")
            .addQueryParameter("Recursive", "true")
            .addQueryParameter("IncludeItemTypes", "Movie,Episode")
            .addQueryParameter("Fields", "Path,MediaSources,DateCreated,Overview,ProviderIds,PremiereDate,ParentIndexNumber,IndexNumber,SeriesName")
            .addQueryParameter("StartIndex", (page?.offset ?: 0).toString())
            .addQueryParameter("Limit", (page?.limit ?: 250).toString())
            .build()
        val request = requestBuilder(requestUrl).get().build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw SourceFailure.AuthenticationRejected()
            if (!response.isSuccessful) throw SourceFailure.HostUnreachable()
            val body = response.body?.string().orEmpty()
            parseMediaServerResponse(body, page)
        }
    }

    override suspend fun stat(path: RemotePath): RemoteFileInfo = throw SourceFailure.UnsupportedOperation()

    override suspend fun open(path: RemotePath): RemoteReadHandle {
        checkConnected()
        return HttpRangeReadHandle(client, streamUrl(path), requestHeaders())
    }

    override fun close() {
        connected = false
    }

    private fun checkConnected() {
        if (!connected) throw SourceFailure.HostUnreachable()
    }

    private fun requestHeaders(): okhttp3.Headers = okhttp3.Headers.Builder()
        .add("X-Emby-Token", token)
        .add("X-MediaBrowser-Token", token)
        .build()

    private fun streamUrl(path: RemotePath): HttpUrl {
        val itemId = path.value.trim('/').removePrefix("item/").substringBeforeLast('.')
        return baseUrl().newBuilder()
            .addPathSegment("Videos")
            .addPathSegment(itemId)
            .addPathSegment("stream")
            .addQueryParameter("Static", "true")
            .addQueryParameter("api_key", token)
            .build()
    }

    private fun requestBuilder(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("X-Emby-Token", token)
        .header("X-MediaBrowser-Token", token)

}

internal fun parseMediaServerResponse(body: String, page: PageRequest?): Page<RemoteEntry> {
    val root = org.json.JSONObject(body)
    val values = root.optJSONArray("Items") ?: org.json.JSONArray()
    val total = root.optInt("TotalRecordCount", values.length())
    val entries = buildList {
        for (index in 0 until values.length()) {
            val item = values.optJSONObject(index) ?: continue
            val id = item.optString("Id").takeIf(String::isNotBlank) ?: continue
            val type = item.optString("Type")
            val container = item.optString("Container").ifBlank { "mkv" }.lowercase()
            val season = item.optInt("ParentIndexNumber", -1).takeUnless { it < 0 }
            val episode = item.optInt("IndexNumber", -1).takeUnless { it < 0 }
            val title = item.optString("Name").ifBlank { id }
            val displayName = if (type == "Episode" && season != null && episode != null) {
                "${item.optString("SeriesName").ifBlank { "剧集" }} - S%02dE%02d - %s.%s".format(season, episode, title, container)
            } else "$title.$container"
            val size = item.optJSONArray("MediaSources")?.optJSONObject(0)?.optLong("Size", -1L)?.takeIf { it >= 0L }
            val modified = item.optString("DateCreated").takeIf(String::isNotBlank)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            add(RemoteEntry(displayName, RemotePath("item/$id.$container"), false, size, modified))
        }
    }
    val next = (page?.offset ?: 0) + entries.size
    return Page(entries, next.takeIf { it < total }?.let { PageRequest(it, page?.limit ?: 250) })
}
