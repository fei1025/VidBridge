package com.vidbridge.protocol.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DlnaSsdpDiscoveryTest {
    @Test
    fun parsesPrivateMediaServerResponse() {
        val device = DlnaSsdpDiscovery.parseResponse(
            """
            HTTP/1.1 200 OK
            LOCATION: http://192.168.1.40:8200/device.xml
            USN: uuid:media-server::upnp:rootdevice
            SERVER: TestDLNA/1.0
            """.trimIndent(),
        )

        assertEquals("http://192.168.1.40:8200/device.xml", device?.location)
        assertEquals("uuid:media-server::upnp:rootdevice", device?.usn)
    }

    @Test
    fun rejectsPublicDescriptionUrl() {
        assertNull(
            DlnaSsdpDiscovery.parseResponse(
                "HTTP/1.1 200 OK\r\nLOCATION: http://8.8.8.8/device.xml\r\n\r\n",
            ),
        )
    }
}
