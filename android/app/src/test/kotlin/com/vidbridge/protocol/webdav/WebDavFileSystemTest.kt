package com.vidbridge.protocol.webdav

import com.vidbridge.core.security.CredentialStore
import com.vidbridge.core.security.Credentials
import com.vidbridge.protocol.api.*
import kotlinx.coroutines.test.runTest
import okhttp3.Credentials as HttpCredentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class WebDavFileSystemTest {
    private lateinit var server: MockWebServer
    private lateinit var credentials: FakeCredentialStore
    private lateinit var certificates: HandshakeCertificates
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        certificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .addTrustedCertificate(certificate.certificate)
            .build()
        server.useHttps(certificates.sslSocketFactory(), false)
        server.start()
        credentials = FakeCredentialStore(mapOf("credential" to "secret"))
        client = OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listParsesCollectionsUnicodeAndMetadataWithoutReturningSelf() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus(
            response("/dav/Videos/", "Videos", collection = true),
            response("/dav/Videos/Season%201/", "Season 1", collection = true),
            response("/dav/Videos/%E7%94%B5%E5%BD%B1%20A.mkv", "电影 A.mkv", length = 5_368_709_120),
        )))

        WebDavFileSystem(config(), credentials, client).use { fs ->
            val page = fs.list(RemotePath("dav/Videos"))

            assertEquals(2, page.items.size)
            assertEquals("Season 1", page.items[0].name)
            assertTrue(page.items[0].isDirectory)
            assertEquals("电影 A.mkv", page.items[1].name)
            assertEquals(5_368_709_120, page.items[1].size)
            assertEquals("dav/Videos/电影 A.mkv", page.items[1].path.value)
        }

        server.takeRequest().apply {
            assertEquals("PROPFIND", method)
            assertEquals("1", getHeader("Depth"))
            assertEquals("/dav/Videos/", path)
            assertEquals(HttpCredentials.basic("alice", "secret", Charsets.UTF_8), getHeader("Authorization"))
            assertFalse(requestUrl.toString().contains("secret"))
        }
    }

    @Test
    fun readAtUsesSingleHttpRangeAndReturnsOnlyRequestedBytes() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus(
            response("/dav/Videos/movie.mkv", "movie.mkv", length = 10),
        )))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 3-6/10")
                .setBody("3456"),
        )

        WebDavFileSystem(config(), credentials, client).use { fs ->
            fs.open(RemotePath("dav/Videos/movie.mkv")).use { handle ->
                assertEquals(10L, handle.size)
                assertArrayEquals("3456".toByteArray(), handle.readAt(3, 4))
            }
        }

        assertEquals("/dav/Videos/movie.mkv", server.takeRequest().path)
        server.takeRequest().apply {
            assertEquals("GET", method)
            assertEquals("bytes=3-6", getHeader("Range"))
        }
    }

    @Test
    fun nonZeroRangeRejectsServerThatIgnoresRange() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus(
            response("/dav/Videos/movie.mkv", "movie.mkv", length = 10),
        )))
        server.enqueue(MockResponse().setResponseCode(200).setBody("0123456789"))

        WebDavFileSystem(config(), credentials, client).use { fs ->
            fs.open(RemotePath("dav/Videos/movie.mkv")).use { handle ->
                assertThrows(SourceFailure.ProtocolMismatch::class.java) {
                    kotlinx.coroutines.runBlocking { handle.readAt(3, 4) }
                }
            }
        }
    }

    @Test
    fun transientRangeFailureRetriesWithTheSameRange() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus(
            response("/dav/Videos/movie.mkv", "movie.mkv", length = 10),
        )))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 3-6/10")
                .setBody("3456"),
        )

        WebDavFileSystem(config(), credentials, client).use { fs ->
            fs.open(RemotePath("dav/Videos/movie.mkv")).use { handle ->
                assertArrayEquals("3456".toByteArray(), handle.readAt(3, 4))
            }
        }

        assertEquals("PROPFIND", server.takeRequest().method)
        assertEquals("bytes=3-6", server.takeRequest().getHeader("Range"))
        assertEquals("bytes=3-6", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun missingCredentialFailsBeforeNetworkRequest() = runTest {
        val fs = WebDavFileSystem(config(), FakeCredentialStore(emptyMap()), client)
        assertThrows(SourceFailure.AuthenticationRequired::class.java) {
            kotlinx.coroutines.runBlocking { fs.connect() }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun cleartextWebDavIsRejectedBeforeNetworkRequest() = runTest {
        val insecure = config().copy(endpoint = config().endpoint.copy(scheme = "http", tls = false))
        val fs = WebDavFileSystem(insecure, credentials, client)
        assertThrows(SourceFailure.InsecureTransport::class.java) {
            kotlinx.coroutines.runBlocking { fs.connect() }
        }
        assertEquals(0, server.requestCount)
    }

    private fun config() = MediaSourceConfig(
        id = "webdav",
        displayName = "WebDAV",
        type = MediaSourceType.WEBDAV,
        endpoint = Endpoint("https", server.hostName, server.port, tls = true),
        rootPath = "dav/Videos",
        shareName = null,
        username = "alice",
        credentialId = "credential",
        enabled = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun response(
        href: String,
        name: String,
        collection: Boolean = false,
        length: Long? = null,
    ) = """
        <d:response>
          <d:href>$href</d:href>
          <d:propstat><d:prop>
            <d:displayname>$name</d:displayname>
            ${if (collection) "<d:resourcetype><d:collection/></d:resourcetype>" else "<d:resourcetype/>"}
            ${length?.let { "<d:getcontentlength>$it</d:getcontentlength>" }.orEmpty()}
            <d:getlastmodified>Wed, 21 Oct 2015 07:28:00 GMT</d:getlastmodified>
          </d:prop></d:propstat>
        </d:response>
    """.trimIndent()

    private fun multistatus(vararg responses: String) =
        """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:">${responses.joinToString("")}</d:multistatus>"""
}

private class FakeCredentialStore(private val passwords: Map<String, String>) : CredentialStore {
    override fun put(password: String, existingId: String?): String = existingId ?: "generated"
    override fun get(id: String): Credentials? = passwords[id]?.let(::Credentials)
    override fun delete(id: String) = Unit
}
