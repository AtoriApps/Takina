package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.models.BareJid

enum class SecurityMode {
    DIRECT_TLS,
    START_TLS,
    PLAIN;

    val defaultPort: Int
        get() = when (this) {
            DIRECT_TLS -> ConnectionDefaults.PORT_DIRECT_TLS

            START_TLS , PLAIN -> ConnectionDefaults.PORT_START_TLS_AND_PLAIN
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


object ConnectionDefaults {
    const val RESOURCE: String = "takina"

    val SASL_MECHANISMS: List<String> = platformDefaultSaslMechanisms()

    const val CONNECT_TIMEOUT_MILLIS: Int = 10_000
    const val TRUST_ALL_CERTIFICATES: Boolean = false
    val SECURITY_MODE: SecurityMode = SecurityMode.START_TLS
    const val PORT_START_TLS_AND_PLAIN = 5222
    const val PORT_DIRECT_TLS = 5223
}

internal expect fun platformDefaultSaslMechanisms(): List<String>