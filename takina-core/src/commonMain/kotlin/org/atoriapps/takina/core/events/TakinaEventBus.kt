package org.atoriapps.takina.core.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.reflect.KClass

class EventSubscription internal constructor(
    val id: Long,
    val eventClass: KClass<out TakinaEvent>,
)

class TakinaEventBus {
    private var sequence: Long = 0
    private val handlers = linkedMapOf<KClass<out TakinaEvent>, MutableMap<Long, (TakinaEvent) -> Unit>>()
    private val stream = MutableSharedFlow<TakinaEvent>(replay = 1, extraBufferCapacity = 256)

    fun emit(event: TakinaEvent) {
        stream.tryEmit(event)
        handlers[event::class]?.values?.toList()?.forEach { it(event) }
    }

    fun <T : TakinaEvent> on(type: KClass<T>, handler: T.() -> Unit): EventSubscription {
        val id = ++sequence
        val typed: (TakinaEvent) -> Unit = { event ->
            if (type.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                (event as T).handler()
            }
        }
        handlers.getOrPut(type) { linkedMapOf() }[id] = typed
        return EventSubscription(id = id, eventClass = type)
    }

    fun <T : TakinaEvent> on(type: TakinaEventType<T>, handler: T.() -> Unit): EventSubscription = on(type.kClass, handler)

    fun <T : TakinaEvent> once(type: KClass<T>, handler: T.()  -> Unit): EventSubscription {
        var subscription: EventSubscription? = null
        subscription = on(type) {
            removeOn(subscription!!)
            handler()
        }
        return subscription
    }

    fun <T : TakinaEvent> once(type: TakinaEventType<T>, handler: T.() -> Unit): EventSubscription = once(type.kClass, handler)

    fun removeOn(subscription: EventSubscription) {
        handlers[subscription.eventClass]?.remove(subscription.id)
    }

    fun <T : TakinaEvent> flow(type: KClass<T>): Flow<T> = stream.filterIsInstance(type)

    fun <T : TakinaEvent> flow(type: TakinaEventType<T>): Flow<T> = flow(type.kClass)
}

inline fun <reified T : TakinaEvent> TakinaEventBus.on(noinline handler: (T) -> Unit): EventSubscription = on(T::class, handler)
inline fun <reified T : TakinaEvent> TakinaEventBus.once(noinline handler: (T) -> Unit): EventSubscription = once(T::class, handler)
inline fun <reified T : TakinaEvent> TakinaEventBus.flow(): Flow<T> = flow(T::class)
