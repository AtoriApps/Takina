
package lib.takina.core.xmpp.stanzas

import lib.takina.core.xml.element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class PresenceTest {

	@Test
	fun testTypeUnavailable() {
		val e = element("presence") {
			attribute("type", "unavailable")
		}
		val p = wrap<Presence>(e)
		assertEquals(PresenceType.Unavailable, p.type)
	}

	@Test
	fun testTypeSet() {
		val p = presence { type = PresenceType.Error }
		assertEquals(PresenceType.Error, p.type)
		assertEquals("error", p.attributes["type"])
		p.type = PresenceType.Probe
		assertEquals(PresenceType.Probe, p.type)
		assertEquals("probe", p.attributes["type"])
		p.type = null
		assertNull(p.attributes["type"])
	}

	@Test
	fun testShowSet() {
		val p = presence {
			"show" { +"dnd" }
		}
		assertEquals(Show.DnD, p.show)
		p.show = Show.Away
		assertEquals(Show.Away, p.show)
		assertEquals("away", p.getFirstChild("show")?.value)
	}

	@Test
	fun testPrioritySet() {
		val p = presence {}
		p.priority = 17
		assertEquals("17", p.getFirstChild("priority")?.value)
		assertEquals(17, p.priority)
	}

	@Test
	fun testTypeNull() {
		val e = element("presence") { }
		val p = wrap<Presence>(e)
		assertNull(p.type)
	}

	@Test
	fun testTypeUnknown() {
		val e = element("presence") {
			attribute("type", "x")
		}
		val p = wrap<Presence>(e)
		try {
			p.type
			fail("Exception should be throw")
		} catch (e: XMPPException) {
			assertEquals(ErrorCondition.BadRequest, e.condition)
		}
	}

}