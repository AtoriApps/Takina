package org.atoriapps.takina.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

class EventSubscription internal constructor(
    val id: Long,
    val eventClass: KClass<out TakinaEvent>,
    val boundBus: TakinaEventBus,
) {
    fun remove() = boundBus.remove(this)
}

enum class EventDispatchMode {
    ALL_TOGETHER,
    PER_EVENT_COROUTINE,
    PER_HANDLER_COROUTINE,
}

enum class EventHandlerFailureMode {
    EMIT_FAILURE_EVENT,
    IGNORE,
    RE_THROW,
}

data class EventBusPolicy(
    val dispatchMode: EventDispatchMode = EventDispatchMode.ALL_TOGETHER,
    val handlerFailureMode: EventHandlerFailureMode = EventHandlerFailureMode.EMIT_FAILURE_EVENT,
)

data class EventHandlerFailedEvent(
    val originalEventId: String,
    val originalEventType: String,
    val subscribedEventType: String,
    val handlerId: Long,
    val reason: String,
) : BasicTakinaEvent("EventHandlerFailedEvent") {
    companion object : StaticEventProvider<EventHandlerFailedEvent>(EventHandlerFailedEvent::class)
}

class TakinaEventBus {
    private data class RegisteredHandler(
        val id: Long,
        val subscribedType: KClass<out TakinaEvent>,
        val callback: (TakinaEvent) -> Unit,
    )

    private val stateMutex = Mutex()
    private var sequence: Long = 0
    private val exactHandlers = linkedMapOf<KClass<out TakinaEvent>, MutableMap<Long, (TakinaEvent) -> Unit>>()
    private val subtypeHandlers = linkedMapOf<KClass<out TakinaEvent>, MutableMap<Long, (TakinaEvent) -> Unit>>()
    private val stream = MutableSharedFlow<TakinaEvent>(replay = 1, extraBufferCapacity = 256)
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var policyState: EventBusPolicy = EventBusPolicy()

    var policy: EventBusPolicy
        get() = runBlocking { stateMutex.withLock { policyState } }
        set(value) {
            runBlocking {
                stateMutex.withLock {
                    policyState = value
                }
            }
        }

    fun emit(event: TakinaEvent) {
        publishToFlow(event)
        val snapshot = snapshotHandlersFor(event)
        val activePolicy = policy
        dispatch(event, snapshot, activePolicy)
    }

    private fun publishToFlow(event: TakinaEvent) {
        if (!stream.tryEmit(event)) dispatchScope.launch { stream.emit(event) }
    }

    private fun snapshotHandlersFor(event: TakinaEvent): List<RegisteredHandler> = runBlocking {
        stateMutex.withLock {
            val exact = exactHandlers[event::class].orEmpty().entries.map { (id, callback) ->
                RegisteredHandler(
                    id = id,
                    subscribedType = event::class,
                    callback = callback,
                )
            }
            val subtypes = subtypeHandlers.entries.asSequence()
                .filter { (subscribedType, _) -> subscribedType.isInstance(event) }
                .flatMap { (subscribedType, mapped) ->
                    mapped.entries.asSequence().map { (id, callback) ->
                        RegisteredHandler(
                            id = id,
                            subscribedType = subscribedType,
                            callback = callback,
                        )
                    }
                }
                .toList()
            exact + subtypes
        }
    }

    private fun dispatch(event: TakinaEvent, targets: List<RegisteredHandler>, activePolicy: EventBusPolicy) {
        when (activePolicy.dispatchMode) {
            EventDispatchMode.ALL_TOGETHER -> {
                targets.forEach { target ->
                    invokeHandler(target, event, activePolicy, allowFailureEvent = event !is EventHandlerFailedEvent)
                }
            }

            EventDispatchMode.PER_EVENT_COROUTINE -> {
                dispatchScope.launch {
                    targets.forEach { target ->
                        invokeHandler(target, event, activePolicy, allowFailureEvent = event !is EventHandlerFailedEvent)
                    }
                }
            }

            EventDispatchMode.PER_HANDLER_COROUTINE -> {
                targets.forEach { target ->
                    dispatchScope.launch {
                        invokeHandler(target, event, activePolicy, allowFailureEvent = event !is EventHandlerFailedEvent)
                    }
                }
            }
        }
    }

