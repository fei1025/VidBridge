package com.vidbridge.protocol.api

import java.net.InetAddress

/** Cleartext HTTP is acceptable only for explicitly local media endpoints. */
object LocalNetworkPolicy {
    fun isPrivateHost(host: String): Boolean = runCatching {
        InetAddress.getAllByName(host).any {
            it.isAnyLocalAddress ||
                it.isLoopbackAddress ||
                it.isLinkLocalAddress ||
                it.isSiteLocalAddress ||
                it.isUniqueLocalAddress()
        }
    }.getOrDefault(false)

    private fun InetAddress.isUniqueLocalAddress(): Boolean {
        val bytes = address ?: return false
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }
}
