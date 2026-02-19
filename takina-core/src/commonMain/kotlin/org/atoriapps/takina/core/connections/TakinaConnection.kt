package org.atoriapps.takina.core.connections

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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
    private val onConnectionClosed: (TakinaConnection, String) -> Unit = { _, _ -> },
    private val onLifecycleStageChanged: (
        connection: TakinaConnection,
        oldStage: ConnectionLifecycleStage,
        newStage: ConnectionLifecycleStage,
    ) -> Unit = { _, _, _ -> },
) {
    companion object {
        private const val TAG = "TakinaConnection"
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

    private var connector: AbstractConnector? = null
    private var closeRequestedByUser = false
    private val stateMachine = ConnectionStateMachine()

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

            if (config.securityMode == SecurityMode.START_TLS) {
                if (!XmppProtocol.containsStartTls(featuresXml)) {
                    throw TakinaConnectionException("server does not advertise STARTTLS for ${config.jid.domain}")
                }

                createdConnector.send(XmppStream.startTlsRequest())
                val startTlsResponse = createdConnector.readFrame(config.connectTimeoutMillis)
                if (!XmppProtocol.isStartTlsProceed(startTlsResponse)) {
                    throw TakinaConnectionException("STARTTLS negotiation failed: $startTlsResponse")
                }
                createdConnector.upgradeToTls(config)
                moveLifecycle(
                    target = ConnectionLifecycleStage.TLS_NEGOTIATED,
                    allowedFrom = setOf(ConnectionLifecycleStage.STREAM_OPENED),
                    reason = "TLS 升级完成",
                )
                featuresXml = openStreamAndReadFeatures(createdConnector)
            }

            if (!XmppProtocol.containsMechanism(featuresXml, "PLAIN")) {
                throw TakinaConnectionException("server does not advertise SASL PLAIN")
            }

            createdConnector.send(XmppStream.authPlain(config.jid, passwordProvider()))
            val authResult = createdConnector.readFrame(config.connectTimeoutMillis)
            if (!XmppProtocol.isSaslSuccess(authResult)) {
                throw TakinaConnectionException("SASL authentication failed: $authResult")
            }
            moveLifecycle(
                target = ConnectionLifecycleStage.AUTHENTICATED,
                allowedFrom = setOf(ConnectionLifecycleStage.STREAM_OPENED),
                reason = "SASL 认证成功",
            )

            openStreamAndReadFeatures(createdConnector)
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
            failAllPendingIqRequests(TakinaConnectionException("connection failed for account $boundJid", t))
            forceLifecycle(ConnectionLifecycleStage.DISCONNECTED)
            LogUtils.error(TAG, "连接失败", boundJid, t.message ?: "未知错误")
            throw TakinaConnectionException("failed to connect account $boundJid", t)
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
            val type = attrs["type"] ?: throw TakinaConnectionException("bind result misses iq@type")
            if (type != "result") throw TakinaConnectionException("resource binding failed: $frame")
            moveLifecycle(
                target = ConnectionLifecycleStage.RESOURCE_BOUND,
                allowedFrom = setOf(ConnectionLifecycleStage.STREAM_OPENED),
                reason = "收到资源绑定结果",
            )
            return
        }
        throw TakinaConnectionException("resource binding timeout for account $boundJid")
    }

    private fun handleIncomingFrame(frame: String) {
        when (XmppProtocol.rootName(frame)) {
            "iq" -> {
                completePendingIqIfMatched(frame)
                onInboundStanza(this, frame)
            }

            "message", "presence" -> onInboundStanza(this, frame)
            "stream" -> Unit
            else -> Unit
        }
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
        LogUtils.warn(TAG, "连接异常关闭", boundJid, error.message ?: "未知错误")
        failAllPendingIqRequests(TakinaConnectionException("connection frame pump failed", error))
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
