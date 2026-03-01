package org.atoriapps.takina.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.atoriapps.takina.core.events.TakinaEventBus
import org.atoriapps.takina.core.events.TakinaStartedEvent
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
        bus.removeOn(onSub)
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
}
