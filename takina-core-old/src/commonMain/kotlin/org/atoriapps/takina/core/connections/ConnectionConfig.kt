package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.xmpp.BareJid

enum class SecurityMode {
    DIRECT_TLS,
    START_TLS,
    PLAIN,
}

val SecurityMode.defaultPort: Int
    get() = when (this) {
        SecurityMode.DIRECT_TLS -> 5223
        SecurityMode.START_TLS -> 5222
        SecurityMode.PLAIN -> 5222
    }

data class ConnectionConfig(
    val jid: BareJid,
    val host: String = jid.domain,
    val securityMode: SecurityMode = SecurityMode.START_TLS,
    val port: Int = securityMode.defaultPort,
    val resource: String = "takina",
    val connectTimeoutMillis: Int = 10_000,
    val streamLanguage: String = "en",
) {
    init {
        require(host.isNotBlank()) { "host cannot be blank" }
        require(port in 1..65535) { "port must be in 1..65535" }
        require(resource.isNotBlank()) { "resource cannot be blank" }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be > 0" }
    }
}
