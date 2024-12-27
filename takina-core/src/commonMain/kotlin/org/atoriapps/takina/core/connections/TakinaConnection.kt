package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.TakinaConfiguration
import org.atoriapps.takina.core.utils.LogUtils

class TakinaConnection(accountConfig: TakinaConfiguration.AccountConfiguration) {
    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        DISCONNECTING
    }

    val boundJid = accountConfig.jid!!

    var state: ConnectionState = ConnectionState.DISCONNECTED

    init {
        // TODO：初始化
        LogUtils.debug("TakinaConnection", "初始化连接", boundJid)
    }

    fun connect() {
        state = ConnectionState.CONNECTING

        LogUtils.debug("TakinaConnection", "连接", boundJid)

        Thread.sleep(5000)

        // TODO：连接
    }
}

abstract class AbstractConnector {}

expect class Connector : AbstractConnector {

}