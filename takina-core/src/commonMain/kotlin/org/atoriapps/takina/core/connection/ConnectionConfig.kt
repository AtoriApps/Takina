package org.atoriapps.takina.core.connection

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
    val saslMechanisms: List<String> = listOf("SCRAM-SHA-256", "SCRAM-SHA-1", "DIGEST-MD5", "PLAIN"),
    val connectTimeoutMillis: Int = 10_000,
    val trustAllCertificates: Boolean = false,
)
