package org.atoriapps.takina.core

import org.atoriapps.takina.core.connections.ConnectionConfigPaths
import org.atoriapps.takina.core.connections.ConnectionDefaults
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.connections.ReconnectConfigPaths
import org.atoriapps.takina.core.features.InstalledFeature
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.Scope

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class TakinaExperimentalApi

annotation class TakinaInternalApi

annotation class TakinaStableApi

@DslMarker
annotation class TakinaDsl

data class AccountDefinition(
    val jid: BareJid,
    val passwordProvider: () -> String,
    val connectionHost: String? = null,
    val connectionPort: Int? = null,
    val securityMode: SecurityMode? = null,
    val resource: String = ConnectionDefaults.RESOURCE,
    val saslMechanisms: List<String> = ConnectionDefaults.SASL_MECHANISMS,
    val connectTimeoutMillis: Int = ConnectionDefaults.CONNECT_TIMEOUT_MILLIS,
    val trustAllCertificates: Boolean = ConnectionDefaults.TRUST_ALL_CERTIFICATES,
)

internal data class NodePolicyDraft(
    val nodeKey: String,
    val scope: Scope,
    val enabled: Boolean? = null,
    val order: Int? = null,
)

internal data class CapabilityDraft(
    val featureProvider: TakinaFeatureProvider<*>,
    val scope: Scope,
    val enabled: Boolean,
)

internal data class FeatureConfigureDraft(
    val featureProvider: TakinaFeatureProvider<*>,
    val apply: (TakinaFeature) -> Unit,
)

internal data class ConfigDraft(
    val path: String,
    val value: Any?,
    val scope: Scope,
)

internal data class AccountDraft(
    val definition: AccountDefinition,
    val capabilityDrafts: List<CapabilityDraft>,
    val configDrafts: List<ConfigDraft>,
    val nodePolicyDrafts: List<NodePolicyDraft>,
)

private data class PendingCapabilityDraft(
    val featureProvider: TakinaFeatureProvider<*>,
    val scope: Scope?,
    val enabled: Boolean,
)

private data class PendingConfigDraft(
    val path: String,
    val value: Any?,
    val scope: Scope?,
)

internal data class PendingNodePolicyDraft(
    val nodeKey: String,
    val scope: Scope?,
    val enabled: Boolean? = null,
    val order: Int? = null,
)

@TakinaDsl
class TakinaConfiguration internal constructor() {
    internal val accounts = linkedMapOf<BareJid, AccountDefinition>()
    internal val features = mutableListOf<InstalledFeature>()
    internal val featureConfigureDrafts = mutableListOf<FeatureConfigureDraft>()
    internal val capabilityDrafts = mutableListOf<CapabilityDraft>()
    internal val configDrafts = mutableListOf<ConfigDraft>()
    internal val nodePolicyDrafts = mutableListOf<NodePolicyDraft>()

    fun addAccount(init: AccountDsl.() -> Unit) {
        val built = AccountDsl().apply(init).build()
        require(accounts.putIfAbsent(built.definition.jid, built.definition) == null) { "Duplicate account: ${built.definition.jid}" }
        capabilityDrafts += built.capabilityDrafts
        configDrafts += built.configDrafts
        nodePolicyDrafts += built.nodePolicyDrafts
    }

    // TIPS：这个（功能的安装和配置）只能全局
    fun features(init: FeaturesDsl.() -> Unit) {
        FeaturesDsl(features, featureConfigureDrafts).apply(init)
    }

    fun capabilities(init: CapabilityDsl.() -> Unit) {
        CapabilityDsl(sink = { provider, scope, enabled ->
            capabilityDrafts += CapabilityDraft(provider, scope ?: Scope.Global, enabled)
        }).apply(init)
    }

    fun configs(init: ConfigDsl.() -> Unit) {
        ConfigDsl(sink = { path, value, scope ->
            configDrafts += ConfigDraft(path, value, scope ?: Scope.Global)
        }).apply(init)
    }

    fun pipelines(init: PipelinePolicyDsl.() -> Unit) {
        PipelinePolicyDsl(sink = { draft ->
            nodePolicyDrafts += NodePolicyDraft(
                nodeKey = draft.nodeKey,
                scope = draft.scope ?: Scope.Global,
                enabled = draft.enabled,
                order = draft.order,
            )
        }).apply(init)
    }
}

@TakinaDsl
class AccountDsl {
    private var jidProvider: (() -> BareJid?)? = null
    private var passwordProvider: (() -> String?)? = null

    var jid: BareJid?
        get() = jidProvider?.invoke()
        set(value) {
            jidProvider = { value }
        }

    var password: String?
        get() = passwordProvider?.invoke()
        set(value) {
            passwordProvider = { value }
        }

