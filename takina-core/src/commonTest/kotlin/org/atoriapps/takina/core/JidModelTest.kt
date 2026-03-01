package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.atoriapps.takina.core.models.FullJid
import org.atoriapps.takina.core.models.Jid
import org.atoriapps.takina.core.models.JidFormatException
import org.atoriapps.takina.core.models.bareJid
import org.atoriapps.takina.core.models.copy
import org.atoriapps.takina.core.models.createFullJid
import org.atoriapps.takina.core.models.toBareJid
import org.atoriapps.takina.core.models.toFullJid
import org.atoriapps.takina.core.models.toJid

class JidModelTest {
    @Test
    fun `toJid parses and normalizes`() {
        val parsed = "alice@Example.COM/mobile".toJid()
        assertTrue(parsed is FullJid)
        assertEquals("alice", parsed.local)
        assertEquals("example.com", parsed.domain)
        assertEquals("mobile", parsed.resource)
        assertEquals("alice@example.com/mobile", parsed.toString())
    }

    @Test
    fun `toBareJid accepts full jid and drops resource`() {
        assertEquals("alice@example.com", "alice@example.com/mobile".toBareJid().toString())
    }

    @Test
    fun `copy with new resource keeps bare part`() {
        val original = "alice@example.com/phone".toFullJid()
        val copied: Jid = original.copy(resource = "laptop")
        assertEquals("alice@example.com", copied.bareJid.toString())
        assertEquals("laptop", copied.resource)
    }

    @Test
    fun `bare and full jid should not be equal`() {
        val bare = "alice@example.com".toBareJid()
        val full = "alice@example.com/pc".toFullJid()
        assertFalse(bare == full)
        assertFalse(full == bare)
    }

    @Test
    fun `invalid jid cases throw format exception`() {
        assertFailsWith<JidFormatException> { "bad".toBareJid() }
        assertFailsWith<JidFormatException> { "alice@example.com/".toJid() }
        assertFailsWith<JidFormatException> { createFullJid(local = "alice", domain = "example.com", resource = "\u0000") }
    }
}
