
package lib.takina.core.xmpp.stanzas

import lib.takina.core.xml.element
import lib.takina.core.xmpp.toJID
import kotlin.test.*

class StanzaTest {

	@Test
	fun equalsTestAndHashCode() {
		val s1 = iq {
			to = "a@b.c/d".toJID()
			"x" {
				xmlns = "1:2:3"
			}
		}
		val e1 = element("iq") {
			attribute("id", s1.attributes["id"]!!)
			attribute("to", "a@b.c/d")
			"x" {
				xmlns = "1:2:3"
			}
		}

		val e2 = element("iq") {
			attribute("id", s1.attributes["id"]!!)
			attribute("to", "a@b.c/d")
			"x" {
				xmlns = "1:2:3"
				+"x"
			}
		}

		assertTrue(s1.equals(e1))
		assertTrue(e1.equals(s1))

		assertNotSame(s1, e1)
		assertEquals(s1, e1)

		assertNotSame(s1, e2)
		assertNotEquals(e1, e2)

		assertEquals(s1.hashCode(), s1.hashCode())
		assertEquals(s1.hashCode(), e1.hashCode())
	}

	@Test
	fun testTo() {
		val e = element("message") {
			attribute("to", "aaa@bb.c/d")
		}
		val s = wrap<Message>(e)
		assertEquals("aaa@bb.c/d".toJID(), s.to)
		s.to = "plll@qa.pl/sss".toJID()
		assertEquals("plll@qa.pl/sss".toJID(), s.to)
		assertEquals("plll@qa.pl/sss", s.attributes["to"])
	}

	@Test
	fun testFrom() {
		val e = element("message") {
			attribute("from", "aaa@bb.c/d")
		}
		val s = wrap<Message>(e)
		assertEquals("aaa@bb.c/d".toJID(), s.from)
		s.from = "plll@qa.pl/sss".toJID()
		assertEquals("plll@qa.pl/sss".toJID(), s.from)
		assertEquals("plll@qa.pl/sss", s.attributes["from"])
	}

}