    private var host: String? = null
    private var port: Int? = null
    private var securityMode: SecurityMode? = null
    private var trustAllCertificates: Boolean = ConnectionDefaults.TRUST_ALL_CERTIFICATES
    private var resourceProvider: (() -> String)? = { ConnectionDefaults.RESOURCE }
    private var saslMechanismsProvider: (() -> List<String>)? = { ConnectionDefaults.SASL_MECHANISMS }
    private var connectTimeoutMillisProvider: (() -> Int)? = { ConnectionDefaults.CONNECT_TIMEOUT_MILLIS }
    private val accountCapabilityDrafts = mutableListOf<PendingCapabilityDraft>()
    private val accountConfigDrafts = mutableListOf<PendingConfigDraft>()
    private val accountNodePolicyDrafts = mutableListOf<PendingNodePolicyDraft>()

    fun jid(provider: () -> BareJid?) {
        jidProvider = provider
    }

    fun password(provider: () -> String) {
        passwordProvider = provider
    }

    fun resource(provider: () -> String) {
        resourceProvider = provider
    }

    fun connectTimeoutMillis(provider: () -> Int) {
        connectTimeoutMillisProvider = provider
    }

    fun saslMechanisms(provider: () -> List<String>) {
        saslMechanismsProvider = provider
    }

    fun saslMechanisms(vararg mechanisms: String) {
        saslMechanismsProvider = { mechanisms.toList() }
    }

    fun connection(init: ConnectionDsl.() -> Unit) {
        val built = ConnectionDsl().apply(init)
        host = built.host
        port = built.port
        securityMode = built.securityMode
        trustAllCertificates = built.trustAllCertificates ?: ConnectionDefaults.TRUST_ALL_CERTIFICATES
        built.saslMechanisms?.let { saslMechanisms = it }
    }

    var resource: String
        get() = resourceProvider?.invoke() ?: ConnectionDefaults.RESOURCE
        set(value) {
            resourceProvider = { value }
        }

    var connectTimeoutMillis: Int
        get() = connectTimeoutMillisProvider?.invoke() ?: ConnectionDefaults.CONNECT_TIMEOUT_MILLIS
        set(value) {
            connectTimeoutMillisProvider = { value }
        }

    var saslMechanisms: List<String>
        get() = saslMechanismsProvider?.invoke() ?: emptyList()
        set(value) {
            saslMechanismsProvider = { value }
        }

    fun capabilities(init: CapabilityDsl.() -> Unit) {
        CapabilityDsl(sink = { provider, scope, enabled ->
            accountCapabilityDrafts += PendingCapabilityDraft(provider, scope, enabled)
        }).apply(init)
    }

    fun configs(init: ConfigDsl.() -> Unit) {
        ConfigDsl(sink = { path, value, scope ->
            accountConfigDrafts += PendingConfigDraft(path = path, value = value, scope = scope)
        }).apply(init)
    }

    fun pipelines(init: PipelinePolicyDsl.() -> Unit) {
        PipelinePolicyDsl(sink = { draft ->
            accountNodePolicyDrafts += draft
        }).apply(init)
    }

    internal fun build(): AccountDraft {
        val resolvedPasswordProvider = requireNotNull(passwordProvider) { "Account password provider is required" }
        val definition = AccountDefinition(
            jid = requireNotNull(jid) { "Account jid is required" },
            passwordProvider = { requireNotNull(resolvedPasswordProvider.invoke()) { "Account password cannot be null" } },
            connectionHost = host,
            connectionPort = port,
            securityMode = securityMode,
            resource = resource,
            saslMechanisms = saslMechanisms,
            connectTimeoutMillis = connectTimeoutMillis,
            trustAllCertificates = trustAllCertificates,
        )
        val owner = definition.jid
        return AccountDraft(
            definition = definition,
            capabilityDrafts = accountCapabilityDrafts.map { draft ->
                CapabilityDraft(
                    featureProvider = draft.featureProvider,
                    scope = (draft.scope ?: Scope.Account(owner)).enforceAccountScope(owner, "addAccount.capability"),
                    enabled = draft.enabled,
                )
            },
            configDrafts = accountConfigDrafts.map { draft ->
                ConfigDraft(
                    path = draft.path,
                    value = draft.value,
                    scope = (draft.scope ?: Scope.Account(owner)).enforceAccountScope(owner, "addAccount.config"),
                )
            },
            nodePolicyDrafts = accountNodePolicyDrafts.map { draft ->
                NodePolicyDraft(
                    nodeKey = draft.nodeKey,
                    scope = (draft.scope ?: Scope.Account(owner)).enforceAccountScope(owner, "addAccount.pipelines"),
                    enabled = draft.enabled,
                    order = draft.order,
                )
            },
        )
    }
}

