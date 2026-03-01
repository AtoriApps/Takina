package org.atoriapps.takina.core.feature

import org.atoriapps.takina.core.Takina
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.pipeline.InboundNode
import org.atoriapps.takina.core.pipeline.OutboundNode
import kotlin.reflect.KClass

interface TakinaFeature {
    val key: FeatureKey
    val supportedScopes: Set<ScopeKind>
    val applyMode: ApplyMode
    val requires: Set<FeatureKey> get() = emptySet()
    val conflictsWith: Set<FeatureKey> get() = emptySet()

    suspend fun onInstall(context: Takina) {}
    suspend fun onShutdown(context: Takina) {}

    // TODO：缺少`PreBind`和`Pre断连`处理钩子

    fun lifecycleHooks(): List<ConnectionLifecycleHook> = emptyList()
    fun inboundNodes(): List<InboundNode> = emptyList()
    fun outboundNodes(): List<OutboundNode> = emptyList()
}

@JvmInline
value class FeatureKey(val value: String) {
    override fun toString(): String = value
}

interface FeatureApi

data class FeatureApiKey<API : FeatureApi>(
    val featureKey: FeatureKey,
    val apiType: KClass<API>,
)

inline fun <reified API : FeatureApi> featureApiKey(featureKey: FeatureKey): FeatureApiKey<API> = FeatureApiKey(
    featureKey = featureKey,
    apiType = API::class,
)

interface TakinaFeatureProvider<FEATURE : TakinaFeature> {
    val key: FeatureKey
    val featureType: KClass<FEATURE>
    fun create(): FEATURE
}

interface ConnectionLifecycleHook {
    suspend fun onConnectionStateChanged(owner: BareJid, from: ConnectionState, to: ConnectionState) {}
}

interface ApiProvidingFeature<API : FeatureApi> : TakinaFeature {
    val apiKey: FeatureApiKey<API>
    fun api(): API
}

enum class FeaturePreset {
    Minimal,
    Recommended,
    Full,
}

enum class ConfigPreset {
    Default,
}

class FeatureRegistry(
    features: List<TakinaFeature>,
) {
    private val ordered: List<TakinaFeature> = features.distinctBy { it.key }
    private val byKey: Map<FeatureKey, TakinaFeature> = ordered.associateBy { it.key }

    fun all(): List<TakinaFeature> = ordered
    fun contains(key: FeatureKey): Boolean = byKey.containsKey(key)
    fun get(key: FeatureKey): TakinaFeature? = byKey[key]
    fun require(key: FeatureKey): TakinaFeature = requireNotNull(byKey[key]) { "Feature not installed: $key" }
}
