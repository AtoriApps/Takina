package org.atoriapps.takina.core.utils

import kotlin.random.Random
import kotlin.reflect.KClass

object LanguageUtils {
    val KClass<*>.clzName: String
        get() = this.simpleName ?: this.qualifiedName?.substringAfterLast('.') ?: "UnnamedClass"
}

object IdUtils {
    private var seed: Long = 0

    fun newStanzaId(prefix: String = "tk"): String = synchronized(this) {
        seed += 1
        "$prefix-${seed.toString(16)}-${Random.nextLong().toString(16)}"
    }
}

expect fun platformPrint(method: LogUtils.Method, tag: String, message: String)

object LogUtils {
    private fun print(method: Method, tag: String, vararg message: Any) = platformPrint(method, tag, message.joinToString(" "))

    enum class Method(val displayName: String) { INFO("I"), DEBUG("D"), WARN("W"), ERROR("E") }

    fun info(tag: String, vararg message: Any) = print(Method.INFO, tag, *message)
    fun debug(tag: String, vararg message: Any) = print(Method.DEBUG, tag, *message)
    fun warn(tag: String, vararg message: Any) = print(Method.WARN, tag, *message)
    fun error(tag: String, vararg message: Any) = print(Method.ERROR, tag, *message)
}