@TakinaDsl
class ConnectionDsl {
    private var hostProvider: (() -> String?)? = null
    private var portProvider: (() -> Int?)? = null
    private var securityModeProvider: (() -> SecurityMode?)? = null
    private var trustAllCertificatesProvider: (() -> Boolean?)? = null
    private var saslMechanismsProvider: (() -> List<String>?)? = null

    var host: String?
        get() = hostProvider?.invoke()
        set(value) {
            hostProvider = { value }
        }

    var port: Int?
        get() = portProvider?.invoke()
        set(value) {
            portProvider = { value }
        }

    var securityMode: SecurityMode?
        get() = securityModeProvider?.invoke()
        set(value) {
            securityModeProvider = { value }
        }

    var trustAllCertificates: Boolean?
        get() = trustAllCertificatesProvider?.invoke()
        set(value) {
            trustAllCertificatesProvider = { value }
        }

    var saslMechanisms: List<String>?
        get() = saslMechanismsProvider?.invoke()
        set(value) {
            saslMechanismsProvider = { value }
        }

    fun host(provider: () -> String?) {
        hostProvider = provider
    }

    fun port(provider: () -> Int?) {
        portProvider = provider
    }

    fun securityMode(provider: () -> SecurityMode?) {
        securityModeProvider = provider
    }

    fun trustAllCertificates(provider: () -> Boolean?) {
        trustAllCertificatesProvider = provider
    }

    fun saslMechanisms(provider: () -> List<String>?) {
        saslMechanismsProvider = provider
    }

    fun saslMechanisms(vararg mechanisms: String) {
        saslMechanismsProvider = { mechanisms.toList() }
    }
}

@TakinaDsl
class FeaturesDsl internal constructor(
    private val sink: MutableList<InstalledFeature>,
    private val configureSink: MutableList<FeatureConfigureDraft>,
) {
    fun <FEATURE : TakinaFeature> install(provider: TakinaFeatureProvider<FEATURE>) {
        if (sink.none { it.provider.id == provider.id }) sink += InstalledFeature(provider = provider, feature = provider.create())
    }

    fun <FEATURE : TakinaFeature> configure(provider: TakinaFeatureProvider<FEATURE>, init: FEATURE.() -> Unit) {
        configureSink += FeatureConfigureDraft(
            featureProvider = provider,
            apply = { installed ->
                require(provider.featureType.isInstance(installed)) { "Installed feature type mismatch for ${provider.id}" }
                @Suppress("UNCHECKED_CAST") init(installed as FEATURE)
            }
        )
    }

    fun <FEATURE : TakinaFeature> installAndConfigure(provider: TakinaFeatureProvider<FEATURE>, init: FEATURE.() -> Unit) {
        install(provider)
        configure(provider, init)
    }
}

@TakinaDsl
class CapabilityDsl internal constructor(
    private val sink: (TakinaFeatureProvider<*>, Scope?, Boolean) -> Unit,
    private val normalizeScope: (Scope) -> Scope = { it },
) {
    fun enable(provider: TakinaFeatureProvider<*>, scope: Scope? = null) = sink(provider, scope?.let(normalizeScope), true)

    fun disable(provider: TakinaFeatureProvider<*>, scope: Scope? = null) = sink(provider, scope?.let(normalizeScope), false)
}

@TakinaDsl
class ConfigDsl internal constructor(
    private val sink: (path: String, value: Any?, scope: Scope?) -> Unit,
    private val normalizeScope: (Scope) -> Scope = { it },
) {
    fun set(path: String, value: Any?, scope: Scope? = null) {
        require(!ConnectionConfigPaths.isConnectionPath(path)) { "$path is account-definition-only. Configure it via addAccount { connection { ... } } or account properties." }
        sink(path, value, scope?.let(normalizeScope))
    }

    fun reconnect(init: ReconnectDsl.() -> Unit) {
        val dsl = ReconnectDsl().apply(init)
        dsl.enabled?.let { set(ReconnectConfigPaths.ENABLED, it) }
        dsl.delayMillis?.let { set(ReconnectConfigPaths.DELAY, it) }
        dsl.factor?.let { set(ReconnectConfigPaths.FACTOR, it) }
        dsl.jitter?.let { set(ReconnectConfigPaths.JITTER, it) }
        dsl.maxAttempts?.let { set(ReconnectConfigPaths.MAX_ATTEMPTS, it) }
    }

    fun observability(init: ObservabilityDsl.() -> Unit) {
        val dsl = ObservabilityDsl().apply(init)
        dsl.sampling?.let { set("observability.sampling", it) }
        dsl.alertThreshold?.let { set("observability.alertThreshold", it) }
    }

    // TODO：我加密配置呢？即草案文档里的encryptionDsl
}

