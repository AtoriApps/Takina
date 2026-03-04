package org.atoriapps.takina.core.controlling

import org.atoriapps.takina.core.features.InstalledFeatures
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.fallbackChain
import org.atoriapps.takina.core.pipeline.NodeOrderSpec

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
    private val installedFeatures: InstalledFeatures,
    private val configCatalog: Map<String, ConfigSpec<*>> = CoreConfigCatalog.all,
) {
    private data object UnsetMarker

    private sealed interface MutableSpecLookup {
        data class Ready(val spec: ConfigSpec<*>) : MutableSpecLookup
        data class Rejected(val result: ConfigChangeResult) : MutableSpecLookup
    }

    private data class ConfigLookup(
        val found: Boolean,
        val value: Any?,
    )

    private val featureToggles = mutableMapOf<TakinaFeatureProvider<*>, MutableMap<Scope, Boolean>>()
    private val nodeToggles = mutableMapOf<String, MutableMap<Scope, Boolean>>()
    private val nodeOrders = mutableMapOf<String, MutableMap<Scope, NodeOrderSpec>>()

    private val activeConfig = mutableMapOf<String, MutableMap<Scope, Any?>>()
    private val nextItemConfig = mutableMapOf<String, MutableMap<Scope, Any?>>()
    private val nextConnectionConfig = mutableMapOf<String, MutableMap<Scope, Any?>>()

    fun setFeatureEnabled(provider: TakinaFeatureProvider<*>, scope: Scope, enabled: Boolean) {
        featureToggles.getOrPut(provider) { linkedMapOf() }[scope] = enabled
    }

    fun setNodeEnabled(nodeKey: String, scope: Scope, enabled: Boolean) {
        nodeToggles.getOrPut(nodeKey) { linkedMapOf() }[scope] = enabled
    }

    fun setNodeOrder(nodeKey: String, scope: Scope, order: NodeOrderSpec) {
        nodeOrders.getOrPut(nodeKey) { linkedMapOf() }[scope] = order
    }

    fun applyConfig(path: String, value: Any?, scope: Scope = Scope.Global): ConfigChangeResult {
        val spec = when (val resolved = resolveRuntimeMutableSpec(path, scope)) {
            is MutableSpecLookup.Ready -> resolved.spec
            is MutableSpecLookup.Rejected -> return resolved.result
        }

        val normalized = when (val result = spec.normalize(value)) {
            is ConfigNormalizeResult.Accepted -> result.value

            is ConfigNormalizeResult.Rejected -> return ConfigChangeResult(
                path = path,
                applied = false,
                applyMode = spec.applyMode,
                rejectedReason = result.reason,
                rejectCode = result.code,
            )
        }

        return when (spec.applyMode) {
            ApplyMode.IMMEDIATE -> {
                activeConfig.setScoped(path, scope, normalized)
                ConfigChangeResult(path = path, applied = true, applyMode = spec.applyMode)
            }

            ApplyMode.NEXT_ITEM -> {
                nextItemConfig.setScoped(path, scope, normalized)
                ConfigChangeResult(path = path, applied = false, applyMode = spec.applyMode)
            }

            ApplyMode.NEXT_CONNECTION -> {
                nextConnectionConfig.setScoped(path, scope, normalized)
                ConfigChangeResult(path = path, applied = false, applyMode = spec.applyMode)
            }

            ApplyMode.BUILD_TIME_IMMUTABLE -> ConfigChangeResult(
                path = path,
                applied = false,
                applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
                rejectedReason = spec.immutableReason,
                rejectCode = ConfigRejectCode.IMMUTABLE,
            )
        }
    }

    fun unsetConfig(path: String, scope: Scope = Scope.Global): ConfigChangeResult {
        val spec = when (val resolved = resolveRuntimeMutableSpec(path, scope)) {
            is MutableSpecLookup.Ready -> resolved.spec
            is MutableSpecLookup.Rejected -> return resolved.result
        }

        return when (spec.applyMode) {
            ApplyMode.IMMEDIATE -> {
                activeConfig.unsetScoped(path, scope)
                ConfigChangeResult(path = path, applied = true, applyMode = spec.applyMode)
            }

            ApplyMode.NEXT_ITEM -> {
                nextItemConfig.setScoped(path, scope, UnsetMarker)
                ConfigChangeResult(path = path, applied = false, applyMode = spec.applyMode)
            }

            ApplyMode.NEXT_CONNECTION -> {
                nextConnectionConfig.setScoped(path, scope, UnsetMarker)
                ConfigChangeResult(path = path, applied = false, applyMode = spec.applyMode)
            }

            ApplyMode.BUILD_TIME_IMMUTABLE -> ConfigChangeResult(
                path = path,
                applied = false,
                applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
                rejectedReason = spec.immutableReason,
                rejectCode = ConfigRejectCode.IMMUTABLE,
            )
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

    fun currentConfig(path: String, scope: Scope = Scope.Global): Any? = resolveConfig(path, scope).value

    fun <T : Any?> currentConfig(spec: ConfigSpec<T>, scope: Scope = Scope.Global): T? {
        val resolved = resolveConfig(spec.path, scope)
        if (!resolved.found) {
            return if (spec.hasDefault) spec.default else null
        }
        return if (resolved.value == null && spec.nullable) {
            null
        } else {
            spec.decode(resolved.value) ?: if (spec.hasDefault) spec.default else null
        }
    }

    fun <T : Any?> currentConfigOrDefault(spec: ConfigSpec<T>, scope: Scope = Scope.Global): T {
        val current = currentConfig(spec, scope)
        return current ?: spec.default
    }

    fun describeActiveFeatures(scope: Scope): List<FeatureActivation> = installedFeatures.all().map { feature ->
        val explained = isFeatureEnabled(feature.provider, scope)
        FeatureActivation(
            featureId = feature.provider.id,
            enabled = explained.enabled,
            explanation = explained,
        )
    }

    fun explainFeature(provider: TakinaFeatureProvider<*>, scope: Scope): ExplainResult = isFeatureEnabled(provider, scope)

    fun explainFeature(featureId: String, scope: Scope): ExplainResult {
        val provider = installedFeatures.installedById(featureId)?.provider ?: return ExplainResult(
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
        )
    }

    fun sortNodesWithVisibilityConflict(
        nodeKeys: List<String>,
        featureProviderOfNode: (String) -> TakinaFeatureProvider<*>?,
        scope: Scope,
    ): List<NodeActivationState> {
        val states = nodeKeys.distinct().map { node ->
            val explain = explainNode(node, featureProviderOfNode(node), scope)
            NodeActivationState(
                nodeKey = node,
                enabled = explain.enabled,
                order = 0,
                explanation = explain,
            )
        }
        val byKey = states.associateBy { it.nodeKey }
        val indegree = states.associate { it.nodeKey to 0 }.toMutableMap()
        val outgoing = states.associate { it.nodeKey to linkedSetOf<String>() }.toMutableMap()
        val extraReasons = mutableMapOf<String, MutableList<String>>()

        fun addReason(node: String, reason: String) {
            extraReasons.getOrPut(node) { mutableListOf() } += reason
        }

        fun addEdge(from: String, to: String) {
            val targets = outgoing.getValue(from)
            if (to in targets) return
            targets += to
            indegree[to] = indegree.getValue(to) + 1
        }

        states.forEach { state ->
            val spec = resolveNodeOrderSpec(state.nodeKey, scope)

            spec.after.forEach { anchor ->
                if (anchor !in byKey) {
                    addReason(state.nodeKey, "order-anchor-missing:after=$anchor")
                } else {
                    addEdge(anchor, state.nodeKey)
                }
            }

            spec.before.forEach { anchor ->
                if (anchor !in byKey) {
                    addReason(state.nodeKey, "order-anchor-missing:before=$anchor")
                } else {
                    addEdge(state.nodeKey, anchor)
                }
            }
        }

        val ready = indegree.filterValues { it == 0 }.keys.sorted().toMutableList()
        val ordered = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val key = ready.removeAt(0)
            ordered += key
            outgoing.getValue(key).toList().sorted().forEach { target ->
                val left = indegree.getValue(target) - 1
                indegree[target] = left
                if (left == 0) {
                    ready += target
                    ready.sort()
                }
            }
        }

        if (ordered.size != states.size) {
            val cycled = states.map { it.nodeKey }.filter { it !in ordered.toSet() }.sorted()
            ordered += cycled
            cycled.forEach { addReason(it, "order-cycle-detected") }
        }

        return ordered.mapIndexed { index, key ->
            val state = byKey.getValue(key)
            val reasons = extraReasons[key].orEmpty()
            state.copy(
                order = index,
                explanation = state.explanation.copy(
                    order = index,
                    reasonChain = state.explanation.reasonChain + reasons,
                ),
            )
        }
    }

    private fun isFeatureEnabled(provider: TakinaFeatureProvider<*>, scope: Scope): ExplainResult {
        val feature = installedFeatures.get(provider) ?: return ExplainResult(
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
            if (value != null) {
                return ExplainResult(
                    target = provider.id,
                    scope = scope,
                    installed = true,
                    enabled = value,
                    reasonChain = listOf("feature-switch:${s.kind}=$value"),
                    applyMode = feature.applyMode,
                )
            }
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

    private fun resolveRuntimeMutableSpec(path: String, scope: Scope): MutableSpecLookup {
        val spec = configCatalog[path] ?: return MutableSpecLookup.Rejected(
            ConfigChangeResult(
                path = path,
                applied = false,
                applyMode = ApplyMode.IMMEDIATE,
                rejectedReason = "Unknown config path: $path",
                rejectCode = ConfigRejectCode.UNKNOWN_PATH,
            ),
        )

        return if (!spec.supports(scope)) MutableSpecLookup.Rejected(
            ConfigChangeResult(
                path = path,
                applied = false,
                applyMode = spec.applyMode,
                rejectedReason = "$path does not support scope ${scope.kind}",
                rejectCode = ConfigRejectCode.UNSUPPORTED_SCOPE,
            )
        ) else if (!spec.mutable || spec.applyMode == ApplyMode.BUILD_TIME_IMMUTABLE) MutableSpecLookup.Rejected(
            ConfigChangeResult(
                path = path,
                applied = false,
                applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
                rejectedReason = spec.immutableReason,
                rejectCode = ConfigRejectCode.IMMUTABLE,
            )
        ) else  MutableSpecLookup.Ready(spec)
    }

    private fun resolveConfig(path: String, scope: Scope): ConfigLookup {
        val values = activeConfig[path] ?: return ConfigLookup(found = false, value = null)
        for (candidate in scope.fallbackChain()) {
            if (!values.containsKey(candidate)) continue
            val value = values[candidate]
            if (value === UnsetMarker) continue
            return ConfigLookup(found = true, value = value)
        }
        return ConfigLookup(found = false, value = null)
    }

    private fun MutableMap<String, MutableMap<Scope, Any?>>.setScoped(path: String, scope: Scope, value: Any?) {
        getOrPut(path) { linkedMapOf() }[scope] = value
    }

    private fun MutableMap<String, MutableMap<Scope, Any?>>.unsetScoped(path: String, scope: Scope) {
        val scoped = this[path] ?: return
        scoped.remove(scope)
        if (scoped.isEmpty()) remove(path)
    }

    private fun mergeScopedConfig(
        from: Map<String, MutableMap<Scope, Any?>>,
        into: MutableMap<String, MutableMap<Scope, Any?>>,
    ) {
        for ((path, scoped) in from) {
            val target = into.getOrPut(path) { linkedMapOf() }
            for ((scope, value) in scoped) {
                if (value === UnsetMarker) target.remove(scope)
                else target[scope] = value
            }
            if (target.isEmpty()) into.remove(path)
        }
    }

    private fun resolveNodeOrderSpec(nodeKey: String, scope: Scope): NodeOrderSpec {
        val chain = scope.fallbackChain()
        for (candidate in chain) {
            nodeOrders[nodeKey]?.get(candidate)?.let { return it }
        }
        return NodeOrderSpec.Empty
    }
}
