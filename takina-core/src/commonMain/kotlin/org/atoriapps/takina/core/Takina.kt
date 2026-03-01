package org.atoriapps.takina.core

import kotlinx.coroutines.runBlocking
import org.atoriapps.takina.core.bootstrap.FeatureTopologyValidator
import org.atoriapps.takina.core.connections.AccountState
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.connections.ConnectionStateMachine
import org.atoriapps.takina.core.connections.FinalReconnect
import org.atoriapps.takina.core.connections.ReconnectPolicy
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.connections.XmppTransport
import org.atoriapps.takina.core.connections.XmppTransportCallbacks
import org.atoriapps.takina.core.connections.XmppTransportFactoryRegistry
import org.atoriapps.takina.core.controlling.ControlPlane
import org.atoriapps.takina.core.error.ErrorDomain
import org.atoriapps.takina.core.error.TakinaErrors
import org.atoriapps.takina.core.events.AccountAddedEvent
import org.atoriapps.takina.core.events.AccountConfigChangedEvent
import org.atoriapps.takina.core.events.AccountRemovedEvent
import org.atoriapps.takina.core.events.ConfigAppliedEvent
import org.atoriapps.takina.core.events.ConfigApplyDeferredEvent
import org.atoriapps.takina.core.events.ConfigRejectedEvent
import org.atoriapps.takina.core.events.ConnectionStateChangedEvent
import org.atoriapps.takina.core.events.FinalFrameOutboundEvent
import org.atoriapps.takina.core.events.FeatureStateChangedEvent
import org.atoriapps.takina.core.events.FrameInboundParseFailedEvent
import org.atoriapps.takina.core.events.GlobalConfigChangedEvent
import org.atoriapps.takina.core.events.IqReceivedEvent
import org.atoriapps.takina.core.events.MessageReceivedEvent
import org.atoriapps.takina.core.events.MessageSendFailedEvent
import org.atoriapps.takina.core.events.MessageSentEvent
import org.atoriapps.takina.core.events.PresenceReceivedEvent
import org.atoriapps.takina.core.events.RawFrameInboundEvent
import org.atoriapps.takina.core.events.ReconnectExhaustedEvent
import org.atoriapps.takina.core.events.ReconnectScheduledEvent
import org.atoriapps.takina.core.events.RequestFailedEvent
import org.atoriapps.takina.core.events.SessionReadyEvent
import org.atoriapps.takina.core.events.TakinaEventBus
import org.atoriapps.takina.core.events.TakinaShutdownCompletedEvent
import org.atoriapps.takina.core.events.TakinaStartedEvent
import org.atoriapps.takina.core.events.UnexpectedDisconnectedEvent
import org.atoriapps.takina.core.events.UnknownFrameInboundEvent
import org.atoriapps.takina.core.feature.ApiProvidingFeature
import org.atoriapps.takina.core.feature.ConfigPreset
import org.atoriapps.takina.core.feature.FeatureApi
import org.atoriapps.takina.core.feature.FeatureApiKey
import org.atoriapps.takina.core.feature.FeatureKey
import org.atoriapps.takina.core.feature.FeaturePreset
import org.atoriapps.takina.core.feature.FeatureRegistry
import org.atoriapps.takina.core.feature.TakinaFeature
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.pipeline.InboundClassification
import org.atoriapps.takina.core.pipeline.InboundFrame
import org.atoriapps.takina.core.pipeline.OutboundClassification
import org.atoriapps.takina.core.pipeline.OutboundFrame
import org.atoriapps.takina.core.pipeline.PipelineRuntime
import org.atoriapps.takina.core.pipeline.classifyInbound
import org.atoriapps.takina.core.request.IqOutcome
import org.atoriapps.takina.core.request.IqRequest
import org.atoriapps.takina.core.request.MessageOutcome
import org.atoriapps.takina.core.request.MessageRequest
import org.atoriapps.takina.core.request.MessageRequestDsl
import org.atoriapps.takina.core.request.PresenceOutcome
import org.atoriapps.takina.core.request.PresenceRequest
import org.atoriapps.takina.core.request.RequestExecutor
import org.atoriapps.takina.core.request.TakinaRequestApi
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.runtime.TakinaRuntime
import org.atoriapps.takina.core.utils.ParsingUtils.asStringListOrNull
import org.atoriapps.takina.core.utils.ParsingUtils.toBareJidOrNull
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlWriter
import org.atoriapps.takina.core.xml.xml

