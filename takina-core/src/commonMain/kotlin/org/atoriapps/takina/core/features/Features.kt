package org.atoriapps.takina.core.features

import org.atoriapps.takina.core.Takina
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.pipeline.InboundClassification
import org.atoriapps.takina.core.pipeline.InboundNode
import org.atoriapps.takina.core.pipeline.OutboundNode
import kotlin.reflect.KClass

interface TakinaFeature {
    val supportedScopes: Set<ScopeKind>

    val applyMode: ApplyMode

    val requires: Set<TakinaFeatureProvider<out TakinaFeature>> get() = emptySet()

    val conflictsWith: Set<TakinaFeatureProvider<out TakinaFeature>> get() = emptySet()

    // TIPS：时机在管线、API被注册后
    suspend fun onInstall(context: Takina) {}
    suspend fun onShutdown(context: Takina) {}

    fun preBindHooks(): List<PreBindNegotiationHook> = emptyList()
    fun unexpectedDisconnectHooks(): List<UnexpectedDisconnectHook> = emptyList()
    fun inboundClaimers(): List<InboundFrameClaimer> = emptyList()
    fun outboundBusinessObservers(): List<OutboundBusinessObserver> = emptyList()
    fun inboundStanzaObservers(): List<InboundStanzaObserver> = emptyList()

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

data class FeatureContribution<T>(
    val provider: TakinaFeatureProvider<*>,
    val contribution: T,
)

interface ConnectionLifecycleHook {
    suspend fun onConnectionStateChanged(owner: BareJid, from: ConnectionState, to: ConnectionState) {}
}

interface PreBindNegotiationTransport {
    suspend fun sendRawFrame(xml: String)
    suspend fun readFrame(): String?
}

data class PreBindNegotiationContext(
    val owner: BareJid,
    val featuresXml: String,
    val transport: PreBindNegotiationTransport,
)

sealed interface PreBindNegotiationDecision {
    data object ContinueToBind : PreBindNegotiationDecision
    data class ResumeSucceeded(val boundJid: String) : PreBindNegotiationDecision
}

interface PreBindNegotiationHook {
    suspend fun onPreBind(context: PreBindNegotiationContext): PreBindNegotiationDecision = PreBindNegotiationDecision.ContinueToBind
}

data class UnexpectedDisconnectContext(
    val owner: BareJid,
    val reason: String?,
    val authHardFailure: Boolean,
)

enum class UnexpectedDisconnectHandling {
    NOT_HANDLED,
    HANDLED,
}

interface UnexpectedDisconnectHook {
    suspend fun onUnexpectedDisconnect(context: UnexpectedDisconnectContext): UnexpectedDisconnectHandling = UnexpectedDisconnectHandling.NOT_HANDLED
}

interface InboundFrameClaimer {
    val key: String
    fun claim(raw: String): InboundClassification?
}

interface ControlInboundNode : InboundNode

interface OutboundBusinessObserver {
    suspend fun onBusinessFrameSent(owner: BareJid, xml: String) {}
}

interface InboundStanzaObserver {
    suspend fun onInboundStanzaHandled(owner: BareJid, classification: InboundClassification, xml: String) {}
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

class InstalledFeatures(
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

    fun lifecycleHooks(): List<FeatureContribution<ConnectionLifecycleHook>> = ordered.flatMap { installed ->
        installed.feature.lifecycleHooks().map { hook ->
            FeatureContribution(installed.provider, hook)
        }
    }

    fun preBindHooks(): List<FeatureContribution<PreBindNegotiationHook>> = ordered.flatMap { installed ->
        installed.feature.preBindHooks().map { hook ->
            FeatureContribution(installed.provider, hook)
        }
    }

    fun unexpectedDisconnectHooks(): List<FeatureContribution<UnexpectedDisconnectHook>> = ordered.flatMap { installed ->
        installed.feature.unexpectedDisconnectHooks().map { hook ->
            FeatureContribution(installed.provider, hook)
        }
    }

    fun inboundClaimers(): List<FeatureContribution<InboundFrameClaimer>> = ordered.flatMap { installed ->
        installed.feature.inboundClaimers().map { claimer ->
            FeatureContribution(installed.provider, claimer)
        }
    }

    fun inboundNodes(): List<FeatureContribution<InboundNode>> = ordered.flatMap { installed ->
        installed.feature.inboundNodes().map { node ->
            FeatureContribution(installed.provider, node)
        }
    }

    fun outboundNodes(): List<FeatureContribution<OutboundNode>> = ordered.flatMap { installed ->
        installed.feature.outboundNodes().map { node ->
            FeatureContribution(installed.provider, node)
        }
    }

    fun outboundBusinessObservers(): List<FeatureContribution<OutboundBusinessObserver>> = ordered.flatMap { installed ->
        installed.feature.outboundBusinessObservers().map { observer ->
            FeatureContribution(installed.provider, observer)
        }
    }

    fun inboundStanzaObservers(): List<FeatureContribution<InboundStanzaObserver>> = ordered.flatMap { installed ->
        installed.feature.inboundStanzaObservers().map { observer ->
            FeatureContribution(installed.provider, observer)
        }
    }
}
