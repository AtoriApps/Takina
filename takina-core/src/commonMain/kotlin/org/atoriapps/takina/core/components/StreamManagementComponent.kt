package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.components.TakinaFrameInterceptAction.DROP
import org.atoriapps.takina.core.exceptions.TakinaConnectionException
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.Jid
import kotlin.math.min

class StreamManagementComponent internal constructor(
    private val takina: AbstractTakina,
) : TakinaConnectionLifecycleComponent, TakinaInboundFrameInterceptor, TakinaInboundStanzaInterceptor, TakinaOutboundFrameObserver, TakinaOutboundFrameInterceptor {
    companion object : TakinaComponentProvider<StreamManagementComponent> {
        private const val TAG = "流管理组件"

        const val NAMESPACE: String = "urn:xmpp:sm:3"

        override fun getInstance(context: TakinaContext): StreamManagementComponent {
            val core = context as? AbstractTakina ?: error("流管理组件只能安装在 Takina 核心上下文中")
            return StreamManagementComponent(core)
        }

        override fun getComponentType() = StreamManagementComponent::class
    }

    var autoReconnectOnConnectionDropped: Boolean = true
    var autoReconnectMaxAttempts: Int = 3
    var autoReconnectDelayMillis: Int = 1_000
    var autoAckRequestInterval: Int = 10
    var persistStateToStore: Boolean = false
    var restorePersistedStateOnStartup: Boolean = false
    var stateStore: StreamManagementStateStore? = null
    var nowMillisProvider: () -> Long = { System.currentTimeMillis() }

    private val statesByJid = linkedMapOf<BareJid, SessionState>()
    private val jidByState = linkedMapOf<SessionState, BareJid>()
    override val priority: Int = 100

    override fun onAfterConnected(connection: TakinaConnection, context: TakinaContext) {
        if (!connection.supportsStreamManagement) return LogUtils.debug(TAG, "服务端没说支持流管理，跳过协商", connection.boundJid)
        val state = stateFor(connection.boundJid)
        resetReconnectAttempts(state)
        if (isResumeExpired(state)) {
            LogUtils.warn(TAG, "流管理会话已过期，放弃 resume，改为 enable", connection.boundJid)
            invalidateSessionForEnable(state)
        }
        val previousId = state.sessionId
        val canResume = state.enabled && state.allowResume && !previousId.isNullOrBlank()

        if (canResume) {
            runCatching {
                markResumeRequested(state, previousId)
                sendResume(from = connection.boundJid, previousId = previousId, handledByClient = state.inboundHandledCount)
                LogUtils.debug(TAG, "已发送流管理 resume", connection.boundJid, "previd=$previousId", "h=${state.inboundHandledCount}")
            }.onFailure { error ->
                LogUtils.warn(TAG, "发送流管理 resume 失败，回退 enable", connection.boundJid, error.message ?: "未知错误")
                sendEnableInternal(connection.boundJid)
            }

            return
        }

        sendEnableInternal(connection.boundJid)
    }

    override fun onBeforeDisconnect(jid: BareJid, context: TakinaContext) {
        clearState(jid)
    }

    override fun onAfterDisconnected(jid: BareJid, reason: String?, context: TakinaContext) {
        if (reason.isNullOrBlank()) return
        if (!autoReconnectOnConnectionDropped) return
        val state = stateFor(jid)
        while (shouldAutoReconnect(state)) {
            val attempt = markReconnectAttempt(state)
            LogUtils.warn(TAG, "流管理自动重连开始", jid, "attempt=$attempt")
            val result = runCatching { takina.connect(jid) }
            if (result.isSuccess) return LogUtils.warn(TAG, "流管理自动重连成功", jid, "attempt=$attempt")
            val err = result.exceptionOrNull()
            val kind = (err as? TakinaConnectionException)?.kind?.name ?: "UNKNOWN"
            LogUtils.warn(TAG, "流管理自动重连失败", jid, "attempt=$attempt", "kind=$kind", err?.message ?: "未知错误")
            if (autoReconnectDelayMillis > 0 && shouldAutoReconnect(state)) runCatching { Thread.sleep(autoReconnectDelayMillis.toLong()) }
        }
        LogUtils.warn(TAG, "流管理自动重连次数已达上限", jid, "max=$autoReconnectMaxAttempts", "可调大 autoReconnectMaxAttempts/autoReconnectDelayMillis 后再试")
    }

    override fun interceptOutboundFrame(connection: TakinaConnection, xml: String, context: TakinaContext): TakinaFrameInterceptResult {
        val state = stateFor(connection.boundJid)
        if (!shouldDeferOutboundUntilSmSettled(state)) return TakinaFrameInterceptResult(xml = xml)
        if (isSmControlFrame(xml)) return TakinaFrameInterceptResult(xml = xml)

        val root = org.atoriapps.takina.core.connections.XmppProtocol.rootName(xml)
        if (root != "message" && root != "presence" && root != "iq") return TakinaFrameInterceptResult(xml = xml)
        synchronized(state) { state.deferredOutboundBeforeSmReady += xml }
        LogUtils.debug(TAG, "流管理恢复握手未完成，暂缓出站 stanza", connection.boundJid, "root=$root", "queued=${state.deferredOutboundBeforeSmReady.size}")
        return TakinaFrameInterceptResult(action = DROP, xml = xml)
    }

    override fun interceptInboundStanza(
        connection: TakinaConnection,
        stanzaType: String,
        xml: String,
        context: TakinaContext,
    ): TakinaInboundStanzaInterceptResult {
        onInboundStanzaHandled(stateFor(connection.boundJid))
        return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
    }

    override fun interceptInboundFrame(connection: TakinaConnection, xml: String, context: TakinaContext): TakinaFrameInterceptResult {
        val state = stateFor(connection.boundJid)
        val frame = onInboundFrame(connection.boundJid, xml) ?: return TakinaFrameInterceptResult(xml = xml)
        when (frame) {
            is InboundFrame.Enabled -> {
                LogUtils.warn(TAG, "流管理已启用", connection.boundJid, "sessionId=${frame.id ?: "<none>"}", "resume=${frame.allowResume}")
                replayPendingAfterEnable(connection)
            }

            is InboundFrame.Resumed -> {
                if (!isResumedFrameConsistent(state, frame)) {
                    LogUtils.warn(TAG, "流管理 resumed 的 previd 与请求不一致，回退 enable", connection.boundJid, "expected=${state.lastResumePreviousId}", "actual=${frame.previousId}")
                    invalidateSessionForEnable(state)
                    sendEnableInternal(connection.boundJid)
                    return TakinaFrameInterceptResult(xml = xml)
                }

                LogUtils.warn(TAG, "流管理已恢复", connection.boundJid, "acked=${frame.handledByServer}")
                replayUnackedAfterResume(connection)
            }

            is InboundFrame.Acknowledged ->
                LogUtils.debug(TAG, "收到服务端确认", connection.boundJid, "acked=${frame.handledByServer}")

            InboundFrame.AckRequest -> {
                val state = stateFor(connection.boundJid)
                sendAck(from = connection.boundJid, handledByClient = state.inboundHandledCount)
                LogUtils.debug(TAG, "收到服务端 Ack 请求并已响应", connection.boundJid, "h=${state.inboundHandledCount}")
            }

            is InboundFrame.Failed -> {
                LogUtils.warn(TAG, "流管理失败，将重新 enable", connection.boundJid, "acked=${frame.handledByServer ?: "<none>"}")
                sendEnableInternal(connection.boundJid)
            }
        }
        return TakinaFrameInterceptResult(xml = xml)
    }

    override fun onOutboundFrameSent(connection: TakinaConnection, xml: String, context: TakinaContext) {
        val root = org.atoriapps.takina.core.connections.XmppProtocol.rootName(xml)
        if (root != "message" && root != "presence" && root != "iq") return
        val jid = connection.boundJid
        val state = stateFor(jid)
        onOutboundStanzaSent(state, xml)
        if (!shouldSendAckRequest(state)) return
        runCatching {
            sendAckRequest(from = jid)
            markAckRequestSent(state)
            LogUtils.debug(TAG, "已发送服务端 Ack 请求", jid, "interval=$autoAckRequestInterval")
        }.onFailure { error -> LogUtils.warn(TAG, "发送服务端 Ack 请求失败", jid, error.message ?: "未知错误") }
    }

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
        handledByClient: Long,
    ): XmlElement {
        require(previousId.isNotBlank()) { "previousId 不能为空" }
        require(handledByClient >= 0) { "handledByClient 不能小于 0" }
        return XmlElement(
            name = "resume",
            namespace = NAMESPACE,
            attributes = mapOf(
                "previd" to previousId,
                "h" to handledByClient.toString(),
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
    ) = takina.sendRaw(from, enable(allowResume, maxResumeSeconds).toXmlString())

    fun sendResume(
        from: Jid? = null,
        previousId: String,
        handledByClient: Long,
    ) = takina.sendRaw(from, resume(previousId, handledByClient).toXmlString())

    fun sendAckRequest(from: Jid? = null) = takina.sendRaw(from, ackRequest().toXmlString())

    fun sendAck(
        from: Jid? = null,
        handledByClient: Long,
    ) = takina.sendRaw(from, ack(handledByClient).toXmlString())

    private fun sendEnableInternal(jid: BareJid) {
        val state = stateFor(jid)
        runCatching {
            markEnableRequested(state)
            sendEnable(from = jid, allowResume = true)
            LogUtils.debug(TAG, "已发送流管理 enable", jid)
        }.onFailure { error -> LogUtils.warn(TAG, "发送流管理 enable 失败", jid, error.message ?: "未知错误") }
    }

    private fun replayPendingAfterEnable(connection: TakinaConnection) {
        val state = stateFor(connection.boundJid)
        val replay = consumePendingReplayAfterEnable(state)
        if (replay.isEmpty()) {
            replayDeferredAfterSmReady(connection, state)
            return
        }
        LogUtils.warn(TAG, "流管理新会话需要重放", "count=${replay.size}")
        var sentCount = 0
        runCatching {
            replay.forEachIndexed { index, xml ->
                takina.sendRaw(connection.boundJid, xml)
                sentCount = index + 1
            }
            LogUtils.warn(TAG, "流管理新会话重放完成", connection.boundJid)
            replayDeferredAfterSmReady(connection, state)
        }.onFailure { error ->
            val unsent = replay.drop(sentCount)
            prependPendingReplayAfterEnable(state, unsent)
            LogUtils.warn(TAG, "流管理新会话重放中断", connection.boundJid, "已发送=$sentCount", "剩余=${unsent.size}", error.message ?: "未知错误")
        }
    }

    private fun replayUnackedAfterResume(connection: TakinaConnection) {
        val state = stateFor(connection.boundJid)
        val replay = snapshotUnackedForResume(state)
        if (replay.isEmpty()) {
            replayDeferredAfterSmReady(connection, state)
            return
        }
        scheduleResumeReplay(state, replay)
        runCatching {
            replay.forEach { xml -> takina.sendRaw(connection.boundJid, xml) }
            LogUtils.warn(TAG, "流管理会话恢复后重放完成", connection.boundJid, "count=${replay.size}")
            replayDeferredAfterSmReady(connection, state)
        }.onFailure { error -> LogUtils.warn(TAG, "流管理会话恢复后重放失败", connection.boundJid, error.message ?: "未知错误") }
    }

    private fun replayDeferredAfterSmReady(connection: TakinaConnection, state: SessionState) {
        val deferred = consumeDeferredOutbound(state)
        if (deferred.isEmpty()) return
        LogUtils.warn(TAG, "流管理握手后开始补发暂缓的 stanza", connection.boundJid, "count=${deferred.size}")
        var sentCount = 0
        runCatching {
            deferred.forEachIndexed { index, xml ->
                takina.sendRaw(connection.boundJid, xml)
                sentCount = index + 1
            }
            LogUtils.warn(TAG, "流管理握手后暂缓的 stanza 补发完成", connection.boundJid)
        }.onFailure { error ->
            val unsent = deferred.drop(sentCount)
            prependDeferredOutbound(state, unsent)
            LogUtils.warn(TAG, "流管理握手后暂缓的 stanza 补发中断", connection.boundJid, "已发送=$sentCount", "剩余=${unsent.size}", error.message ?: "未知错误")
        }
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
    }.also { persistStateIfNeeded(state) }

    fun onInboundStanzaHandled(state: SessionState): Long = synchronized(state) {
        state.inboundHandledCount += 1
        state.inboundHandledCount
    }

    fun onServerAcknowledged(state: SessionState, handledByServer: Long): Long {
        require(handledByServer >= 0) { "handledByServer 不能小于 0" }
        return synchronized(state) {
            if (handledByServer < state.lastServerAckCount) return@synchronized state.lastServerAckCount
            val normalized = min(handledByServer, state.outboundSentCount)
            if (normalized > state.lastServerAckCount) {
                state.lastServerAckCount = normalized
            }
            state.pendingOutbound.removeAll { it.sequence <= state.lastServerAckCount }
            state.awaitingServerAckReply = false
            state.outboundSinceAckRequest = 0
            state.lastServerAckCount
        }.also { persistStateIfNeeded(state) }
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

    private fun consumeDeferredOutbound(state: SessionState): List<String> = synchronized(state) {
        if (state.deferredOutboundBeforeSmReady.isEmpty()) return@synchronized emptyList()
        val snapshot = state.deferredOutboundBeforeSmReady.toList()
        state.deferredOutboundBeforeSmReady.clear()
        snapshot
    }

    private fun prependDeferredOutbound(state: SessionState, stanzas: List<String>) {
        if (stanzas.isEmpty()) return
        synchronized(state) { state.deferredOutboundBeforeSmReady.addAll(0, stanzas) }
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
                state.sessionStartedAtEpochMillis = nowMillisProvider()
            }

            is InboundFrame.Resumed -> synchronized(state) {
                state.enabled = true
                state.resumed = true
                onServerAcknowledged(state, frame.handledByServer)
                state.enableRequested = false
                state.resumeRequested = false
                state.lastResumePreviousId = null
                if (state.sessionStartedAtEpochMillis == 0L) state.sessionStartedAtEpochMillis = nowMillisProvider()
            }

            is InboundFrame.Acknowledged -> onServerAcknowledged(state, frame.handledByServer)
            InboundFrame.AckRequest -> Unit
            is InboundFrame.Failed -> synchronized(state) {
                if (frame.handledByServer != null) onServerAcknowledged(state, frame.handledByServer)

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

                invalidateSessionForEnable(state)
            }
        }

        persistStateIfNeeded(state)
    }

    fun onInboundFrame(jid: BareJid, xml: String): InboundFrame? {
        val frame = parseInboundFrame(xml) ?: return null
        applyInboundFrame(stateFor(jid), frame)
        return frame
    }

    private fun isSmNamespace(attributes: Map<String, String>): Boolean =
        if (attributes["xmlns"] == NAMESPACE) true
        else attributes.any { (key, value) -> key.startsWith("xmlns:") && value == NAMESPACE }

    private fun restoreFromStoreIfNeeded(
        jid: BareJid,
        state: SessionState,
    ) {
        if (!restorePersistedStateOnStartup) return
        val persisted = stateStore?.load(jid) ?: return
        if (persisted.sessionId.isBlank()) return
        if (!persisted.allowResume) return
        if (persisted.lastServerAckCount < 0L || persisted.outboundSentCount < persisted.lastServerAckCount) return
        if (persisted.maxResumeSeconds != null && persisted.maxResumeSeconds > 0) {
            val maxAge = persisted.maxResumeSeconds.toLong() * 1_000L
            if (nowMillisProvider() - persisted.persistedAtEpochMillis > maxAge) {
                stateStore?.clear(jid)
                return
            }
        }

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
            state.sessionStartedAtEpochMillis = persisted.persistedAtEpochMillis

            var sequence = persisted.lastServerAckCount
            persisted.pendingOutbound.forEach { xml ->
                sequence += 1
                state.pendingOutbound += PendingOutboundStanza(sequence = sequence, xml = xml)
            }

            if (sequence > state.outboundSentCount) state.outboundSentCount = sequence
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
                persistedAtEpochMillis = state.sessionStartedAtEpochMillis,
            )
        }

        if (snapshot == null) store.clear(jid)
        else store.save(jid, snapshot)
    }

    private fun invalidateSessionForEnable(state: SessionState) = synchronized(state) {
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

    internal fun isResumedFrameConsistent(state: SessionState, frame: InboundFrame.Resumed): Boolean = synchronized(state) {
        val expected = state.lastResumePreviousId
        if (expected.isNullOrBlank()) return@synchronized true
        val actual = frame.previousId ?: return@synchronized false
        actual == expected
    }

    private fun isResumeExpired(state: SessionState): Boolean = synchronized(state) {
        val maxSeconds = state.maxResumeSeconds ?: return@synchronized false
        if (maxSeconds <= 0) return@synchronized false
        if (state.sessionStartedAtEpochMillis <= 0L) return@synchronized false
        nowMillisProvider() - state.sessionStartedAtEpochMillis > maxSeconds.toLong() * 1_000L
    }

    internal fun shouldDeferOutboundUntilSmSettled(state: SessionState): Boolean = synchronized(state) {
        state.resumeRequested || (state.enableRequested && !state.enabled)
    }

    internal fun isSmControlFrame(xml: String): Boolean {
        val root = runCatching { org.atoriapps.takina.core.connections.XmppProtocol.rootName(xml) }.getOrNull() ?: return false
        if (root != "enable" && root != "resume" && root != "a" && root != "r") return false
        val parsed = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return false
        return isSmNamespace(parsed.attributes)
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

        internal val deferredOutboundBeforeSmReady: MutableList<String> = mutableListOf()

        var reconnectAttemptCount: Int = 0
            internal set

        var sessionStartedAtEpochMillis: Long = 0L
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
        val persistedAtEpochMillis: Long,
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

val TakinaContext.streamManagement: StreamManagementComponent get() = requireComponent(StreamManagementComponent)

private fun String?.toBooleanLike(): Boolean {
    if (this == null) return false
    return this.equals("true", ignoreCase = true) || this == "1"
}
