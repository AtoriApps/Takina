package org.atoriapps.takina.core.exceptions

open class TakinaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class JidFormatException(message: String) : TakinaException(message)

class TakinaConnectionException(message: String, cause: Throwable? = null) : TakinaException(message, cause)

class AccountNotFoundException(message: String) : TakinaException(message)

class AmbiguousAccountException(message: String) : TakinaException(message)

class InvalidRequestException(message: String) : TakinaException(message)

class NotConnectedException(message: String) : TakinaException(message)
