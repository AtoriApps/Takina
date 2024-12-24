package org.atoriapps.takina.core

import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.events.恩情Event
import org.atoriapps.takina.core.utils.LanguageUtils.findOrAdd
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xmpp.Jid

actual class Takina actual constructor(cfg: TakinaConfiguration) : AbstractTakina(cfg) {
    companion object {
        private const val TAG = "Takina.JVM"
    }

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

        // TODO：不应该再直接使用 Config 里面的账号
        config.accountConfigurations.filter { accountConfig ->
            // 找到绑定到当前 accountConfig 的连接
            val connectedJids = accountConnections.filter { it.boundJid == accountConfig.jid }

            // 条件：没有任何连接，或者连接未连接
            connectedJids.isEmpty() || connectedJids.any { it.state == TakinaConnection.ConnectionState.DISCONNECTED }
        }.forEach { connect(it) }

        events.emit(恩情Event())
    }

    @Deprecated("不应该再直接使用 Config 类型的账号")
    private fun connect(accountConfig: TakinaConfiguration.AccountConfiguration) {
        LogUtils.debug(TAG, "连接账号", accountConfig.jid!!)
        accountConnections.findOrAdd({
            it.boundJid == accountConfig.jid && it.state == TakinaConnection.ConnectionState.DISCONNECTED
        }) {
            LogUtils.debug(TAG, "连接不存在，创建新连接", accountConfig.jid!!)
            TakinaConnection(accountConfig)
        }.connect()
    }

    override fun connect(jid: Jid) {
        // TODO：不应该再直接使用 Config 里面的账号
        config.accountConfigurations.find { it.jid == jid }?.let { accountConfig ->
            LogUtils.debug(TAG, "存在账号，尝试连接", jid)
            connect(accountConfig)
        }
    }

    override fun disconnectAll() {
        LogUtils.debug(TAG, "开始断开所有账号")
        // TODO：断开所有账号

        events.emit(恩情Event())
    }
}