package org.atoriapps.takina.features.streammanagement

import org.atoriapps.takina.core.CoreTakina
import org.atoriapps.takina.core.Takina
import org.atoriapps.takina.core.connections.ConnectionDefaults
import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.features.ConnectionLifecycleHook
import org.atoriapps.takina.core.features.ControlInboundNode
import org.atoriapps.takina.core.features.InboundFrameClaimer
import org.atoriapps.takina.core.features.InboundStanzaObserver
import org.atoriapps.takina.core.features.OutboundBusinessObserver
import org.atoriapps.takina.core.features.PreBindNegotiationContext
import org.atoriapps.takina.core.features.PreBindNegotiationDecision
import org.atoriapps.takina.core.features.PreBindNegotiationHook
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.features.UnexpectedDisconnectContext
import org.atoriapps.takina.core.features.UnexpectedDisconnectHandling
import org.atoriapps.takina.core.features.UnexpectedDisconnectHook
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.pipeline.InboundClassification
import org.atoriapps.takina.core.pipeline.InboundFrame
import org.atoriapps.takina.core.pipeline.NodeResult
import org.atoriapps.takina.core.xml.XmlParser
import kotlin.math.min
import kotlin.time.Clock

// FIXME：当把SM安装到实例，在没建立连接时就尝试Resume了，sb
// 另外：又收不到消息了，怎么回事？？
class StreamManagementFeature : TakinaFeature {
    companion object : TakinaFeatureProvider<StreamManagementFeature> {
        override val id: String = "stream-management"
        override val featureType = StreamManagementFeature::class
        override fun create(): StreamManagementFeature = StreamManagementFeature()

        const val NAMESPACE: String = "urn:xmpp:sm:3"
        private const val RESUME_READ_LIMIT = 20
        private const val SOURCE_ENABLE = "STREAM_MANAGEMENT_ENABLE"
        private const val SOURCE_ACK = "STREAM_MANAGEMENT_ACK"
        private const val SOURCE_ACK_REQUEST = "STREAM_MANAGEMENT_ACK_REQUEST"
        private const val SOURCE_REPLAY = "STREAM_MANAGEMENT_REPLAY"
    }

    var autoAckRequestInterval: Int = 10
    var persistStateToStore: Boolean = false
    var restorePersistedStateOnStartup: Boolean = false
    var stateStore: StreamManagementStateStore? = null
    var nowMillisProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    private val statesByOwner = linkedMapOf<BareJid, SessionState>()
    private var core: CoreTakina? = null

