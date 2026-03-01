package org.atoriapps.takina.core.models

import org.atoriapps.takina.core.error.TakinaError
import kotlin.time.Duration

sealed interface TakinaResult<out T> {
    data class Ok<T>(
        val value: T,
        val meta: ResultMeta = ResultMeta(),
    ) : TakinaResult<T>

    data class Err(
        val error: TakinaError,
        val meta: ResultMeta = ResultMeta(),
    ) : TakinaResult<Nothing>
}

data class ResultMeta(
    val correlationId: String? = null, // 关联Id
    val retryCount: Int = 0,
    val elapsed: Duration? = null
)
