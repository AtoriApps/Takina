package org.atoriapps.takina.core

import kotlinx.coroutines.runBlocking
import org.atoriapps.takina.core.bootstrap.FeatureTopologyValidator
import org.atoriapps.takina.core.connections.*
import org.atoriapps.takina.core.controlling.ConfigRejectCode
import org.atoriapps.takina.core.controlling.CoreConfigCatalog
import org.atoriapps.takina.core.controlling.UnifiedPolicy
import org.atoriapps.takina.core.error.ErrorDomain
import org.atoriapps.takina.core.error.TakinaError
import org.atoriapps.takina.core.error.TakinaErrors
import org.atoriapps.takina.core.error.toTakinaError
import org.atoriapps.takina.core.events.*
import org.atoriapps.takina.core.models.BatchExecutionOutcome
import org.atoriapps.takina.core.features.*
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.ResultMeta
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.pipeline.*
import org.atoriapps.takina.core.request.*
import org.atoriapps.takina.core.runtime.TakinaRuntime
import org.atoriapps.takina.core.utils.FunctionalUtils
import org.atoriapps.takina.core.utils.ParsingUtils.toBareJidOrNull
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlWriter
import org.atoriapps.takina.core.xml.xml
import kotlin.time.TimeMark
import kotlin.time.TimeSource

fun createTakina(
    featurePreset: FeaturePreset = FeaturePreset.Recommended,
    configPreset: ConfigPreset = ConfigPreset.Default,
    init: TakinaConfiguration.() -> Unit
): Takina {
    val configuration = TakinaConfiguration().apply(init)

    return CoreTakina(featurePreset, configPreset, configuration)
}

interface Takina {
    val events: TakinaEventBus
    val runtime: TakinaRuntime
    val request: TakinaRequestApi

    fun capabilities(init: CapabilityDsl.() -> Unit)
    fun configs(init: ConfigDsl.() -> Unit)
    fun addAccount(init: AccountDsl.() -> Unit)

    fun removeAccount(jid: BareJid)

    fun removeAccount(accountContext: AccountContext) = removeAccount(accountContext.owner)

    fun account(jid: BareJid): AccountContext

    fun <API : FeatureApi, FEATURE> api(provider: TakinaFeatureProvider<FEATURE>): API where FEATURE : TakinaFeature, FEATURE : ApiProvidingFeature<API>
    fun <API : FeatureApi, FEATURE> apiOrNull(provider: TakinaFeatureProvider<FEATURE>): API? where FEATURE : TakinaFeature, FEATURE : ApiProvidingFeature<API>

    suspend fun connect(jid: BareJid): TakinaResult<Unit>
    suspend fun connect(accountContext: AccountContext) = connect(accountContext.owner)
    suspend fun disconnect(jid: BareJid): TakinaResult<Unit>
    suspend fun disconnect(accountContext: AccountContext) = disconnect(accountContext.owner)
    suspend fun connectAll(): TakinaResult<BatchExecutionOutcome>
    suspend fun disconnectAll(): TakinaResult<BatchExecutionOutcome>
    suspend fun shutdown()
}

class AccountContext internal constructor(private val takina: CoreTakina, val owner: BareJid) {
    val request: TakinaRequestApi = TakinaRequestApi(takina, owner)

    // TODO、CHECK：未来要不要提供`.events`？仅监听带我户主的事件。但这样可能要提一个带户主的中间事件层

    fun capabilities(init: CapabilityDsl.() -> Unit) {
        CapabilityDsl(
            sink = { provider, scope, enabled ->
                val resolvedScope = (scope ?: Scope.Account(owner)).enforceAccountScope(owner, "account.capability")
                takina.applyCapability(provider, resolvedScope, enabled)
            },
        ).apply(init)
    }

    fun configs(init: ConfigDsl.() -> Unit) {
        ConfigDsl(
            sink = { path, mutation, scope ->
                val resolvedScope = (scope ?: Scope.Account(owner)).enforceAccountScope(owner, "account.config")
                takina.applyConfig(path, mutation, resolvedScope, owner)
            },
        ).apply(init)
    }

    suspend fun connect(): TakinaResult<Unit> = takina.connect(owner)
    suspend fun disconnect(): TakinaResult<Unit> = takina.disconnect(owner)

    // CHECK：AccountContext是否该提供`api`入口
    fun <API : FeatureApi, FEATURE> api(provider: TakinaFeatureProvider<FEATURE>): API where FEATURE : TakinaFeature, FEATURE : ApiProvidingFeature<API> = takina.api(provider)
    fun <API : FeatureApi, FEATURE> apiOrNull(provider: TakinaFeatureProvider<FEATURE>): API? where FEATURE : TakinaFeature, FEATURE : ApiProvidingFeature<API> = takina.apiOrNull(provider)

    fun chat(peer: BareJid): ChatContext = ChatContext(this, peer)
    fun room(room: BareJid): RoomContext = RoomContext(this, room)

