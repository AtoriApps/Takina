package org.atoriapps.takina.core.controlling

import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.utils.ValueCoercingUtils.booleanValueOrNull
import org.atoriapps.takina.core.utils.ValueCoercingUtils.doubleValueOrNull
import org.atoriapps.takina.core.utils.ValueCoercingUtils.intValueOrNull
import org.atoriapps.takina.core.utils.ValueCoercingUtils.longValueOrNull
import org.atoriapps.takina.core.utils.ValueCoercingUtils.securityModeValueOrNull
import org.atoriapps.takina.core.utils.ValueCoercingUtils.stringListValueOrNull
import org.atoriapps.takina.core.utils.ValueCoercingUtils.stringValueOrNull

enum class ApplyMode {
    IMMEDIATE,
    NEXT_ITEM,
    NEXT_CONNECTION,
    BUILD_TIME_IMMUTABLE,
}

enum class ConfigRejectCode {
    UNKNOWN_PATH,
    UNSUPPORTED_SCOPE,
    IMMUTABLE,
    TYPE_MISMATCH,
    VALIDATION_FAILED,
}

sealed interface ConfigNormalizeResult {
    data class Accepted(val value: Any?) : ConfigNormalizeResult
    data class Rejected(val code: ConfigRejectCode, val reason: String) : ConfigNormalizeResult
}

data class ConfigSpec<T>(
    val path: String,
    val applyMode: ApplyMode,
    val mutable: Boolean,
    val expectedType: String,
    val immutableReason: String = "Config is build-time immutable",
    val defaultValue: T? = null,
    val hasDefault: Boolean = defaultValue != null,
    val nullable: Boolean = false,
    val allowedScopes: Set<ScopeKind> = ScopeKind.entries.toSet(),
    private val coerce: (Any) -> T?,
    private val validate: (T) -> String? = { null },
) {
    val default: T
        get() {
            check(hasDefault) { "Config $path has no default value" }
            @Suppress("UNCHECKED_CAST")
            return defaultValue as T
        }

    fun supports(scope: Scope): Boolean = scope.kind in allowedScopes

    fun decode(raw: Any?): T? {
        if (raw == null) {
            if (!nullable) return null
            @Suppress("UNCHECKED_CAST")
            val nullValue = null as T
            return if (validate(nullValue) == null) nullValue else null
        }
        val coerced = coerce(raw) ?: return null
        return if (validate(coerced) == null) coerced else null
    }

    fun normalize(raw: Any?): ConfigNormalizeResult {
        if (raw == null) {
            if (!nullable) return ConfigNormalizeResult.Rejected(
                code = ConfigRejectCode.TYPE_MISMATCH,
                reason = "$path must be $expectedType",
            )
            @Suppress("UNCHECKED_CAST")
            val nullValue = null as T
            validate(nullValue)?.let { reason ->
                return ConfigNormalizeResult.Rejected(
                    code = ConfigRejectCode.VALIDATION_FAILED,
                    reason = reason,
                )
            }
            return ConfigNormalizeResult.Accepted(null)
        }

        val coerced = coerce(raw) ?: return ConfigNormalizeResult.Rejected(
            code = ConfigRejectCode.TYPE_MISMATCH,
            reason = "$path must be $expectedType",
        )
        validate(coerced)?.let { reason ->
            return ConfigNormalizeResult.Rejected(
                code = ConfigRejectCode.VALIDATION_FAILED,
                reason = reason,
            )
        }
        return ConfigNormalizeResult.Accepted(coerced)
    }
}

data class ConfigChangeResult(
    val path: String,
    val applied: Boolean,
    val applyMode: ApplyMode,
    val rejectedReason: String? = null,
    val rejectCode: ConfigRejectCode? = null,
)

object CoreConfigCatalog {
    private val accountWideScopes = setOf(
        ScopeKind.PRESET,
        ScopeKind.GLOBAL,
        ScopeKind.ACCOUNT,
    )

