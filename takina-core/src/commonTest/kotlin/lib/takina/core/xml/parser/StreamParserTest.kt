
package lib.takina.core.xml.parser

import lib.takina.core.xml.Element
import lib.takina.core.xml.XmlException
import kotlin.test.*


class StreamParserTest {

    data class Result(val element: Element?, val error: Boolean)

    private fun parse(data: String): Element = parseXML(data)

    @Test
    fun testParse0() {
        val input = "<x>ok</x>"
        assertEquals(input, parse(input).getAsString())
    }

    @Test
    fun testParse() {
        val input =
            "<message><body>body</body><html><body><p><em>Wow</em>*, I&apos;m* <span>green</span>with <strong>envy</strong>!</p></body></html></message>"
        assertNotEquals(input, parse(input).getAsString())
    }

    @Test
    fun testEntities() {

        var e = parse("<message from=\"test@example.com\"><body>© §      ∉ ⇒ </body></message>")
        assertEquals("© §      ∉ ⇒ ", e.findChild("message", "body")!!.value)

        e = parse("<message from=\"test@example.com\"><body>123 - &#123;</body></message>")
        assertEquals("123 - &#123;", e.findChild("message", "body")!!.value)

        e = parse("<message from=\"test@example.com\" id=\"&a123;\"></message>")
        assertEquals("&a123;", e.attributes["id"])

        e = parse("<message from=\"test@example.com\" id=\"&#123;\"></message>")
        assertEquals("&#123;", e.attributes["id"])

        try {
            parse("<message from=\"test@example.com\"><body>123 - &123;</body></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<message from=\"test@example.com\"><body>123 - &#123</body></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<message from=\"test@example.com\"><body>123 - &a123</body></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<message from=\"test@example.com\" id=\"&123;\"></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<message from=\"test@example.com\" id=\"&a123\"></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<mes&sage from=\"test@example.com\"></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<mes&amp;sage from=\"test@example.com\"></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<message from=\"test@example.com\"><<body>Test</body></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<message from=\"test@example.com\"><body>Test</body1></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

        try {
            parse("<message to=\"test@zeus\" type=\"chat\" id=\"t&amp;t<\"><body>Test &amp; done</body></message>")
            fail("Error expected!")
        } catch (_: Exception) {
        }

    }

    @Test
    fun testStreamParser() {
        var tmp = parse("<iq/>")
        assertEquals("iq", tmp.name)

        tmp = parse("<iq xmlns:ack='http://jabber.org/protocol/ack'><ack:a>9</ack:a></iq>")
        println(tmp.getAsString())
        assertEquals("iq", tmp.name)
        assertEquals("9", tmp.getChildrenNS("a", "http://jabber.org/protocol/ack")?.value)
    }

    @Test
    fun testStreamParserTestWhitespaces() {
        var e: Throwable? = null
        val parser = object : StreamParser() {
            override fun onParseError(errorMessage: String) {
                e = XmlException(errorMessage)
            }

            override fun onNextElement(element: Element) {
                println("stream element: $element")
            }

            override fun onStreamClosed() {
                println("stream closed")
            }

            override fun onStreamStarted(attrs: Map<String, String>) {
                println("stream opened")
            }
        }

        parser.parse(
            "<stream:stream>\n" +
                    "<iq/>\n" +
                    "</stream:stream>"
        )
        assertNull(e)
    }

    @Test
    fun testInvalidCharactersBetweenTags() {
        assertFailsWith<XmlException> { parse("<x>ok</x>INVALID<x>ok</x>") }

        var e: Throwable? = null
        val parser = object : StreamParser() {
            override fun onParseError(errorMessage: String) {
                e = XmlException(errorMessage)
            }

            override fun onNextElement(element: Element) {
                println("stream element: $element")
            }

            override fun onStreamClosed() {
                println("stream closed")
            }

            override fun onStreamStarted(attrs: Map<String, String>) {
                println("stream opened")
            }
        }

        parser.parse(
            "<stream:stream>\n" +
                    "<x>ok</x>INVALID<x>ok</x>\n" +
                    "</stream:stream>"
        )
        assertIs<XmlException>(e).let {
            assertEquals("Invalid characters between tags.", it.message)
        }
    }

}