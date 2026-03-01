package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlWriter
import org.atoriapps.takina.core.xml.xml

class XmlCodecTest {
    @Test
    fun `xml dsl renders escaped stanza`() {
        val message = xml("message") {
            attr("to", "bob@example.com")
            element("body") { text("1 < 2 & 3") }
        }

        val out = XmlWriter.render(message)
        assertTrue(out.contains("<message"))
        assertTrue(out.contains("to='bob@example.com'"))
        assertTrue(out.contains("1 &lt; 2 &amp; 3"))
    }

    @Test
    fun `parser reads nested elements and text content`() {
        val parsed = XmlParser.parseElementOrNull(
            "<message from='alice@example.com'><body>Hello</body><x><y>World</y></x></message>",
        )
        assertNotNull(parsed)
        assertEquals("message", parsed.localName)
        assertEquals("alice@example.com", parsed.attribute("from"))
        assertEquals("Hello", parsed.firstDescendant("body")?.textContent())
        assertEquals("World", parsed.firstDescendant("y")?.textContent())
    }

    @Test
    fun `parse start tag supports stream header`() {
        val tag = XmlParser.parseStartTagOrNull(
            "<stream:stream to='example.com' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>",
        )
        assertNotNull(tag)
        assertEquals("stream", tag.localName)
        assertEquals("example.com", tag.attribute("to"))
    }

    @Test
    fun `writer separates multiple attributes with spaces`() {
        val out = XmlWriter.startTag(
            "stream:stream",
            mapOf(
                "to" to "example.com",
                "xmlns" to "jabber:client",
                "xmlns:stream" to "http://etherx.jabber.org/streams",
                "version" to "1.0",
            ),
        )
        assertTrue(out.contains("to='example.com' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'"))
    }
}