fun createTakina(
    featurePreset: FeaturePreset = FeaturePreset.Recommended,
    configPreset: ConfigPreset = ConfigPreset.Default,
    init: TakinaConfiguration.() -> Unit,
): Takina {
    val configuration = TakinaConfiguration().apply(init)
    return CoreTakina(featurePreset, configPreset, configuration)
}

interface Takina {
    val events: TakinaEventBus
    val runtime: TakinaRuntime
    val request: TakinaRequestApi

    fun capability(init: CapabilityDsl.() -> Unit)
    fun config(init: ConfigDsl.() -> Unit)
    fun addAccount(init: AccountDsl.() -> Unit)
    fun removeAccount(jid: BareJid)
    fun removeAccount(accountHandle: AccountHandle)= removeAccount(accountHandle.owner)
    fun getAccountHandleFor(jid: BareJid): AccountHandle

    fun <API : FeatureApi> api(key: FeatureApiKey<API>): API
    fun <API : FeatureApi> apiOrNull(key: FeatureApiKey<API>): API?

    suspend fun connect(jid: BareJid)
    suspend fun connect(accountHandle: AccountHandle) = connect(accountHandle.owner)
    suspend fun disconnect(jid: BareJid)
    suspend fun disconnect(accountHandle: AccountHandle) = disconnect(accountHandle.owner)
    suspend fun connectAll()
    suspend fun disconnectAll()
    suspend fun shutdown()
}

class AccountHandle internal constructor(
    private val takina: CoreTakina,
    val owner: BareJid,
) {
    val request: TakinaRequestApi = TakinaRequestApi(takina, owner)
    val events: TakinaEventBus get() = takina.events

    fun capability(init: CapabilityDsl.() -> Unit) {
        CapabilityDsl { key, scope, enabled ->
            val resolvedScope = if (scope == Scope.Global) Scope.Account(owner) else scope
            takina.applyCapability(key, resolvedScope, enabled)
        }.apply(init)
    }

    fun config(init: ConfigDsl.() -> Unit) {
        ConfigDsl(Scope.Account(owner)) { path, value, scope ->
            takina.applyConfig(path, value, scope, owner)
        }.apply(init)
    }

    suspend fun connect() = takina.connect(owner)
    suspend fun disconnect() = takina.disconnect(owner)

    fun <API : FeatureApi> api(key: FeatureApiKey<API>): API = takina.api(key)
    fun <API : FeatureApi> apiOrNull(key: FeatureApiKey<API>): API? = takina.apiOrNull(key)

    fun getDirectChatHandleFor(peer: BareJid): DirectChatHandle = DirectChatHandle(this, peer)
    fun getMucHandleFor(room: BareJid): MucHandle = MucHandle(this, room)
}

class DirectChatHandle internal constructor(
    private val account: AccountHandle,
    private val peer: BareJid,
) {
    fun message(init: MessageRequestDsl.() -> Unit) = account.request.message {
        to = peer
        init()
    }

    // TODO：应再提供拉黑等方法，但能力由Feature提供，所以应该是扩展方法？
}

// HACK：我觉得这个类应该由Feature作为扩展提供，或者是Feature注入扩展方法。因为Muc是Feature提供的。MucHandle应提供如join、leave的便捷方法
class MucHandle internal constructor(
    private val account: AccountHandle,
    private val room: BareJid,
) {
    fun message(init: MessageRequestDsl.() -> Unit) = account.request.message {
        to = room
        init()
    }
}

