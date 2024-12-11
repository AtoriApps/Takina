
package lib.takina.core.eventbus

import lib.takina.core.AbstractTakina
import lib.takina.core.TickEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

actual class EventBus actual constructor(context: AbstractTakina) : AbstractEventBus(context) {

	override fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>> = ConcurrentHashMap()

	override fun createHandlersSet(): MutableSet<EventHandler<*>> = ConcurrentHashMap.newKeySet()

	enum class Mode { NoThread,
		ThreadPerEvent,
		ThreadPerHandler
	}

	private var threadCounter = 0

	var mode = Mode.NoThread

	private val executor = Executors.newSingleThreadExecutor { r ->
		val t = Thread(r)
		t.name = "EventBus-Thread-" + ++threadCounter
		t.isDaemon = true
		t
	}

	@Suppress("UNCHECKED_CAST")
	private fun fireNoThread(event: Event, handlers: Collection<EventHandler<*>>) {
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as EventHandler<Event>).onEvent(event)
			} catch (e: Exception) {
				log.warning(e) { "Problem on handling event ${event.eventType}" }
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun fireThreadPerEvent(event: Event, handlers: Collection<EventHandler<*>>) {
		executor.execute {
			handlers.forEach { eventHandler ->
				try {
					(eventHandler as EventHandler<Event>).onEvent(event)
				} catch (e: Exception) {
					log.warning(e) { "Problem on handling event ${event.eventType}" }
				}
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun fireThreadPerHandler(event: Event, handlers: Collection<EventHandler<*>>) {
		handlers.forEach { eventHandler ->
			executor.execute {
				try {
					(eventHandler as EventHandler<Event>).onEvent(event)
				} catch (e: Exception) {
					log.warning(e) { "Problem on handling event ${event.eventType}" }
				}
			}
		}
	}

	override fun fire(event: Event, handlers: Collection<EventHandler<*>>) {
		if(event !is TickEvent)
		log.finest { "Firing event $event with ${handlers.size} handlers" }

		when (mode) {
			Mode.NoThread -> fireNoThread(event, handlers)
			Mode.ThreadPerEvent -> fireThreadPerEvent(event, handlers)
			Mode.ThreadPerHandler -> fireThreadPerHandler(event, handlers)
		}

	}

}
