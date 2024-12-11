
package lib.takina.core.xmpp

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdGeneratorTest {

	@Test
	fun testUIDGenerator() {
		val g = lib.takina.core.xmpp.IdGenerator()
		val v1 = g.nextId()
		val v2 = g.nextId()
		assertTrue(v1.isNotEmpty())
		assertTrue(v1.isNotBlank())
		assertTrue(v2.isNotEmpty())
		assertTrue(v2.isNotBlank())
		assertNotEquals(v1, v2)
	}
}