internal fun Scope.enforceAccountScope(owner: BareJid, entry: String): Scope = when (this) {
    Scope.Global, Scope.Preset -> throw IllegalArgumentException("$entry does not allow ${this.kind}. Maximum scope is ACCOUNT for owner=$owner")

    is Scope.Account -> {
        require(this.owner == owner) { "$entry scope owner mismatch: expected $owner, actual ${this.owner}" }
        this
    }

    is Scope.Conversation -> {
        require(this.owner == owner) { "$entry scope owner mismatch: expected $owner, actual ${this.owner}" }
        this
    }

    is Scope.Message -> {
        require(this.owner == owner) { "$entry scope owner mismatch: expected $owner, actual ${this.owner}" }
        this
    }
}

@TakinaDsl
class ReconnectDsl {
    private var enabledProvider: (() -> Boolean?)? = null
    private var delayMillisProvider: (() -> Long?)? = null
    private var factorProvider: (() -> Double?)? = null
    private var jitterProvider: (() -> Double?)? = null
    private var maxAttemptsProvider: (() -> Int?)? = null

    var enabled: Boolean?
        get() = enabledProvider?.invoke()
        set(value) {
            enabledProvider = { value }
        }

    var delayMillis: Long?
        get() = delayMillisProvider?.invoke()
        set(value) {
            delayMillisProvider = { value }
        }

    var factor: Double?
        get() = factorProvider?.invoke()
        set(value) {
            factorProvider = { value }
        }

    var jitter: Double?
        get() = jitterProvider?.invoke()
        set(value) {
            jitterProvider = { value }
        }

    var maxAttempts: Int?
        get() = maxAttemptsProvider?.invoke()
        set(value) {
            maxAttemptsProvider = { value }
        }

    fun enabled(provider: () -> Boolean?) {
        enabledProvider = provider
    }

    fun delayMillis(provider: () -> Long?) {
        delayMillisProvider = provider
    }

    fun factor(provider: () -> Double?) {
        factorProvider = provider
    }

    fun jitter(provider: () -> Double?) {
        jitterProvider = provider
    }

    fun maxAttempts(provider: () -> Int?) {
        maxAttemptsProvider = provider
    }
}

@TakinaDsl
class ObservabilityDsl {
    private var samplingProvider: (() -> Double?)? = null
    private var alertThresholdProvider: (() -> Double?)? = null

    var sampling: Double?
        get() = samplingProvider?.invoke()
        set(value) {
            samplingProvider = { value }
        }

    var alertThreshold: Double?
        get() = alertThresholdProvider?.invoke()
        set(value) {
            alertThresholdProvider = { value }
        }

    fun sampling(provider: () -> Double?) {
        samplingProvider = provider
    }

    fun alertThreshold(provider: () -> Double?) {
        alertThresholdProvider = provider
    }
}

@TakinaDsl
class PipelinePolicyDsl internal constructor(
    private val sink: (PendingNodePolicyDraft) -> Unit,
    private val normalizeScope: (Scope) -> Scope = { it },
) {
    fun inbound(init: DirectionPolicyDsl.() -> Unit) = DirectionPolicyDsl(sink, normalizeScope).apply(init)
    fun outbound(init: DirectionPolicyDsl.() -> Unit) = DirectionPolicyDsl(sink, normalizeScope).apply(init)
}

@TakinaDsl
class DirectionPolicyDsl internal constructor(
    private val sink: (PendingNodePolicyDraft) -> Unit,
    private val normalizeScope: (Scope) -> Scope = { it },
) {
    fun about(nodeKey: String, scope: Scope? = null, init: NodePolicyDsl.() -> Unit) {
        val policy = NodePolicyDsl().apply(init)
        sink(
            PendingNodePolicyDraft(
                nodeKey = nodeKey,
                scope = scope?.let(normalizeScope),
                enabled = policy.enabled,
                order = policy.order,
            ),
        )
    }
}

@TakinaDsl
class NodePolicyDsl {
    private var enabledProvider: (() -> Boolean?)? = null
    private var orderProvider: (() -> Int?)? = null

    var enabled: Boolean?
        get() = enabledProvider?.invoke()
        set(value) {
            enabledProvider = { value }
        }

    var order: Int?
        get() = orderProvider?.invoke()
        set(value) {
            orderProvider = { value }
        }

    fun enabled(provider: () -> Boolean?) {
        enabledProvider = provider
    }

    fun order(provider: () -> Int?) {
        orderProvider = provider
    }
}