    // 账号失效（被移除）了怎么办
}

abstract class BaseConversationContext internal constructor(
    private val account: AccountContext,
    private val peer: BareJid
) {
    fun message(init: MessageRequestDsl.() -> Unit) = account.request.message {
        to = peer
        init()
    }
}

// TODO：应再提供拉黑等方法，但能力由Feature提供，所以应该是扩展方法？
class ChatContext internal constructor(account: AccountContext, peer: BareJid) : BaseConversationContext(account, peer) {
}

// TODO、CHECK：我觉得这个类应该由Feature作为扩展提供（未来移走）。因为Muc是Feature提供的。RoomCtx应提供如join、leave的便捷方法
class RoomContext internal constructor(account: AccountContext, peer: BareJid) : BaseConversationContext(account, peer) {
}

// TODO、CHECK：内部要不要拆，会不会太重？
internal class CoreTakina(
    private val featurePreset: FeaturePreset,
    private val configPreset: ConfigPreset,
    private val bootstrapConfiguration: TakinaConfiguration,
) : Takina, RequestExecutor {
    override val events: TakinaEventBus = TakinaEventBus()

    private val installedFeatures: List<InstalledFeature> = resolvePresetFeatures(featurePreset) + bootstrapConfiguration.features
    private val featureRegistry: FeatureRegistry
    private val unifiedPolicy: UnifiedPolicy
    private val pipelineRuntime: PipelineRuntime
    override val runtime: TakinaRuntime
    override val request: TakinaRequestApi
    private val featureApisByProvider = mutableMapOf<TakinaFeatureProvider<*>, FeatureApi>()

    private val accounts = linkedMapOf<BareJid, AccountDefinition>()
    private val stateMachines = linkedMapOf<BareJid, ConnectionStateMachine>()
    private val transports = linkedMapOf<BareJid, XmppTransport>()
    private val intentionalDisconnectOwners = mutableSetOf<BareJid>()
    private var started = false
    private var shutdown = false

    init {
        applyFeatureConfigureDrafts(installedFeatures, bootstrapConfiguration.featureConfigureDrafts)
        FeatureTopologyValidator.validateOrThrow(installedFeatures)

        featureRegistry = FeatureRegistry(installedFeatures)
        unifiedPolicy = UnifiedPolicy(featureRegistry)
        pipelineRuntime = PipelineRuntime(unifiedPolicy) { failure ->
            val error = failure.cause.toTakinaError(
                domain = ErrorDomain.PIPELINE,
                number = 210,
                retryable = true,
                fallbackMessage = "Pipeline node ${failure.nodeKey} execution failed",
            )
            events.emit(
                PipelineNodeFailedEvent(
                    owner = failure.owner,
                    direction = failure.direction,
                    nodeKey = failure.nodeKey,
                    error = error,
                ),
            )
        }
        runtime = TakinaRuntime(unifiedPolicy, pipelineRuntime)
        request = TakinaRequestApi(this)

        // 从功能实例提取API和节点并注册
        installedFeatures.forEach { installed ->
            val feature = installed.feature

            feature.inboundNodes().forEach { pipelineRuntime.registerInboundNode(it, installed.provider) }
            feature.outboundNodes().forEach { pipelineRuntime.registerOutboundNode(it, installed.provider) }

            if (feature is ApiProvidingFeature<*>) featureApisByProvider[installed.provider] = feature.api()
        }

        // 应用配置
        applyConfigPreset(configPreset)
        bootstrapConfiguration.configDrafts.forEach { applyConfigDraft(it) }
        bootstrapConfiguration.capabilityDrafts.forEach { applyCapabilityDraft(it) }
        bootstrapConfiguration.nodePolicyDrafts.forEach { applyNodePolicyDraft(it) }

        // 安装账号
        bootstrapConfiguration.accounts.values.forEach { installAccount(it) }

        // 回调onInstall
        runBlocking { installedFeatures.forEach { installed -> installed.feature.onInstall(this@CoreTakina) } }

        started = true
        events.emit(TakinaStartedEvent())
    }

    override fun capabilities(init: CapabilityDsl.() -> Unit) {
        ensureStarted()
        CapabilityDsl(sink = { provider, scope, enabled -> applyCapability(provider, scope ?: Scope.Global, enabled) }).apply(init)
    }

    override fun configs(init: ConfigDsl.() -> Unit) {
        ensureStarted()
        ConfigDsl(sink = { path, mutation, scope -> applyConfig(path, mutation, scope ?: Scope.Global, null) }).apply(init)
    }

    override fun addAccount(init: AccountDsl.() -> Unit) {
        ensureStarted()
        applyAccountDraft(AccountDsl().apply(init).build())
    }

    private fun applyAccountDraft(draft: AccountDraft) {
        installAccount(draft.definition)
        draft.capabilityDrafts.forEach { applyCapabilityDraft(it) }
        draft.configDrafts.forEach { applyConfigDraft(it, draft.definition.jid) }
        draft.nodePolicyDrafts.forEach { applyNodePolicyDraft(it) }
    }

    private fun applyCapabilityDraft(draft: CapabilityDraft) {
        applyCapability(draft.featureProvider, draft.scope, draft.enabled)
    }

    private fun applyConfigDraft(draft: ConfigDraft, accountOwner: BareJid? = null) {
        applyConfig(draft.path, draft.mutation, draft.scope, accountOwner)
    }

    private fun applyNodePolicyDraft(draft: NodePolicyDraft) {
        draft.enabled?.let { enabled -> unifiedPolicy.setNodeEnabled(draft.nodeKey, draft.scope, enabled) }
        draft.order?.let { order -> unifiedPolicy.setNodeOrder(draft.nodeKey, draft.scope, order) }
    }

    override fun removeAccount(jid: BareJid) {
        ensureStarted()

        val machine = requireNotNull(stateMachines[jid]) { "Account not found: $jid" }
        val connection = machine.currentState()
        require(connection == ConnectionState.IDLE || connection == ConnectionState.CLOSED) { "Account must be detached from active connection before removal: $jid" }
        runtime.setAccountState(jid, AccountState.REMOVED)
        runBlocking { transports.remove(jid)?.disconnect() }
        stateMachines.remove(jid)
        accounts.remove(jid)
        events.emit(AccountRemovedEvent(jid))
    }

    override fun account(jid: BareJid): AccountContext {
        ensureStarted()
        require(accounts.containsKey(jid)) { "Account not found: $jid" }
        return AccountContext(this, jid)
    }

    override fun <API : FeatureApi, FEATURE> api(provider: TakinaFeatureProvider<FEATURE>): API where FEATURE : TakinaFeature, FEATURE : ApiProvidingFeature<API> =
        requireNotNull(apiOrNull(provider)) { "Feature API not available: ${provider.id}" }

    override fun <API : FeatureApi, FEATURE> apiOrNull(provider: TakinaFeatureProvider<FEATURE>): API? where FEATURE : TakinaFeature, FEATURE : ApiProvidingFeature<API> {
        val raw = featureApisByProvider[provider] ?: return null
        @Suppress("UNCHECKED_CAST")
        return raw as API
    }

    override suspend fun connect(jid: BareJid): TakinaResult<Unit> {
        val correlationId = FunctionalUtils.newTraceId("conn")
        val mark = TimeSource.Monotonic.markNow() // 记录开始时间点

        inactiveErrorOrNull()?.let { error ->
            events.emit(RequestFailedEvent(jid, CoreRequestTypes.CONNECT, error))
            return errResult(error, correlationId, mark)
        }

        val account = accounts[jid]
        if (account == null) {
            val error = TakinaErrors.of(
                domain = ErrorDomain.CONFIG,
                number = 302,
                message = "Account not found: $jid",
                retryable = false,
            )
            events.emit(RequestFailedEvent(jid, CoreRequestTypes.CONNECT, error))
            return errResult(error, correlationId, mark)
        }

        val machine = stateMachines[jid]
        if (machine == null) {
            val error = TakinaErrors.of(
                domain = ErrorDomain.INTERNAL,
                number = 302,
                message = "State machine not found: $jid",
                retryable = false,
            )
            events.emit(RequestFailedEvent(jid, CoreRequestTypes.CONNECT, error))
            return errResult(error, correlationId, mark)
        }

        if (machine.currentState() == ConnectionState.ESTABLISHED) {
            runtime.setAccountState(jid, AccountState.ONLINE)
            return okResult(Unit, correlationId, mark)
        }

        if (machine.currentState() == ConnectionState.CLOSED) {
            val error = transition(jid, machine, ConnectionState.IDLE)
            if (error != null) {
                runtime.setAccountState(jid, AccountState.DEGRADED)
                return errResult(error, correlationId, mark)
            }
        }

        runtime.setAccountState(jid, AccountState.CONNECTING)
        unifiedPolicy.onNextConnectionBoundary()

        val transport = XmppTransportFactoryRegistry.factory(
            account.getConnectionConfig(),
            transportCallbacksFor(jid),
        )

        runCatching { transports.remove(jid)?.disconnect() }
        transports[jid] = transport

        return runCatching {
            transport.connect(account.passwordProvider())
            runtime.setAccountState(jid, AccountState.ONLINE)
            events.emit(SessionReadyEvent(jid))
            okResult(Unit, correlationId, mark)
        }.getOrElse { failure ->
            runtime.setAccountState(jid, AccountState.DEGRADED)
            val error = failure.toTakinaError(
                domain = ErrorDomain.TRANSPORT,
                number = 104,
                retryable = true,
                fallbackMessage = failure.message ?: "connect failed",
            )
            events.emit(RequestFailedEvent(jid, CoreRequestTypes.CONNECT, error))
            errResult(error, correlationId, mark)
        }
    }

    override suspend fun disconnect(jid: BareJid): TakinaResult<Unit> {
        val correlationId = FunctionalUtils.newTraceId("disc")
        val mark = TimeSource.Monotonic.markNow()
        inactiveErrorOrNull()?.let { error ->
            events.emit(RequestFailedEvent(jid, CoreRequestTypes.DISCONNECT, error))
            return errResult(error, correlationId, mark)
        }

        val machine = stateMachines[jid]
        if (machine == null) {
            val error = TakinaErrors.of(
                domain = ErrorDomain.CONFIG,
                number = 303,
                message = "Account not found: $jid",
                retryable = false,
            )
            events.emit(RequestFailedEvent(jid, CoreRequestTypes.DISCONNECT, error))
            return errResult(error, correlationId, mark)
        }

        intentionalDisconnectOwners += jid
        try {
            val disconnectFailure = runCatching { transports.remove(jid)?.disconnect() }.exceptionOrNull()
            if (disconnectFailure != null) {
                val error = disconnectFailure.toTakinaError(
                    domain = ErrorDomain.TRANSPORT,
                    number = 105,
                    retryable = true,
                    fallbackMessage = disconnectFailure.message ?: "disconnect failed",
                )
                runtime.setAccountState(jid, AccountState.DEGRADED)
                events.emit(RequestFailedEvent(jid, CoreRequestTypes.DISCONNECT, error))
                return errResult(error, correlationId, mark)
            }

            if (machine.currentState() != ConnectionState.CLOSED) {
                val error = transition(jid, machine, ConnectionState.CLOSED)
                if (error != null) {
                    runtime.setAccountState(jid, AccountState.DEGRADED)
                    return errResult(error, correlationId, mark)
                }
            }

            runtime.setAccountState(jid, AccountState.OFFLINE)
            return okResult(Unit, correlationId, mark)
        } finally {
            intentionalDisconnectOwners -= jid
        }
    }

    override suspend fun connectAll(): TakinaResult<BatchExecutionOutcome> {
        val correlationId = FunctionalUtils.newTraceId("conn-all")
        val mark = TimeSource.Monotonic.markNow()
        inactiveErrorOrNull()?.let { error ->
            events.emit(RequestFailedEvent(null, CoreRequestTypes.CONNECT_ALL, error))
            return errResult(error, correlationId, mark)
        }

        val results = linkedMapOf<BareJid, TakinaResult<Unit>>()
        return runCatching {
            accounts.keys.toList().forEach { owner ->
                results[owner] = connect(owner)
            }

            val failedCount = results.values.count { it is TakinaResult.Err }
            val outcome = BatchExecutionOutcome(
                succeed = results.size - failedCount,
                failed = failedCount,
            )
            events.emit(AllConnectEvent(outcome = outcome, results = results))
            okResult(outcome, correlationId, mark)
        }.getOrElse { failure ->
            val error = failure.toTakinaError(
                domain = ErrorDomain.INTERNAL,
                number = 151,
                retryable = true,
                fallbackMessage = failure.message ?: "connectAll did not complete",
            )
            events.emit(RequestFailedEvent(null, CoreRequestTypes.CONNECT_ALL, error))
            errResult(error, correlationId, mark)
        }
    }

    override suspend fun disconnectAll(): TakinaResult<BatchExecutionOutcome> {
        val correlationId = FunctionalUtils.newTraceId("disc-all")
        val mark = TimeSource.Monotonic.markNow()
        inactiveErrorOrNull()?.let { error ->
            events.emit(RequestFailedEvent(null, CoreRequestTypes.DISCONNECT_ALL, error))
            return errResult(error, correlationId, mark)
        }

        val results = linkedMapOf<BareJid, TakinaResult<Unit>>()
        return runCatching {
            accounts.keys.toList().forEach { owner ->
                results[owner] = disconnect(owner)
            }

            val failedCount = results.values.count { it is TakinaResult.Err }
            val outcome = BatchExecutionOutcome(
                succeed = results.size - failedCount,
                failed = failedCount,
            )
            events.emit(AllDisconnectEvent(outcome = outcome, results = results))
            okResult(outcome, correlationId, mark)
        }.getOrElse { failure ->
            val error = failure.toTakinaError(
                domain = ErrorDomain.INTERNAL,
                number = 152,
                retryable = true,
                fallbackMessage = failure.message ?: "disconnectAll did not complete",
            )
            events.emit(RequestFailedEvent(null, CoreRequestTypes.DISCONNECT_ALL, error))
            errResult(error, correlationId, mark)
        }
    }

    override suspend fun shutdown() {
        if (shutdown) return
        disconnectAll()
        installedFeatures.forEach { installed -> installed.feature.onShutdown(this@CoreTakina) }
        transports.clear()
        shutdown = true
        events.emit(TakinaShutdownCompletedEvent())
    }

    // HACK：这几个是不是不建议在这里构建吧，没准未来解耦？
    override suspend fun sendMessage(request: MessageRequest): TakinaResult<MessageOutcome> {
        val correlationId = request.messageId
        val mark = TimeSource.Monotonic.markNow()
        inactiveErrorOrNull()?.let { error ->
            events.emit(RequestFailedEvent(request.from, CoreRequestTypes.MESSAGE, error))
            request.from?.let { owner -> events.emit(MessageSendFailedEvent(owner, request.to, error)) }
            return errResult(error, correlationId, mark)
        }

        unifiedPolicy.onNextItemBoundary()
        val (owner, ownerError) = resolveOwnerOrError(request.from)
        if (ownerError != null || owner == null) {
            val error = ownerError ?: TakinaErrors.of(ErrorDomain.INTERNAL, 901, "Owner resolution failed", retryable = false)
            events.emit(RequestFailedEvent(request.from, CoreRequestTypes.REQUEST_ROUTING, error))
            return errResult(error, correlationId, mark)
        }

        val (transport, transportError) = connectedTransportOrError(owner)
        if (transportError != null || transport == null) {
            val error = transportError ?: TakinaErrors.of(ErrorDomain.INTERNAL, 902, "Transport resolution failed", retryable = false)
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.MESSAGE, error))
            events.emit(MessageSendFailedEvent(owner, request.to, error))
            return errResult(error, correlationId, mark)
        }

        val scope = Scope.Message(owner, request.to, request.messageId)
        val raw = XmlWriter.render(xml("message") {
            attr("id", request.messageId)
            attr("to", request.to.toString())
            attr("from", transport.boundJid)
            element("body") { text(request.body) }
        })

        val processed = pipelineRuntime.executeOutbound(OutboundFrame(raw, OutboundClassification.BUSINESS, owner), scope)
        if (processed == null) {
            val error = TakinaErrors.of(ErrorDomain.PIPELINE, 201, "Outbound message dropped", retryable = false)
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.MESSAGE, error))
            events.emit(MessageSendFailedEvent(owner, request.to, error))
            return errResult(error, correlationId, mark)
        }

        return runCatching {
            events.emit(FinalFrameOutboundEvent(owner = owner, xml = processed, classification = OutboundClassification.BUSINESS, source = OutboundSources.MESSAGE))

            transport.sendRaw(processed)
            events.emit(MessageSentEvent(owner = owner, to = request.to, body = request.body))
            okResult(MessageOutcome(request.messageId), correlationId, mark)
        }.getOrElse { failure ->
            val error = failure.toTakinaError(
                domain = ErrorDomain.TRANSPORT,
                number = 101,
                retryable = true,
                fallbackMessage = failure.message ?: "send failed",
            )
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.MESSAGE, error))
            events.emit(MessageSendFailedEvent(owner, request.to, error))
            errResult(error, correlationId, mark)
        }
    }

    override suspend fun sendPresence(request: PresenceRequest): TakinaResult<PresenceOutcome> {
        val correlationId = FunctionalUtils.newTraceId("presence")
        val mark = TimeSource.Monotonic.markNow()
        inactiveErrorOrNull()?.let { error ->
            events.emit(RequestFailedEvent(request.from, CoreRequestTypes.PRESENCE, error))
            return errResult(error, correlationId, mark)
        }

        unifiedPolicy.onNextItemBoundary()
        val (owner, ownerError) = resolveOwnerOrError(request.from)
        if (ownerError != null || owner == null) {
            val error = ownerError ?: TakinaErrors.of(ErrorDomain.INTERNAL, 903, "Owner resolution failed", retryable = false)
            events.emit(RequestFailedEvent(request.from, CoreRequestTypes.REQUEST_ROUTING, error))
            return errResult(error, correlationId, mark)
        }

        val (transport, transportError) = connectedTransportOrError(owner)
        if (transportError != null || transport == null) {
            val error = transportError ?: TakinaErrors.of(ErrorDomain.INTERNAL, 904, "Transport resolution failed", retryable = false)
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.PRESENCE, error))
            return errResult(error, correlationId, mark)
        }

        val raw = XmlWriter.render(xml("presence") {
            request.to?.let { attr("to", it.toString()) }
            attr("from", transport.boundJid)
            request.show?.let { element("show") { text(it.wireValue) } }
            request.status?.let { element("status") { text(it) } }
        })

        val scope = Scope.Account(owner)
        val processed = pipelineRuntime.executeOutbound(OutboundFrame(raw, OutboundClassification.BUSINESS, owner), scope)
        if (processed == null) {
            val error = TakinaErrors.of(ErrorDomain.PIPELINE, 202, "Outbound presence dropped", retryable = false)
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.PRESENCE, error))
            return errResult(error, correlationId, mark)
        }

        return runCatching {
            events.emit(FinalFrameOutboundEvent(owner = owner, xml = processed, classification = OutboundClassification.BUSINESS, source = OutboundSources.PRESENCE))

            transport.sendRaw(processed)
            okResult(PresenceOutcome(), correlationId, mark)
        }.getOrElse { failure ->
            val error = failure.toTakinaError(
                domain = ErrorDomain.TRANSPORT,
                number = 102,
                retryable = true,
                fallbackMessage = failure.message ?: "presence send failed",
            )
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.PRESENCE, error))
            errResult(error, correlationId, mark)
        }
    }

    override suspend fun sendIq(request: IqRequest): TakinaResult<IqOutcome> {
        val correlationId = request.id
        val mark = TimeSource.Monotonic.markNow()
        inactiveErrorOrNull()?.let { error ->
            events.emit(RequestFailedEvent(request.from, CoreRequestTypes.IQ, error))
            return errResult(error, correlationId, mark)
        }

        unifiedPolicy.onNextItemBoundary()
        val (owner, ownerError) = resolveOwnerOrError(request.from)
        if (ownerError != null || owner == null) {
            val error = ownerError ?: TakinaErrors.of(ErrorDomain.INTERNAL, 905, "Owner resolution failed", retryable = false)
            events.emit(RequestFailedEvent(request.from, CoreRequestTypes.REQUEST_ROUTING, error))
            return errResult(error, correlationId, mark)
        }

        val (transport, transportError) = connectedTransportOrError(owner)
        if (transportError != null || transport == null) {
            val error = transportError ?: TakinaErrors.of(ErrorDomain.INTERNAL, 906, "Transport resolution failed", retryable = false)
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.IQ, error))
            return errResult(error, correlationId, mark)
        }

        val raw = XmlWriter.render(xml("iq") {
            attr("id", request.id)
            attr("type", request.type)
            attr("to", request.to?.toString())
            attr("from", transport.boundJid)
            request.payload?.let { node(it) }
        })
        val scope = Scope.Account(owner)
        val processed = pipelineRuntime.executeOutbound(OutboundFrame(raw, OutboundClassification.BUSINESS, owner), scope)
        if (processed == null) {
            val error = TakinaErrors.of(ErrorDomain.PIPELINE, 203, "Outbound iq dropped", retryable = false)
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.IQ, error))
            return errResult(error, correlationId, mark)
        }

        return runCatching {
            events.emit(FinalFrameOutboundEvent(owner = owner, xml = processed, classification = OutboundClassification.BUSINESS, source = OutboundSources.IQ))

            transport.sendRaw(processed)
            okResult(IqOutcome(request.id), correlationId, mark)
        }.getOrElse { failure ->
            val error = failure.toTakinaError(
                domain = ErrorDomain.TRANSPORT,
                number = 103,
                retryable = true,
                fallbackMessage = failure.message ?: "iq send failed",
            )
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.IQ, error))
            errResult(error, correlationId, mark)
        }
    }

    internal fun applyCapability(featureProvider: TakinaFeatureProvider<*>, scope: Scope, enabled: Boolean) {
        unifiedPolicy.setFeatureEnabled(featureProvider, scope, enabled)
        events.emit(FeatureStateChangedEvent(feature = featureProvider.id, enabled = enabled))
    }

    internal fun applyConfig(path: String, mutation: ConfigMutation, scope: Scope, accountOwner: BareJid?) {
        val result = when (mutation) {
            is ConfigMutation.Set -> unifiedPolicy.applyConfig(path, mutation.value, scope)
            ConfigMutation.Unset -> unifiedPolicy.unsetConfig(path, scope)
        }
        val eventPath = result.path

        when {
            result.rejectedReason != null -> events.emit(
                ConfigRejectedEvent(
                    path = eventPath,
                    rejectCode = result.rejectCode ?: ConfigRejectCode.VALIDATION_FAILED,
                    reason = result.rejectedReason,
                ),
            )
            result.applied -> events.emit(ConfigAppliedEvent(eventPath, result.applyMode.name))
            else -> events.emit(ConfigApplyDeferredEvent(eventPath, result.applyMode.name))
        }

        when (scope) {
            Scope.Global, Scope.Preset -> events.emit(GlobalConfigChangedEvent(eventPath))

            is Scope.Account -> events.emit(AccountConfigChangedEvent(scope.owner, eventPath))

            is Scope.Conversation -> events.emit(AccountConfigChangedEvent(scope.owner, eventPath))

            is Scope.Message -> events.emit(AccountConfigChangedEvent(scope.owner, eventPath))
        }

        if (accountOwner != null && scope !is Scope.Account && scope !is Scope.Conversation && scope !is Scope.Message) {
            events.emit(AccountConfigChangedEvent(accountOwner, eventPath))
        }
    }

    suspend fun onUnexpectedDisconnect(owner: BareJid, reason: String?, authHardFailure: Boolean) {
        val machine = stateMachines[owner] ?: return
        runtime.setAccountState(owner, AccountState.DEGRADED)
        events.emit(UnexpectedDisconnectedEvent(owner, reason))

        // TODO：要在这里加个插件回调钩子，如果插件（如SM）处理成功，则不触发FinalReconnect。可能也要加发一个原始断连事件？

        val finalReconnect = FinalReconnect(
            onSchedule = { attempt, delay ->
                runtime.setAccountState(owner, AccountState.RECONNECTING)
                if (machine.currentState() != ConnectionState.RECONNECT_WAIT) transition(owner, machine, ConnectionState.RECONNECT_WAIT)
                events.emit(ReconnectScheduledEvent(owner, attempt, delay))
            }, connectAttempt = {
                connect(owner) is TakinaResult.Ok
            }
        )

        val policy = ReconnectPolicy(
            enabled = unifiedPolicy.currentConfigOrDefault(CoreConfigCatalog.Reconnect.ENABLED, Scope.Account(owner)),
            delayMillis = unifiedPolicy.currentConfigOrDefault(CoreConfigCatalog.Reconnect.DELAY, Scope.Account(owner)),
            factor = unifiedPolicy.currentConfigOrDefault(CoreConfigCatalog.Reconnect.FACTOR, Scope.Account(owner)),
            jitter = unifiedPolicy.currentConfigOrDefault(CoreConfigCatalog.Reconnect.JITTER, Scope.Account(owner)),
            maxAttempts = unifiedPolicy.currentConfigOrDefault(CoreConfigCatalog.Reconnect.MAX_ATTEMPTS, Scope.Account(owner)),
        )

        val outcome = finalReconnect.perform(owner, policy, authHardFailure = authHardFailure)
        if (!outcome.succeed) {
            runtime.setAccountState(owner, AccountState.FAILED)
            events.emit(ReconnectExhaustedEvent(owner, outcome.attempts))
        }
    }

    private fun transportCallbacksFor(owner: BareJid): XmppTransportCallbacks {
        val machine = requireNotNull(stateMachines[owner]) { "State machine not found for owner $owner" }

        return object : XmppTransportCallbacks {
            override suspend fun onStateChanged(to: ConnectionState) {
                transition(owner, machine, to)
            }

            override suspend fun onFrame(frame: String) {
                handleInboundFrame(owner, frame)
            }

            override suspend fun onFrameParseFailed(raw: String, reason: String) {
                events.emit(FrameInboundParseFailedEvent(owner = owner, raw = raw, reason = reason))
            }

            override suspend fun onClosed(reason: String?) {
                if (owner in intentionalDisconnectOwners || shutdown) return
                onUnexpectedDisconnect(owner, reason, authHardFailure = false)
            }
        }
    }

    private suspend fun handleInboundFrame(owner: BareJid, frame: String) {
        events.emit(RawFrameInboundEvent(owner = owner, xml = frame))

        unifiedPolicy.onNextItemBoundary()
        val classification = classifyInbound(frame)
        val scope = Scope.Account(owner)
        val processed = pipelineRuntime.executeInbound(
            frame = InboundFrame(raw = frame, classification = classification, owner = owner),
            scope = scope,
        ) ?: return
        val parsed = XmlParser.parseElementOrNull(processed)

        when (classification) {
            InboundClassification.STANZA_MESSAGE -> {
                if (parsed == null) {
                    events.emit(FrameInboundParseFailedEvent(owner = owner, raw = processed, reason = "Invalid message stanza XML"))
                    return
                }
                val from = parsed.attribute("from")?.toBareJidOrNull()
                val body = parsed.firstDescendant("body")?.textContent()
                events.emit(MessageReceivedEvent(owner = owner, from = from, body = body))
            }

            InboundClassification.STANZA_PRESENCE -> {
                if (parsed == null) {
                    events.emit(FrameInboundParseFailedEvent(owner = owner, raw = processed, reason = "Invalid presence stanza XML"))
                    return
                }
                val from = parsed.attribute("from")?.toBareJidOrNull()
                events.emit(PresenceReceivedEvent(owner = owner, from = from))
            }

            InboundClassification.STANZA_IQ -> {
                if (parsed == null) {
                    events.emit(FrameInboundParseFailedEvent(owner = owner, raw = processed, reason = "Invalid iq stanza XML"))
                    return
                }
                val from = parsed.attribute("from")?.toBareJidOrNull()
                events.emit(IqReceivedEvent(owner = owner, from = from))
            }

            InboundClassification.UNKNOWN -> events.emit(UnknownFrameInboundEvent(owner = owner, raw = processed))

            else -> Unit
        }
    }

    private fun transition(owner: BareJid, machine: ConnectionStateMachine, next: ConnectionState): TakinaError? {
        if (machine.currentState() == next) return null
        val result = machine.transitionTo(next)
        if (!result.accepted) {
            val error = result.errorCode?.let { code ->
                TakinaError(
                    code = code,
                    domain = ErrorDomain.STREAM,
                    message = "Illegal transition ${result.from} -> $next",
                    retryable = false,
                )
            } ?: TakinaErrors.of(
                domain = ErrorDomain.STREAM,
                number = 101,
                message = "Illegal transition ${result.from} -> $next",
                retryable = false,
            )
            events.emit(RequestFailedEvent(owner, CoreRequestTypes.STATE_TRANSITION, error))
            return error
        }
        runtime.setConnectionState(owner, next)
        runBlocking {
            installedFeatures.forEach { installed ->
                installed.feature.lifecycleHooks().forEach { hook ->
                    hook.onConnectionStateChanged(owner, result.from, result.to)
                }
            }
        }
        events.emit(ConnectionStateChangedEvent(owner, result.from, result.to))
        return null
    }

    private fun installAccount(account: AccountDefinition) {
        require(accounts.putIfAbsent(account.jid, account) == null) { "Duplicate account: ${account.jid}" }

        stateMachines[account.jid] = ConnectionStateMachine(ConnectionState.IDLE)
        runtime.setAccountState(account.jid, AccountState.REGISTERED)
        runtime.setConnectionState(account.jid, ConnectionState.IDLE)

        events.emit(AccountAddedEvent(account.jid))
    }

    private fun connectedTransportOrError(owner: BareJid): Pair<XmppTransport?, TakinaError?> {
        val transport = transports[owner]
        if (transport == null || !transport.isConnected) {
            return null to TakinaErrors.of(
                domain = ErrorDomain.TRANSPORT,
                number = 106,
                message = "Account $owner is not connected",
                retryable = true,
            )
        }
        return transport to null
    }

    private fun resolveOwnerOrError(requested: BareJid?): Pair<BareJid?, TakinaError?> {
        if (requested != null) return requested to null
        if (accounts.size == 1) return accounts.keys.first() to null
        return null to TakinaErrors.of(
            domain = ErrorDomain.CONFIG,
            number = 301,
            message = "Ambiguous account owner for request",
            retryable = false,
        )
    }

    private fun applyConfigPreset(configPreset: ConfigPreset) {
        when (configPreset) {
            ConfigPreset.Default -> {
                CoreConfigCatalog.presetDefaults.forEach { (spec, defaultValue) ->
                    unifiedPolicy.applyConfig(spec.path, defaultValue, Scope.Preset)
                }
            }
        }
    }

    private fun applyFeatureConfigureDrafts(installed: List<InstalledFeature>, drafts: List<FeatureConfigureDraft>) {
        if (drafts.isEmpty()) return
        val installedByProvider = installed.associateBy { it.provider }
        drafts.forEach { draft ->
            val target = installedByProvider[draft.featureProvider]?.feature
                ?: throw IllegalArgumentException("Feature ${draft.featureProvider.id} is not installed, cannot configure")
            draft.apply(target)
        }
    }

    private fun resolvePresetFeatures(featurePreset: FeaturePreset): List<InstalledFeature> = when (featurePreset) {
        FeaturePreset.Minimal -> emptyList()
        FeaturePreset.Recommended -> emptyList()
        FeaturePreset.Full -> emptyList()
    }

    private fun inactiveErrorOrNull(): TakinaError? = if (started && !shutdown) {
        null
    } else {
        TakinaErrors.of(
            domain = ErrorDomain.INTERNAL,
            number = 100,
            message = "Takina is not active",
            retryable = false,
        )
    }

    private fun <T> okResult(value: T, correlationId: String?, mark: TimeMark): TakinaResult.Ok<T> = TakinaResult.Ok(
        value = value,
        meta = ResultMeta(
            correlationId = correlationId,
            elapsed = mark.elapsedNow(),
        ),
    )

    private fun errResult(error: TakinaError, correlationId: String?, mark: TimeMark): TakinaResult.Err = TakinaResult.Err(
        error = error,
        meta = ResultMeta(
            correlationId = correlationId,
            elapsed = mark.elapsedNow(),
        )
    )

    private fun ensureStarted() = check(started && !shutdown) { "Takina is not active" }

    private fun AccountDefinition.getConnectionConfig(): ConnectionConfig {
        val mode = securityMode ?: ConnectionDefaults.SECURITY_MODE

        return ConnectionConfig(
            owner = jid,
            host = connectionHost ?: jid.domain,
            port = connectionPort ?: mode.defaultPort,
            securityMode = mode,
            resource = resource,
            saslMechanisms = saslMechanisms,
            connectTimeoutMillis = connectTimeoutMillis,
            trustAllCertificates = trustAllCertificates,
        )
    }
}

