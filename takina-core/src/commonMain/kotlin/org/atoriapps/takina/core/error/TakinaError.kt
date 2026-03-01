package org.atoriapps.takina.core.error

// TODO、CHECK：何意味这个错误系统，感觉也怪怪的

enum class ErrorDomain {
    TRANSPORT,
    TLS,
    STREAM,
    AUTH,
    BIND,
    SM,
    TIMEOUT,
    CANCELLED,
    FEATURE,
    PIPELINE,
    STORE,
    CONFIG,
    INTERNAL,
}

data class TakinaError(
    val code: String,
    val domain: ErrorDomain,
    val message: String,
    val retryable: Boolean,
    val cause: Throwable? = null,
) {
    init {
        require(code.matches(Regex("""TAKINA-[A-Z]+-\d{3}"""))) { "Error code must match TAKINA-<DOMAIN>-<NNN>, was: $code" }
    }
}

object TakinaErrors {
    fun of(
        domain: ErrorDomain,
        number: Int,
        message: String,
        retryable: Boolean,
        cause: Throwable? = null,
    ): TakinaError = TakinaError(
        code = "TAKINA-${domain.name}-${number.toString().padStart(3, '0')}",
        domain = domain,
        message = message,
        retryable = retryable,
        cause = cause,
    )
}
