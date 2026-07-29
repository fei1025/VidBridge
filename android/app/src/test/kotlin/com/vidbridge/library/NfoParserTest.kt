package com.vidbridge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NfoParserTest {
    @Test
    fun parsesDirectorAndActors() {
        val metadata = NfoParser.parse(
            """
            <movie>
              <title>测试电影</title>
              <director>张导演</director>
              <actor><name>演员甲</name></actor>
              <actor><name>演员乙</name></actor>
            </movie>
            """.trimIndent(),
        )

        assertNotNull(metadata)
        assertEquals("张导演", metadata?.director)
        assertEquals(listOf("演员甲", "演员乙"), metadata?.castMembers)
    }
}
