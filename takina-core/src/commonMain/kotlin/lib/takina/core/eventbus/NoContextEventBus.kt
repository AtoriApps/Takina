
package lib.takina.core.eventbus

import lib.takina.core.TickEvent
import lib.takina.core.eventbus.EventBusInterface.Companion.ALL_EVENTS
import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerFactory

@Suppress("LeakingThis")
abstract class NoContextEventBus : EventBusInterface {

	protected val log = LoggerFactory.logger("lib.takina.core.eventbus.EventBus")

	protected var handlersMap: MutableMap<String, MutableSet<EventHandler<*>>> = createHandlersMap()

	protected abstract fun createHandlersMap(): MutableMap<String, MutableSet<EventHandler<*>>>

	protected abstract fun createHandlersSet(): MutableSet<EventHandler<*>>

	private fun getHandlers(eventType: String): Collection<EventHandler<*>> {
		val result = HashSet<EventHandler<*>>()

		val a = handlersMap[ALL_EVENTS]
		if (a != null && a.isNotEmpty()) {
			result.addAll(a)
		}

		val h = handlersMap[eventType]
		if (h != null && h.isNotEmpty()) {
			result.addAll(h)
		}

		return result
	}

	abstract fun updateBeforeFire(event: Event)

	override fun fire(event: Event) {
		updateBeforeFire(event)
		val handlers = getHandlers(event.eventType)
		fire(event, handlers)
	}

	@Suppress("UNCHECKED_CAST")
	protected open fun fire(event: Event, handlers: Collection<EventHandler<*>>) {
		if (event !is TickEvent || log.isLoggable(Level.FINEST)) log.fine { "Firing event $event with ${handlers.size} handlers" }
		handlers.forEach { eventHandler ->
			try {
				(eventHandler as EventHandler<Event>).onEvent(event)
			} catch (e: Exception) {
				log.warning(e) { "Problem on handling event ${event.eventType}" }
			}
		}
	}

	override fun <T : Event> register(eventType: String, handler: EventHandler<T>) {
		var handlers = handlersMap[eventType]
		if (handlers == null) {
			handlers = createHandlersSet()
			handlersMap[eventType] = handlers
		}
		handlers.add(handler)
	}

	override fun <T : Event> register(definition: EventDefinition<T>, handler: EventHandler<T>) =
		register(definition.TYPE, handler)

	override fun <T : Event> register(eventType: String, handler: (T) -> Unit) {
		register(eventType, object : EventHandler<T> {
			override fun onEvent(event: T) {
				handler.invoke(event)
			}
		})
	}

	override fun <T : Event> register(definition: EventDefinition<T>, handler: (T) -> Unit) =
		register(definition.TYPE, handler)

	override fun unregister(eventType: String, handler: EventHandler<*>) {
		handlersMap[eventType]?.remove(handler)
	}

	override fun unregister(definition: EventDefinition<*>, handler: EventHandler<*>) =
		unregister(definition.TYPE, handler)

	override fun unregister(handler: EventHandler<*>) {
		for ((_, handlers) in handlersMap) {
			handlers.remove(handler)
		}
	}
}