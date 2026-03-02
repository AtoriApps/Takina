package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.models.BareJid

enum class SecurityMode {
    DIRECT_TLS,
    START_TLS,
    PLAIN;

    val defaultPort: Int
        get() = when (this) {
            DIRECT_TLS -> 5223

            START_TLS -> 5222

            PLAIN -> 5222
        }
}

data class ConnectionConfig(
    val owner: BareJid,
    val host: String,
    val port: Int,
    val securityMode: SecurityMode,
    val resource: String,
    val saslMechanisms: List<String> = ConnectionDefaults.SASL_MECHANISMS,
    val connectTimeoutMillis: Int = ConnectionDefaults.CONNECT_TIMEOUT_MILLIS,
    val trustAllCertificates: Boolean = ConnectionDefaults.TRUST_ALL_CERTIFICATES,
)
