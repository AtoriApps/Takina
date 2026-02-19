@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.TakinaComponent
import org.atoriapps.takina.core.components.TakinaComponentProvider
import org.atoriapps.takina.core.connections.IqResult
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.events.AllConnectedEvent
import org.atoriapps.takina.core.events.AllDisconnectedEvent
import org.atoriapps.takina.core.events.ConnectionClosedEvent
import org.atoriapps.takina.core.events.ConnectionConnectedEvent
import org.atoriapps.takina.core.events.ConnectionFailedEvent
import org.atoriapps.takina.core.events.ConnectionStageChangedEvent
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.events.TakinaEventBus
import org.atoriapps.takina.core.exceptions.AccountNotFoundException
import org.atoriapps.takina.core.exceptions.AmbiguousAccountException
import org.atoriapps.takina.core.exceptions.NotConnectedException
import org.atoriapps.takina.core.requests.TakinaRequest
import org.atoriapps.takina.core.utils.LanguageUtils.clzName
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.MessageStanza
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza
import org.atoriapps.takina.core.xmpp.stanzas.XmppStanza
import kotlin.reflect.KClass

abstract class AbstractTakina(val config: TakinaConfiguration) : TakinaContext {
    companion object {
        private const val TAG = "AbstractTakina"
    }

    final override val events: TakinaEventBus = TakinaEventBus(this)
    final override val request: TakinaRequest = TakinaRequest(this)

    private val components = mutableListOf<TakinaComponent>()
    private val componentsByType = linkedMapOf<KClass<out TakinaComponent>, TakinaComponent>()
    private val accountDefinitions = linkedMapOf<BareJid, TakinaConfiguration.AccountConfiguration>()
    private val accountConnections = linkedMapOf<BareJid, TakinaConnection>()

    final override val configuredAccounts: List<BareJid> get() = accountDefinitions.keys.toList()

    final override val activeConnections: List<TakinaConnection> get() = accountConnections.values.toList()

    @Suppress("UNCHECKED_CAST")
    final override fun <COMPONENT : TakinaComponent> findComponent(type: KClass<COMPONENT>): COMPONENT? = componentsByType[type] as? COMPONENT

    init {
        LogUtils.debug(TAG, "初始化 Takina 核心")

        config.componentConfigurations.forEach { cfg ->
            val clzName = cfg.clz.clzName
            LogUtils.debug(TAG, "安装组件", clzName)
            val component = cfg.config.provider.getInstance(this)
            cfg.config.provider.configure(this, component)
            cfg.config.configurers.forEach { configurer -> configurer(component, this) }
            components += component
            componentsByType[cfg.clz] = component
        }

        config.accountConfigurations.forEach { account ->
            val bareJid = account.jid?.bareJid ?: error("account jid cannot be null")
            accountDefinitions[bareJid] = account
        }
    }

    open fun connectAll() {
        LogUtils.info(TAG, "开始连接全部账号", "总数=${accountDefinitions.size}")
        var connected = 0
        accountDefinitions.keys.forEach { jid ->
            runCatching {
                if (connectInternal(jid)) connected += 1
            }.onFailure { error ->
                LogUtils.error(TAG, "账号连接失败", jid, error.message ?: "未知错误")
            }
        }
        LogUtils.info(TAG, "全部账号连接流程结束", "成功=$connected", "总数=${accountDefinitions.size}")
        events.emit(AllConnectedEvent(connectedCount = connected, configuredCount = accountDefinitions.size))
    }

    open fun disconnectAll() {
        LogUtils.info(TAG, "开始断开全部账号连接", "连接数=${accountConnections.size}")

        accountConnections.values.forEach { connection ->
            if (connection.state != TakinaConnection.ConnectionState.DISCONNECTED) connection.disconnect()
        }

        LogUtils.info(TAG, "全部账号已断开", "连接数=${accountConnections.size}")
        events.emit(AllDisconnectedEvent(connectionCount = accountConnections.size))
    }

    open fun connect(jid: Jid) {
        connectInternal(jid.bareJid)
    }

    open fun disconnect(jid: Jid) {
        accountConnections[jid.bareJid]?.disconnect()
    }

    internal fun sendMessage(stanza: MessageStanza) {
        sendStanza(stanza.from, stanza)
    }

    internal fun sendStanza(stanza: XmppStanza) {
        val from = when (stanza) {
            is MessageStanza -> stanza.from
            is PresenceStanza -> stanza.from
            is IqStanza -> stanza.from
        }
        sendStanza(from, stanza)
    }

    internal fun sendStanza(from: Jid?, stanza: XmppStanza) {
        val connection = resolveConnectedConnectionForOutbound(from?.bareJid)
        connection.send(stanza)
    }

    internal suspend fun sendIqAndAwaitResult(
        from: Jid?,
        stanza: IqStanza,
        timeoutMillis: Long,
    ): IqResult {
        val connection = resolveConnectedConnectionForOutbound(from?.bareJid)
        return connection.sendIqAndAwaitResult(stanza, timeoutMillis)
    }

