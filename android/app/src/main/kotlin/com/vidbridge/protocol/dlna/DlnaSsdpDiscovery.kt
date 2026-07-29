package com.vidbridge.protocol.dlna

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import com.vidbridge.protocol.api.LocalNetworkPolicy

data class DlnaDevice(
    val location: String,
    val usn: String?,
    val server: String?,
)

/**
 * Safe DLNA discovery only: SSDP carries no credentials and responses are accepted
 * only when their description URL points to a private/local address.
 */
object DlnaSsdpDiscovery {
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaServer:1"

    suspend fun discover(timeoutMs: Int = 1_500): List<DlnaDevice> = withContext(Dispatchers.IO) {
        require(timeoutMs in 100..10_000)
        val request = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 1\r\n")
            append("ST: $SEARCH_TARGET\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)
        val destination = InetAddress.getByName(SSDP_ADDRESS)
        val results = linkedMapOf<String, DlnaDevice>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 100
            socket.send(DatagramPacket(request, request.size, destination, SSDP_PORT))
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            val buffer = ByteArray(8 * 1024)
            while (System.nanoTime() < deadline) {
                currentCoroutineContext().ensureActive()
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    parseResponse(String(packet.data, packet.offset, packet.length, Charsets.US_ASCII))
                        ?.let { results.putIfAbsent(it.location, it) }
                } catch (_: SocketTimeoutException) {
                    // Poll in short intervals so coroutine cancellation remains responsive.
                }
            }
        }
        results.values.toList()
    }

    internal fun parseResponse(response: String): DlnaDevice? {
        val headers = response.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
            }
            .toMap()
        val location = headers["location"] ?: return null
        val uri = runCatching { URI(location) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (uri.scheme !in setOf("http", "https") || !LocalNetworkPolicy.isPrivateHost(host)) return null
        return DlnaDevice(location, headers["usn"], headers["server"])
    }

}
