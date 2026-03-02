package org.atoriapps.takina.core.connections

import kotlinx.coroutines.delay
import org.atoriapps.takina.core.models.BareJid
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

@Deprecated("原因在下方HACK")
enum class SmResumeResult {
    SUCCESS,
    FAILED,
    NOT_ENABLED,
}

@Deprecated("原因在下方HACK")
interface SmRecoveryCoordinator {
    suspend fun tryResume(owner: BareJid): SmResumeResult
}

data class ReconnectPolicy(
    val enabled: Boolean = ReconnectDefaults.ENABLED,
    val delayMillis: Long = ReconnectDefaults.DELAY_MILLIS,
    val factor: Double = ReconnectDefaults.FACTOR,
    val jitter: Double = ReconnectDefaults.JITTER,
    val maxAttempts: Int = ReconnectDefaults.MAX_ATTEMPTS,
)

data class ReconnectOutcome(
    val succeed: Boolean,
    val attempts: Int,
)

class FinalReconnect(
    // private val smRecoveryCoordinator: SmRecoveryCoordinator,
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

        // HACK：但我觉得应该有个断连判断的钩子链（断连时触发，如果都没有处理，才走本方法，处理了就当做无事发生，且歌且舞），然后让SM实现钩子
        /*when (smRecoveryCoordinator.tryResume(owner)) {
            SmResumeResult.SUCCESS -> return ReconnectOutcome(resumedBySm = true, connected = true, attempts = 0)

            SmResumeResult.FAILED, SmResumeResult.NOT_ENABLED -> Unit
        }*/

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