    internal fun sendRaw(from: Jid?, xml: String) {
        val connection = resolveConnectedConnectionForOutbound(from?.bareJid)
        connection.sendRaw(xml)
    }

    private fun connectInternal(jid: BareJid): Boolean {
        val account = accountDefinitions[jid] ?: throw AccountNotFoundException("account $jid not found")
        val connection = accountConnections.getOrPut(jid) {
            TakinaConnection(
                config = account.resolveConnectionConfig(),
                passwordProvider = account.requirePasswordProvider(),
                onInboundStanza = { connection, xml ->
                    handleInboundStanza(connection, xml)
                },
                onConnectionClosed = { connection, reason ->
                    handleConnectionClosed(connection, reason)
                },
                onLifecycleStageChanged = { connection, oldStage, newStage ->
                    handleConnectionStageChanged(connection, oldStage, newStage)
                },
            )
        }
        if (connection.state == TakinaConnection.ConnectionState.CONNECTED) return false
        return try {
            LogUtils.debug(TAG, "开始连接账号", jid)
            connection.connect()
            LogUtils.info(TAG, "账号连接成功", jid)
            events.emit(ConnectionConnectedEvent(jid))
            true
        } catch (t: Throwable) {
            val reason = t.message ?: "未知连接错误"
            LogUtils.error(TAG, "账号连接异常", jid, reason)
            events.emit(ConnectionFailedEvent(jid, reason))
            throw t
        }
    }

    private fun resolveConnectedConnectionForOutbound(from: BareJid?): TakinaConnection {
        if (from != null) {
            return ensureConnectedConnection(from)
        }

        val connected = accountConnections.values.filter { it.state == TakinaConnection.ConnectionState.CONNECTED }
        if (connected.size == 1) return connected.first()

        if (configuredAccounts.size == 1) {
            val single = configuredAccounts.first()
            return ensureConnectedConnection(single)
        }

        throw AmbiguousAccountException("multiple accounts configured, message.from is required")
    }

    private fun ensureConnectedConnection(jid: BareJid): TakinaConnection {
        val current = accountConnections[jid]
        if (current == null) return connectAndGet(jid)
        if (current.state == TakinaConnection.ConnectionState.CONNECTED) return current

        connectInternal(jid)
        val connected = accountConnections[jid]
            ?: throw AccountNotFoundException("account $jid not found after reconnect")
        if (connected.state != TakinaConnection.ConnectionState.CONNECTED) {
            throw NotConnectedException("account $jid is not connected")
        }
        return connected
    }

    private fun connectAndGet(jid: BareJid): TakinaConnection {
        connectInternal(jid)
        return accountConnections[jid]
            ?: throw AccountNotFoundException("account $jid not found after connect")
    }

    private fun handleInboundStanza(connection: TakinaConnection, xml: String) {
        val stanzaType = org.atoriapps.takina.core.connections.XmppProtocol.rootName(xml)
        events.emit(StanzaReceivedEvent(connection.boundJid, stanzaType, xml))
    }

    private fun handleConnectionClosed(connection: TakinaConnection, reason: String) {
        LogUtils.warn(TAG, "连接已关闭", connection.boundJid, reason)
        events.emit(ConnectionClosedEvent(connection.boundJid, reason))
    }

    private fun handleConnectionStageChanged(
        connection: TakinaConnection,
        oldStage: org.atoriapps.takina.core.connections.ConnectionLifecycleStage,
        newStage: org.atoriapps.takina.core.connections.ConnectionLifecycleStage,
    ) {
        LogUtils.debug(TAG, "连接阶段变化", connection.boundJid, "${oldStage.name} -> ${newStage.name}")
        events.emit(ConnectionStageChangedEvent(connection.boundJid, oldStage, newStage))
    }
}

expect class Takina(cfg: TakinaConfiguration) : AbstractTakina

interface TakinaContext {
    val events: TakinaEventBus
    val request: TakinaRequest
    val configuredAccounts: List<BareJid>
    val activeConnections: List<TakinaConnection>

    fun <COMPONENT : TakinaComponent> findComponent(type: KClass<COMPONENT>): COMPONENT?

    fun <COMPONENT : TakinaComponent> requireComponent(type: KClass<COMPONENT>): COMPONENT =
        findComponent(type)
            ?: throw IllegalStateException("组件 ${type.clzName} 未加载")

    fun <COMPONENT : TakinaComponent> findComponent(provider: TakinaComponentProvider<COMPONENT>): COMPONENT? =
        findComponent(provider.getComponentType())

    fun <COMPONENT : TakinaComponent> requireComponent(provider: TakinaComponentProvider<COMPONENT>): COMPONENT =
        requireComponent(provider.getComponentType())
}

inline fun <reified COMPONENT : TakinaComponent> TakinaContext.findComponent(): COMPONENT? =
    findComponent(COMPONENT::class)

inline fun <reified COMPONENT : TakinaComponent> TakinaContext.requireComponent(): COMPONENT =
    requireComponent(COMPONENT::class)

@DslMarker
annotation class TakinaConfigDsl
