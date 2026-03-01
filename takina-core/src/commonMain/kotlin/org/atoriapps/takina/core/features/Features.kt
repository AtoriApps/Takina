package org.atoriapps.takina.core.features

import org.atoriapps.takina.core.Takina
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.pipeline.InboundNode
import org.atoriapps.takina.core.pipeline.OutboundNode
import kotlin.reflect.KClass

interface TakinaFeature {
    val supportedScopes: Set<ScopeKind>
    val applyMode: ApplyMode
    val requires: Set<TakinaFeatureProvider<out TakinaFeature>> get() = emptySet()
    val conflictsWith: Set<TakinaFeatureProvider<out TakinaFeature>> get() = emptySet()

    suspend fun onInstall(context: Takina) {}
    suspend fun onShutdown(context: Takina) {}

    // TODO：缺少`PreBind`和`Pre断连`处理钩子

    fun lifecycleHooks(): List<ConnectionLifecycleHook> = emptyList()
    fun inboundNodes(): List<InboundNode> = emptyList()
    fun outboundNodes(): List<OutboundNode> = emptyList()
}

interface FeatureApi

interface TakinaFeatureProvider<FEATURE : TakinaFeature> {
    val id: String
    val featureType: KClass<FEATURE>
    fun create(): FEATURE
}

data class InstalledFeature(
    val provider: TakinaFeatureProvider<out TakinaFeature>,
    val feature: TakinaFeature,
)

interface ConnectionLifecycleHook {
    suspend fun onConnectionStateChanged(owner: BareJid, from: ConnectionState, to: ConnectionState) {}
}

interface ApiProvidingFeature<API : FeatureApi> : TakinaFeature {
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
    features: List<InstalledFeature>,
) {
    private val ordered: List<InstalledFeature> = features.distinctBy { it.provider.id }
    private val byProvider: Map<TakinaFeatureProvider<*>, InstalledFeature> = ordered.associateBy { it.provider }
    private val byId: Map<String, InstalledFeature> = ordered.associateBy { it.provider.id }

    fun all(): List<InstalledFeature> = ordered
    fun contains(provider: TakinaFeatureProvider<*>): Boolean = byProvider.containsKey(provider)
    fun containsId(id: String): Boolean = byId.containsKey(id)
    fun installed(provider: TakinaFeatureProvider<*>): InstalledFeature? = byProvider[provider]
    fun installedById(id: String): InstalledFeature? = byId[id]
    fun get(provider: TakinaFeatureProvider<*>): TakinaFeature? = byProvider[provider]?.feature
    fun getById(id: String): TakinaFeature? = byId[id]?.feature
    fun require(provider: TakinaFeatureProvider<*>): TakinaFeature = requireNotNull(byProvider[provider]?.feature) { "Feature not installed: ${provider.id}" }
}
