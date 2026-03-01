package org.atoriapps.takina.core.exceptions

open class TakinaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class JidFormatException(message: String) : TakinaException(message)

enum class ConnectionFailureKind {
    INVALID_CREDENTIALS,
    AUTH_MECHANISM_UNSUPPORTED,
    STARTTLS_UNSUPPORTED,
    TLS_NEGOTIATION_FAILED,
    RESOURCE_BIND_FAILED,
    NETWORK_TIMEOUT,
    DNS_RESOLUTION_FAILED,
    CONNECTION_CLOSED,
    NETWORK_UNREACHABLE,
    SERVER_REJECTED,
    UNKNOWN,
}

class TakinaConnectionException(
    message: String,
    cause: Throwable? = null,
    val kind: ConnectionFailureKind = ConnectionFailureKind.UNKNOWN,
    val detail: String? = null,
) : TakinaException(message, cause)

class AccountNotFoundException(message: String) : TakinaException(message)

class AmbiguousAccountException(message: String) : TakinaException(message)

class InvalidRequestException(message: String) : TakinaException(message)

class NotConnectedException(message: String) : TakinaException(message)
