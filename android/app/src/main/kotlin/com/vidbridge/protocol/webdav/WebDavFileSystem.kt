package com.vidbridge.protocol.webdav

import android.util.Xml
import com.vidbridge.core.security.CredentialStore
import com.vidbridge.protocol.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebDavFileSystem(
    private val config: MediaSourceConfig,
    private val credentialStore: CredentialStore,
    private val client: OkHttpClient,
) : RemoteFileSystem {
    override val sourceId: String = config.id
    override val capabilities = SourceCapabilities(
        canList = true,
        canStat = true,
        canSeekRead = true,
        canStreamRead = true,
    )

    override suspend fun connect() {
        propfind(RemotePath(config.rootPath), depth = 0, directory = true).close()
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> {
        val requestUrl = url(path, directory = true)
        val resources = propfind(path, depth = 1, directory = true).use { response ->
            parseMultiStatus(response.body?.string().orEmpty())
        }
        val requestPath = decodePath(requestUrl.encodedPath)
        val all = resources.asSequence()
            .filterNot { decodePath(it.hrefPath).trimEnd('/') == requestPath.trimEnd('/') }
            .mapNotNull { resource ->
                val name = resource.displayName?.takeIf { it.isNotBlank() }
                    ?: decodePath(resource.hrefPath).trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                RemoteEntry(
                    name = name,
                    path = path.child(name),
                    isDirectory = resource.collection,
                    size = resource.contentLength.takeUnless { resource.collection },
                    modifiedAt = resource.modifiedAt,
                )
            }
            .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()
        val request = page ?: PageRequest(0, all.size.coerceAtLeast(1))
        val items = all.drop(request.offset).take(request.limit)
        val nextOffset = request.offset + items.size
        return Page(items, if (nextOffset < all.size) PageRequest(nextOffset, request.limit) else null)
    }

    override suspend fun stat(path: RemotePath): RemoteFileInfo {
        val resource = propfind(path, depth = 0, directory = false).use { response ->
            parseMultiStatus(response.body?.string().orEmpty()).firstOrNull()
                ?: throw SourceFailure.NotFound()
        }
        return RemoteFileInfo(path, resource.contentLength, resource.modifiedAt)
    }

    override suspend fun open(path: RemotePath): RemoteReadHandle {
        val info = stat(path)
        return WebDavReadHandle(url(path, directory = false), info.size, headers(), client)
    }

    override fun close() = Unit

    private suspend fun propfind(path: RemotePath, depth: Int, directory: Boolean): Response {
        val body = PROPFIND_BODY.toRequestBody(XML)
        val request = Request.Builder()
            .url(url(path, directory))
            .headers(headers())
            .header("Depth", depth.toString())
            .method("PROPFIND", body)
            .build()
        return execute(request) { code -> code == 207 }
    }

    private fun url(path: RemotePath, directory: Boolean): HttpUrl {
        val scheme = if (config.endpoint.tls) "https" else "http"
        val builder = HttpUrl.Builder()
            .scheme(scheme)
            .host(config.endpoint.host)
            .port(config.endpoint.port)
        path.value.trim('/').split('/').filter { it.isNotEmpty() }.forEach(builder::addPathSegment)
        if (directory && !path.value.endsWith('/')) builder.addPathSegment("")
        return builder.build()
    }

    private fun headers(): Headers = Headers.Builder().apply {
        val username = config.username
        if (!username.isNullOrBlank()) {
            val password = config.credentialId?.let(credentialStore::get)?.password
                ?: throw SourceFailure.AuthenticationRequired()
            add("Authorization", Credentials.basic(username, password, Charsets.UTF_8))
        }
    }.build()

    private suspend fun execute(request: Request, accepted: (Int) -> Boolean): Response {
        val response = try {
            client.newCall(request).await()
        } catch (error: Throwable) {
            throw mapFailure(error)
        }
        if (accepted(response.code)) return response
        val failure = when (response.code) {
            401 -> SourceFailure.AuthenticationRejected()
            403 -> SourceFailure.PermissionDenied()
            404 -> SourceFailure.NotFound()
            408, 504 -> SourceFailure.Timeout()
            else -> SourceFailure.ProtocolMismatch(IllegalStateException("WebDAV HTTP ${response.code}"))
        }
        response.close()
        throw failure
    }

    private fun mapFailure(error: Throwable): SourceFailure = when (error) {
        is SourceFailure -> error
        is javax.net.ssl.SSLHandshakeException, is javax.net.ssl.SSLPeerUnverifiedException -> SourceFailure.CertificateRejected(error)
        is java.net.SocketTimeoutException -> SourceFailure.Timeout(error)
        is java.net.UnknownHostException, is java.net.ConnectException -> SourceFailure.HostUnreachable(error)
        else -> SourceFailure.Unknown(error)
    }

    private companion object {
        val XML = "application/xml; charset=utf-8".toMediaType()
        const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8" ?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""
    }
}

private class WebDavReadHandle(
    private val url: HttpUrl,
    override val size: Long?,
    private val headers: Headers,
    private val client: OkHttpClient,
) : RemoteReadHandle {
    override val seekable = true
    @Volatile private var closed = false
    private val activeCalls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()

    override suspend fun readAt(offset: Long, length: Int): ByteArray {
        check(!closed) { "读取句柄已关闭" }
        require(offset >= 0 && length >= 0)
        if (length == 0 || (size != null && offset >= size)) return ByteArray(0)
        val end = size?.let { minOf(offset + length - 1, it - 1) } ?: (offset + length - 1)
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .header("Range", "bytes=$offset-$end")
            .get()
            .build()
        val call = client.newCall(request)
        activeCalls += call
        val response = try {
            call.await()
        } catch (error: Throwable) {
            throw when (error) {
                is javax.net.ssl.SSLHandshakeException, is javax.net.ssl.SSLPeerUnverifiedException -> SourceFailure.CertificateRejected(error)
                is java.net.SocketTimeoutException -> SourceFailure.Timeout(error)
                is java.net.UnknownHostException, is java.net.ConnectException -> SourceFailure.HostUnreachable(error)
                else -> SourceFailure.Unknown(error)
            }
        } finally {
            activeCalls -= call
        }
        return response.use {
            when {
                it.code == 206 -> readLimited(it.body, length)
                it.code == 200 && offset == 0L -> readLimited(it.body, length)
                it.code == 401 -> throw SourceFailure.AuthenticationRejected()
                it.code == 403 -> throw SourceFailure.PermissionDenied()
                it.code == 404 || it.code == 416 -> return ByteArray(0)
                else -> throw SourceFailure.ProtocolMismatch(
                    IllegalStateException("Range 请求返回 HTTP ${it.code}"),
                )
            }
        }
    }

    override fun stream(startOffset: Long): Flow<ByteArray> = flow {
        var offset = startOffset
        while (!closed && (size == null || offset < size)) {
            val bytes = readAt(offset, CHUNK_SIZE)
            if (bytes.isEmpty()) break
            emit(bytes)
            offset += bytes.size
        }
    }

    override fun close() {
        closed = true
        activeCalls.forEach(Call::cancel)
        activeCalls.clear()
    }

    private fun readLimited(body: ResponseBody?, limit: Int): ByteArray {
        if (body == null) return ByteArray(0)
        val output = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
        body.byteStream().use { input ->
            val buffer = ByteArray(minOf(64 * 1024, limit.coerceAtLeast(1)))
            var remaining = limit
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        return output.toByteArray()
    }

    private companion object { const val CHUNK_SIZE = 1024 * 1024 }
}

private data class DavResource(
    val hrefPath: String,
    val displayName: String?,
    val collection: Boolean,
    val contentLength: Long?,
    val modifiedAt: java.time.Instant?,
)

private fun parseMultiStatus(xml: String): List<DavResource> {
    if (xml.isBlank()) return emptyList()
    val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(StringReader(xml))
    }
    val result = mutableListOf<DavResource>()
    var href: String? = null
    var displayName: String? = null
    var collection = false
    var length: Long? = null
    var modified: java.time.Instant? = null
    var currentTag: String? = null
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                currentTag = parser.name.lowercase()
                if (currentTag == "response") {
                    href = null
                    displayName = null
                    collection = false
                    length = null
                    modified = null
                } else if (currentTag == "collection") {
                    collection = true
                }
            }
            XmlPullParser.TEXT -> {
                val value = parser.text.trim()
                if (value.isNotEmpty()) when (currentTag) {
                    "href" -> href = value
                    "displayname" -> displayName = value
                    "getcontentlength" -> length = value.toLongOrNull()
                    "getlastmodified" -> modified = runCatching {
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                    }.getOrNull()
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name.equals("response", ignoreCase = true)) {
                    href?.let { result += DavResource(pathFromHref(it), displayName, collection, length, modified) }
                }
                currentTag = null
            }
        }
        event = parser.next()
    }
    return result
}

private fun pathFromHref(href: String): String = runCatching {
    val uri = java.net.URI(href)
    uri.rawPath ?: href
}.getOrDefault(href.substringBefore('?'))

private fun decodePath(value: String): String =
    URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
