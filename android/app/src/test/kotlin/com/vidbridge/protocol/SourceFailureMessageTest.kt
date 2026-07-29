package com.vidbridge.protocol

import com.vidbridge.protocol.api.SourceFailure
import com.vidbridge.protocol.api.safeUserMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceFailureMessageTest {
    @Test
    fun hidesRawMessagesFromUnknownFailures() {
        val error = IllegalStateException("sftp://user:secret@example/video")

        assertEquals("下载失败", error.safeUserMessage("下载失败"))
    }

    @Test
    fun keepsSafeProtocolMessage() {
        assertEquals("连接超时", SourceFailure.Timeout(IllegalStateException("raw detail")).safeUserMessage("失败"))
    }
}
