package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.xmpp.Jid

data class ConnectionConfig(
    val jid: Jid,
    val password: String,
    // 接下来的内容和连接、认证、安全相关
    val host: String,
    val port: Int,
    val connectMethod: String,
) {
}

