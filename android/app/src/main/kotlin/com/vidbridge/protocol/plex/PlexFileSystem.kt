package com.vidbridge.protocol.plex

import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.api.*
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/** Read-only Plex library adapter. Plex API responses are requested as JSON. */
class PlexFileSystem(
    override val sourceId: String,
    private val config: MediaSourceConfig,
    private val credentials: CredentialStore,
    private val client: OkHttpClient,
) : RemoteFileSystem {
    override val capabilities = SourceCapabilities(true, false, true, true, canSearch = true, supportsServerSideMetadata = true)
    private var connected = false
    private val token get() = config.credentialId?.let(credentials::get)?.password.orEmpty()

    private fun base(): HttpUrl = HttpUrl.Builder()
        .scheme(if (config.endpoint.tls) "https" else "http")
        .host(config.endpoint.host)
        .port(config.endpoint.port)
        .apply { config.rootPath.trim('/').takeIf(String::isNotBlank)?.split('/')?.forEach(::addPathSegment) }
        .build()

    override suspend fun connect() {
        if (token.isBlank()) throw SourceFailure.AuthenticationRequired()
        if (!config.endpoint.tls && !LocalNetworkPolicy.isPrivateHost(config.endpoint.host)) {
            throw SourceFailure.InsecurePublicHttp()
        }
        val url = base().newBuilder().addPathSegment("identity").build()
        client.newCall(request(url).get().build()).execute().use { response ->
            when {
                response.isSuccessful -> connected = true
                response.code == 401 || response.code == 403 -> throw SourceFailure.AuthenticationRejected()
                else -> throw SourceFailure.HostUnreachable()
            }
        }
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> {
        if (!connected) throw SourceFailure.HostUnreachable()
        val sections = getJson(base().newBuilder().addPathSegment("library").addPathSegment("sections").build())
            .optJSONObject("MediaContainer")?.optJSONArray("Directory") ?: org.json.JSONArray()
        val allEntries = buildList {
            for (index in 0 until sections.length()) {
                val section = sections.optJSONObject(index) ?: continue
                val key = section.optString("key").takeIf(String::isNotBlank) ?: continue
                val type = section.optString("type")
                if (type != "movie" && type != "show") continue
                var start = 0
                var fetched = 0
                do {
                    val url = base().newBuilder()
                        .addPathSegment("library").addPathSegment("sections").addPathSegment(key).addPathSegment("all")
                        .addQueryParameter("type", if (type == "movie") "1" else "4")
                        .addQueryParameter("X-Plex-Container-Start", start.toString())
                        .addQueryParameter("X-Plex-Container-Size", PLEX_PAGE_SIZE.toString())
                        .build()
                    val container = getJson(url).optJSONObject("MediaContainer") ?: break
                    val metadata = container.optJSONArray("Metadata") ?: break
                    fetched = metadata.length()
                    addAll(parsePlexMetadata(metadata))
                    val total = container.optInt("totalSize", start + metadata.length())
                    start += metadata.length()
                } while (fetched == PLEX_PAGE_SIZE && start < total)
            }
        }
        val offset = page?.offset ?: 0
        val limit = page?.limit ?: PLEX_PAGE_SIZE
        val entries = allEntries.drop(offset).take(limit)
        val nextOffset = offset + entries.size
        return Page(entries, nextOffset.takeIf { it < allEntries.size }?.let { PageRequest(it, limit) })
    }

    override suspend fun stat(path: RemotePath): RemoteFileInfo = throw SourceFailure.UnsupportedOperation()
    override suspend fun open(path: RemotePath): RemoteReadHandle {
        if (!connected) throw SourceFailure.HostUnreachable()
        return HttpRangeReadHandle(client, streamUrl(path), requestHeaders())
    }
    override fun close() { connected = false }

    private fun getJson(url: HttpUrl): org.json.JSONObject = client.newCall(request(url).get().build()).execute().use { response ->
        if (response.code == 401 || response.code == 403) throw SourceFailure.AuthenticationRejected()
        if (!response.isSuccessful) throw SourceFailure.HostUnreachable()
        org.json.JSONObject(response.body?.string().orEmpty())
    }

    private fun request(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("X-Plex-Token", token)
        .header("X-Plex-Product", "VidBridge")

    private fun requestHeaders(): okhttp3.Headers = okhttp3.Headers.Builder()
        .add("X-Plex-Token", token)
        .add("X-Plex-Product", "VidBridge")
        .build()

    private fun streamUrl(path: RemotePath): HttpUrl {
        val itemId = path.value.trim('/').removePrefix("item/").substringBeforeLast('.')
        return base().newBuilder()
            .addPathSegment("library")
            .addPathSegment("metadata")
            .addPathSegment(itemId)
            .addQueryParameter("download", "1")
            .addQueryParameter("X-Plex-Token", token)
            .build()
    }
}

private const val PLEX_PAGE_SIZE = 250

internal fun parsePlexMetadata(metadata: org.json.JSONArray): List<RemoteEntry> = buildList {
    for (index in 0 until metadata.length()) {
        val item = metadata.optJSONObject(index) ?: continue
        val id = item.optString("ratingKey").takeIf(String::isNotBlank) ?: continue
        val media = item.optJSONArray("Media")?.optJSONObject(0)
        val container = media?.optString("container").ifNullOrBlank { "mkv" }.lowercase()
        val part = media?.optJSONArray("Part")?.optJSONObject(0)
        val size = part?.optLong("size", -1L)?.takeIf { it >= 0L }
        val type = item.optString("type")
        val season = item.optInt("parentIndex", -1).takeUnless { it < 0 }
        val episode = item.optInt("index", -1).takeUnless { it < 0 }
        val title = item.optString("title").ifBlank { id }
        val name = if (type == "episode" && season != null && episode != null) {
            "${item.optString("grandparentTitle").ifBlank { "剧集" }} - S%02dE%02d - %s.%s".format(season, episode, title, container)
        } else "$title.$container"
        val modified = item.optString("addedAt").toLongOrNull()?.let { Instant.ofEpochSecond(it) }
        add(RemoteEntry(name, RemotePath("item/$id.$container"), false, size, modified))
    }
}

private fun String?.ifNullOrBlank(default: () -> String): String = this?.takeIf(String::isNotBlank) ?: default()
