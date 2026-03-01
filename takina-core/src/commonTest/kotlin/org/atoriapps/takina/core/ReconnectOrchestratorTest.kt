package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.atoriapps.takina.core.connection.ReconnectOrchestrator
import org.atoriapps.takina.core.connection.ReconnectPolicy
import org.atoriapps.takina.core.connection.SmRecoveryCoordinator
import org.atoriapps.takina.core.connection.SmResumeResult
import org.atoriapps.takina.core.models.toBareJid

class ReconnectOrchestratorTest {
    private val owner = "alice@example.com".toBareJid()

    @Test
    fun `sm success bypasses core reconnect`() = runTest {
        var attempts = 0
        val orchestrator = ReconnectOrchestrator(
            smRecoveryCoordinator = object : SmRecoveryCoordinator {
                override suspend fun tryResume(owner: org.atoriapps.takina.core.models.BareJid): SmResumeResult = SmResumeResult.SUCCESS
            },
            onSchedule = { _, _ -> attempts += 1 },
            connectAttempt = { false },
        )

        val outcome = orchestrator.recoverAfterUnexpectedDisconnect(owner, ReconnectPolicy(), authHardFailure = false)
        assertTrue(outcome.resumedBySm)
        assertEquals(0, attempts)
        assertEquals(0, outcome.attempts)
    }

    @Test
    fun `failed sm falls back to core reconnect`() = runTest {
        var connectCalls = 0
        val orchestrator = ReconnectOrchestrator(
            smRecoveryCoordinator = object : SmRecoveryCoordinator {
                override suspend fun tryResume(owner: org.atoriapps.takina.core.models.BareJid): SmResumeResult = SmResumeResult.FAILED
            },
            onSchedule = { _, _ -> Unit },
            connectAttempt = {
                connectCalls += 1
                connectCalls >= 2
            },
        )

        val outcome = orchestrator.recoverAfterUnexpectedDisconnect(
            owner = owner,
            policy = ReconnectPolicy(delayMillis = 1, maxAttempts = 3),
            authHardFailure = false,
        )
        assertTrue(outcome.connected)
        assertEquals(2, outcome.attempts)
    }

    @Test
    fun `auth hard failure is not retried`() = runTest {
        val orchestrator = ReconnectOrchestrator(
            smRecoveryCoordinator = object : SmRecoveryCoordinator {
                override suspend fun tryResume(owner: org.atoriapps.takina.core.models.BareJid): SmResumeResult = SmResumeResult.NOT_ENABLED
            },
            onSchedule = { _, _ -> Unit },
            connectAttempt = { true },
        )
        val outcome = orchestrator.recoverAfterUnexpectedDisconnect(owner, ReconnectPolicy(), authHardFailure = true)
        assertFalse(outcome.connected)
        assertEquals(0, outcome.attempts)
    }
}
