
package lib.takina.core.eventbus

interface EventBusInterface {

	companion object {

		const val ALL_EVENTS = "EventBus#ALL_EVENTS"
	}

	fun fire(event: Event)

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: EventHandler<T>)

	fun <T : Event> register(definition: EventDefinition<T>, handler: EventHandler<T>)

	fun <T : Event> register(eventType: String = ALL_EVENTS, handler: (T) -> Unit)

	fun <T : Event> register(definition: EventDefinition<T>, handler: (T) -> Unit)

	fun unregister(eventType: String = ALL_EVENTS, handler: EventHandler<*>)

	fun unregister(definition: EventDefinition<*>, handler: EventHandler<*>)

	fun unregister(handler: EventHandler<*>)

}

inline fun <T : Event> handler(crossinline handler: (T) -> Unit): EventHandler<T> = object : EventHandler<T> {
	override fun onEvent(event: T) {
		handler.invoke(event)
	}
}

inline fun <T : Event> EventDefinition<T>.handler(crossinline handler: (T) -> Unit): EventHandler<T> =
	object : EventHandler<T> {
		override fun onEvent(event: T) {
			handler.invoke(event)
		}
	}