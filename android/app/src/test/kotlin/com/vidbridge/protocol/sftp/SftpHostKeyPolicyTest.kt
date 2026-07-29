package com.vidbridge.protocol.sftp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpHostKeyPolicyTest {
    @Test
    fun acceptsFirstUseButRejectsChangedHostKey() {
        assertTrue(SftpHostKeyPolicy.promptYesNo("The authenticity of host cannot be established"))
        assertFalse(SftpHostKeyPolicy.promptYesNo("WARNING: HostKey has been changed"))
    }
}
