
package takina.convene

import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventBusInterface
import lib.takina.core.eventbus.EventHandler
import lib.takina.core.eventbus.NoContextEventBus
import java.util.concurrent.ConcurrentHashMap

class MultiEventBus : NoContextEventBus() {

	private val eventBuses: MutableSet<EventBusInterface> = ConcurrentHashMap.newKeySet()

	override fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>> = ConcurrentHashMap()

	override fun createHandlersSet(): MutableSet<EventHandler<*>> = ConcurrentHashMap.newKeySet()

	override fun updateBeforeFire(event: Event) {}

	private val handler = object : EventHandler<Event> {
		override fun onEvent(event: Event) {
			this@MultiEventBus.fire(event)
		}
	}

	fun add(eventBus: EventBusInterface) {
		val added = eventBuses.add(eventBus)
		if (added) {
			eventBus.register(handler = handler)
		}
	}

	fun remove(eventBus: EventBusInterface) {
		eventBus.unregister(handler)
		eventBuses.remove(eventBus)
	}

}