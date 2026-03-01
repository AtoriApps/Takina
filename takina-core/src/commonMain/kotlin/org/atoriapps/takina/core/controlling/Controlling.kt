package org.atoriapps.takina.core.controlling

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

// TODO、CHECK：下面是啥，预定义的配置项吗？我的想法是仅保留Core的配置项，其它都由feat本身提供

object CoreConfigMetaCatalog {
    val all: Map<String, ConfigMeta> = listOf(
        ConfigMeta("connection.host", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("connection.port", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("securityMode", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("tls.trustChain", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("tls.pin", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("auth.sasl", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("resource", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("compression.enabled", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("sm.enabled", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("sm.resumePolicy", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("timeout.handshake", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("pipeline.businessInbound.enabled", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("pipeline.businessOutbound.enabled", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("pipeline.businessInbound.order", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("pipeline.businessOutbound.order", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("defaults.messageEncryption", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("defaults.request.timeout", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("defaults.request.retry", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("inbound.unknownFramePolicy", ApplyMode.NEXT_ITEM, mutable = true),
        ConfigMeta("reconnect.enabled", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("reconnect.delay", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("reconnect.factor", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("reconnect.jitter", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("reconnect.maxAttempts", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("observability.sampling", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("observability.alertThreshold", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("events.filter", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("feature.nonNegotiatedSwitch", ApplyMode.IMMEDIATE, mutable = true),
        ConfigMeta("feature.negotiatedSwitch", ApplyMode.NEXT_CONNECTION, mutable = true),
        ConfigMeta("features.installSet", ApplyMode.BUILD_TIME_IMMUTABLE, mutable = false),
    ).associateBy { it.path }
}