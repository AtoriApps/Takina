package org.atoriapps.takina.core.events

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.atoriapps.takina.core.TakinaContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass

interface TakinaEventHandler<in T : TakinaEvent> {
    fun onEvent(event: T, context: TakinaContext)
}

abstract class TakinaEvent {
    abstract val description: String
}

interface TakinaEventDescriber<EVENT : TakinaEvent> {
    val eventTokens: List<String>

    val eventType: KClass<EVENT>
}

interface TakinaEventBusInterface {
    fun emit(takinaEvent: TakinaEvent)

    fun <EVENT : TakinaEvent> on(takinaEventClz: KClass<EVENT>, handler: TakinaEventHandler<EVENT>)
    fun <EVENT : TakinaEvent> removeOn(takinaEventClz: KClass<EVENT>, handler: TakinaEventHandler<EVENT>)

    fun shutdown()

    var enableEventLog: Boolean
}

data class TakinaEventFlowBackpressure(
    val capacity: Int = Channel.BUFFERED,
    val overflow: BufferOverflow = BufferOverflow.SUSPEND,
) {
    init {
        require(capacity >= 0 || capacity == Channel.BUFFERED || capacity == Channel.CONFLATED || capacity == Channel.UNLIMITED) {
            "capacity 必须为非负数或 Channel 常量"
        }
    }
}

abstract class AbstractTakinaEventBus(val context: TakinaContext) : TakinaEventBusInterface {
    private val lambdaToHandler = linkedMapOf<Any, TakinaEventHandler<TakinaEvent>>()

    @Volatile
    override var enableEventLog = true

    fun <EVENT : TakinaEvent> on(takinaEventClz: TakinaEventDescriber<EVENT>, handler: TakinaEventHandler<EVENT>) {
        on(takinaEventClz.eventType, handler)
    }

    fun <EVENT : TakinaEvent> removeOn(takinaEventClz: TakinaEventDescriber<EVENT>, handler: TakinaEventHandler<EVENT>) {
        removeOn(takinaEventClz.eventType, handler)
    }

    fun <EVENT : TakinaEvent> on(takinaEventClz: TakinaEventDescriber<EVENT>, handler: (EVENT) -> Unit) {
        on(takinaEventClz.eventType, handler)
    }

    fun <EVENT : TakinaEvent> removeOn(takinaEventClz: TakinaEventDescriber<EVENT>, handler: (EVENT) -> Unit) {
        removeOn(takinaEventClz.eventType, handler)
    }

    fun <EVENT : TakinaEvent> on(takinaEventClz: KClass<EVENT>, handler: (EVENT) -> Unit) {
        val wrappedHandler = object : TakinaEventHandler<EVENT> {
            override fun onEvent(event: EVENT, context: TakinaContext) {
                // Lambda 不传入 Context，不然使用不够轻松
                handler(event)
            }
        }
        synchronized(lambdaToHandler) { lambdaToHandler[handler] = wrappedHandler as TakinaEventHandler<TakinaEvent> }
        on(takinaEventClz, wrappedHandler)
    }

    fun <EVENT : TakinaEvent> removeOn(takinaEventClz: KClass<EVENT>, handler: (EVENT) -> Unit) {
        val wrappedHandler = synchronized(lambdaToHandler) { lambdaToHandler.remove(handler) } as? TakinaEventHandler<EVENT>
        wrappedHandler?.let { removeOn(takinaEventClz, it) }
    }

    fun <EVENT : TakinaEvent> flow(
        takinaEventClz: KClass<EVENT>,
        backpressure: TakinaEventFlowBackpressure = TakinaEventFlowBackpressure(),
    ): Flow<EVENT> = callbackFlow {
        val handler = object : TakinaEventHandler<EVENT> {
            override fun onEvent(event: EVENT, context: TakinaContext) {
                trySend(event)
            }
        }

        on(takinaEventClz, handler)
        awaitClose { removeOn(takinaEventClz, handler) }
    }.buffer(capacity = backpressure.capacity, onBufferOverflow = backpressure.overflow)

    fun <EVENT : TakinaEvent> flow(
        takinaEventClz: TakinaEventDescriber<EVENT>,
        backpressure: TakinaEventFlowBackpressure = TakinaEventFlowBackpressure(),
    ): Flow<EVENT> = flow(takinaEventClz.eventType, backpressure)

    inline fun <reified EVENT : TakinaEvent> flow(
        backpressure: TakinaEventFlowBackpressure = TakinaEventFlowBackpressure(),
    ): Flow<EVENT> = flow(EVENT::class, backpressure)
}

expect class TakinaEventBus(context: TakinaContext) : AbstractTakinaEventBus
