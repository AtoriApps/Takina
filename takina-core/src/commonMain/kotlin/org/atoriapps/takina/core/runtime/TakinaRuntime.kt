package org.atoriapps.takina.core.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.atoriapps.takina.core.connections.AccountState
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.controlling.UnifiedPolicy
import org.atoriapps.takina.core.controlling.FeatureActivation
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.pipeline.NodeMetrics
import org.atoriapps.takina.core.pipeline.PipelineDescription
import org.atoriapps.takina.core.pipeline.PipelineDirection
import org.atoriapps.takina.core.pipeline.PipelineRuntime

data class RuntimeHealth(
    val degradedAccounts: Int = 0,
    val failedAccounts: Int = 0,
)

// TODO：还有什么可以放进Rt，这里面已有的有什么用？
class TakinaRuntime(
    private val unifiedPolicy: UnifiedPolicy,
    private val pipelineRuntime: PipelineRuntime,
) {
    private val _accountStates = MutableStateFlow<Map<BareJid, AccountState>>(emptyMap())
    private val _connectionStates = MutableStateFlow<Map<BareJid, ConnectionState>>(emptyMap())
    private val _health = MutableStateFlow(RuntimeHealth())

    val accountStates: StateFlow<Map<BareJid, AccountState>> = _accountStates.asStateFlow()
    val connectionStates: StateFlow<Map<BareJid, ConnectionState>> = _connectionStates.asStateFlow()
    val health: StateFlow<RuntimeHealth> = _health.asStateFlow()

    internal fun setAccountState(owner: BareJid, state: AccountState) {
        _accountStates.value += (owner to state)
        recomputeHealth()
    }

    internal fun setConnectionState(owner: BareJid, state: ConnectionState) {
        _connectionStates.value += (owner to state)
    }

    fun describeActiveFeatures(scope: Scope): List<FeatureActivation> = unifiedPolicy.describeActiveFeatures(scope)

    fun describeActivePipeline(direction: PipelineDirection, scope: Scope): PipelineDescription =
        pipelineRuntime.describeActivePipeline(direction, scope)

    fun explainWhyEnabled(target: String, scope: Scope) = if (target.startsWith("feature:")) {
        unifiedPolicy.explainFeature(target.removePrefix("feature:"), scope)
    } else {
        unifiedPolicy.explainNode(target.removePrefix("node:"), null, scope)
    }

    fun nodeMetrics(): List<NodeMetrics> = pipelineRuntime.metricsSnapshot()

    private fun recomputeHealth() {
        val values = _accountStates.value.values
        _health.value = RuntimeHealth(
            degradedAccounts = values.count { it == AccountState.DEGRADED || it == AccountState.RECONNECTING },
            failedAccounts = values.count { it == AccountState.FAILED },
        )
    }
}
