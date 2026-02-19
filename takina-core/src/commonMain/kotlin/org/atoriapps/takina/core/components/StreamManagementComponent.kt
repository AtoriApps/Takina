package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.Jid
import kotlin.math.min

class StreamManagementComponent internal constructor(
    private val takina: AbstractTakina,
) : TakinaComponent {
    companion object : TakinaComponentProvider<StreamManagementComponent> {
        const val NAMESPACE: String = "urn:xmpp:sm:3"

        override fun getInstance(context: TakinaContext): StreamManagementComponent {
            val core = context as? AbstractTakina
                ?: error("StreamManagementComponent 只能安装在 Takina 核心上下文中")
            return StreamManagementComponent(core)
        }

        override fun getComponentType() = StreamManagementComponent::class
    }

    var autoReconnectOnConnectionDropped: Boolean = true
    var autoReconnectMaxAttempts: Int = 3
    var autoAckRequestInterval: Int = 10
    var persistStateToStore: Boolean = false
    var restorePersistedStateOnStartup: Boolean = false
    var stateStore: StreamManagementStateStore? = null

    private val statesByJid = linkedMapOf<BareJid, SessionState>()
    private val jidByState = linkedMapOf<SessionState, BareJid>()

    @Synchronized
    fun stateFor(jid: BareJid): SessionState {
        statesByJid[jid]?.let { return it }

        val state = SessionState()
        statesByJid[jid] = state
        jidByState[state] = jid
        restoreFromStoreIfNeeded(jid, state)
        return state
    }

    @Synchronized
    fun clearState(jid: BareJid) {
        val state = statesByJid.remove(jid)
        if (state != null) jidByState.remove(state)
        stateStore?.clear(jid)
    }

    fun enable(
        allowResume: Boolean = true,
        maxResumeSeconds: Int? = null,
    ): XmlElement {
        if (maxResumeSeconds != null) require(maxResumeSeconds > 0) { "maxResumeSeconds 必须大于 0" }
        val attrs = linkedMapOf("resume" to if (allowResume) "true" else "false")
        if (maxResumeSeconds != null) attrs["max"] = maxResumeSeconds.toString()
        return XmlElement(
            name = "enable",
            namespace = NAMESPACE,
            attributes = attrs,
        )
    }

    fun resume(
        previousId: String,
        handledByServer: Long,
    ): XmlElement {
        require(previousId.isNotBlank()) { "previousId 不能为空" }
        require(handledByServer >= 0) { "handledByServer 不能小于 0" }
        return XmlElement(
            name = "resume",
            namespace = NAMESPACE,
            attributes = mapOf(
                "previd" to previousId,
                "h" to handledByServer.toString(),
            ),
        )
    }

    fun ackRequest(): XmlElement = XmlElement(
        name = "r",
        namespace = NAMESPACE,
    )

    fun ack(handledByClient: Long): XmlElement {
        require(handledByClient >= 0) { "handledByClient 不能小于 0" }
        return XmlElement(
            name = "a",
            namespace = NAMESPACE,
            attributes = mapOf("h" to handledByClient.toString()),
        )
    }

    fun sendEnable(
        from: Jid? = null,
        allowResume: Boolean = true,
        maxResumeSeconds: Int? = null,
    ) {
        takina.sendRaw(from, enable(allowResume, maxResumeSeconds).toXmlString())
    }

    fun sendResume(
        from: Jid? = null,
        previousId: String,
        handledByServer: Long,
    ) {
        takina.sendRaw(from, resume(previousId, handledByServer).toXmlString())
    }

    fun sendAckRequest(from: Jid? = null) {
        takina.sendRaw(from, ackRequest().toXmlString())
    }

    fun sendAck(
        from: Jid? = null,
        handledByClient: Long,
    ) {
        takina.sendRaw(from, ack(handledByClient).toXmlString())
    }

    fun onOutboundStanzaSent(state: SessionState, xml: String): Long = synchronized(state) {
        val replayHead = state.resumeReplayQueue.firstOrNull()
        if (replayHead == xml) {
            state.resumeReplayQueue.removeFirst()
            return@synchronized state.outboundSentCount
        }

        state.outboundSentCount += 1
        state.outboundSinceAckRequest += 1
        state.pendingOutbound += PendingOutboundStanza(
            sequence = state.outboundSentCount,
            xml = xml,
        )
        state.outboundSentCount
    }.also {
        persistStateIfNeeded(state)
    }

    fun onInboundStanzaHandled(state: SessionState): Long = synchronized(state) {
        state.inboundHandledCount += 1
        state.inboundHandledCount
    }

    fun onServerAcknowledged(state: SessionState, handledByServer: Long): Long {
        require(handledByServer >= 0) { "handledByServer 不能小于 0" }
        return synchronized(state) {
            val normalized = min(handledByServer, state.outboundSentCount)
            if (normalized > state.lastServerAckCount) {
                state.lastServerAckCount = normalized
            }
            state.pendingOutbound.removeAll { it.sequence <= state.lastServerAckCount }
            state.awaitingServerAckReply = false
            state.outboundSinceAckRequest = 0
            state.lastServerAckCount
        }.also {
            persistStateIfNeeded(state)
        }
    }

    fun snapshotUnackedForResume(state: SessionState): List<String> = synchronized(state) {
        state.pendingOutbound.map { it.xml }
    }

    fun scheduleResumeReplay(state: SessionState, stanzas: List<String>) = synchronized(state) {
        state.resumeReplayQueue.clear()
        state.resumeReplayQueue.addAll(stanzas)
    }

    fun consumePendingReplayAfterEnable(state: SessionState): List<String> = synchronized(state) {
        if (state.pendingReplayAfterEnable.isEmpty()) return emptyList()
        val replay = state.pendingReplayAfterEnable.toList()
        state.pendingReplayAfterEnable.clear()
        replay
    }

    fun prependPendingReplayAfterEnable(state: SessionState, stanzas: List<String>) {
        if (stanzas.isEmpty()) return

        synchronized(state) { state.pendingReplayAfterEnable.addAll(0, stanzas) }
    }

    fun markEnableRequested(state: SessionState) = synchronized(state) {
        state.enableRequested = true
        state.resumeRequested = false
        state.lastResumePreviousId = null
    }

    fun markResumeRequested(
        state: SessionState,
        previousId: String,
    ) = synchronized(state) {
        state.resumeRequested = true
        state.enableRequested = false
        state.lastResumePreviousId = previousId
    }

    fun shouldSendAckRequest(state: SessionState): Boolean = synchronized(state) {
        if (!state.enabled) return@synchronized false
        if (autoAckRequestInterval <= 0) return@synchronized false
        if (state.awaitingServerAckReply) return@synchronized false
        state.outboundSinceAckRequest >= autoAckRequestInterval
    }

    fun markAckRequestSent(state: SessionState) = synchronized(state) {
        state.awaitingServerAckReply = true
        state.outboundSinceAckRequest = 0
    }

    fun resetReconnectAttempts(state: SessionState) = synchronized(state) {
        state.reconnectAttemptCount = 0
    }

    fun shouldAutoReconnect(state: SessionState): Boolean = synchronized(state) {
        state.reconnectAttemptCount < autoReconnectMaxAttempts
    }

    fun markReconnectAttempt(state: SessionState): Int = synchronized(state) {
        state.reconnectAttemptCount += 1
        state.reconnectAttemptCount
    }

    fun parseInboundFrame(xml: String): InboundFrame? {
        val parsed = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        val localName = parsed.rootName.substringAfter(':')
        if (!isSmNamespace(parsed.attributes)) return null
        return when (localName) {
            "enabled" -> InboundFrame.Enabled(
                id = parsed.attributes["id"],
                allowResume = parsed.attributes["resume"].toBooleanLike(),
                maxResumeSeconds = parsed.attributes["max"]?.toIntOrNull(),
            )

            "resumed" -> InboundFrame.Resumed(
                previousId = parsed.attributes["previd"],
                handledByServer = parsed.attributes["h"]?.toLongOrNull() ?: 0L,
            )

            "a" -> parsed.attributes["h"]?.toLongOrNull()?.let { InboundFrame.Acknowledged(it) }
            "r" -> InboundFrame.AckRequest
            "failed" -> InboundFrame.Failed(parsed.attributes["h"]?.toLongOrNull())
            else -> null
        }
    }

    fun applyInboundFrame(state: SessionState, frame: InboundFrame) {
        when (frame) {
            is InboundFrame.Enabled -> synchronized(state) {
                if (state.resumeRequested && state.pendingOutbound.isNotEmpty()) {
                    val remaining = state.pendingOutbound.map { it.xml }
                    state.pendingReplayAfterEnable.clear()
                    state.pendingReplayAfterEnable.addAll(remaining)
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
                state.resumeReplayQueue.clear()
                state.enableRequested = false
                state.resumeRequested = false
                state.lastResumePreviousId = null
            }

            is InboundFrame.Resumed -> synchronized(state) {
                state.enabled = true
                state.resumed = true
                onServerAcknowledged(state, frame.handledByServer)
                state.enableRequested = false
                state.resumeRequested = false
                state.lastResumePreviousId = null
            }

            is InboundFrame.Acknowledged -> onServerAcknowledged(state, frame.handledByServer)
            InboundFrame.AckRequest -> Unit
            is InboundFrame.Failed -> synchronized(state) {
                if (frame.handledByServer != null) {
                    onServerAcknowledged(state, frame.handledByServer)
                }
                val remaining = state.pendingOutbound.map { it.xml }
                state.pendingReplayAfterEnable.clear()
                state.pendingReplayAfterEnable.addAll(remaining)
                state.pendingOutbound.clear()
                state.resumeReplayQueue.clear()
                state.outboundSentCount = 0
                state.lastServerAckCount = 0
                state.inboundHandledCount = 0
                state.awaitingServerAckReply = false
                state.outboundSinceAckRequest = 0
                state.enabled = false
                state.resumed = false
                state.sessionId = null
                state.allowResume = false
                state.maxResumeSeconds = null
                state.enableRequested = false
                state.resumeRequested = false
                state.lastResumePreviousId = null
            }
        }
        persistStateIfNeeded(state)
    }

    fun onInboundFrame(jid: BareJid, xml: String): InboundFrame? {
        val frame = parseInboundFrame(xml) ?: return null
        applyInboundFrame(stateFor(jid), frame)
        return frame
    }

    private fun isSmNamespace(attributes: Map<String, String>): Boolean {
        if (attributes["xmlns"] == NAMESPACE) return true
        return attributes.any { (key, value) -> key.startsWith("xmlns:") && value == NAMESPACE }
    }

    private fun restoreFromStoreIfNeeded(
        jid: BareJid,
        state: SessionState,
    ) {
        if (!restorePersistedStateOnStartup) return
        val persisted = stateStore?.load(jid) ?: return
        if (persisted.sessionId.isBlank()) return
        if (!persisted.allowResume) return
        if (persisted.lastServerAckCount < 0L || persisted.outboundSentCount < persisted.lastServerAckCount) return

        synchronized(state) {
            state.enabled = true
            state.resumed = false
            state.sessionId = persisted.sessionId
            state.allowResume = true
            state.maxResumeSeconds = persisted.maxResumeSeconds
            state.outboundSentCount = persisted.outboundSentCount
            state.inboundHandledCount = 0
            state.lastServerAckCount = persisted.lastServerAckCount
            state.enableRequested = false
            state.resumeRequested = false
            state.lastResumePreviousId = null
            state.awaitingServerAckReply = false
            state.outboundSinceAckRequest = 0
            state.pendingOutbound.clear()

            var sequence = persisted.lastServerAckCount
            persisted.pendingOutbound.forEach { xml ->
                sequence += 1
                state.pendingOutbound += PendingOutboundStanza(sequence = sequence, xml = xml)
            }
            if (sequence > state.outboundSentCount) {
                state.outboundSentCount = sequence
            }
        }
    }

    private fun persistStateIfNeeded(state: SessionState) {
        if (!persistStateToStore) return
        val store = stateStore ?: return
        val jid = synchronized(this) { jidByState[state] } ?: return

        val snapshot = synchronized(state) {
            if (!state.enabled) return@synchronized null
            val sessionId = state.sessionId ?: return@synchronized null
            if (!state.allowResume) return@synchronized null

            PersistedSessionState(
                sessionId = sessionId,
                allowResume = state.allowResume,
                maxResumeSeconds = state.maxResumeSeconds,
                outboundSentCount = state.outboundSentCount,
                lastServerAckCount = state.lastServerAckCount,
                pendingOutbound = state.pendingOutbound.map { it.xml },
            )
        }

        if (snapshot == null) {
            store.clear(jid)
        } else {
            store.save(jid, snapshot)
        }
    }

    sealed interface InboundFrame {
        data class Enabled(
            val id: String?,
            val allowResume: Boolean,
            val maxResumeSeconds: Int?,
        ) : InboundFrame

        data class Resumed(
            val previousId: String?,
            val handledByServer: Long,
        ) : InboundFrame

        data class Acknowledged(
            val handledByServer: Long,
        ) : InboundFrame

        data object AckRequest : InboundFrame

        data class Failed(
            val handledByServer: Long?,
        ) : InboundFrame
    }

    class SessionState {
        var enabled: Boolean = false
            internal set
        var resumed: Boolean = false
            internal set
        var sessionId: String? = null
            internal set
        var allowResume: Boolean = false
            internal set
        var maxResumeSeconds: Int? = null
            internal set
        var outboundSentCount: Long = 0
            internal set
        var inboundHandledCount: Long = 0
            internal set
        var lastServerAckCount: Long = 0
            internal set
        var enableRequested: Boolean = false
            internal set
        var resumeRequested: Boolean = false
            internal set
        var lastResumePreviousId: String? = null
            internal set
        var awaitingServerAckReply: Boolean = false
            internal set
        var outboundSinceAckRequest: Int = 0
            internal set
        internal val pendingOutbound: MutableList<PendingOutboundStanza> = mutableListOf()
        internal val pendingReplayAfterEnable: MutableList<String> = mutableListOf()
        internal val resumeReplayQueue: ArrayDeque<String> = ArrayDeque()
        var reconnectAttemptCount: Int = 0
            internal set
    }

    internal data class PendingOutboundStanza(
        val sequence: Long,
        val xml: String,
    )

    data class PersistedSessionState(
        val sessionId: String,
        val allowResume: Boolean,
        val maxResumeSeconds: Int?,
        val outboundSentCount: Long,
        val lastServerAckCount: Long,
        val pendingOutbound: List<String>,
    )

    interface StreamManagementStateStore {
        fun load(jid: BareJid): PersistedSessionState?
        fun save(
            jid: BareJid,
            state: PersistedSessionState,
        )

        fun clear(jid: BareJid)
    }
}

fun TakinaContext.streamManagement(): StreamManagementComponent = requireComponent(StreamManagementComponent)

private fun String?.toBooleanLike(): Boolean {
    if (this == null) return false
    return this.equals("true", ignoreCase = true) || this == "1"
}
