package org.atoriapps.takina.core.events

import kotlin.reflect.KClass

interface EventHandler<in T : Event> {
    fun onEvent(event: T)
}

abstract class Event {

}

interface IEventBus {
    fun emit(event: Event)

    fun <EVENT : Event> on(eventClz: KClass<out Event>, handler: EventHandler<EVENT>)
    fun <EVENT : Event> removeOn(eventClz: KClass<out Event>, handler: EventHandler<EVENT>)

    fun shutdown()
}

abstract class AbstractEventBus : IEventBus {
    val lambdaToHandler = mutableMapOf<Any, EventHandler<Event>>()

    inline fun <reified EVENT : Event> on(noinline handler: (EVENT) -> Unit) {
        val wrappedHandler = object : EventHandler<EVENT> {
            override fun onEvent(event: EVENT) {
                handler(event)
            }
        }
        lambdaToHandler[handler] = wrappedHandler as EventHandler<Event>
        on(EVENT::class, wrappedHandler)
    }

    inline fun <reified EVENT : Event> removeOn(noinline handler: (EVENT) -> Unit) {
        val wrappedHandler = lambdaToHandler.remove(handler) as? EventHandler<EVENT>
        wrappedHandler?.let { removeOn(EVENT::class, it) }
    }

    inline fun <reified EVENT : Event> on(handler: EventHandler<EVENT>) {
        on(EVENT::class, handler)
    }

    inline fun <reified EVENT : Event> removeOn(handler: EventHandler<EVENT>) {
        removeOn(EVENT::class, handler)
    }
}

expect class EventBus : AbstractEventBus