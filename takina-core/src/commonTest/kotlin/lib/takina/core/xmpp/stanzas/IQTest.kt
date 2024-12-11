
package lib.takina.core.xmpp.stanzas

import lib.takina.core.xml.element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class IQTest {

	@Test
	fun testTypeUnavailable() {
		val e = element("iq") {
			attribute("type", "set")
		}
		val p = wrap<IQ>(e)
		assertEquals(IQType.Set, p.type)
	}

	@Test
	fun testTypeSet() {
		val p = iq { type = IQType.Error }
		assertEquals(IQType.Error, p.type)
		assertEquals("error", p.attributes["type"])
		p.type = IQType.Get
		assertEquals(IQType.Get, p.type)
		assertEquals("get", p.attributes["type"])
	}

	@Test
	fun testTypeNull() {
		val e = element("iq") { }
		val p = wrap<IQ>(e)
		try {
			p.type
			fail("Exception should be throw")
		} catch (e: XMPPException) {
			assertEquals(ErrorCondition.BadRequest, e.condition)
		}
	}

	@Test
	fun testTypeUnknown() {
		val e = element("iq") {
			attribute("type", "x")
		}
		val p = wrap<IQ>(e)
		try {
			p.type
			fail("Exception should be throw")
		} catch (e: XMPPException) {
			assertEquals(ErrorCondition.BadRequest, e.condition)
		}
	}

}