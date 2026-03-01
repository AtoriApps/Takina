package org.atoriapps.takina.core

import org.atoriapps.takina.core.connections.SecurityMode
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
    val resource: String = "takina",
    val saslMechanisms: List<String> = listOf("SCRAM-SHA-256", "SCRAM-SHA-1", "DIGEST-MD5", "PLAIN"),
    val connectTimeoutMillis: Int = 10_000,
    val trustAllCertificates: Boolean = false,
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
        require(accounts.putIfAbsent(built.jid, built) == null) { "Duplicate account: ${built.jid}" }
    }

    fun features(init: FeaturesDsl.() -> Unit) {
        FeaturesDsl(features, featureConfigureDrafts).apply(init)
    }

    // CHECK：好像不对，能力不是按作用域控制功能的开关吗
    fun capability(init: CapabilityDsl.() -> Unit) {
        CapabilityDsl { provider, scope, enabled ->
            capabilityDrafts += CapabilityDraft(provider, scope, enabled)
        }.apply(init)
    }

    fun config(init: ConfigDsl.() -> Unit) {
        ConfigDsl(Scope.Global) { path, value, scope ->
            configDrafts += ConfigDraft(path = path, value = value, scope = scope)
        }.apply(init)
    }

    fun pipeline(init: PipelinePolicyDsl.() -> Unit) {
        PipelinePolicyDsl { draft -> nodePolicyDrafts += draft }.apply(init)
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
    private var trustAllCertificates: Boolean = false
    private var resourceProvider: (() -> String)? = { "takina" }
    private var saslMechanismsProvider: (() -> List<String>)? = { listOf("SCRAM-SHA-256", "SCRAM-SHA-1", "DIGEST-MD5", "PLAIN") }
    private var connectTimeoutMillisProvider: (() -> Int)? = { 10_000 }

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
        trustAllCertificates = built.trustAllCertificates ?: false
        built.saslMechanisms?.let { saslMechanisms = it }
    }

    var resource: String
        get() = resourceProvider?.invoke() ?: "takina"
        set(value) {
            resourceProvider = { value }
        }

    var connectTimeoutMillis: Int
        get() = connectTimeoutMillisProvider?.invoke() ?: 10_000
        set(value) {
            connectTimeoutMillisProvider = { value }
        }

    var saslMechanisms: List<String>
        get() = saslMechanismsProvider?.invoke() ?: emptyList()
        set(value) {
            saslMechanismsProvider = { value }
        }

    // TODO：账号粒度的cfg的快捷配置呢？

    internal fun build(): AccountDefinition {
        val resolvedPasswordProvider = requireNotNull(passwordProvider) { "Account password provider is required" }
        return AccountDefinition(
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
                @Suppress("UNCHECKED_CAST")
                (installed as FEATURE).init()
            },
        )
    }

    fun <FEATURE : TakinaFeature> installAndConfigure(provider: TakinaFeatureProvider<FEATURE>, init: FEATURE.() -> Unit) {
        install(provider)
        configure(provider, init)
    }
}

@TakinaDsl
class CapabilityDsl internal constructor(
    private val sink: (TakinaFeatureProvider<*>, Scope, Boolean) -> Unit,
) {
    fun enable(provider: TakinaFeatureProvider<*>, scope: Scope = Scope.Global) = sink(provider, scope, true)
    fun disable(provider: TakinaFeatureProvider<*>, scope: Scope = Scope.Global) = sink(provider, scope, false)
}

@TakinaDsl
class ConfigDsl internal constructor(
    private val defaultScope: Scope,
    private val sink: (path: String, value: Any?, scope: Scope) -> Unit,
) {
    fun set(path: String, value: Any?, scope: Scope = defaultScope) = sink(path, value, scope)

    fun reconnect(init: ReconnectDsl.() -> Unit) {
        val dsl = ReconnectDsl().apply(init)
        dsl.enabled?.let { set("reconnect.enabled", it) }
        dsl.delayMillis?.let { set("reconnect.delay", it) }
        dsl.factor?.let { set("reconnect.factor", it) }
        dsl.jitter?.let { set("reconnect.jitter", it) }
        dsl.maxAttempts?.let { set("reconnect.maxAttempts", it) }
    }

    fun observability(init: ObservabilityDsl.() -> Unit) {
        val dsl = ObservabilityDsl().apply(init)
        dsl.sampling?.let { set("observability.sampling", it) }
        dsl.alertThreshold?.let { set("observability.alertThreshold", it) }
    }

    // TODO：我加密呢？
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
    private val sink: (NodePolicyDraft) -> Unit,
) {
    fun inbound(init: DirectionPolicyDsl.() -> Unit) = DirectionPolicyDsl(sink).apply(init)
    fun outbound(init: DirectionPolicyDsl.() -> Unit) = DirectionPolicyDsl(sink).apply(init)
}

@TakinaDsl
class DirectionPolicyDsl internal constructor(private val sink: (NodePolicyDraft) -> Unit ) {
    fun about(nodeKey: String, scope: Scope = Scope.Global, init: NodePolicyDsl.() -> Unit) {
        val policy = NodePolicyDsl().apply(init)
        sink(NodePolicyDraft(nodeKey = nodeKey, scope = scope, enabled = policy.enabled, order = policy.order))
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