    object Connection {
        val HOST = ConfigSpec(
            path = ConnectionConfigPaths.HOST,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "String",
            immutableReason = "${ConnectionConfigPaths.HOST} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::stringValueOrNull,
        )
        val PORT = ConfigSpec(
            path = ConnectionConfigPaths.PORT,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "Int > 0",
            immutableReason = "${ConnectionConfigPaths.PORT} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::intValueOrNull,
            validate = { value -> if (value > 0) null else "${ConnectionConfigPaths.PORT} must be > 0" },
        )
        val SECURITY_MODE = ConfigSpec(
            path = ConnectionConfigPaths.SECURITY_MODE,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "SecurityMode",
            immutableReason = "${ConnectionConfigPaths.SECURITY_MODE} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::securityModeValueOrNull,
        )
        val RESOURCE = ConfigSpec(
            path = ConnectionConfigPaths.RESOURCE,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "String",
            immutableReason = "${ConnectionConfigPaths.RESOURCE} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::stringValueOrNull,
        )
        val AUTH_SASL = ConfigSpec(
            path = ConnectionConfigPaths.AUTH_SASL,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "List<String>",
            immutableReason = "${ConnectionConfigPaths.AUTH_SASL} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::stringListValueOrNull,
        )
        val TIMEOUT_HANDSHAKE = ConfigSpec(
            path = ConnectionConfigPaths.TIMEOUT_HANDSHAKE,
            applyMode = ApplyMode.BUILD_TIME_IMMUTABLE,
            mutable = false,
            expectedType = "Int > 0",
            immutableReason = "${ConnectionConfigPaths.TIMEOUT_HANDSHAKE} is account-definition-only. Configure connection params in addAccount { connection { ... } }.",
            coerce = ::intValueOrNull,
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
            hasDefault = true,
            allowedScopes = accountWideScopes,
            coerce = ::booleanValueOrNull,
        )
        val DELAY = ConfigSpec(
            path = ReconnectConfigPaths.DELAY,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Long >= 0",
            defaultValue = 1_000L,
            hasDefault = true,
            allowedScopes = accountWideScopes,
            coerce = ::longValueOrNull,
            validate = { value -> if (value >= 0L) null else "${ReconnectConfigPaths.DELAY} must be >= 0" },
        )
        val FACTOR = ConfigSpec(
            path = ReconnectConfigPaths.FACTOR,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Double > 0",
            defaultValue = 2.0,
            hasDefault = true,
            allowedScopes = accountWideScopes,
            coerce = ::doubleValueOrNull,
            validate = { value -> if (value > 0.0) null else "${ReconnectConfigPaths.FACTOR} must be > 0" },
        )
        val JITTER = ConfigSpec(
            path = ReconnectConfigPaths.JITTER,
            applyMode = ApplyMode.IMMEDIATE,
            mutable = true,
            expectedType = "Double in [0, 1]",
            defaultValue = 0.0,
            hasDefault = true,
            allowedScopes = accountWideScopes,
            coerce = ::doubleValueOrNull,
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
            hasDefault = true,
            allowedScopes = accountWideScopes,
            coerce = ::intValueOrNull,
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
            hasDefault = true,
            allowedScopes = accountWideScopes,
            coerce = ::doubleValueOrNull,
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
            hasDefault = true,
            allowedScopes = accountWideScopes,
            coerce = ::doubleValueOrNull,
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

    val presetDefaults: List<Pair<ConfigSpec<*>, Any?>> = specsInOrder.mapNotNull { spec ->
        if (!spec.hasDefault) return@mapNotNull null
        if (!spec.mutable || spec.applyMode == ApplyMode.BUILD_TIME_IMMUTABLE) return@mapNotNull null
        spec to spec.defaultValue
    }

    fun getSpec(path: String): ConfigSpec<*>? = all[path]

    fun isRuntimeSettable(path: String): Boolean = getSpec(path)?.let { it.mutable && it.applyMode != ApplyMode.BUILD_TIME_IMMUTABLE } == true
}
