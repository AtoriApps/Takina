package org.atoriapps.takina.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.assertFailsWith
import org.atoriapps.takina.core.events.TakinaEventBus
import org.atoriapps.takina.core.events.TakinaStartedEvent
import org.atoriapps.takina.core.events.EventBusPolicy
import org.atoriapps.takina.core.events.EventDispatchMode
import org.atoriapps.takina.core.events.EventHandlerFailedEvent
import org.atoriapps.takina.core.events.EventHandlerFailureMode
import org.atoriapps.takina.core.events.TakinaEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class EventBusTest {
    @Test
    fun `on once removeOn semantics`() {
        val bus = TakinaEventBus()
        var onCount = 0
        var onceCount = 0

        val onSub = bus.on(TakinaStartedEvent) { onCount += 1 }
        bus.once(TakinaStartedEvent) { onceCount += 1 }

        bus.emit(TakinaStartedEvent())
        bus.emit(TakinaStartedEvent())
        onSub.remove()
        bus.emit(TakinaStartedEvent())

        assertEquals(2, onCount)
        assertEquals(1, onceCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `flow receives emitted events`() = runTest {
        val bus = TakinaEventBus()
        val deferred = async { bus.flow(TakinaStartedEvent).first().type }
        bus.emit(TakinaStartedEvent())
        assertEquals("TakinaStartedEvent", deferred.await())
    }

    @Test
    fun `on uses exact type matching`() {
        val bus = TakinaEventBus()
        var count = 0
        bus.on(TakinaEvent::class) { count += 1 }
        bus.emit(TakinaStartedEvent())
        assertEquals(0, count)
    }

    @Test
    fun `handler exception is isolated and emits failure event by default`() {
        val bus = TakinaEventBus()
        var okCount = 0
        var failureCount = 0

        bus.on(EventHandlerFailedEvent) { failureCount += 1 }
        bus.on(TakinaStartedEvent) { error("boom") }
        bus.on(TakinaStartedEvent) { okCount += 1 }

        bus.emit(TakinaStartedEvent())

        assertEquals(1, okCount)
        assertEquals(1, failureCount)
    }

    @Test
    fun `rethrow mode propagates handler exception in inline mode`() {
        val bus = TakinaEventBus()
        bus.policy =
            EventBusPolicy(
                dispatchMode = EventDispatchMode.ALL_TOGETHER,
                handlerFailureMode = EventHandlerFailureMode.RE_THROW,
            )
        bus.on(TakinaStartedEvent) { error("boom") }

        assertFailsWith<IllegalStateException> {
            bus.emit(TakinaStartedEvent())
        }
    }

    @Test
    fun `per handler coroutine mode dispatches handlers asynchronously`() = runTest {
        val bus = TakinaEventBus()
        bus.policy =
            EventBusPolicy(
                dispatchMode = EventDispatchMode.PER_HANDLER_COROUTINE,
                handlerFailureMode = EventHandlerFailureMode.EMIT_FAILURE_EVENT,
            )

        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        bus.on(TakinaStartedEvent) { first.complete(Unit) }
        bus.on(TakinaStartedEvent) { second.complete(Unit) }

        bus.emit(TakinaStartedEvent())

        withTimeout(1_000) { first.await() }
        withTimeout(1_000) { second.await() }
    }
}
