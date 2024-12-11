
package lib.takina.core.eventbus

import lib.takina.core.Takina
import lib.takina.core.builder.createConfiguration
import lib.takina.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertNotNull

class EventBusTest {

	class Event01 : Event(TYPE) {

		companion object : EventDefinition<Event01> {

			override val TYPE = "Event01"
		}
	}

	@Test
	fun testEventBus() {
		val takina = Takina(createConfiguration {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
		})
		val eventBus = EventBus(takina)

		var resultH1: Event01? = null
		var resultH2: Event01? = null
		var resultH3: Event01? = null

		eventBus.register(Event01.TYPE, object : EventHandler<Event01> {
			override fun onEvent(event: Event01) {
				resultH1 = event
			}
		})
		eventBus.register<Event01>(Event01.TYPE) { resultH2 = it }
		eventBus.register(Event01) { resultH3 = it }


		eventBus.fire(Event01())

		assertNotNull(resultH1)
		assertNotNull(resultH2)
		assertNotNull(resultH3)

	}

}


