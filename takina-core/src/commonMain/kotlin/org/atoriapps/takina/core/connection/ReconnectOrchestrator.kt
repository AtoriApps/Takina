package org.atoriapps.takina.core.connection

import kotlinx.coroutines.delay
import org.atoriapps.takina.core.models.BareJid
import kotlin.math.min
import kotlin.random.Random

enum class SmResumeResult {
    SUCCESS,
    FAILED,
    NOT_ENABLED,
}

interface SmRecoveryCoordinator {
    suspend fun tryResume(owner: BareJid): SmResumeResult
}

data class ReconnectPolicy(
    val enabled: Boolean = true,
    val delayMillis: Long = 1_000,
    val factor: Double = 2.0,
    val jitter: Double = 0.0,
    val maxAttempts: Int = 5,
)

data class ReconnectOutcome(
    val resumedBySm: Boolean,
    val connected: Boolean,
    val attempts: Int,
)

class ReconnectOrchestrator(
    private val smRecoveryCoordinator: SmRecoveryCoordinator,
    private val onSchedule: suspend (attempt: Int, delayMillis: Long) -> Unit,
    private val connectAttempt: suspend () -> Boolean,
) {
    suspend fun recoverAfterUnexpectedDisconnect(
        owner: BareJid,
        policy: ReconnectPolicy,
        authHardFailure: Boolean,
    ): ReconnectOutcome {
        if (authHardFailure) return ReconnectOutcome(resumedBySm = false, connected = false, attempts = 0)
        if (!policy.enabled) return ReconnectOutcome(resumedBySm = false, connected = false, attempts = 0)

        // XEP-0198 path is attempted before core reconnect.
        when (smRecoveryCoordinator.tryResume(owner)) {
            SmResumeResult.SUCCESS -> return ReconnectOutcome(resumedBySm = true, connected = true, attempts = 0)

            SmResumeResult.FAILED, SmResumeResult.NOT_ENABLED -> Unit
        }

        var attempts = 0
        while (attempts < policy.maxAttempts) {
            attempts += 1
            val nextDelay = computeBackoffDelayMillis(policy, attempts)
            onSchedule(attempts, nextDelay)
            delay(nextDelay)
            if (connectAttempt()) return ReconnectOutcome(resumedBySm = false, connected = true, attempts = attempts)
        }
        return ReconnectOutcome(resumedBySm = false, connected = false, attempts = attempts)
    }

    private fun computeBackoffDelayMillis(policy: ReconnectPolicy, attempt: Int): Long {
        val base = policy.delayMillis * policy.factor.pow(attempt - 1)
        val withCap = min(base, (policy.delayMillis * 32).toDouble())
        if (policy.jitter <= 0.0) return withCap.toLong()
        val factor = 1.0 + Random.nextDouble(from = -policy.jitter, until = policy.jitter)
        return (withCap * factor).coerceAtLeast(0.0).toLong()
    }
}

private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}