    private fun invokeHandler(target: RegisteredHandler, event: TakinaEvent, activePolicy: EventBusPolicy, allowFailureEvent: Boolean) {
        try {
            target.callback(event)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            handleHandlerFailure(
                activePolicy = activePolicy,
                event = event,
                target = target,
                throwable = t,
                allowFailureEvent = allowFailureEvent,
            )
        }
    }

    private fun handleHandlerFailure(
        activePolicy: EventBusPolicy, event: TakinaEvent,
        target: RegisteredHandler, throwable: Throwable, allowFailureEvent: Boolean,
    ) {
        when (activePolicy.handlerFailureMode) {
            EventHandlerFailureMode.IGNORE -> Unit
            EventHandlerFailureMode.RE_THROW -> throw throwable
            EventHandlerFailureMode.EMIT_FAILURE_EVENT -> {
                if (!allowFailureEvent) return
                val failureEvent = EventHandlerFailedEvent(
                    originalEventId = event.eventId,
                    originalEventType = event.type,
                    subscribedEventType = target.subscribedType.simpleName ?: target.subscribedType.toString(),
                    handlerId = target.id,
                    reason = throwable.message ?: throwable::class.simpleName ?: "handler failed",
                )
                emit(failureEvent)
            }
        }
    }

    private fun <T : TakinaEvent> registerHandler(
        registry: MutableMap<KClass<out TakinaEvent>, MutableMap<Long, (TakinaEvent) -> Unit>>, type: KClass<T>, handler: T.() -> Unit,
    ): EventSubscription {
        val typed: (TakinaEvent) -> Unit = { event ->
            if (type.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                (event as T).handler()
            }
        }

        val id = runBlocking {
            stateMutex.withLock {
                val nextId = ++sequence
                registry.getOrPut(type) { linkedMapOf() }[nextId] = typed
                nextId
            }
        }

        return EventSubscription(id, type, this)
    }

    fun <T : TakinaEvent> on(type: KClass<T>, handler: T.() -> Unit) = registerHandler(exactHandlers, type, handler)

    fun <T : TakinaEvent> on(type: TakinaEventProvider<T>, handler: T.() -> Unit) = on(type.eventClass, handler)

    fun <T : TakinaEvent> onSubtypes(type: KClass<T>, handler: T.() -> Unit) = registerHandler(subtypeHandlers, type, handler)

    fun <T : TakinaEvent> onSubtypes(type: TakinaEventProvider<T>, handler: T.() -> Unit) = onSubtypes(type.eventClass, handler)

    fun onAny(handler: TakinaEvent.() -> Unit) = onSubtypes(TakinaEvent::class, handler)

    fun <T : TakinaEvent> once(type: KClass<T>, handler: T.() -> Unit): EventSubscription {
        var subscription: EventSubscription? = null

        subscription = on(type) {
            subscription!!.remove()
            handler()
        }

        return subscription
    }

    fun <T : TakinaEvent> once(type: TakinaEventProvider<T>, handler: T.() -> Unit) = once(type.eventClass, handler)

    fun <T : TakinaEvent> onceSubtypes(type: KClass<T>, handler: T.() -> Unit): EventSubscription {
        var subscription: EventSubscription? = null

        subscription = onSubtypes(type) {
            subscription!!.remove()
            handler()
        }

        return subscription
    }

    fun <T : TakinaEvent> onceSubtypes(type: TakinaEventProvider<T>, handler: T.() -> Unit) = onceSubtypes(type.eventClass, handler)

    fun onceAny(handler: TakinaEvent.() -> Unit) = onceSubtypes(TakinaEvent::class, handler)

    fun remove(subscription: EventSubscription) {
        runBlocking {
            stateMutex.withLock {
                exactHandlers[subscription.eventClass]?.remove(subscription.id)
                subtypeHandlers[subscription.eventClass]?.remove(subscription.id)
            }
        }
    }

    fun <T : TakinaEvent> flow(type: KClass<T>): Flow<T> = stream.filterIsInstance(type)

    fun <T : TakinaEvent> flow(type: TakinaEventProvider<T>) = flow(type.eventClass)
}
