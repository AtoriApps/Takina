package org.atoriapps.takina.core.connections

import kotlinx.coroutines.delay
import org.atoriapps.takina.core.controlling.CoreConfigCatalog
import org.atoriapps.takina.core.models.BareJid
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

data class ReconnectPolicy(
    val enabled: Boolean = CoreConfigCatalog.Reconnect.ENABLED.default,
    val delayMillis: Long = CoreConfigCatalog.Reconnect.DELAY.default,
    val factor: Double = CoreConfigCatalog.Reconnect.FACTOR.default,
    val jitter: Double = CoreConfigCatalog.Reconnect.JITTER.default,
    val maxAttempts: Int = CoreConfigCatalog.Reconnect.MAX_ATTEMPTS.default,
)

data class ReconnectOutcome(
    val succeed: Boolean,
    val attempts: Int,
)

class FinalReconnect(
    private val onSchedule: suspend (attempt: Int, delayMillis: Long) -> Unit,
    private val connectAttempt: suspend () -> Boolean,
) {
    suspend fun perform(
        owner: BareJid,
        policy: ReconnectPolicy,
        authHardFailure: Boolean,
    ): ReconnectOutcome {
        if (authHardFailure) return ReconnectOutcome(succeed = false, attempts = 0)
        if (!policy.enabled) return ReconnectOutcome(succeed = false, attempts = 0)

        var attempts = 0
        while (attempts < policy.maxAttempts) {
            attempts += 1
            val nextDelay = computeBackoffDelayMillis(policy, attempts)
            onSchedule(attempts, nextDelay)
            delay(nextDelay)
            if (connectAttempt()) return ReconnectOutcome(succeed = true, attempts = attempts)
        }

        return ReconnectOutcome(succeed = false, attempts = attempts)
    }

    private fun computeBackoffDelayMillis(policy: ReconnectPolicy, attempt: Int): Long {
        val base = policy.delayMillis * policy.factor.pow(attempt - 1)
        val withCap = min(base, (policy.delayMillis * 32).toDouble())
        if (policy.jitter <= 0.0) return withCap.toLong()
        val factor = 1.0 + Random.nextDouble(from = -policy.jitter, until = policy.jitter)
        return (withCap * factor).coerceAtLeast(0.0).toLong()
    }
}
