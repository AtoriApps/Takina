package org.atoriapps.takina.core.utils

import org.atoriapps.takina.core.events.TakinaEvent
import kotlin.reflect.KClass

object LanguageUtils {
    /*fun KClass<TakinaEvent>.getInstance(): TakinaEvent {
        return this.constructors.first().call() as TakinaEvent
    }*/

    val KClass<*>.clzName: String get() = this.simpleName ?: this.qualifiedName?.substringAfterLast('.') ?: "无名类"

    fun <T> MutableList<T>.findOrAdd(predicate: (T) -> Boolean, factory: () -> T): T {
        val found = find(predicate)
        return if (found == null) {
            val new = factory()

            add(new)
            new
        } else found
    }
}

object LogUtils {
    // TODO：豪华的日志系统
    private fun print(method: String, tag: String, vararg message: Any) {
        println("[$method] $tag > ${message.joinToString(" ")}")
    }

    fun info(tag: String, vararg message: Any) = print("INFO", tag, *message)
    fun debug(tag: String, vararg message: Any) = print("DEBUG", tag, *message)
    fun warn(tag: String, vararg message: Any) = print("WARN", tag, *message)
    fun error(tag: String, vararg message: Any) = print("ERROR", tag, *message)
}