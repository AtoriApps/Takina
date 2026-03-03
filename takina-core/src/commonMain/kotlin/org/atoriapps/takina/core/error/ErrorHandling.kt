package org.atoriapps.takina.core.error

import kotlin.coroutines.cancellation.CancellationException

class TakinaFailureException(
    val error: TakinaError,
) : RuntimeException(error.message, error.cause)

fun Throwable.toTakinaError(
    domain: ErrorDomain,
    number: Int,
    retryable: Boolean,
    fallbackMessage: String = this.message ?: "Unexpected error",
): TakinaError {
    if (this is CancellationException) throw this
    return when (this) {
        is TakinaFailureException -> this.error // TIPS：直接解包

        else -> TakinaErrors.of(
            domain = domain,
            number = number,
            message = fallbackMessage,
            retryable = retryable,
            cause = this,
        )
    }
}