package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.atoriapps.takina.core.connection.ConnectionState
import org.atoriapps.takina.core.connection.ConnectionStateMachine
import kotlin.test.assertEquals

class ConnectionStateMachineTest {
    @Test
    fun `accepts valid state transitions`() {
        val machine = ConnectionStateMachine()
        assertTrue(machine.transitionTo(ConnectionState.TCP_CONNECTING).accepted)
        assertTrue(machine.transitionTo(ConnectionState.TLS_HANDSHAKING).accepted)
        assertTrue(machine.transitionTo(ConnectionState.STREAM_OPENING).accepted)
        assertTrue(machine.transitionTo(ConnectionState.AUTHENTICATING).accepted)
        assertTrue(machine.transitionTo(ConnectionState.BINDING_RESOURCE).accepted)
        assertTrue(machine.transitionTo(ConnectionState.ESTABLISHED).accepted)
    }

    @Test
    fun `rejects invalid state transitions`() {
        val machine = ConnectionStateMachine()
        val result = machine.transitionTo(ConnectionState.ESTABLISHED)
        assertFalse(result.accepted)
        assertEquals(result.errorCode?.startsWith("TAKINA-STREAM-"), true)
    }
}
