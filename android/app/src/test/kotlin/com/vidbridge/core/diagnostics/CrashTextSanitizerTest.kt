package com.vidbridge.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashTextSanitizerTest {
    @Test
    fun removesCredentialsFromUrisAndFields() {
        val result = CrashTextSanitizer.redact(
            "smb://media:secret@nas/video?token=abc123 password=hunter2 authorization=Bearer-xyz",
        )

        assertTrue(result.contains("smb://<redacted>@nas"))
        assertTrue(result.contains("token=<redacted>"))
        assertTrue(result.contains("password=<redacted>"))
        assertTrue(result.contains("authorization=<redacted>"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("hunter2"))
        assertFalse(result.contains("Bearer-xyz"))
    }
}
