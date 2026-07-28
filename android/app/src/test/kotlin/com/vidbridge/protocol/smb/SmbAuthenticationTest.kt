package com.vidbridge.protocol.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbAuthenticationTest {
    @Test
    fun blankUsernameUsesGuestInsteadOfAnonymousSession() {
        val authentication = smbAuthenticationContext(null, "")

        assertTrue(authentication.isGuest)
        assertFalse(authentication.isAnonymous)
    }

    @Test
    fun usernameUsesPasswordAuthentication() {
        val authentication = smbAuthenticationContext("media", "secret")

        assertFalse(authentication.isGuest)
        assertFalse(authentication.isAnonymous)
        assertEquals("media", authentication.username)
        assertEquals("secret", authentication.password.concatToString())
    }
}
