package org.atoriapps.takina.core.controlling

import org.atoriapps.takina.core.connections.ConnectionConfigPaths
import org.atoriapps.takina.core.connections.ReconnectConfigPaths
import org.atoriapps.takina.core.features.FeatureRegistry
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.fallbackChain

data class ExplainResult(
    val target: String,
    val scope: Scope,
    val installed: Boolean,
    val enabled: Boolean,
    val reasonChain: List<String>,
    val applyMode: ApplyMode? = null,
    val order: Int? = null,
)

data class FeatureActivation(
    val featureId: String,
    val enabled: Boolean,
    val explanation: ExplainResult,
)

data class NodeActivationState(
    val nodeKey: String,
    val enabled: Boolean,
    val order: Int,
    val explanation: ExplainResult,
)

class UnifiedPolicy(
    private val featureRegistry: FeatureRegistry,
    private val configMetaCatalog: Map<String, ConfigMeta> = CoreConfigMetaCatalog.all,
) {
    private data object UnsetMarker

    private val featureToggles = mutableMapOf<TakinaFeatureProvider<*>, MutableMap<Scope, Boolean>>()
    private val nodeToggles = mutableMapOf<String, MutableMap<Scope, Boolean>>()
    private val nodeOrders = mutableMapOf<String, MutableMap<Scope, Int>>()

    private val activeConfig = mutableMapOf<String, MutableMap<Scope, Any>>()
    private val nextItemConfig = mutableMapOf<String, MutableMap<Scope, Any>>()
    private val nextConnectionConfig = mutableMapOf<String, MutableMap<Scope, Any>>()

    fun setFeatureEnabled(provider: TakinaFeatureProvider<*>, scope: Scope, enabled: Boolean, ) {
        featureToggles.getOrPut(provider) { linkedMapOf() }[scope] = enabled
    }

    fun setNodeEnabled(nodeKey: String, scope: Scope, enabled: Boolean, ) {
        nodeToggles.getOrPut(nodeKey) { linkedMapOf() }[scope] = enabled
    }

    fun setNodeOrder(nodeKey: String, scope: Scope, order: Int, ) {
        nodeOrders.getOrPut(nodeKey) { linkedMapOf() }[scope] = order
    }

    // CHECK：NULL遮蔽？？
    fun applyConfig(path: String, value: Any?, scope: Scope = Scope.Global): ConfigChangeResult {
        // CHECK：确保只阻止用户设定，不阻止内部合并（有的话）
        if (ConnectionConfigPaths.isConnectionPath(path)) return ConfigChangeResult(
            path = path,
            applied = false,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            rejectedReason = "$path is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
        )

        val meta = configMetaCatalog[path] ?: ConfigMeta(path, ApplyMode.IMMEDIATE, mutable = true)

        // TODO：这个只有几个，最好统一，搞个类似这样的{Key(or Path),Default,Validator}
        validateValue(path, value)?.let { reason ->
            return ConfigChangeResult(
                path = path,
                applied = false,
                applyMode = meta.applyMode,
                rejectedReason = reason,
            )
        }

        if (!meta.mutable || meta.applyMode == ApplyMode.BUILD_TIME_IMMUTABLE) return ConfigChangeResult(
            path = path,
            applied = false,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            rejectedReason = "Config is build-time immutable",
        )

        return when (meta.applyMode) {
            ApplyMode.IMMEDIATE -> {
                if (value == null) activeConfig.unsetScoped(path, scope)
                else activeConfig.setScoped(path, scope, value)
                ConfigChangeResult(path = path, applied = true, applyMode = meta.applyMode)
            }

            ApplyMode.NEXT_ITEM -> {
                nextItemConfig.setScoped(path, scope, value ?: UnsetMarker)
                ConfigChangeResult(path = path, applied = false, applyMode = meta.applyMode)
            }

            ApplyMode.NEXT_CONNECTION -> {
                nextConnectionConfig.setScoped(path, scope, value ?: UnsetMarker)
                ConfigChangeResult(path = path, applied = false, applyMode = meta.applyMode)
            }
        }
    }

    fun onNextItemBoundary() {
        if (nextItemConfig.isEmpty()) return
        mergeScopedConfig(nextItemConfig, activeConfig)
        nextItemConfig.clear()
    }

    fun onNextConnectionBoundary() {
        if (nextConnectionConfig.isEmpty()) return
        mergeScopedConfig(nextConnectionConfig, activeConfig)
        nextConnectionConfig.clear()
    }

    fun currentConfig(path: String, scope: Scope = Scope.Global): Any? {
        val values = activeConfig[path] ?: return null
        for (candidate in scope.fallbackChain()) {
            val value = values[candidate] ?: continue
            if (value === UnsetMarker) continue
            return value
        }
        return null
    }

    fun describeActiveFeatures(scope: Scope): List<FeatureActivation> = featureRegistry.all().map { feature ->
        val explained = isFeatureEnabled(feature.provider, scope)
        FeatureActivation(
            featureId = feature.provider.id,
            enabled = explained.enabled,
            explanation = explained,
        )
    }

    fun explainFeature(provider: TakinaFeatureProvider<*>, scope: Scope): ExplainResult = isFeatureEnabled(provider, scope)

    fun explainFeature(featureId: String, scope: Scope): ExplainResult {
        val provider = featureRegistry.installedById(featureId)?.provider ?: return ExplainResult(
            target = featureId,
            scope = scope,
            installed = false,
            enabled = false,
            reasonChain = listOf("feature-not-installed"),
        )
        return isFeatureEnabled(provider, scope)
    }

    fun explainNode(
        nodeKey: String,
        featureProvider: TakinaFeatureProvider<*>?,
        scope: Scope,
    ): ExplainResult {
        val reason = mutableListOf<String>()
        var installed = true

        val featureResult = featureProvider?.let { isFeatureEnabled(it, scope) }
        if (featureResult != null) {
            installed = featureResult.installed
            if (!featureResult.enabled) {
                reason += "feature-disabled:${featureProvider.id}"
                return ExplainResult(
                    target = nodeKey,
                    scope = scope,
                    installed = installed,
                    enabled = false,
                    reasonChain = reason + featureResult.reasonChain,
                    order = resolveNodeOrder(nodeKey, scope),
                )
            }
        }

        val chain = scope.fallbackChain()
        for (s in chain) {
            val value = nodeToggles[nodeKey]?.get(s)
            if (value != null) {
                reason += "node-switch:${s.kind}=$value"
                return ExplainResult(
                    target = nodeKey,
                    scope = scope,
                    installed = installed,
                    enabled = value,
                    reasonChain = reason,
                    order = resolveNodeOrder(nodeKey, scope),
                )
            }
        }

        reason += "node-switch:default=true"
        return ExplainResult(
            target = nodeKey,
            scope = scope,
            installed = installed,
            enabled = true,
            reasonChain = reason,
            order = resolveNodeOrder(nodeKey, scope),
        )
    }

    fun resolveNodeOrder(nodeKey: String, scope: Scope): Int {
        val chain = scope.fallbackChain()
        for (s in chain) nodeOrders[nodeKey]?.get(s)?.let { return it }
        return 0
    }

    fun sortNodesWithVisibilityConflict(
        nodeKeys: List<String>,
        featureProviderOfNode: (String) -> TakinaFeatureProvider<*>?,
        scope: Scope,
    ): List<NodeActivationState> {
        val states = nodeKeys.map { node ->
            val explain = explainNode(node, featureProviderOfNode(node), scope)
            NodeActivationState(
                nodeKey = node,
                enabled = explain.enabled,
                order = explain.order ?: 0,
                explanation = explain,
            )
        }
        val enabled = states.filter { it.enabled }
        val duplicates = enabled.groupBy { it.order }.filterValues { it.size > 1 }.keys
        return states.sortedWith(compareBy<NodeActivationState> { it.order }.thenBy { it.nodeKey }).map {
            if (it.order in duplicates) it.copy(
                explanation = it.explanation.copy(
                    reasonChain = it.explanation.reasonChain + "order-conflict-visible:order=${it.order}",
                ),
            ) else it
        }
    }

    private fun isFeatureEnabled(provider: TakinaFeatureProvider<*>, scope: Scope): ExplainResult {
        val feature = featureRegistry.get(provider) ?: return ExplainResult(
            target = provider.id,
            scope = scope,
            installed = false,
            enabled = false,
            reasonChain = listOf("feature-not-installed"),
        )

        val chain = scope.fallbackChain()
        for (s in chain) {
            if (s.kind !in feature.supportedScopes) continue
            val value = featureToggles[provider]?.get(s)
            if (value != null) return ExplainResult(
                target = provider.id,
                scope = scope,
                installed = true,
                enabled = value,
                reasonChain = listOf("feature-switch:${s.kind}=$value"),
                applyMode = feature.applyMode,
            )
        }

        if (chain.none { it.kind in feature.supportedScopes }) {
            return ExplainResult(
                target = provider.id,
                scope = scope,
                installed = true,
                enabled = false,
                reasonChain = listOf("feature-scope-unsupported:${scope.kind}"),
                applyMode = feature.applyMode,
            )
        }

        return ExplainResult(
            target = provider.id,
            scope = scope,
            installed = true,
            enabled = true,
            reasonChain = listOf("feature-switch:default=true"),
            applyMode = feature.applyMode,
        )
    }

    private fun MutableMap<String, MutableMap<Scope, Any>>.setScoped(path: String, scope: Scope, value: Any) {
        getOrPut(path) { linkedMapOf() }[scope] = value
    }

    private fun MutableMap<String, MutableMap<Scope, Any>>.unsetScoped(path: String, scope: Scope) {
        val scoped = this[path] ?: return
        scoped.remove(scope)
        if (scoped.isEmpty()) remove(path)
    }

    private fun validateValue(path: String, value: Any?): String? = when (path) {
        ReconnectConfigPaths.ENABLED -> if (value == null || value is Boolean) null else "${ReconnectConfigPaths.ENABLED} must be Boolean"
        ReconnectConfigPaths.DELAY -> if (value == null || (value is Number && value.toLong() >= 0L)) null else "${ReconnectConfigPaths.DELAY} must be Number >= 0"
        ReconnectConfigPaths.FACTOR -> if (value == null || (value is Number && value.toDouble() > 0.0)) null else "${ReconnectConfigPaths.FACTOR} must be Number > 0"
        ReconnectConfigPaths.JITTER -> if (value == null || (value is Number && value.toDouble() >= 0.0 && value.toDouble() <= 1.0)) null else "${ReconnectConfigPaths.JITTER} must be Number in [0, 1]"
        ReconnectConfigPaths.MAX_ATTEMPTS -> if (value == null || (value is Number && value.toInt() >= 0)) null else "${ReconnectConfigPaths.MAX_ATTEMPTS} must be Number >= 0"
        else -> null
    }

    private fun mergeScopedConfig(from: Map<String, MutableMap<Scope, Any>>, into: MutableMap<String, MutableMap<Scope, Any>>, ) {
        for ((path, scoped) in from) {
            val target = into.getOrPut(path) { linkedMapOf() }
            for ((scope, value) in scoped) {
                if (value === UnsetMarker) target.remove(scope)
                else target[scope] = value
            }
            if (target.isEmpty()) into.remove(path)
        }
    }
}
