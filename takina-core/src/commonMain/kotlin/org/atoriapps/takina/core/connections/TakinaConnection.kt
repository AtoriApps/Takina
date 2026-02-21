package org.atoriapps.takina.core.connections

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.atoriapps.takina.core.exceptions.ConnectionFailureKind
import org.atoriapps.takina.core.exceptions.NotConnectedException
import org.atoriapps.takina.core.exceptions.TakinaConnectionException
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import org.atoriapps.takina.core.xmpp.stanzas.XmppStanza

class TakinaConnection(
    val config: ConnectionConfig,
    private val passwordProvider: () -> String,
    private val connectorFactory: () -> AbstractConnector = { Connector() },
    private val onInboundStanza: (TakinaConnection, String) -> Unit = { _, _ -> },
    private val onInboundFrame: (TakinaConnection, String) -> Unit = { _, _ -> },
    private val onOutboundFrame: (TakinaConnection, String) -> Unit = { _, _ -> },
    private val onConnectionClosed: (TakinaConnection, String) -> Unit = { _, _ -> },
    private val onLifecycleStageChanged: (
        connection: TakinaConnection,
        oldStage: ConnectionLifecycleStage,
        newStage: ConnectionLifecycleStage,
    ) -> Unit = { _, _, _ -> },
) {
    companion object {
        private const val TAG = "Takina连接"
    }

    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        DISCONNECTING,
    }

    val boundJid = config.jid

    var state: ConnectionState = ConnectionState.DISCONNECTED
        private set

    val lifecycleStage: ConnectionLifecycleStage
        get() = stateMachine.stage

    val supportsStreamManagement: Boolean
        get() = lastFeaturesXml?.let(XmppProtocol::containsStreamManagement) == true

    private var connector: AbstractConnector? = null
    private var closeRequestedByUser = false
    private val stateMachine = ConnectionStateMachine()
    private var lastFeaturesXml: String? = null

    private val pendingIqLock = Any()
    private val pendingIqRequests = linkedMapOf<String, CompletableDeferred<IqResult>>()

    fun connect() {
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return

        state = ConnectionState.CONNECTING
        LogUtils.info(
            TAG,
            "开始连接账号",
            boundJid,
            "地址=${config.host}:${config.port}",
            "安全模式=${config.securityMode}",
        )
        val createdConnector = connectorFactory()
        try {
            createdConnector.connect(config)
            moveLifecycle(
                target = ConnectionLifecycleStage.TCP_CONNECTED,
                allowedFrom = setOf(ConnectionLifecycleStage.DISCONNECTED),
                reason = "TCP 连接已建立",
            )
            var featuresXml = openStreamAndReadFeatures(createdConnector)
            lastFeaturesXml = featuresXml

            if (config.securityMode == SecurityMode.START_TLS) {
                if (!XmppProtocol.containsStartTls(featuresXml)) {
                    throw TakinaConnectionException(
                        message = "服务端不支持 STARTTLS，无法继续连接",
                        kind = ConnectionFailureKind.STARTTLS_UNSUPPORTED,
                        detail = "server does not advertise STARTTLS for ${config.jid.domain}",
                    )
                }

                createdConnector.send(XmppStream.startTlsRequest())
                val startTlsResponse = createdConnector.readFrame(config.connectTimeoutMillis)
                if (!XmppProtocol.isStartTlsProceed(startTlsResponse)) {
                    throw TakinaConnectionException(
                        message = "TLS 协商失败",
                        kind = ConnectionFailureKind.TLS_NEGOTIATION_FAILED,
                        detail = startTlsResponse,
                    )
                }
                createdConnector.upgradeToTls(config)
                moveLifecycle(
                    target = ConnectionLifecycleStage.TLS_NEGOTIATED,
                    allowedFrom = setOf(ConnectionLifecycleStage.STREAM_OPENED),
                    reason = "TLS 升级完成",
                )
                featuresXml = openStreamAndReadFeatures(createdConnector)
                lastFeaturesXml = featuresXml
            }

            if (!XmppProtocol.containsMechanism(featuresXml, "PLAIN")) {
                throw TakinaConnectionException(
                    message = "服务端不支持 SASL PLAIN，无法认证",
                    kind = ConnectionFailureKind.AUTH_MECHANISM_UNSUPPORTED,
                    detail = featuresXml,
                )
            }

            createdConnector.send(XmppStream.authPlain(config.jid, passwordProvider()))
            val authResult = createdConnector.readFrame(config.connectTimeoutMillis)
            if (!XmppProtocol.isSaslSuccess(authResult)) {
                val saslFailure = parseSaslFailure(authResult)
                throw TakinaConnectionException(
                    message = saslFailure.userMessage,
                    kind = saslFailure.kind,
                    detail = authResult,
                )
            }
            moveLifecycle(
                target = ConnectionLifecycleStage.AUTHENTICATED,
                allowedFrom = setOf(ConnectionLifecycleStage.STREAM_OPENED),
                reason = "SASL 认证成功",
            )

            openStreamAndReadFeatures(createdConnector).also { refreshed ->
                lastFeaturesXml = refreshed
            }
            bindResource(createdConnector)

            connector = createdConnector
            state = ConnectionState.CONNECTED
            moveLifecycle(
                target = ConnectionLifecycleStage.ONLINE,
                allowedFrom = setOf(ConnectionLifecycleStage.RESOURCE_BOUND),
                reason = "资源绑定完成并已上线",
            )
            createdConnector.startFramePump(
                onFrame = { frame -> handleIncomingFrame(frame) },
                onError = { error -> handlePumpFailure(error) },
            )
            LogUtils.info(TAG, "连接完成，账号已上线", boundJid)
        } catch (t: Throwable) {
            state = ConnectionState.DISCONNECTED
            runCatching { createdConnector.close() }
            lastFeaturesXml = null
            val normalized = normalizeConnectFailure(t)
            failAllPendingIqRequests(normalized)
            forceLifecycle(ConnectionLifecycleStage.DISCONNECTED)
            val detail = normalized.detail?.takeIf { it.isNotBlank() } ?: t.message ?: "未知错误"
            LogUtils.error(TAG, "连接失败", boundJid, "原因=${normalized.message}", "细节=$detail")
            throw normalized
        }
    }

    fun disconnect() {
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.DISCONNECTING) return
        closeRequestedByUser = true
        state = ConnectionState.DISCONNECTING
        LogUtils.info(TAG, "开始断开连接", boundJid)
        forceLifecycle(ConnectionLifecycleStage.DISCONNECTING)
        try {
            connector?.stopFramePump()
            connector?.send(XmppStream.closingStream())
        } finally {
            runCatching { connector?.close() }
            connector = null
            lastFeaturesXml = null
            state = ConnectionState.DISCONNECTED
            closeRequestedByUser = false
            failAllPendingIqRequests(NotConnectedException("account $boundJid disconnected"))
            forceLifecycle(ConnectionLifecycleStage.DISCONNECTED)
            LogUtils.info(TAG, "连接已断开", boundJid)
        }
    }

    fun send(stanza: XmppStanza) {
        sendRaw(stanza.toXml())
    }

    fun sendRaw(xml: String) {
        val activeConnector = connector ?: throw NotConnectedException("account $boundJid is not connected")
        activeConnector.send(xml)
        onOutboundFrame(this, xml)
    }

    suspend fun sendIqAndAwaitResult(
        iqStanza: IqStanza,
        timeoutMillis: Long = 10_000,
    ): IqResult {
        if (timeoutMillis <= 0) throw IllegalArgumentException("timeoutMillis must be > 0")
        val iqId = iqStanza.id
        val waiter = CompletableDeferred<IqResult>()

        synchronized(pendingIqLock) {
            if (pendingIqRequests.containsKey(iqId)) throw TakinaConnectionException("duplicate pending iq id: $iqId")
            pendingIqRequests[iqId] = waiter
        }

        return try {
            send(iqStanza)
            withTimeout(timeoutMillis) { waiter.await() }
        } catch (timeout: TimeoutCancellationException) {
            throw TakinaConnectionException("iq request timed out: id=$iqId timeout=${timeoutMillis}ms", timeout)
        } finally {
            synchronized(pendingIqLock) {
                pendingIqRequests.remove(iqId)
            }
        }
    }

    private fun openStreamAndReadFeatures(connector: AbstractConnector): String {
        connector.send(XmppStream.openingStream(config))
        val firstFrame = connector.readFrame(config.connectTimeoutMillis)
        val firstRoot = XmppProtocol.rootName(firstFrame)
        val featuresXml = when (firstRoot) {
            "stream" -> {
                val secondFrame = connector.readFrame(config.connectTimeoutMillis)
                XmppProtocol.expectRoot(secondFrame, "features", "expected stream features")
                secondFrame
            }

            "features" -> firstFrame
            else -> throw TakinaConnectionException("expected <stream:stream> or <stream:features>, got <$firstRoot>")
        }
        moveLifecycle(
            target = ConnectionLifecycleStage.STREAM_OPENED,
            allowedFrom = setOf(
                ConnectionLifecycleStage.TCP_CONNECTED,
                ConnectionLifecycleStage.TLS_NEGOTIATED,
                ConnectionLifecycleStage.AUTHENTICATED,
            ),
            reason = "已打开流并收到能力特性",
        )
        return featuresXml
    }

    private fun bindResource(connector: AbstractConnector) {
        val requestId = IdUtils.newStanzaId("bind")
        connector.send(XmppStream.bindResource(config.resource, requestId))
        repeat(20) {
            val frame = connector.readFrame(config.connectTimeoutMillis)
            if (XmppProtocol.rootName(frame) != "iq") return@repeat
            val attrs = XmppProtocol.rootAttributes(frame)
            if (attrs["id"] != requestId) return@repeat
            val type = attrs["type"] ?: throw TakinaConnectionException(
                message = "资源绑定失败：返回缺少 iq@type",
                kind = ConnectionFailureKind.RESOURCE_BIND_FAILED,
                detail = frame,
            )
            if (type != "result") throw TakinaConnectionException(
                message = "资源绑定失败：服务端拒绝绑定",
                kind = ConnectionFailureKind.RESOURCE_BIND_FAILED,
                detail = frame,
            )
            moveLifecycle(
                target = ConnectionLifecycleStage.RESOURCE_BOUND,
                allowedFrom = setOf(ConnectionLifecycleStage.STREAM_OPENED),
                reason = "收到资源绑定结果",
            )
            return
        }
        throw TakinaConnectionException(
            message = "资源绑定超时，请检查网络或服务端状态",
            kind = ConnectionFailureKind.NETWORK_TIMEOUT,
            detail = "resource binding timeout for account $boundJid",
        )
    }

    private fun handleIncomingFrame(frame: String) {
        val root = XmppProtocol.rootName(frame)
        when (root) {
            "iq" -> {
                completePendingIqIfMatched(frame)
                onInboundStanza(this, frame)
            }

            "message", "presence" -> onInboundStanza(this, frame)
            "stream" -> Unit
            else -> Unit
        }
        if (root != "stream") onInboundFrame(this, frame)
    }

    private fun completePendingIqIfMatched(frame: String) {
        val attrs = XmppProtocol.rootAttributes(frame)
        val id = attrs["id"] ?: return
        val type = attrs["type"]?.let(IqType::fromWireValue) ?: return
        if (type != IqType.RESULT && type != IqType.ERROR) return

        val waiter = synchronized(pendingIqLock) { pendingIqRequests[id] } ?: return
        waiter.complete(
            IqResult(
                id = id,
                type = type,
                xml = frame,
            ),
        )
    }

    private fun handlePumpFailure(error: Throwable) {
        if (closeRequestedByUser) return
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.DISCONNECTING) return
        state = ConnectionState.DISCONNECTED
        connector = null
        lastFeaturesXml = null
        LogUtils.warn(TAG, "连接异常关闭", boundJid, error.message ?: "未知错误")
        failAllPendingIqRequests(normalizeConnectFailure(error))
        forceLifecycle(ConnectionLifecycleStage.DISCONNECTED)
        onConnectionClosed(this, error.message ?: "连接中断")
    }

    private fun moveLifecycle(
        target: ConnectionLifecycleStage,
        allowedFrom: Set<ConnectionLifecycleStage>,
        reason: String,
    ) {
        val previous = stateMachine.moveTo(target, allowedFrom, reason)
        if (previous != target) onLifecycleStageChanged(this, previous, target)
    }

    private fun forceLifecycle(target: ConnectionLifecycleStage) {
        val previous = stateMachine.forceTo(target)
        if (previous != target) onLifecycleStageChanged(this, previous, target)
    }

    private fun failAllPendingIqRequests(error: Throwable) {
        val waiters = synchronized(pendingIqLock) {
            val values = pendingIqRequests.values.toList()
            pendingIqRequests.clear()
            values
        }
        waiters.forEach { waiter ->
            waiter.completeExceptionally(error)
        }
    }

    private fun parseSaslFailure(authResultXml: String): SaslFailure {
        val lowered = authResultXml.lowercase()
        return when {
            lowered.contains("<not-authorized") -> SaslFailure(
                userMessage = "账号或密码错误，请检查后重试",
                kind = ConnectionFailureKind.INVALID_CREDENTIALS,
            )

            lowered.contains("<temporary-auth-failure") -> SaslFailure(
                userMessage = "服务端认证临时失败，请稍后重试",
                kind = ConnectionFailureKind.SERVER_REJECTED,
            )

            else -> SaslFailure(
                userMessage = "登录认证失败，请检查账号配置",
                kind = ConnectionFailureKind.SERVER_REJECTED,
            )
        }
    }

    private fun normalizeConnectFailure(error: Throwable): TakinaConnectionException {
        if (error is TakinaConnectionException) return error

        val causeMessage = generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
            .ifBlank { "未知错误" }
        val normalized = classifyByMessage(causeMessage)
        return TakinaConnectionException(
            message = normalized.userMessage,
            cause = error,
            kind = normalized.kind,
            detail = causeMessage,
        )
    }

    private fun classifyByMessage(message: String): NormalizedFailure {
        val lowered = message.lowercase()
        return when {
            lowered.contains("read timed out") || lowered.contains("timed out") -> NormalizedFailure(
                userMessage = "连接超时，请检查网络或服务端状态",
                kind = ConnectionFailureKind.NETWORK_TIMEOUT,
            )

            lowered.contains("unknownhost") || lowered.contains("no address associated") || lowered.matches(Regex("""^[a-z0-9.-]+\.[a-z]{2,}$""")) -> NormalizedFailure(
                userMessage = "无法解析服务器域名，请检查服务器地址或 DNS",
                kind = ConnectionFailureKind.DNS_RESOLUTION_FAILED,
            )

            lowered.contains("connection closed while waiting for frame") || lowered.contains("broken pipe") || lowered.contains("connection reset") || lowered.contains("eof") -> NormalizedFailure(
                userMessage = "连接被中断，请检查网络稳定性后重试",
                kind = ConnectionFailureKind.CONNECTION_CLOSED,
            )

            lowered.contains("network is unreachable") || lowered.contains("no route to host") -> NormalizedFailure(
                userMessage = "网络不可达，请确认网络连接后重试",
                kind = ConnectionFailureKind.NETWORK_UNREACHABLE,
            )

            else -> NormalizedFailure(
                userMessage = "连接失败，请检查网络和账号配置",
                kind = ConnectionFailureKind.UNKNOWN,
            )
        }
    }

    private data class SaslFailure(
        val userMessage: String,
        val kind: ConnectionFailureKind,
    )

    private data class NormalizedFailure(
        val userMessage: String,
        val kind: ConnectionFailureKind,
    )
}

abstract class AbstractConnector {
    abstract val isConnected: Boolean

    abstract fun connect(config: ConnectionConfig)

    abstract fun send(xml: String)

    abstract fun readFrame(timeoutMillis: Int): String

    abstract fun upgradeToTls(config: ConnectionConfig)

    abstract fun startFramePump(
        onFrame: (String) -> Unit,
        onError: (Throwable) -> Unit,
    )

    abstract fun stopFramePump()

    abstract fun close()
}

expect class Connector() : AbstractConnector
