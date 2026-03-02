package org.atoriapps.takina.core.connections

object ConnectionConfigPaths {
    const val HOST: String = "connection.host"
    const val PORT: String = "connection.port"
    const val SECURITY_MODE: String = "connection.securityMode"
    const val RESOURCE: String = "connection.resource"
    const val AUTH_SASL: String = "connection.auth.sasl"
    const val TIMEOUT_HANDSHAKE: String = "connection.timeout.handshake"

    val all: Set<String> = setOf(HOST, PORT, SECURITY_MODE, RESOURCE, AUTH_SASL, TIMEOUT_HANDSHAKE)

    fun isConnectionPath(path: String): Boolean = path in all
}

object ReconnectConfigPaths {
    const val ENABLED: String = "reconnect.enabled"
    const val DELAY: String = "reconnect.delay"
    const val FACTOR: String = "reconnect.factor"
    const val JITTER: String = "reconnect.jitter"
    const val MAX_ATTEMPTS: String = "reconnect.maxAttempts"
}

object ConnectionDefaults {
    const val RESOURCE: String = "takina"
    val SASL_MECHANISMS: List<String> = listOf("SCRAM-SHA-256", "SCRAM-SHA-1", "DIGEST-MD5", "PLAIN")
    const val CONNECT_TIMEOUT_MILLIS: Int = 10_000
    const val TRUST_ALL_CERTIFICATES: Boolean = false
    val SECURITY_MODE: SecurityMode = SecurityMode.START_TLS
}

object ReconnectDefaults {
    const val ENABLED: Boolean = true
    const val DELAY_MILLIS: Long = 1_000L
    const val FACTOR: Double = 2.0
    const val JITTER: Double = 0.0
    const val MAX_ATTEMPTS: Int = 5
}
