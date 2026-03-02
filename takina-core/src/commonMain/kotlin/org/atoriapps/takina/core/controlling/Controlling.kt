package org.atoriapps.takina.core.controlling

import org.atoriapps.takina.core.connections.ReconnectConfigPaths

enum class ApplyMode {
    IMMEDIATE,
    NEXT_ITEM,
    NEXT_CONNECTION,
    BUILD_TIME_IMMUTABLE,
}


data class ConfigMeta(
    val path: String,
    val applyMode: ApplyMode,
    val mutable: Boolean,
)

data class ConfigChangeResult(
    val path: String,
    val applied: Boolean,
    val applyMode: ApplyMode,
    val rejectedReason: String? = null,
)

// CHECK：这啥，再看看
object CoreConfigMetaCatalog {
    val all: Map<String, ConfigMeta> = listOf(
        ConfigMeta(ReconnectConfigPaths.ENABLED, ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta(ReconnectConfigPaths.DELAY, ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta(ReconnectConfigPaths.FACTOR, ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta(ReconnectConfigPaths.JITTER, ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta(ReconnectConfigPaths.MAX_ATTEMPTS, ApplyMode.IMMEDIATE, mutable = true),
    ).associateBy { it.path }
}