package org.atoriapps.takina.core.events

import kotlinx.coroutines.*
import org.atoriapps.takina.core.utils.LogUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

actual class EventBus : AbstractEventBus() {
    companion object {
        private const val TAG = "EventBus.JVM"
    }

    enum class Mode {
        NoThread,
        ThreadPerEvent,
        ThreadPerHandler
    }

    // 存储事件类型及其监听器列表
    private val handlers = ConcurrentHashMap<KClass<out Event>, MutableList<EventHandler<Event>>>()
    private val scope = CoroutineScope(Dispatchers.Default)

    var mode = Mode.NoThread

    // 注册事件监听器
    override fun <EVENT : Event> on(eventClz: KClass<out Event>, handler: EventHandler<EVENT>) {
        LogUtils.debug(TAG, "注册事件监听器", eventClz)

        handlers.computeIfAbsent(eventClz) { CopyOnWriteArrayList() }
            .add(handler as EventHandler<Event>)
    }

    // 取消事件监听器
    override fun <EVENT : Event> removeOn(eventClz: KClass<out Event>, handler: EventHandler<EVENT>) {
        LogUtils.debug(TAG, "移除事件监听器", eventClz)

        handlers[eventClz]?.remove(handler)
    }

    // 发射事件
    override fun emit(event: Event) {
        val eventType = event::class

        LogUtils.debug(TAG, "发射事件：$eventType")

        when (mode) {
            Mode.NoThread -> handlers[eventType]?.forEach { handler ->
                handler.onEvent(event)
            }

            Mode.ThreadPerEvent -> scope.launch {
                handlers[eventType]?.forEach { handler ->
                    handler.onEvent(event)
                }
            }

            Mode.ThreadPerHandler -> handlers[eventType]?.forEach { handler ->
                scope.launch {
                    handler.onEvent(event)
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