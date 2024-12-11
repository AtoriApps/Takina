
package lib.takina.core.eventbus

import lib.takina.core.Takina
import lib.takina.core.builder.createConfiguration
import lib.takina.core.eventbus.EventBusInterface.Companion.ALL_EVENTS
import lib.takina.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventBusJvmTest {

	@Test
	fun testBasic() {

		val takina = Takina(createConfiguration {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
		})
		val eventBus = EventBus(takina)
		val responses = mutableListOf<Any>()

		val handler = object : EventHandler<TestEvent> {
			@Override
			override fun onEvent(event: TestEvent) {
				responses.add(event.value!!)
			}
		}

		eventBus.register(TestEvent.TYPE, handler)

		eventBus.fire(TestEvent("01"))
		eventBus.fire(TestEvent("02"))
		eventBus.fire(TestEvent("03"))
		eventBus.fire(TestEvent("04"))
		eventBus.fire(TestEvent("05"))

		assertTrue(responses.contains("01"))
		assertTrue(responses.contains("02"))
		assertTrue(responses.contains("03"))
		assertTrue(responses.contains("04"))
		assertTrue(responses.contains("05"))
		assertFalse(responses.contains("06"))

		eventBus.unregister(handler)

		eventBus.fire(TestEvent("06"))
		assertFalse(responses.contains("06"))

		eventBus.register(ALL_EVENTS, handler)

		eventBus.fire(TestEvent("07"))
		assertTrue(responses.contains("07"))

	}

	internal class TestEvent(val value: String?) : Event(TYPE) {

		companion object : EventDefinition<TestEvent> {

			override val TYPE = "test"
		}

	}

}
