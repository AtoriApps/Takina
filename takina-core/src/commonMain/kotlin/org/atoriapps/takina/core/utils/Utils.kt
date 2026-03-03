package org.atoriapps.takina.core.utils

import org.atoriapps.takina.core.connections.SecurityMode
import kotlin.random.Random

expect object CodecUtils {
    fun base64Encode(input: ByteArray, padding: Boolean = true): String
    fun base64Decode(input: String): ByteArray

}

object IdsUtils {
    fun newPrefixedId(prefix: String): String = buildString {
        append(prefix)
        append('-')
        repeat(12) { append(Random.nextInt(16).toString(16)) }
    }
}

object ValueCoercingUtils {
    fun booleanValueOrNull(value: Any): Boolean? = value as? Boolean

    fun stringValueOrNull(value: Any): String? = value as? String

    fun securityModeValueOrNull(value: Any): SecurityMode? = value as? SecurityMode

    fun stringListValueOrNull(value: Any): List<String>? {
        val list = value as? List<*> ?: return null

        if (list.any { it !is String }) return null

        @Suppress("UNCHECKED_CAST") return list as List<String>
    }

    fun longValueOrNull(value: Any): Long? = when (value) {
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        is Float -> if (value.isFinite() && value % 1f == 0f) value.toLong() else null
        is Double -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong() else null
        else -> null
    }

    fun intValueOrNull(value: Any): Int? {
        val longValue = longValueOrNull(value) ?: return null
        return if (longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) longValue.toInt() else null
    }

    fun doubleValueOrNull(value: Any): Double? = when (value) {
        is Byte, is Short, is Int, is Long -> (value as Number).toDouble()
        is Float -> if (value.isFinite()) value.toDouble() else null
        is Double -> if (value.isFinite()) value else null
        else -> null
    }
}