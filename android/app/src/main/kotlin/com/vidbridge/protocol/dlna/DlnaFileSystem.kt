package com.vidbridge.protocol.dlna

import com.vidbridge.protocol.api.MediaSourceConfig
import com.vidbridge.protocol.api.LocalNetworkPolicy
import com.vidbridge.protocol.api.HttpRangeReadHandle
import com.vidbridge.protocol.api.Page
import com.vidbridge.protocol.api.PageRequest
import com.vidbridge.protocol.api.RemoteEntry
import com.vidbridge.protocol.api.RemoteFileInfo
import com.vidbridge.protocol.api.RemoteFileSystem
import com.vidbridge.protocol.api.RemotePath
import com.vidbridge.protocol.api.RemoteReadHandle
import com.vidbridge.protocol.api.SourceCapabilities
import com.vidbridge.protocol.api.SourceFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.time.Instant
import java.util.Base64

/** Read-only UPnP ContentDirectory adapter. DLNA endpoints are restricted to local addresses. */
class DlnaFileSystem(
    override val sourceId: String,
    private val config: MediaSourceConfig,
    private val client: OkHttpClient,
) : RemoteFileSystem {
    override val capabilities = SourceCapabilities(
        canList = true,
        canStat = true,
        canSeekRead = true,
        canStreamRead = true,
        supportsServerSideMetadata = true,
    )

    private var connected = false
    private var directory: DlnaContentDirectory? = null

    override suspend fun connect() {
        val location = descriptionLocation()
        val uri = runCatching { URI(location) }.getOrElse { throw SourceFailure.ProtocolMismatch(it) }
        requireLocalHost(uri.host)
        val request = Request.Builder().url(location).get().build()
        execute(request).use { response ->
            if (!response.isSuccessful) throw SourceFailure.HostUnreachable()
            val body = response.body?.string().orEmpty()
            directory = DlnaXmlParsers.parseDeviceDescription(body, location)
                ?: throw SourceFailure.ProtocolMismatch()
        }
        connected = true
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> {
        checkConnected()
        val requestPage = page ?: PageRequest()
        val objectId = objectId(path)
        val browse = browse(objectId, requestPage)
        val entries = browse.entries.map { it.toRemoteEntry() }
        val nextOffset = requestPage.offset + entries.size
        return Page(
            items = entries,
            next = nextOffset.takeIf { it < browse.totalMatches }?.let { PageRequest(it, requestPage.limit) },
        )
    }

    override suspend fun stat(path: RemotePath): RemoteFileInfo {
        val resource = resourceUrl(path) ?: throw SourceFailure.NotFound()
        val request = Request.Builder().url(resource).head().build()
        execute(request).use { response ->
            if (!response.isSuccessful) throw SourceFailure.NotFound()
            return RemoteFileInfo(
                path = path,
                size = response.header("Content-Length")?.toLongOrNull(),
                modifiedAt = response.header("Last-Modified")?.let { null },
            )
        }
    }

    override suspend fun open(path: RemotePath): RemoteReadHandle {
        checkConnected()
        val resource = resourceUrl(path) ?: throw SourceFailure.NotFound()
        val size = runCatching {
            val request = Request.Builder().url(resource).head().build()
            execute(request).use { response ->
                response.header("Content-Length")?.toLongOrNull()
            }
        }.getOrNull()
        return HttpRangeReadHandle(client, resource.toHttpUrl(), size = size)
    }

    override fun close() {
        connected = false
        directory = null
    }

    private suspend fun browse(objectId: String, page: PageRequest): DlnaBrowsePage {
        val contentDirectory = directory ?: throw SourceFailure.HostUnreachable()
        val soapAction = "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\""
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                  <ObjectID>${xmlEscape(objectId)}</ObjectID>
                  <BrowseFlag>BrowseDirectChildren</BrowseFlag>
                  <Filter>*</Filter>
                  <StartingIndex>${page.offset}</StartingIndex>
                  <RequestedCount>${page.limit}</RequestedCount>
                  <SortCriteria></SortCriteria>
                </u:Browse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        val request = Request.Builder()
            .url(contentDirectory.controlUrl)
            .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .header("SOAPAction", soapAction)
            .header("Accept", "text/xml, application/xml")
            .build()
        execute(request).use { response ->
            if (response.code == 401 || response.code == 403) throw SourceFailure.PermissionDenied()
            if (!response.isSuccessful) throw SourceFailure.HostUnreachable()
            val parsed = DlnaXmlParsers.parseBrowseResponse(response.body?.string().orEmpty())
                ?: throw SourceFailure.ProtocolMismatch()
            return parsed
        }
    }

    private fun descriptionLocation(): String {
        val configured = config.rootPath.trim()
        if (configured.startsWith("http://", true) || configured.startsWith("https://", true)) return configured
        return HttpUrl.Builder()
            .scheme(if (config.endpoint.tls) "https" else "http")
            .host(config.endpoint.host)
            .port(config.endpoint.port)
            .apply {
                configured.trim('/').ifBlank { "device.xml" }
                    .split('/').filter(String::isNotEmpty).forEach(::addPathSegment)
            }
            .build()
            .toString()
    }

    private fun objectId(path: RemotePath): String = path.value
        .removePrefix("dlna-object/")
        .takeIf { it != path.value }
        ?: "0"

    private fun resourceUrl(path: RemotePath): String? {
        val encoded = path.value.removePrefix("dlna-item/").takeIf { it != path.value } ?: return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrNull()?.takeIf { isAllowedUrl(it) }
    }

    private fun DlnaContentEntry.toRemoteEntry(): RemoteEntry {
        val displayName = if (isContainer) title else title.withResourceExtension(resourceUrl)
        val entryPath = if (isContainer) {
            RemotePath("dlna-object/${id}")
        } else {
            val resource = resourceUrl ?: ""
            RemotePath("dlna-item/${Base64.getUrlEncoder().withoutPadding().encodeToString(resource.toByteArray(Charsets.UTF_8))}")
        }
        return RemoteEntry(name = displayName, path = entryPath, isDirectory = isContainer, size = size, modifiedAt = null)
    }

    private fun String.withResourceExtension(resource: String?): String {
        if (substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) return this
        val extension = runCatching { URI(resource.orEmpty()).path.orEmpty().substringAfterLast('.', "") }
            .getOrNull()
            ?.lowercase()
            ?.takeIf { it in VIDEO_EXTENSIONS }
            ?: return this
        return "$this.$extension"
    }

    private fun checkConnected() {
        if (!connected || directory == null) throw SourceFailure.HostUnreachable()
    }

    private fun requireLocalHost(host: String?) {
        if (host.isNullOrBlank() || !LocalNetworkPolicy.isPrivateHost(host)) throw SourceFailure.InsecurePublicHttp()
    }

    private fun isAllowedUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") && uri.host != null && LocalNetworkPolicy.isPrivateHost(uri.host)
    }.getOrDefault(false)

    private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw SourceFailure.HostUnreachable(error)
        }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private val VIDEO_EXTENSIONS = setOf(
    "3gp", "avi", "flv", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "ts", "webm", "wmv",
)
