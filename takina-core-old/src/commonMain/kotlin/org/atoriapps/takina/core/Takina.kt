@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.TakinaComponent
import org.atoriapps.takina.core.components.TakinaComponentProvider
import org.atoriapps.takina.core.components.TakinaConnectionLifecycleComponent
import org.atoriapps.takina.core.components.TakinaFrameInterceptAction
import org.atoriapps.takina.core.components.TakinaInboundFrameInterceptor
import org.atoriapps.takina.core.components.TakinaInboundStanzaInterceptor
import org.atoriapps.takina.core.components.TakinaOutboundFrameInterceptor
import org.atoriapps.takina.core.components.TakinaOutboundFrameObserver
import org.atoriapps.takina.core.components.TakinaPreBindNegotiationComponent
import org.atoriapps.takina.core.connections.IqResult
import org.atoriapps.takina.core.connections.PreBindNegotiationResult
import org.atoriapps.takina.core.connections.PreBindNegotiationTransport
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.connections.ConnectionLifecycleStage
import org.atoriapps.takina.core.events.AllConnectedEvent
import org.atoriapps.takina.core.events.AllDisconnectedEvent
import org.atoriapps.takina.core.events.ConnectionClosedEvent
import org.atoriapps.takina.core.events.ConnectionConnectedEvent
import org.atoriapps.takina.core.events.ConnectionFailedEvent
import org.atoriapps.takina.core.events.ConnectionStageChangedEvent
import org.atoriapps.takina.core.events.FrameInboundEvent
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.events.FrameOutboundEvent
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
        private const val TAG = "Takina"
    }

    final override val events: TakinaEventBus = TakinaEventBus(this)
    final override val request: TakinaRequest = TakinaRequest(this)

    private val components = mutableListOf<TakinaComponent>()
    private val componentsByType = linkedMapOf<KClass<out TakinaComponent>, TakinaComponent>()
    private val accountRuntime = AccountRuntime()
    private val orderedComponents: List<TakinaComponent> get() = components.sortedByDescending { it.priority }
    private var isShutdown: Boolean = false

    final override val configuredAccounts: List<BareJid> get() = accountRuntime.configuredAccounts

    final override val activeConnections: List<TakinaConnection> get() = accountRuntime.activeConnections

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
            val bareJid = account.jid?.bareJid ?: error("未提供账号Jid")
            accountRuntime.addAccountDefinition(bareJid, account)
        }

        orderedComponents.forEach { component ->
            runCatching { component.onInstall(this) }.onFailure { error -> LogUtils.error(TAG, "组件安装回调异常", component::class.clzName, error.message ?: "未知错误") }
        }
    }

    open fun connectAll() {
        ensureNotShutdown()
        LogUtils.info(TAG, "开始连接全部账号", "总数=${accountRuntime.accountCount}")

        var connected = 0
        configuredAccounts.forEach { jid ->
            runCatching { if (connectInternal(jid)) connected += 1 }.onFailure { error ->
                LogUtils.error(TAG, "账号连接失败", jid, error.message ?: "未知错误")
            }
        }

        LogUtils.info(TAG, "全部账号连接流程结束", "成功=$connected", "总数=${accountRuntime.accountCount}")
        events.emit(AllConnectedEvent(connectedCount = connected, configuredCount = accountRuntime.accountCount))
    }

    open fun disconnectAll() {
        val connections = accountRuntime.activeConnections
        LogUtils.info(TAG, "开始断开全部账号连接", "连接数=${connections.size}")

        connections.forEach { connection ->
            dispatchBeforeDisconnect(connection.boundJid)
            synchronized(connection) { if (connection.state != TakinaConnection.ConnectionState.DISCONNECTED) connection.disconnect() }
            dispatchAfterDisconnected(connection.boundJid, reason = null)
        }

        LogUtils.info(TAG, "全部账号已断开", "连接数=${connections.size}")
        events.emit(AllDisconnectedEvent(connectionCount = connections.size))
    }

    open fun shutdown() {
        synchronized(this) {
            if (isShutdown) return
            isShutdown = true
        }

        disconnectAll()
        shutdownInstalledComponents()

        events.shutdown()
    }

    open fun connect(jid: Jid) {
        ensureNotShutdown()
        connectInternal(jid.bareJid)
    }

    open fun disconnect(jid: Jid) {
        dispatchBeforeDisconnect(jid.bareJid)

        accountRuntime.findConnection(jid.bareJid)?.let { connection ->
            synchronized(connection) { connection.disconnect() }
        }

        dispatchAfterDisconnected(jid.bareJid, reason = null)
    }

    internal fun sendMessage(stanza: MessageStanza) {
        ensureNotShutdown()
        sendStanza(stanza.from, stanza)
    }

    internal fun sendStanza(stanza: XmppStanza) {
        ensureNotShutdown()

        val from = when (stanza) {
            is MessageStanza -> stanza.from
            is PresenceStanza -> stanza.from
            is IqStanza -> stanza.from
        }

        sendStanza(from, stanza)
    }

    internal fun sendStanza(from: Jid?, stanza: XmppStanza) {
        val connection = resolveConnectedConnectionForOutbound(from?.bareJid)
        sendRawWithInterceptors(connection, stanza.toXml())
    }

    // HACK：Iq没走拦截
    internal suspend fun sendIqAndAwaitResult(
        from: Jid?,
        stanza: IqStanza,
        timeoutMillis: Long,
    ): IqResult {
        ensureNotShutdown()
        val connection = resolveConnectedConnectionForOutbound(from?.bareJid)
        return connection.sendIqAndAwaitResult(stanza, timeoutMillis)
    }

    internal fun sendRaw(from: Jid?, xml: String) {
        ensureNotShutdown()
        val connection = resolveConnectedConnectionForOutbound(from?.bareJid)
        sendRawWithInterceptors(connection, xml)
    }

    private fun connectInternal(jid: BareJid): Boolean {
        val account = accountRuntime.requireAccountDefinition(jid)

        val connection = accountRuntime.getOrCreateConnection(jid) {
            TakinaConnection(
                config = account.resolveConnectionConfig(),
                passwordProvider = account.requirePasswordProvider(),
                onInboundStanza = { connection, xml ->
                    handleInboundStanza(connection, xml)
                },
                onInboundFrame = { connection, xml ->
                    handleInboundFrame(connection, xml)
                },
                onOutboundFrame = { connection, xml ->
                    handleOutboundFrame(connection, xml)
                },
                onConnectionClosed = { connection, reason ->
                    handleConnectionClosed(connection, reason)
                },
                onLifecycleStageChanged = { connection, oldStage, newStage ->
                    handleConnectionStageChanged(connection, oldStage, newStage)
                },
                onTryPreBindNegotiation = { connection, featuresXml, transport ->
                    dispatchTryPreBindNegotiation(connection, featuresXml, transport)
                },
            )
        }

        return synchronized(connection) {
            if (connection.state == TakinaConnection.ConnectionState.CONNECTED) return@synchronized false // 不应该true吗？已连接

            try {
                dispatchBeforeConnect(jid)
                LogUtils.debug(TAG, "开始连接账号", jid)
                connection.connect()
                dispatchAfterConnected(connection)
                LogUtils.info(TAG, "账号连接成功", jid)
                events.emit(ConnectionConnectedEvent(jid))
                true
            } catch (t: Throwable) {
                val reason = t.message ?: "未知连接错误"
                dispatchAfterConnectFailed(jid, reason)
                LogUtils.error(TAG, "账号连接异常", jid, reason)
                events.emit(ConnectionFailedEvent(jid, reason))
                throw t
            }
        }
    }

    private fun resolveConnectedConnectionForOutbound(from: BareJid?): TakinaConnection {
        if (from != null) return ensureConnectedConnection(from)

        val connected = accountRuntime.connectedConnections
        if (connected.size == 1) return connected.first()

        if (configuredAccounts.size == 1) {
            val single = configuredAccounts.first()
            return ensureConnectedConnection(single)
        }

        throw AmbiguousAccountException("由于添加了多个账号，请显式指定发送方账号（From）")
    }

    private fun ensureConnectedConnection(jid: BareJid): TakinaConnection {
        val current = accountRuntime.findConnection(jid) ?: return connectAndGet(jid)

        if (current.state == TakinaConnection.ConnectionState.CONNECTED) return current

        connectInternal(jid)
        val connected = accountRuntime.findConnection(jid) ?: throw AccountNotFoundException("重连接后找不到账号 $jid")

        if (connected.state != TakinaConnection.ConnectionState.CONNECTED) throw NotConnectedException("账号 $jid 未连接")
        return connected
    }

    private fun connectAndGet(jid: BareJid): TakinaConnection {
        connectInternal(jid)
        return accountRuntime.findConnection(jid) ?: throw AccountNotFoundException("连接后找不到账号 $jid")
    }

    private fun handleInboundStanza(connection: TakinaConnection, xml: String) {
        val stanzaType = org.atoriapps.takina.core.connections.XmppProtocol.rootName(xml)
        val intercepted = applyInboundStanzaInterceptors(connection, stanzaType, xml) ?: return
        events.emit(StanzaReceivedEvent(connection.boundJid, intercepted.first, intercepted.second))
    }

    private fun handleInboundFrame(connection: TakinaConnection, xml: String) {
        events.emit(FrameInboundEvent(connection.boundJid, xml))
        applyInboundFrameInterceptors(connection, xml)
    }

    private fun handleOutboundFrame(connection: TakinaConnection, xml: String) {
        dispatchOutboundFrameSent(connection, xml)
        events.emit(FrameOutboundEvent(connection.boundJid, xml))
    }

    private fun handleConnectionClosed(connection: TakinaConnection, reason: String) {
        LogUtils.warn(TAG, "连接已关闭", connection.boundJid, reason)
        events.emit(ConnectionClosedEvent(connection.boundJid, reason))
        dispatchAfterDisconnected(connection.boundJid, reason)
    }

    private fun handleConnectionStageChanged(
        connection: TakinaConnection,
        oldStage: ConnectionLifecycleStage,
        newStage: ConnectionLifecycleStage,
    ) {
        LogUtils.debug(TAG, "连接阶段变化", connection.boundJid, "${oldStage.name} -> ${newStage.name}")
        dispatchConnectionStageChanged(connection.boundJid, oldStage, newStage)
        events.emit(ConnectionStageChangedEvent(connection.boundJid, oldStage, newStage))
    }

    private fun ensureNotShutdown() {
        if (isShutdown) throw IllegalStateException("Takina 已休止，不能继续执行该操作")
    }

    // TODO：未来可能要提取拦截为Runtime

    private fun sendRawWithInterceptors(connection: TakinaConnection, xml: String) {
        val finalXml = applyOutboundFrameInterceptors(connection, xml) ?: return
        connection.sendRaw(finalXml)
    }

    private fun applyOutboundFrameInterceptors(connection: TakinaConnection, xml: String): String? {
        var current = xml
        for (component in orderedComponents) {
            val interceptor = component as? TakinaOutboundFrameInterceptor ?: continue

            val result = runCatching { interceptor.interceptOutboundFrame(connection, current, this) }
                .onFailure { error -> LogUtils.error(TAG, "出站拦截器异常", component::class.clzName, error.message ?: "未知错误") }
                .getOrNull() ?: continue

            if (result.action == TakinaFrameInterceptAction.DROP) {
                LogUtils.warn(TAG, "出站帧被拦截器丢弃", connection.boundJid, component::class.clzName)
                return null
            }

            current = result.xml
        }
        return current
    }

    // TIPS：入站有帧拦截器和节拦截器。而出站因为节是我方构造的，所以无节拦截器，只有帧拦截器

    private fun applyInboundFrameInterceptors(connection: TakinaConnection, xml: String): String? {
        var current = xml
        for (component in orderedComponents) {
            val interceptor = component as? TakinaInboundFrameInterceptor ?: continue

            val result = runCatching { interceptor.interceptInboundFrame(connection, current, this) }
                .onFailure { error -> LogUtils.error(TAG, "入站帧拦截器异常", component::class.clzName, error.message ?: "未知错误") }
                .getOrNull() ?: continue

            if (result.action == TakinaFrameInterceptAction.DROP) {
                LogUtils.warn(TAG, "入站帧被拦截器丢弃", connection.boundJid, component::class.clzName)
                return null
            }

            current = result.xml
        }
        return current
    }

    private fun applyInboundStanzaInterceptors(connection: TakinaConnection, stanzaType: String, xml: String): Pair<String, String>? {
        var currentType = stanzaType
        var currentXml = xml
        for (component in orderedComponents) {
            val interceptor = component as? TakinaInboundStanzaInterceptor ?: continue

            val result = runCatching { interceptor.interceptInboundStanza(connection, currentType, currentXml, this) }
                .onFailure { error -> LogUtils.error(TAG, "入站 stanza 拦截器异常", component::class.clzName, error.message ?: "未知错误") }
                .getOrNull() ?: continue

            if (result.action == TakinaFrameInterceptAction.DROP) {
                LogUtils.warn(TAG, "入站 stanza 被拦截器丢弃", connection.boundJid, component::class.clzName)
                return null
            }

            currentType = result.stanzaType
            currentXml = result.xml
        }
        return currentType to currentXml
    }

    private fun dispatchOutboundFrameSent(connection: TakinaConnection, xml: String) {
        for (component in orderedComponents) {
            val observer = component as? TakinaOutboundFrameObserver ?: continue
            runCatching { observer.onOutboundFrameSent(connection, xml, this) }.onFailure { error -> LogUtils.error(TAG, "出站发送后回调异常", component::class.clzName, error.message ?: "未知错误") }
        }
    }

    private fun dispatchBeforeConnect(jid: BareJid) {
        for (component in orderedComponents) {
            val lifecycle = component as? TakinaConnectionLifecycleComponent ?: continue
            runCatching { lifecycle.onBeforeConnect(jid, this) }.onFailure { error ->
                LogUtils.error(TAG, "组件连接前回调异常", component::class.clzName, error.message ?: "未知错误")
            }
        }
    }

    private fun dispatchAfterConnected(connection: TakinaConnection) {
        for (component in orderedComponents) {
            val lifecycle = component as? TakinaConnectionLifecycleComponent ?: continue
            runCatching { lifecycle.onAfterConnected(connection, this) }.onFailure { error ->
                LogUtils.error(TAG, "组件连接后回调异常", component::class.clzName, error.message ?: "未知错误")
            }
        }
    }

    private fun dispatchAfterConnectFailed(jid: BareJid, reason: String) {
        for (component in orderedComponents) {
            val lifecycle = component as? TakinaConnectionLifecycleComponent ?: continue
            runCatching { lifecycle.onAfterConnectFailed(jid, reason, this) }.onFailure { error ->
                LogUtils.error(TAG, "组件连接失败回调异常", component::class.clzName, error.message ?: "未知错误")
            }
        }
    }

    private fun dispatchBeforeDisconnect(jid: BareJid) {
        for (component in orderedComponents) {
            val lifecycle = component as? TakinaConnectionLifecycleComponent ?: continue
            runCatching { lifecycle.onBeforeDisconnect(jid, this) }.onFailure { error ->
                LogUtils.error(TAG, "组件断开前回调异常", component::class.clzName, error.message ?: "未知错误")
            }
        }
    }

    private fun dispatchAfterDisconnected(jid: BareJid, reason: String?) {
        for (component in orderedComponents) {
            val lifecycle = component as? TakinaConnectionLifecycleComponent ?: continue
            runCatching { lifecycle.onAfterDisconnected(jid, reason, this) }.onFailure { error ->
                LogUtils.error(TAG, "组件断开后回调异常", component::class.clzName, error.message ?: "未知错误")
            }
        }
    }

    private fun dispatchConnectionStageChanged(jid: BareJid, oldStage: ConnectionLifecycleStage, newStage: ConnectionLifecycleStage) {
        for (component in orderedComponents) {
            val lifecycle = component as? TakinaConnectionLifecycleComponent ?: continue
            runCatching { lifecycle.onConnectionStageChanged(jid, oldStage, newStage, this) }.onFailure { error ->
                LogUtils.error(TAG, "组件阶段变化回调异常", component::class.clzName, error.message ?: "未知错误")
            }
        }
    }

    private fun dispatchTryPreBindNegotiation(
        connection: TakinaConnection,
        featuresXml: String,
        transport: PreBindNegotiationTransport,
    ): PreBindNegotiationResult {
        for (component in orderedComponents) {
            val negotiation = component as? TakinaPreBindNegotiationComponent ?: continue
            val result = runCatching { negotiation.tryPreBindNegotiation(connection, featuresXml, transport, this) }
                .onFailure { error -> LogUtils.error(TAG, "组件预绑定协商回调异常", component::class.clzName, error.message ?: "未知错误") }
                .getOrNull() ?: continue
            if (result != PreBindNegotiationResult.SKIPPED) return result
        }
        return PreBindNegotiationResult.SKIPPED
    }

    private fun shutdownInstalledComponents() {
        for (component in orderedComponents) {
            runCatching { component.onShutdown(this) }.onFailure { error ->
                LogUtils.error(TAG, "组件 shutdown 回调异常", component::class.clzName, error.message ?: "未知错误")
            }
        }
    }

    private inner class AccountRuntime {
        private val lock = Any()
        private val accountDefinitions = linkedMapOf<BareJid, TakinaConfiguration.AccountConfiguration>()
        private val accountConnections = linkedMapOf<BareJid, TakinaConnection>()

        val configuredAccounts: List<BareJid> get() = synchronized(lock) { accountDefinitions.keys.toList() }
        val activeConnections: List<TakinaConnection> get() = synchronized(lock) { accountConnections.values.toList() }
        val connectedConnections: List<TakinaConnection> get() = synchronized(lock) { accountConnections.values.filter { it.state == TakinaConnection.ConnectionState.CONNECTED } }
        val accountCount: Int get() = synchronized(lock) { accountDefinitions.size }

        fun addAccountDefinition(jid: BareJid, config: TakinaConfiguration.AccountConfiguration) = synchronized(lock) { accountDefinitions[jid] = config }

        fun requireAccountDefinition(jid: BareJid): TakinaConfiguration.AccountConfiguration = synchronized(lock) { accountDefinitions[jid] } ?: throw AccountNotFoundException("account $jid not found")

        fun getOrCreateConnection(jid: BareJid, factory: () -> TakinaConnection): TakinaConnection = synchronized(lock) {
            accountConnections[jid] ?: factory().also { accountConnections[jid] = it }
        }

        fun findConnection(jid: BareJid): TakinaConnection? = synchronized(lock) { accountConnections[jid] }
    }
}

expect class Takina(cfg: TakinaConfiguration) : AbstractTakina

interface TakinaContext {
    val events: TakinaEventBus
    val request: TakinaRequest
    val configuredAccounts: List<BareJid>
    val activeConnections: List<TakinaConnection>

    fun <COMPONENT : TakinaComponent> findComponent(type: KClass<COMPONENT>): COMPONENT?

    fun <COMPONENT : TakinaComponent> requireComponent(type: KClass<COMPONENT>): COMPONENT = findComponent(type) ?: throw IllegalStateException("组件 ${type.clzName} 未加载")

    fun <COMPONENT : TakinaComponent> findComponent(provider: TakinaComponentProvider<COMPONENT>): COMPONENT? = findComponent(provider.getComponentType())

    fun <COMPONENT : TakinaComponent> requireComponent(provider: TakinaComponentProvider<COMPONENT>): COMPONENT = requireComponent(provider.getComponentType())
}

inline fun <reified COMPONENT : TakinaComponent> TakinaContext.findComponent(): COMPONENT? = findComponent(COMPONENT::class)

inline fun <reified COMPONENT : TakinaComponent> TakinaContext.requireComponent(): COMPONENT = requireComponent(COMPONENT::class)

@DslMarker
annotation class TakinaConfigDsl
