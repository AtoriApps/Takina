package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.atoriapps.takina.core.connections.FinalReconnect
import org.atoriapps.takina.core.connections.ReconnectPolicy
import org.atoriapps.takina.core.models.toBareJid

class FinalReconnectTest {
    private val owner = "alice@example.com".toBareJid()

    @Test
    fun `auth hard failure is not retried`() = runTest {
        val orchestrator = FinalReconnect(
            onSchedule = { _, _ -> Unit },
            connectAttempt = { true },
        )
        val outcome = orchestrator.perform(owner, ReconnectPolicy(), authHardFailure = true)
        assertFalse(outcome.succeed)
        assertEquals(0, outcome.attempts)
    }
}