    override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL, ScopeKind.ACCOUNT)
    override val applyMode: ApplyMode = ApplyMode.NEXT_CONNECTION

    override suspend fun onInstall(context: Takina) {
        core = context as? CoreTakina
    }

    override suspend fun onShutdown(context: Takina) {
        core = null
    }

    override fun preBindHooks(): List<PreBindNegotiationHook> = listOf(object : PreBindNegotiationHook {
        override suspend fun onPreBind(context: PreBindNegotiationContext): PreBindNegotiationDecision =
            runPreBindNegotiation(context)
    })

    override fun unexpectedDisconnectHooks(): List<UnexpectedDisconnectHook> = listOf(object : UnexpectedDisconnectHook {
        override suspend fun onUnexpectedDisconnect(context: UnexpectedDisconnectContext): UnexpectedDisconnectHandling =
            runUnexpectedDisconnectRecovery(context)
    })

    override fun inboundClaimers(): List<InboundFrameClaimer> = listOf(object : InboundFrameClaimer {
        override val key: String = "sm-control-claimer"

        override fun claim(raw: String): InboundClassification? {
            val element = XmlParser.parseStartTagOrNull(raw) ?: return null
            if (element.localName !in setOf("enabled", "resumed", "failed", "a", "r")) return null
            return if (isSmNamespace(element.name, element.attributes)) {
                InboundClassification.CONTROL
            } else {
                null
            }
        }
    })

    override fun inboundNodes(): List<org.atoriapps.takina.core.pipeline.InboundNode> = listOf(object : ControlInboundNode {
        override val key: String = "sm-control-inbound"

        override suspend fun execute(frame: InboundFrame): NodeResult {
            val owner = frame.owner ?: return NodeResult.Continue(frame.raw)
            val parsed = parseInboundFrame(frame.raw) ?: return NodeResult.Continue(frame.raw)
            val state = stateFor(owner)
            when (parsed) {
                is SmInboundFrame.Enabled -> {
                    applyEnabledFrame(state, parsed)
                    replayPendingAfterEnable(owner, state)
                }

                is SmInboundFrame.Resumed -> {
                    if (!isResumedConsistent(state, parsed.previousId)) {
                        core?.events?.emit(
                            StreamManagementResumeFailedEvent(
                                owner = owner,
                                reason = "resumed-previd-mismatch",
                            ),
                        )
                        invalidateSessionForEnable(state)
                        sendEnable(owner, state)
                    } else {
                        applyResumedFrame(owner, state, parsed)
                        replayUnackedAfterResume(owner, state)
                    }
                }

                is SmInboundFrame.Acknowledged -> onServerAcknowledged(owner, state, parsed.handledByServer)

                SmInboundFrame.AckRequest -> {
                    core?.sendFeatureControlFrame(owner, ack(state.inboundHandledCount), SOURCE_ACK)
                }

                is SmInboundFrame.Failed -> {
                    applyFailedFrame(state, parsed.handledByServer)
                    core?.events?.emit(StreamManagementResumeFailedEvent(owner, "sm-failed"))
                    sendEnable(owner, state)
                }
            }
            return NodeResult.Drop
        }
    })

    override fun lifecycleHooks(): List<ConnectionLifecycleHook> = listOf(object : ConnectionLifecycleHook {
        override suspend fun onConnectionStateChanged(owner: BareJid, from: ConnectionState, to: ConnectionState) {
            if (to != ConnectionState.ESTABLISHED) return
            val core = core ?: return
            val state = stateFor(owner)
            synchronized(state) {
                state.lastBoundJid = core.sessionBoundJidOrNull(owner) ?: state.lastBoundJid
            }
            if (!state.serverSupportsSm) return
            if (synchronized(state) { state.enabled || state.enableRequested || state.resumed }) return
            sendEnable(owner, state)
        }
    })

    override fun outboundBusinessObservers(): List<OutboundBusinessObserver> = listOf(object : OutboundBusinessObserver {
        override suspend fun onBusinessFrameSent(owner: BareJid, xml: String) {
            val state = stateFor(owner)
            if (synchronized(state) { !state.enabled }) return

            synchronized(state) {
                state.outboundSentCount += 1
                state.outboundSinceAckRequest += 1
                state.pendingOutbound += PendingOutbound(state.outboundSentCount, xml)
            }
            persistStateIfNeeded(owner, state)

            if (shouldSendAckRequest(state)) {
                val sent = core?.sendFeatureControlFrame(owner, ackRequest(), SOURCE_ACK_REQUEST) == true
                synchronized(state) {
                    state.awaitingServerAckReply = sent
                    if (sent) state.outboundSinceAckRequest = 0
                }
            }
        }
    })

    override fun inboundStanzaObservers(): List<InboundStanzaObserver> = listOf(object : InboundStanzaObserver {
        override suspend fun onInboundStanzaHandled(owner: BareJid, classification: InboundClassification, xml: String) {
            val state = stateFor(owner)
            if (synchronized(state) { !state.enabled }) return
            synchronized(state) { state.inboundHandledCount += 1 }
            persistStateIfNeeded(owner, state)
        }
    })

    private suspend fun runPreBindNegotiation(context: PreBindNegotiationContext): PreBindNegotiationDecision {
        val owner = context.owner
        val state = stateFor(owner)
        val supportsSm = containsSmNamespace(context.featuresXml)
        synchronized(state) { state.serverSupportsSm = supportsSm }
        if (!supportsSm) return PreBindNegotiationDecision.ContinueToBind

        val resumable = synchronized(state) { state.enabled && state.allowResume && !state.sessionId.isNullOrBlank() && !isResumeExpired(state) }
        if (!resumable) return PreBindNegotiationDecision.ContinueToBind

        val previousId = synchronized(state) { state.sessionId }
        if (previousId.isNullOrBlank()) return PreBindNegotiationDecision.ContinueToBind
        val handledByClient = synchronized(state) { state.inboundHandledCount }

        context.transport.sendRawFrame(resume(previousId, handledByClient))
        synchronized(state) {
            state.resumeRequested = true
            state.lastResumePreviousId = previousId
        }

        repeat(RESUME_READ_LIMIT) {
            val frameXml = context.transport.readFrame() ?: return@repeat
            val frame = parseInboundFrame(frameXml) ?: return@repeat
            when (frame) {
                is SmInboundFrame.Resumed -> {
                    if (!isResumedConsistent(state, frame.previousId)) {
                        invalidateSessionForEnable(state)
                        core?.events?.emit(StreamManagementResumeFailedEvent(owner, "resumed-previd-mismatch"))
                        return PreBindNegotiationDecision.ContinueToBind
                    }
                    applyResumedFrame(owner, state, frame)
                    snapshotUnacked(state).forEach { outbound ->
                        context.transport.sendRawFrame(outbound)
                    }
                    val bound = synchronized(state) { state.lastBoundJid } ?: "${owner}/${ConnectionDefaults.RESOURCE}"
                    return PreBindNegotiationDecision.ResumeSucceeded(bound)
                }

                is SmInboundFrame.Failed -> {
                    applyFailedFrame(state, frame.handledByServer)
                    core?.events?.emit(StreamManagementResumeFailedEvent(owner, "resume-failed"))
                    return PreBindNegotiationDecision.ContinueToBind
                }

                SmInboundFrame.AckRequest -> {
                    context.transport.sendRawFrame(ack(synchronized(state) { state.inboundHandledCount }))
                }

                is SmInboundFrame.Acknowledged -> onServerAcknowledged(owner, state, frame.handledByServer)
                is SmInboundFrame.Enabled -> applyEnabledFrame(state, frame)
            }
        }

        synchronized(state) { state.resumeRequested = false }
        core?.events?.emit(StreamManagementResumeFailedEvent(owner, "resume-timeout"))
        return PreBindNegotiationDecision.ContinueToBind
    }

    private suspend fun runUnexpectedDisconnectRecovery(context: UnexpectedDisconnectContext): UnexpectedDisconnectHandling {
        if (context.authHardFailure) return UnexpectedDisconnectHandling.NOT_HANDLED
        val owner = context.owner
        val state = stateFor(owner)
        val canResume = synchronized(state) { state.enabled && state.allowResume && !state.sessionId.isNullOrBlank() }
        if (!canResume) return UnexpectedDisconnectHandling.NOT_HANDLED
        val core = core ?: return UnexpectedDisconnectHandling.NOT_HANDLED
        return if (core.connect(owner) is TakinaResult.Ok) {
            UnexpectedDisconnectHandling.HANDLED
        } else {
            core.events.emit(StreamManagementResumeFailedEvent(owner, "reconnect-failed"))
            UnexpectedDisconnectHandling.NOT_HANDLED
        }
    }

    private fun shouldSendAckRequest(state: SessionState): Boolean = synchronized(state) {
        if (!state.enabled) return@synchronized false
        if (autoAckRequestInterval <= 0) return@synchronized false
        if (state.awaitingServerAckReply) return@synchronized false
        state.outboundSinceAckRequest >= autoAckRequestInterval
    }

    private fun onServerAcknowledged(owner: BareJid, state: SessionState, handledByServer: Long) {
        synchronized(state) {
            if (handledByServer > state.outboundSentCount) {
                core?.events?.emit(
                    StreamManagementGapDetectedEvent(
                        owner = owner,
                        expectedAck = state.outboundSentCount,
                        actualAck = handledByServer,
                    ),
                )
            }
            val normalized = min(handledByServer, state.outboundSentCount)
            if (normalized < state.lastServerAckCount) return@synchronized
            state.lastServerAckCount = normalized
            state.pendingOutbound.removeAll { it.sequence <= normalized }
            state.awaitingServerAckReply = false
            state.outboundSinceAckRequest = 0
        }
        persistStateIfNeeded(owner, state)
    }

    private suspend fun replayUnackedAfterResume(owner: BareJid, state: SessionState) {
        val core = core ?: return
        val replay = snapshotUnacked(state)
        replay.forEach { xml ->
            core.sendFeatureBusinessReplayFrame(owner, xml, SOURCE_REPLAY)
        }
    }

    private suspend fun replayPendingAfterEnable(owner: BareJid, state: SessionState) {
        val core = core ?: return
        val replay = consumePendingReplayAfterEnable(state)
        replay.forEach { xml ->
            core.sendFeatureBusinessReplayFrame(owner, xml, SOURCE_REPLAY)
        }
    }

    private suspend fun sendEnable(owner: BareJid, state: SessionState) {
        synchronized(state) {
            state.enableRequested = true
            state.resumeRequested = false
            state.lastResumePreviousId = null
        }
        val sent = core?.sendFeatureControlFrame(owner, enable(), SOURCE_ENABLE) == true
        if (!sent) {
            synchronized(state) { state.enableRequested = false }
        }
    }

    private fun applyEnabledFrame(state: SessionState, frame: SmInboundFrame.Enabled) {
        synchronized(state) {
            if (state.resumeRequested && state.pendingOutbound.isNotEmpty()) {
                state.pendingReplayAfterEnable.clear()
                state.pendingReplayAfterEnable.addAll(state.pendingOutbound.map { it.xml })
            }
            state.enabled = true
            state.resumed = false
            state.sessionId = frame.id
            state.allowResume = frame.allowResume
            state.maxResumeSeconds = frame.maxResumeSeconds
            state.outboundSentCount = 0
            state.lastServerAckCount = 0
            state.inboundHandledCount = 0
            state.awaitingServerAckReply = false
            state.outboundSinceAckRequest = 0
            state.pendingOutbound.clear()
            state.enableRequested = false
            state.resumeRequested = false
            state.lastResumePreviousId = null
            state.sessionStartedAtEpochMillis = nowMillisProvider()
        }
    }

    private fun applyResumedFrame(owner: BareJid, state: SessionState, frame: SmInboundFrame.Resumed) {
        synchronized(state) {
            state.enabled = true
            state.resumed = true
            state.resumeRequested = false
            state.enableRequested = false
            state.lastResumePreviousId = null
            if (state.sessionStartedAtEpochMillis <= 0L) {
                state.sessionStartedAtEpochMillis = nowMillisProvider()
            }
        }
        onServerAcknowledged(owner, state, frame.handledByServer)
        core?.events?.emit(StreamManagementResumedEvent(owner = owner, handledByServer = frame.handledByServer))
    }

    private fun applyFailedFrame(state: SessionState, handledByServer: Long?) {
        synchronized(state) {
            if (handledByServer != null) {
                val normalized = min(handledByServer, state.outboundSentCount)
                state.lastServerAckCount = maxOf(state.lastServerAckCount, normalized)
                state.pendingOutbound.removeAll { it.sequence <= normalized }
            }
            state.pendingReplayAfterEnable.clear()
            state.pendingReplayAfterEnable.addAll(state.pendingOutbound.map { it.xml })
            state.pendingOutbound.clear()
            state.outboundSentCount = 0
            state.lastServerAckCount = 0
            state.inboundHandledCount = 0
            state.awaitingServerAckReply = false
            state.outboundSinceAckRequest = 0
            invalidateSessionForEnable(state)
        }
    }

    private fun invalidateSessionForEnable(state: SessionState) {
        synchronized(state) {
            state.enabled = false
            state.resumed = false
            state.sessionId = null
            state.allowResume = false
            state.maxResumeSeconds = null
            state.enableRequested = false
            state.resumeRequested = false
            state.lastResumePreviousId = null
            state.sessionStartedAtEpochMillis = 0L
        }
    }

    private fun isResumedConsistent(state: SessionState, actualPreviousId: String?): Boolean = synchronized(state) {
        val expected = state.lastResumePreviousId
        if (expected.isNullOrBlank()) true else expected == actualPreviousId
    }

    private fun isResumeExpired(state: SessionState): Boolean = synchronized(state) {
        val max = state.maxResumeSeconds ?: return@synchronized false
        if (max <= 0 || state.sessionStartedAtEpochMillis <= 0L) return@synchronized false
        nowMillisProvider() - state.sessionStartedAtEpochMillis > max.toLong() * 1_000L
    }

    private fun snapshotUnacked(state: SessionState): List<String> = synchronized(state) {
        state.pendingOutbound.map { it.xml }
    }

    private fun consumePendingReplayAfterEnable(state: SessionState): List<String> = synchronized(state) {
        if (state.pendingReplayAfterEnable.isEmpty()) return@synchronized emptyList()
        val snapshot = state.pendingReplayAfterEnable.toList()
        state.pendingReplayAfterEnable.clear()
        snapshot
    }

    @Synchronized
    private fun stateFor(owner: BareJid): SessionState {
        statesByOwner[owner]?.let { return it }
        val state = SessionState()
        statesByOwner[owner] = state
        restoreIfNeeded(owner, state)
        return state
    }

    private fun restoreIfNeeded(owner: BareJid, state: SessionState) {
        if (!restorePersistedStateOnStartup) return
        val persisted = stateStore?.load(owner) ?: return
        if (persisted.sessionId.isBlank() || !persisted.allowResume) return
        if (persisted.maxResumeSeconds != null && persisted.maxResumeSeconds > 0) {
            val deadline = persisted.persistedAtEpochMillis + persisted.maxResumeSeconds.toLong() * 1_000L
            if (nowMillisProvider() > deadline) {
                stateStore?.clear(owner)
                return
            }
        }
        synchronized(state) {
            state.enabled = true
            state.resumed = false
            state.serverSupportsSm = true
            state.sessionId = persisted.sessionId
            state.allowResume = persisted.allowResume
            state.maxResumeSeconds = persisted.maxResumeSeconds
            state.outboundSentCount = persisted.outboundSentCount
            state.lastServerAckCount = persisted.lastServerAckCount
            state.lastBoundJid = persisted.boundJid
            state.sessionStartedAtEpochMillis = persisted.persistedAtEpochMillis
            state.pendingOutbound.clear()
            var sequence = state.lastServerAckCount
            persisted.pendingOutbound.forEach { xml ->
                sequence += 1
                state.pendingOutbound += PendingOutbound(sequence, xml)
            }
            state.outboundSentCount = maxOf(state.outboundSentCount, sequence)
        }
    }

    private fun persistStateIfNeeded(owner: BareJid, state: SessionState) {
        if (!persistStateToStore) return
        val store = stateStore ?: return
        val snapshot = synchronized(state) {
            if (!state.enabled || !state.allowResume || state.sessionId.isNullOrBlank()) return@synchronized null
            PersistedSessionState(
                sessionId = requireNotNull(state.sessionId),
                allowResume = state.allowResume,
                maxResumeSeconds = state.maxResumeSeconds,
                outboundSentCount = state.outboundSentCount,
                lastServerAckCount = state.lastServerAckCount,
                pendingOutbound = state.pendingOutbound.map { it.xml },
                boundJid = state.lastBoundJid,
                persistedAtEpochMillis = state.sessionStartedAtEpochMillis,
            )
        }
        if (snapshot == null) store.clear(owner) else store.save(owner, snapshot)
    }

    private fun containsSmNamespace(featuresXml: String): Boolean {
        val root = XmlParser.parseElementOrNull(featuresXml) ?: return false
        return root.childElements().any { child ->
            child.localName == "sm" && isSmNamespace(child.name, child.attributes)
        }
    }

    private fun parseInboundFrame(xml: String): SmInboundFrame? {
        val element = XmlParser.parseElementOrNull(xml) ?: return null
        if (!isSmNamespace(element.name, element.attributes)) return null
        return when (element.localName) {
            "enabled" -> SmInboundFrame.Enabled(
                id = element.attribute("id"),
                allowResume = element.attribute("resume").toBooleanLike(),
                maxResumeSeconds = element.attribute("max")?.toIntOrNull(),
            )

            "resumed" -> SmInboundFrame.Resumed(
                previousId = element.attribute("previd"),
                handledByServer = element.attribute("h")?.toLongOrNull() ?: 0L,
            )

            "a" -> element.attribute("h")?.toLongOrNull()?.let { SmInboundFrame.Acknowledged(it) }
            "r" -> SmInboundFrame.AckRequest
            "failed" -> SmInboundFrame.Failed(handledByServer = element.attribute("h")?.toLongOrNull())
            else -> null
        }
    }

    private fun isSmNamespace(name: String, attributes: Map<String, String>): Boolean {
        val defaultNs = attributes["xmlns"]
        if (defaultNs == NAMESPACE) return true
        val prefix = name.substringBefore(':', "")
        if (prefix.isBlank()) return false
        return attributes["xmlns:$prefix"] == NAMESPACE
    }

    private fun enable(): String = "<enable xmlns='$NAMESPACE' resume='true'/>"

    private fun resume(previousId: String, handledByClient: Long): String =
        "<resume xmlns='$NAMESPACE' previd='${escape(previousId)}' h='$handledByClient'/>"

    private fun ackRequest(): String = "<r xmlns='$NAMESPACE'/>"

    private fun ack(handledByClient: Long): String = "<a xmlns='$NAMESPACE' h='$handledByClient'/>"

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private sealed interface SmInboundFrame {
        data class Enabled(
            val id: String?,
            val allowResume: Boolean,
            val maxResumeSeconds: Int?,
        ) : SmInboundFrame

        data class Resumed(
            val previousId: String?,
            val handledByServer: Long,
        ) : SmInboundFrame

        data class Acknowledged(
            val handledByServer: Long,
        ) : SmInboundFrame

        data object AckRequest : SmInboundFrame

        data class Failed(
            val handledByServer: Long?,
        ) : SmInboundFrame
    }

    private data class PendingOutbound(
        val sequence: Long,
        val xml: String,
    )

    private class SessionState {
        var serverSupportsSm: Boolean = false
        var enabled: Boolean = false
        var resumed: Boolean = false
        var sessionId: String? = null
        var allowResume: Boolean = false
        var maxResumeSeconds: Int? = null
        var outboundSentCount: Long = 0
        var inboundHandledCount: Long = 0
        var lastServerAckCount: Long = 0
        var awaitingServerAckReply: Boolean = false
        var outboundSinceAckRequest: Int = 0
        var enableRequested: Boolean = false
        var resumeRequested: Boolean = false
        var lastResumePreviousId: String? = null
        var lastBoundJid: String? = null
        var sessionStartedAtEpochMillis: Long = 0L
        val pendingOutbound: MutableList<PendingOutbound> = mutableListOf()
        val pendingReplayAfterEnable: MutableList<String> = mutableListOf()
    }

    data class PersistedSessionState(
        val sessionId: String,
        val allowResume: Boolean,
        val maxResumeSeconds: Int?,
        val outboundSentCount: Long,
        val lastServerAckCount: Long,
        val pendingOutbound: List<String>,
        val boundJid: String?,
        val persistedAtEpochMillis: Long,
    )

    interface StreamManagementStateStore {
        fun load(owner: BareJid): PersistedSessionState?
        fun save(owner: BareJid, state: PersistedSessionState)
        fun clear(owner: BareJid)
    }
}

private fun String?.toBooleanLike(): Boolean {
    if (this == null) return false
    return this.equals("true", ignoreCase = true) || this == "1"
}
