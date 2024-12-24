package org.atoriapps.takina.core.events

import kotlinx.coroutines.*
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.utils.LanguageUtils.clzName
import org.atoriapps.takina.core.utils.LogUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

actual class TakinaEventBus actual constructor(context: TakinaContext) : AbstractTakinaEventBus(context) {
    companion object {
        private const val TAG = "EventBus.JVM"
    }

    enum class Mode {
        NoThread,
        ThreadPerEvent,
        ThreadPerHandler
    }

    // 存储事件类型及其监听器列表
    private val handlers = ConcurrentHashMap<KClass<out TakinaEvent>, CopyOnWriteArrayList<TakinaEventHandler<TakinaEvent>>>()
    private val scope = CoroutineScope(Dispatchers.Default)

    var mode = Mode.NoThread

    // 注册事件监听器
    override fun <EVENT : TakinaEvent> on(takinaEventClz: KClass<EVENT>, handler: TakinaEventHandler<EVENT>) {
        LogUtils.debug(TAG, "注册事件监听器", takinaEventClz.clzName)

        handlers.computeIfAbsent(takinaEventClz) { CopyOnWriteArrayList() }
            .add(handler as TakinaEventHandler<TakinaEvent>)
    }

    // 取消事件监听器
    override fun <EVENT : TakinaEvent> removeOn(takinaEventClz: KClass<EVENT>, handler: TakinaEventHandler<EVENT>) {
        LogUtils.debug(TAG, "移除事件监听器", takinaEventClz)

        handlers[takinaEventClz]?.remove(handler)
    }

    // 发射事件
    override fun emit(takinaEvent: TakinaEvent) {
        val eventType = takinaEvent::class

        LogUtils.debug(TAG, "发射事件", eventType.clzName)

        when (mode) {
            Mode.NoThread -> handlers[eventType]?.forEach { handler ->
                handler.onEvent(takinaEvent, context)
            }

            Mode.ThreadPerEvent -> scope.launch {
                handlers[eventType]?.forEach { handler ->
                    handler.onEvent(takinaEvent, context)
                }
            }

            Mode.ThreadPerHandler -> handlers[eventType]?.forEach { handler ->
                scope.launch {
                    handler.onEvent(takinaEvent, context)
                }
            }
        }
    }

    // 关闭事件总线（清理资源）
    override fun shutdown() {
        scope.cancel()
        handlers.clear()
    }
}