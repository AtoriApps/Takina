package org.atoriapps.takina.core

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.atoriapps.takina.core.events.AllConnectedEvent
import org.atoriapps.takina.core.events.ConnectionFailedEvent
import org.atoriapps.takina.core.events.FrameOutboundEvent
import org.atoriapps.takina.core.events.TakinaEventFlowBackpressure
import org.atoriapps.takina.core.xmpp.toBareJid
import kotlin.test.Test
import kotlin.test.assertEquals

class EventBusFlowTest {
    @Test
    fun eventBusFlow_shouldReceiveEmittedEvent() = runBlocking {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }
        val waiting = async { takina.events.flow(AllConnectedEvent).first() }
        delay(20)
        takina.events.emit(AllConnectedEvent(connectedCount = 1, configuredCount = 1))
        val event = withTimeout(1_000) { waiting.await() }
        assertEquals(1, event.connectedCount)
        assertEquals(1, event.configuredCount)
    }

    @Test
    fun eventDescribeForLog_shouldBeProvidedByEvent() {
        val event = ConnectionFailedEvent("alice@example.com".toBareJid(), reason = "timeout")
        assertEquals("账号=alice@example.com 原因=timeout", event.description)
    }

    @Test
    fun frameOutboundEvent_shouldSupportFlowAndDescription() = runBlocking {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }
        val waiting = async { takina.events.flow(FrameOutboundEvent).first() }
        delay(20)
        takina.events.emit(FrameOutboundEvent("alice@example.com".toBareJid(), "<message id='m1'/>"))
        val event = withTimeout(1_000) { waiting.await() }
        assertEquals("账号=alice@example.com", event.description)
    }

    @Test
    fun eventBusFlow_shouldSupportBackpressureConfiguration() = runBlocking {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }
        val waiting = async {
            takina.events.flow(
                AllConnectedEvent,
                backpressure = TakinaEventFlowBackpressure(
                    capacity = 1,
                    overflow = BufferOverflow.DROP_OLDEST,
                ),
            ).first()
        }
        delay(20)
        takina.events.emit(AllConnectedEvent(connectedCount = 1, configuredCount = 1))
        val event = withTimeout(1_000) { waiting.await() }
        assertEquals(1, event.connectedCount)
    }
}
