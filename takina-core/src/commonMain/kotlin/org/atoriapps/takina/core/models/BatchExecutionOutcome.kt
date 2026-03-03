package org.atoriapps.takina.core.models

data class BatchExecutionOutcome(
    val succeed: Int,
    val failed: Int,
    val total: Int = succeed + failed,
)