internal class CoreTakina(
    private val featurePreset: FeaturePreset,
    private val configPreset: ConfigPreset,
    private val bootstrapConfiguration: TakinaConfiguration,
) : Takina, RequestExecutor {
    override val events: TakinaEventBus = TakinaEventBus()

    private val installedFeatures: List<TakinaFeature> = resolvePresetFeatures(featurePreset) + bootstrapConfiguration.features
    private val featureRegistry: FeatureRegistry
    private val controlPlane: ControlPlane
    private val pipelineRuntime: PipelineRuntime
    override val runtime: TakinaRuntime
    override val request: TakinaRequestApi
    private val featureApisByKey = mutableMapOf<FeatureKey, FeatureApi>()

    private val accounts = linkedMapOf<BareJid, AccountDefinition>()
    private val stateMachines = linkedMapOf<BareJid, ConnectionStateMachine>()
    private val transports = linkedMapOf<BareJid, XmppTransport>()
    private val intentionalDisconnectOwners = mutableSetOf<BareJid>()
    private var started = false
    private var shutdown = false

    init {
        FeatureTopologyValidator.validateOrThrow(installedFeatures)

        featureRegistry = FeatureRegistry(installedFeatures)
        controlPlane = ControlPlane(featureRegistry)
        pipelineRuntime = PipelineRuntime(controlPlane)
        runtime = TakinaRuntime(controlPlane, pipelineRuntime)
        request = TakinaRequestApi(this)

        installedFeatures.forEach { feature ->
            feature.inboundNodes().forEach { pipelineRuntime.registerInboundNode(it, feature.key) }
            feature.outboundNodes().forEach { pipelineRuntime.registerOutboundNode(it, feature.key) }
            if (feature is ApiProvidingFeature<*>) featureApisByKey[feature.apiKey.featureKey] = feature.api()
        }

        applyConfigPreset(configPreset)
        bootstrapConfiguration.configDrafts.forEach { applyConfig(it.path, it.value, it.scope, null) }
        bootstrapConfiguration.capabilityDrafts.forEach { applyCapability(it.featureKey, it.scope, it.enabled) }
        bootstrapConfiguration.nodePolicyDrafts.forEach {
            it.enabled?.let { enabled -> controlPlane.setNodeEnabled(it.nodeKey, it.scope, enabled) }
            it.order?.let { order -> controlPlane.setNodeOrder(it.nodeKey, it.scope, order) }
        }
        bootstrapConfiguration.accounts.values.forEach { installAccount(it, emitEvent = true) }

        runBlocking { installedFeatures.forEach { feature -> feature.onInstall(this@CoreTakina) } }

        started = true
        events.emit(TakinaStartedEvent())
    }

    override fun capability(init: CapabilityDsl.() -> Unit) {
        ensureStarted()
        CapabilityDsl { key, scope, enabled -> applyCapability(key, scope, enabled) }.apply(init)
    }

    override fun config(init: ConfigDsl.() -> Unit) {
        ensureStarted()
        ConfigDsl(Scope.Global) { path, value, scope -> applyConfig(path, value, scope, null) }.apply(init)
    }

    override fun addAccount(init: AccountDsl.() -> Unit) {
        ensureStarted()
        installAccount(AccountDsl().apply(init).build(), emitEvent = true)
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

    override fun getAccountHandleFor(jid: BareJid): AccountHandle {
        ensureStarted()
        require(accounts.containsKey(jid)) { "Account not found: $jid" }
        return AccountHandle(this, jid)
    }

    override fun <API : FeatureApi> api(key: FeatureApiKey<API>): API = requireNotNull(apiOrNull(key)) { "Feature API not available: ${key.featureKey}" }

    override fun <API : FeatureApi> apiOrNull(key: FeatureApiKey<API>): API? {
        val raw = featureApisByKey[key.featureKey] ?: return null
        if (!key.apiType.isInstance(raw)) return null
        @Suppress("UNCHECKED_CAST")
        return raw as API
    }

    override suspend fun connect(jid: BareJid) {
        ensureStarted()
        val account = requireNotNull(accounts[jid]) { "Account not found: $jid" }
        val machine = requireNotNull(stateMachines[jid]) { "State machine not found: $jid" }
        val accountScope = Scope.Account(jid)

        if (machine.currentState() == ConnectionState.ESTABLISHED) {
            runtime.setAccountState(jid, AccountState.ONLINE)
            return
        }
        if (machine.currentState() == ConnectionState.CLOSED) {
            transition(jid, machine, ConnectionState.IDLE)
        }

        runtime.setAccountState(jid, AccountState.CONNECTING)
        controlPlane.onNextConnectionBoundary()

        val transport = XmppTransportFactoryRegistry.factory(
            account.getConnectionConfig(accountScope),
            transportCallbacksFor(jid),
        )
        transports.remove(jid)?.let { runCatching { it.disconnect() } }
        transports[jid] = transport

        runCatching { transport.connect(account.passwordProvider()) }
            .onFailure {
                runtime.setAccountState(jid, AccountState.DEGRADED)
                events.emit(RequestFailedEvent(jid, "connect", it.message ?: "connect failed"))
                throw it
            }

        runtime.setAccountState(jid, AccountState.ONLINE)
        events.emit(SessionReadyEvent(jid))
    }

    override suspend fun disconnect(jid: BareJid) {
        ensureStarted()
        val machine = requireNotNull(stateMachines[jid]) { "Account not found: $jid" }
        intentionalDisconnectOwners += jid
        runCatching { transports.remove(jid)?.disconnect() }
        if (machine.currentState() != ConnectionState.CLOSED) {
            transition(jid, machine, ConnectionState.CLOSED)
        }
        runtime.setAccountState(jid, AccountState.OFFLINE)
        intentionalDisconnectOwners -= jid
    }

    override suspend fun connectAll() {
        ensureStarted()
        accounts.keys.forEach { connect(it) }
    }

    override suspend fun disconnectAll() {
        ensureStarted()
        accounts.keys.forEach { disconnect(it) }
    }

    override suspend fun shutdown() {
        if (shutdown) return
        disconnectAll()
        installedFeatures.forEach { feature -> feature.onShutdown(this@CoreTakina) }
        transports.clear()
        shutdown = true
        events.emit(TakinaShutdownCompletedEvent())
    }

    override suspend fun sendMessage(request: MessageRequest): TakinaResult<MessageOutcome> {
        ensureStarted()
        controlPlane.onNextItemBoundary()
        val owner = resolveOwner(request.from)
        val transport = requireConnectedTransport(owner)
        val scope = Scope.Message(owner, request.to, request.messageId)
        val raw = XmlWriter.render(xml("message") {
            attr("id", request.messageId)
            attr("to", request.to.toString())
            attr("from", transport.boundJid)
            element("body") { text(request.body) }
        })
        val processed = pipelineRuntime.executeOutbound(OutboundFrame(raw, OutboundClassification.BUSINESS, owner), scope)
        return if (processed == null) {
            events.emit(MessageSendFailedEvent(owner, request.to, "Dropped by outbound node"))
            TakinaResult.Err(TakinaErrors.of(ErrorDomain.PIPELINE, 201, "Outbound message dropped", retryable = false))
        } else runCatching {
            events.emit(FinalFrameOutboundEvent(owner = owner, xml = processed, classification = OutboundClassification.BUSINESS, source = "message"))
            transport.sendRaw(processed)
            events.emit(MessageSentEvent(owner = owner, to = request.to, body = request.body))
            TakinaResult.Ok(MessageOutcome(request.messageId))
        }.getOrElse {
            events.emit(MessageSendFailedEvent(owner, request.to, it.message ?: "send failed"))
            TakinaResult.Err(TakinaErrors.of(ErrorDomain.TRANSPORT, 101, it.message ?: "send failed", retryable = true, cause = it))
        }
    }

    override suspend fun sendPresence(request: PresenceRequest): TakinaResult<PresenceOutcome> {
        ensureStarted()
        controlPlane.onNextItemBoundary()
        val owner = resolveOwner(request.from)
        val transport = requireConnectedTransport(owner)
        val raw = XmlWriter.render(xml("presence") {
            attr("to", request.to?.toString())
            attr("from", transport.boundJid)
            request.show?.let { element("show") { text(it) } }
            request.status?.let { element("status") { text(it) } }
        })
        val scope = Scope.Account(owner)
        val processed = pipelineRuntime.executeOutbound(OutboundFrame(raw, OutboundClassification.BUSINESS, owner), scope)
            ?: return TakinaResult.Err(TakinaErrors.of(ErrorDomain.PIPELINE, 202, "Outbound presence dropped", retryable = false))
        return runCatching {
            events.emit(FinalFrameOutboundEvent(owner = owner, xml = processed, classification = OutboundClassification.BUSINESS, source = "presence"))
            transport.sendRaw(processed)
            TakinaResult.Ok(PresenceOutcome())
        }.getOrElse {
            events.emit(RequestFailedEvent(owner, "presence", it.message ?: "presence send failed"))
            TakinaResult.Err(TakinaErrors.of(ErrorDomain.TRANSPORT, 102, it.message ?: "presence send failed", retryable = true, cause = it))
        }
    }

    override suspend fun sendIq(request: IqRequest): TakinaResult<IqOutcome> {
        ensureStarted()
        controlPlane.onNextItemBoundary()
        val owner = resolveOwner(request.from)
        val transport = requireConnectedTransport(owner)
        val payloadElement = request.payload.trim().takeIf { it.isNotEmpty() }?.let { XmlParser.parseElementOrNull(it) }
        if (request.payload.isNotBlank() && payloadElement == null) {
            return TakinaResult.Err(TakinaErrors.of(ErrorDomain.CONFIG, 302, "IQ payload must be valid XML element", retryable = false))
        }
        val raw = XmlWriter.render(xml("iq") {
            attr("id", request.id)
            attr("type", request.type)
            attr("to", request.to?.toString())
            attr("from", transport.boundJid)
            if (payloadElement != null) {
                node(payloadElement)
            }
        })
        val scope = Scope.Account(owner)
        val processed = pipelineRuntime.executeOutbound(OutboundFrame(raw, OutboundClassification.BUSINESS, owner), scope)
            ?: return TakinaResult.Err(TakinaErrors.of(ErrorDomain.PIPELINE, 203, "Outbound iq dropped", retryable = false))
        return runCatching {
            events.emit(FinalFrameOutboundEvent(owner = owner, xml = processed, classification = OutboundClassification.BUSINESS, source = "iq"))
            transport.sendRaw(processed)
            TakinaResult.Ok(IqOutcome(request.id))
        }.getOrElse {
            events.emit(RequestFailedEvent(owner, "iq", it.message ?: "iq send failed"))
            TakinaResult.Err(TakinaErrors.of(ErrorDomain.TRANSPORT, 103, it.message ?: "iq send failed", retryable = true, cause = it))
        }
    }

    internal fun applyCapability(featureKey: FeatureKey, scope: Scope, enabled: Boolean) {
        controlPlane.setFeatureEnabled(featureKey, scope, enabled)
        events.emit(FeatureStateChangedEvent(feature = featureKey.value, enabled = enabled))
    }

    internal fun applyConfig(path: String, value: Any?, scope: Scope, accountOwner: BareJid?) {
        val result = controlPlane.applyConfig(path, value, scope)
        when {
            result.rejectedReason != null -> events.emit(ConfigRejectedEvent(path, result.rejectedReason))
            result.applied -> events.emit(ConfigAppliedEvent(path, result.applyMode.name))
            else -> events.emit(ConfigApplyDeferredEvent(path, result.applyMode.name))
        }

        when (scope) {
            Scope.Global, Scope.Preset -> events.emit(GlobalConfigChangedEvent(path))
            is Scope.Account -> events.emit(AccountConfigChangedEvent(scope.owner, path))
            is Scope.Conversation -> events.emit(AccountConfigChangedEvent(scope.owner, path))
            is Scope.Message -> events.emit(AccountConfigChangedEvent(scope.owner, path))
        }
        if (
            accountOwner != null &&
            scope !is Scope.Account &&
            scope !is Scope.Conversation &&
            scope !is Scope.Message
        ) {
            events.emit(AccountConfigChangedEvent(accountOwner, path))
        }
    }

    suspend fun onUnexpectedDisconnect(owner: BareJid, reason: String?, authHardFailure: Boolean) {
        val machine = stateMachines[owner] ?: return
        runtime.setAccountState(owner, AccountState.DEGRADED)
        events.emit(UnexpectedDisconnectedEvent(owner, reason))

        val reconnect = FinalReconnect(
            onSchedule = { attempt, delay ->
                runtime.setAccountState(owner, AccountState.RECONNECTING)
                if (machine.currentState() != ConnectionState.RECONNECT_WAIT) {
                    transition(owner, machine, ConnectionState.RECONNECT_WAIT)
                }
                events.emit(ReconnectScheduledEvent(owner, attempt, delay))
            },
            connectAttempt = {
                runCatching { connect(owner) }.isSuccess
            },
        )

        val policy = ReconnectPolicy(
            enabled = controlPlane.currentConfig("reconnect.enabled", Scope.Account(owner)) as? Boolean ?: true,
            delayMillis = controlPlane.currentConfig("reconnect.delay", Scope.Account(owner)) as? Long ?: 1_000L,
            factor = controlPlane.currentConfig("reconnect.factor", Scope.Account(owner)) as? Double ?: 2.0,
            jitter = controlPlane.currentConfig("reconnect.jitter", Scope.Account(owner)) as? Double ?: 0.0,
            maxAttempts = controlPlane.currentConfig("reconnect.maxAttempts", Scope.Account(owner)) as? Int ?: 5,
        )

        val outcome = reconnect.perform(owner, policy, authHardFailure = authHardFailure)
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
        controlPlane.onNextItemBoundary()
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

            InboundClassification.UNKNOWN -> {
                events.emit(UnknownFrameInboundEvent(owner = owner, raw = processed))
            }

            else -> Unit
        }
    }

    private fun transition(owner: BareJid, machine: ConnectionStateMachine, next: ConnectionState) {
        if (machine.currentState() == next) return
        val result = machine.transitionTo(next)
        if (!result.accepted) {
            events.emit(RequestFailedEvent(owner, "state-transition", "Illegal transition ${result.from} -> $next"))
            return
        }
        runtime.setConnectionState(owner, next)
        runBlocking {
            installedFeatures.forEach { feature ->
                feature.lifecycleHooks().forEach { hook ->
                    hook.onConnectionStateChanged(owner, result.from, result.to)
                }
            }
        }
        events.emit(ConnectionStateChangedEvent(owner, result.from, result.to))
    }

    private fun installAccount(account: AccountDefinition, emitEvent: Boolean) {
        require(accounts.putIfAbsent(account.jid, account) == null) { "Duplicate account: ${account.jid}" }
        stateMachines[account.jid] = ConnectionStateMachine(ConnectionState.IDLE)
        runtime.setAccountState(account.jid, AccountState.REGISTERED)
        runtime.setConnectionState(account.jid, ConnectionState.IDLE)
        if (emitEvent) events.emit(AccountAddedEvent(account.jid))
    }

    private fun requireConnectedTransport(owner: BareJid): XmppTransport {
        val transport = transports[owner]
        if (transport == null || !transport.isConnected) {
            throw IllegalStateException("Account $owner is not connected")
        }
        return transport
    }

    private fun resolveOwner(requested: BareJid?): BareJid {
        if (requested != null) return requested
        if (accounts.size == 1) return accounts.keys.first()
        val error = TakinaErrors.of(ErrorDomain.CONFIG, 301, "Ambiguous account owner for request", retryable = false)
        events.emit(RequestFailedEvent(null, "request-routing", error.message))
        throw IllegalStateException(error.message)
    }

    private fun applyConfigPreset(configPreset: ConfigPreset) {
        when (configPreset) {
            ConfigPreset.Default -> {
                controlPlane.applyConfig("reconnect.enabled", true, Scope.Preset)
                controlPlane.applyConfig("reconnect.delay", 1_000L, Scope.Preset)
                controlPlane.applyConfig("reconnect.factor", 2.0, Scope.Preset)
                controlPlane.applyConfig("reconnect.maxAttempts", 5, Scope.Preset)
            }
        }
    }

    private fun resolvePresetFeatures(featurePreset: FeaturePreset): List<TakinaFeature> = when (featurePreset) {
        FeaturePreset.Minimal -> emptyList()
        FeaturePreset.Recommended -> emptyList()
        FeaturePreset.Full -> emptyList()
    }

    private fun ensureStarted() {
        check(started && !shutdown) { "Takina is not active" }
    }

    private fun AccountDefinition.getConnectionConfig(scope: Scope): ConnectionConfig {
        val hostOverride = controlPlane.currentConfig("connection.host", scope) as? String
        val portOverride = (controlPlane.currentConfig("connection.port", scope) as? Number)?.toInt()
        val securityOverride = controlPlane.currentConfig("securityMode", scope) as? SecurityMode
        val resourceOverride = controlPlane.currentConfig("resource", scope)?.toString()
        val saslOverride = controlPlane.currentConfig("auth.sasl", scope).asStringListOrNull()
        val timeoutOverride = (controlPlane.currentConfig("timeout.handshake", scope) as? Number)?.toInt()

        val mode = securityOverride ?: securityMode ?: SecurityMode.START_TLS

        return ConnectionConfig(
            owner = jid,
            host = hostOverride ?: connectionHost ?: jid.domain,
            port = portOverride ?: connectionPort ?: mode.defaultPort,
            securityMode = mode,
            resource = resourceOverride ?: resource,
            saslMechanisms = saslOverride ?: saslMechanisms,
            connectTimeoutMillis = timeoutOverride ?: connectTimeoutMillis,
            trustAllCertificates = trustAllCertificates,
        )
    }

}
