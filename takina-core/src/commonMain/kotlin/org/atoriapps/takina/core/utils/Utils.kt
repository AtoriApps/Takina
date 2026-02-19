package org.atoriapps.takina.core.utils

import kotlin.random.Random
import kotlin.reflect.KClass

object LanguageUtils {
    val KClass<*>.clzName: String
        get() = this.simpleName ?: this.qualifiedName?.substringAfterLast('.') ?: "UnnamedClass"

    fun <T> MutableList<T>.findOrAdd(predicate: (T) -> Boolean, factory: () -> T): T {
        val found = find(predicate)
        return if (found == null) {
            val new = factory()
            add(new)
            new
        } else {
            found
        }
    }
}

object IdUtils {
    private var seed: Long = 0

    fun newStanzaId(prefix: String = "tk"): String = synchronized(this) {
        seed += 1
        "$prefix-${seed.toString(16)}-${Random.nextLong().toString(16)}"
    }
}

object LogUtils {
    private fun print(method: String, tag: String, vararg message: Any) {
        println("[$method] $tag > ${message.joinToString(" ")}")
    }

    fun info(tag: String, vararg message: Any) = print("INFO", tag, *message)
    fun debug(tag: String, vararg message: Any) = print("DEBUG", tag, *message)
    fun warn(tag: String, vararg message: Any) = print("WARN", tag, *message)
    fun error(tag: String, vararg message: Any) = print("ERROR", tag, *message)
}
