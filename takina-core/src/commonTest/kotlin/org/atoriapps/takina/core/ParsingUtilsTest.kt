package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.atoriapps.takina.core.utils.ParsingUtils.toBareJidOrNull
import org.atoriapps.takina.core.utils.ParsingUtils.asStringListOrNull

class ParsingUtilsTest {
    @Test
    fun `toBareJidOrNull parses bare and full jid`() {
        assertEquals("alice@example.com", "alice@example.com".toBareJidOrNull()?.toString())
        assertEquals("alice@example.com", "alice@example.com/mobile".toBareJidOrNull()?.toString())
        assertNull("bad".toBareJidOrNull())
    }

    @Test
    fun `asStringListOrNull parses supported input shapes`() {
        assertEquals(listOf("A", "B"), "A, B".asStringListOrNull())
        assertEquals(listOf("A", "B"), listOf(" A ", "B", "").asStringListOrNull())
        assertEquals(listOf("A", "B"), arrayOf("A", " B ").asStringListOrNull())
        assertNull(" , ".asStringListOrNull())
        assertNull(null.asStringListOrNull())
    }
}

