package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.utils.LanguageUtils.clzName
import org.atoriapps.takina.core.utils.LogUtils
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

interface TakinaEventHandler<in T : TakinaEvent> {
    fun onEvent(event: T, context: TakinaContext)
}

abstract class TakinaEvent {
    /*var eventTime: Instant = Instant.DISTANT_PAST
        internal set*/
}

interface TakinaEventDescriber<EVENT : TakinaEvent> {
    // 啊
    fun getEventTokens(): List<String>

    // 呃
    fun getEventType(): KClass<EVENT>
}

interface TakinaEventBusInterface {
    fun emit(takinaEvent: TakinaEvent)

    fun <EVENT : TakinaEvent> on(takinaEventClz: KClass<EVENT>, handler: TakinaEventHandler<EVENT>)
    fun <EVENT : TakinaEvent> removeOn(takinaEventClz: KClass<EVENT>, handler: TakinaEventHandler<EVENT>)

    fun shutdown()
}

abstract class AbstractTakinaEventBus(val context: TakinaContext) : TakinaEventBusInterface {
    private val lambdaToHandler = mutableMapOf<Any, TakinaEventHandler<TakinaEvent>>()

    fun <EVENT : TakinaEvent> on(takinaEventClz: TakinaEventDescriber<EVENT>, handler: TakinaEventHandler<EVENT>) {
        on(takinaEventClz.getEventType(), handler)
    }

    fun <EVENT : TakinaEvent> removeOn(takinaEventClz: TakinaEventDescriber<EVENT>, handler: TakinaEventHandler<EVENT>) {
        removeOn(takinaEventClz.getEventType(), handler)
    }

    fun <EVENT:TakinaEvent> on(takinaEventClz: TakinaEventDescriber<EVENT>, handler: (EVENT) -> Unit) {
        on(takinaEventClz.getEventType(), handler)
    }

    fun <EVENT:TakinaEvent> removeOn(takinaEventClz: TakinaEventDescriber<EVENT>, handler: (EVENT) -> Unit) {
        removeOn(takinaEventClz.getEventType(), handler)
    }

    fun <EVENT : TakinaEvent> on(takinaEventClz: KClass<EVENT>, handler: (EVENT) -> Unit) {
        val wrappedHandler = object : TakinaEventHandler<EVENT> {
            override fun onEvent(event: EVENT, context: TakinaContext) {
                // Lambda 不传入 Context，不然使用不够轻松
                handler(event)
            }
        }
        lambdaToHandler[handler] = wrappedHandler as TakinaEventHandler<TakinaEvent>
        on(takinaEventClz, wrappedHandler)
    }

    fun <EVENT : TakinaEvent> removeOn(takinaEventClz: KClass<EVENT>, handler: (EVENT) -> Unit) {
        val wrappedHandler = lambdaToHandler.remove(handler) as? TakinaEventHandler<EVENT>
        wrappedHandler?.let { removeOn(takinaEventClz, it) }
    }

    /*@Deprecated("正在升级新范式")
    inline fun <reified EVENT : TakinaEvent> on(noinline handler: (EVENT) -> Unit) {
        val wrappedHandler = object : TakinaEventHandler<EVENT> {
            override fun onEvent(event: EVENT, context: TakinaContext) {
                // Lambda 不传入 Context，不然使用不够轻松
                handler(event)
            }
        }
        lambdaToHandler[handler] = wrappedHandler as TakinaEventHandler<TakinaEvent>
        on(EVENT::class, wrappedHandler)
    }

    @Deprecated("正在升级新范式")
    inline fun <reified EVENT : TakinaEvent> removeOn(noinline handler: (EVENT) -> Unit) {
        val wrappedHandler = lambdaToHandler.remove(handler) as? TakinaEventHandler<EVENT>
        wrappedHandler?.let { removeOn(EVENT::class, it) }
    }*/

    /*@Deprecated("正在升级新范式")
    inline fun <reified EVENT : TakinaEvent> on(handler: TakinaEventHandler<EVENT>) {
        on(EVENT::class, handler)
    }

    @Deprecated("正在升级新范式")
    inline fun <reified EVENT : TakinaEvent> removeOn(handler: TakinaEventHandler<EVENT>) {
        removeOn(EVENT::class, handler)
    }*/
}

expect class TakinaEventBus(context: TakinaContext) : AbstractTakinaEventBus