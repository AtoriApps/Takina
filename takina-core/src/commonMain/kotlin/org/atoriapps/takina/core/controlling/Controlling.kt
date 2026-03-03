package org.atoriapps.takina.core.controlling

import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.ScopeKind

enum class ApplyMode {
    IMMEDIATE,
    NEXT_ITEM,
    NEXT_CONNECTION,
    BUILD_TIME_IMMUTABLE,
}

sealed interface ConfigNormalizeResult {
    data class Accepted(val value: Any) : ConfigNormalizeResult
    data class Rejected(val reason: String) : ConfigNormalizeResult
}

data class ConfigSpec<T : Any>(
    val path: String,
    val applyMode: ApplyMode,
    val mutable: Boolean,
    val expectedType: String,
    val immutableReason: String = "Config is build-time immutable",
    val defaultValue: T? = null,
    val allowedScopes: Set<ScopeKind> = ScopeKind.entries.toSet(),
    private val coerce: (Any) -> T?,
    private val validate: (T) -> String? = { null },
) {
    val hasDefault: Boolean get() = defaultValue != null
    val default: T get() = requireNotNull(defaultValue) { "Config $path has no default value" }

    fun supports(scope: Scope): Boolean = scope.kind in allowedScopes

    fun decode(raw: Any): T? {
        val coerced = coerce(raw) ?: return null
        return if (validate(coerced) == null) coerced else null
    }

    fun normalize(raw: Any): ConfigNormalizeResult {
        val coerced = coerce(raw) ?: return ConfigNormalizeResult.Rejected("$path must be $expectedType")
        validate(coerced)?.let { reason -> return ConfigNormalizeResult.Rejected(reason) }
        return ConfigNormalizeResult.Accepted(coerced)
    }
}

data class ConfigChangeResult(
    val path: String,
    val applied: Boolean,
    val applyMode: ApplyMode,
    val rejectedReason: String? = null,
)

object CoreConfigCatalog {
    object Connection {
        val HOST = ConfigSpec(
            path = ConnectionConfigPaths.HOST,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "String",
            immutableReason = "${ConnectionConfigPaths.HOST} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::stringValue,
        )
        val PORT = ConfigSpec(
            path = ConnectionConfigPaths.PORT,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "Int > 0",
            immutableReason = "${ConnectionConfigPaths.PORT} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::intValue,
            validate = { value -> if (value > 0) null else "${ConnectionConfigPaths.PORT} must be > 0" },
        )
        val SECURITY_MODE = ConfigSpec(
            path = ConnectionConfigPaths.SECURITY_MODE,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "SecurityMode",
            immutableReason = "${ConnectionConfigPaths.SECURITY_MODE} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::securityModeValue,
        )
        val RESOURCE = ConfigSpec(
            path = ConnectionConfigPaths.RESOURCE,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "String",
            immutableReason = "${ConnectionConfigPaths.RESOURCE} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::stringValue,
        )
        val AUTH_SASL = ConfigSpec(
            path = ConnectionConfigPaths.AUTH_SASL,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "List<String>",
            immutableReason = "${ConnectionConfigPaths.AUTH_SASL} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::stringListValue,
        )
        val TIMEOUT_HANDSHAKE = ConfigSpec(
            path = ConnectionConfigPaths.TIMEOUT_HANDSHAKE,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "Int > 0",
            immutableReason = "${ConnectionConfigPaths.TIMEOUT_HANDSHAKE} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::intValue,
            validate = { value -> if (value > 0) null else "${ConnectionConfigPaths.TIMEOUT_HANDSHAKE} must be > 0" },
        )
    }

