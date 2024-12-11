
package lib.takina.core.modules

import lib.takina.core.Context
import lib.takina.core.configuration.Configuration
import lib.takina.core.requests.RequestBuilderFactory
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.FullJID
import lib.takina.core.xmpp.modules.auth.SASLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModulesManagerTest {

	class Module01(override val context: Context) : XmppModule {

		override val type = "Module01"
		override val criteria: Criteria = Criterion.name("iq")
		override val features: Array<String> = arrayOf("1", "2")

		override fun process(element: Element) {
		}
	}

	class Module02(override val context: Context) : XmppModule {

		override val type = "Module02"
		override val criteria = Criterion.name("msg")
		override val features = arrayOf("a", "b")

		override fun process(element: Element) {
		}
	}

	@Test
	fun test01() {
		val mm = ModulesManager()
		mm.context = object : Context {

			override val eventBus: lib.takina.core.eventbus.EventBus
				get() = TODO("not implemented")
			override val config: Configuration
				get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
			override val writer: lib.takina.core.PacketWriter
				get() = TODO("not implemented")
			override val modules: ModulesManager
				get() = TODO("not implemented")
			override val request: RequestBuilderFactory
				get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
			override val authContext: SASLContext
				get() = TODO("Not yet implemented")
			override val boundJID: FullJID
				get() = TODO("Not yet implemented")
		}
		mm.register(Module01(mm.context))
		mm.register(Module02(mm.context))

		assertTrue(
			arrayOf("1", "2", "a", "b").sortedArray() contentDeepEquals mm.getAvailableFeatures().sortedArray()
		)

		assertEquals(0, mm.getModulesFor(element("presence") {}).size)
		assertEquals(1, mm.getModulesFor(element("iq") {}).size)
		assertEquals(1, mm.getModulesFor(element("msg") {}).size)
		assertEquals(
			"Module01", mm.getModulesFor(element("iq") {}).first().type
		)

	}

}