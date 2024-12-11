
package lib.takina.core.xmpp

import lib.takina.core.exceptions.TakinaException
import lib.takina.core.xml.ElementImpl
import lib.takina.core.xmpp.stanzas.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class StanzaBuildersKtTest {

	@Test
	fun testWrap() {
		assertTrue { wrap<Stanza<*>>(ElementImpl("iq")) is IQ }
		assertTrue { wrap<Stanza<*>>(ElementImpl("message")) is Message }
		assertTrue { wrap<Stanza<*>>(ElementImpl("presence")) is Presence }

		try {
			assertTrue { wrap<Stanza<*>>(ElementImpl("UNKNOWN")) is IQ }
			fail("Should throw exception!")
		} catch (_: TakinaException) {
		}

		assertFalse { wrap<Stanza<*>>(ElementImpl("presence")) is IQ }
	}
}