    object Reconnect {
        val ENABLED = ConfigSpec(
            path = ReconnectConfigPaths.ENABLED,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Boolean",
            defaultValue = true,
            coerce = ::booleanValue,
        )
        val DELAY = ConfigSpec(
            path = ReconnectConfigPaths.DELAY,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Long >= 0",
            defaultValue = 1_000L,
            coerce = ::longValue,
            validate = { value -> if (value >= 0L) null else "${ReconnectConfigPaths.DELAY} must be >= 0" },
        )
        val FACTOR = ConfigSpec(
            path = ReconnectConfigPaths.FACTOR,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Double > 0",
            defaultValue = 2.0,
            coerce = ::doubleValue,
            validate = { value -> if (value > 0.0) null else "${ReconnectConfigPaths.FACTOR} must be > 0" },
        )
        val JITTER = ConfigSpec(
            path = ReconnectConfigPaths.JITTER,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Double in [0, 1]",
            defaultValue = 0.0,
            coerce = ::doubleValue,
            validate = { value ->
                if (value in 0.0..1.0) null
                else "${ReconnectConfigPaths.JITTER} must be in [0, 1]"
            },
        )
        val MAX_ATTEMPTS = ConfigSpec(
            path = ReconnectConfigPaths.MAX_ATTEMPTS,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Int >= 0",
            defaultValue = 5,
            coerce = ::intValue,
            validate = { value -> if (value >= 0) null else "${ReconnectConfigPaths.MAX_ATTEMPTS} must be >= 0" },
        )
    }

    object Observability {
        val SAMPLING = ConfigSpec(
            path = ObservabilityConfigPaths.SAMPLING,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Double in [0, 1]",
            defaultValue = 1.0,
            coerce = ::doubleValue,
            validate = { value ->
                if (value in 0.0..1.0) null
                else "${ObservabilityConfigPaths.SAMPLING} must be in [0, 1]"
            },
        )
        val ALERT_THRESHOLD = ConfigSpec(
            path = ObservabilityConfigPaths.ALERT_THRESHOLD,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Double in [0, 1]",
            defaultValue = 0.9,
            coerce = ::doubleValue,
            validate = { value ->
                if (value in 0.0..1.0) null
                else "${ObservabilityConfigPaths.ALERT_THRESHOLD} must be in [0, 1]"
            },
        )
    }

    private val specsInOrder = listOf(
        Connection.HOST,
        Connection.PORT,
        Connection.SECURITY_MODE,
        Connection.RESOURCE,
        Connection.AUTH_SASL,
        Connection.TIMEOUT_HANDSHAKE,
        Reconnect.ENABLED,
        Reconnect.DELAY,
        Reconnect.FACTOR,
        Reconnect.JITTER,
        Reconnect.MAX_ATTEMPTS,
        Observability.SAMPLING,
        Observability.ALERT_THRESHOLD,
    )

    val all: Map<String, ConfigSpec<*>> = specsInOrder.associateBy { it.path }

    val presetDefaults: List<Pair<ConfigSpec<*>, Any>> = specsInOrder.mapNotNull { spec ->
        val defaultValue = spec.defaultValue ?: return@mapNotNull null
        if (!spec.mutable || spec.applyMode == ApplyMode.BUILD_TIME_IMMUTABLE) return@mapNotNull null
        spec to defaultValue
    }

    fun spec(path: String): ConfigSpec<*>? = all[path]

    fun isRegistered(path: String): Boolean = path in all

    fun isRuntimeSettable(path: String): Boolean = spec(path)?.let { it.mutable && it.applyMode != ApplyMode.BUILD_TIME_IMMUTABLE } == true

    private fun booleanValue(value: Any): Boolean? = value as? Boolean

    private fun stringValue(value: Any): String? = value as? String

    private fun securityModeValue(value: Any): org.atoriapps.takina.core.connections.SecurityMode? =
        value as? org.atoriapps.takina.core.connections.SecurityMode

    private fun stringListValue(value: Any): List<String>? {
        val list = value as? List<*> ?: return null
        if (list.any { it !is String }) return null
        @Suppress("UNCHECKED_CAST") return list as List<String>
    }

    private fun longValue(value: Any): Long? = when (value) {
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        is Float -> if (value.isFinite() && value % 1f == 0f) value.toLong() else null
        is Double -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong() else null
        else -> null
    }

    private fun intValue(value: Any): Int? {
        val longValue = longValue(value) ?: return null
        return if (longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) longValue.toInt() else null
    }

    private fun doubleValue(value: Any): Double? = when (value) {
        is Byte, is Short, is Int, is Long -> (value as Number).toDouble()
        is Float -> if (value.isFinite()) value.toDouble() else null
        is Double -> if (value.isFinite()) value else null
        else -> null
    }
}