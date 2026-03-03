package org.atoriapps.takina.core.controlling

import org.atoriapps.takina.core.connections.SecurityMode

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

object ObservabilityConfigPaths {
    const val SAMPLING: String = "observability.sampling"
    const val ALERT_THRESHOLD: String = "observability.alertThreshold"
}