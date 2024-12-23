package org.atoriapps.takina.core

import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.events.AbstractEventBus
import org.atoriapps.takina.core.events.AllConnectedEvent
import org.atoriapps.takina.core.events.EventBus
import org.atoriapps.takina.core.utils.LanguageUtils.findOrAdd
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.Jid

actual class Takina actual constructor(cfg: TakinaConfiguration) : AbstractTakina(cfg) {
    companion object {
        private const val TAG = "Takina.JVM"
    }

    override val events: AbstractEventBus = EventBus()

    init {
        // 再执行实现类的构造函数
        LogUtils.debug(TAG, "开始初始化 Takina")

        LogUtils.debug(TAG, "初始化 Takina完成")
    }

    override fun connectAllAsync() {
        TODO("Not yet implemented")
    }

    override fun disconnectAllAsync() {
        TODO("Not yet implemented")
    }

    // TODO：对了，另外要不要保证这些方法的线程安全性？

    override fun connectAll() {
        LogUtils.debug(TAG, "开始连接所有未连接的账号")

        config.accountConfigurations.filter {
            // TODO：筛除已连接的账号
            true
        }.forEach { connect(it) }

        events.emit(AllConnectedEvent())
    }

    private fun connect(accountConfig: TakinaConfiguration.AccountConfiguration) {
        LogUtils.debug(TAG, "连接账号", accountConfig.jid!!)
        accountConnections.findOrAdd({
            it.boundJid == accountConfig.jid && it.state == TakinaConnection.ConnectionState.DISCONNECTED
        }) {
            LogUtils.debug(TAG, "连接不存在，创建新连接", accountConfig.jid!!)
            TakinaConnection(accountConfig)
        }.connect()
    }

    fun connect(jid: Jid) {
        config.accountConfigurations.find { it.jid == jid }?.let { accountConfig ->
            LogUtils.debug(TAG, "存在账号，尝试连接", jid)
            connect(accountConfig)
        }
    }

    override fun disconnectAll() {
        LogUtils.debug(TAG, "开始断开所有账号")
        // TODO：断开所有账号
    }
}