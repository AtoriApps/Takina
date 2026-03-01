package org.atoriapps.takina.core.utils

import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.toBareJid
import kotlin.random.Random

// TODO、CHECK：下面这个工具类真的有用吗？？另外还有什么该是工具类的没做？
object ParsingUtils {
    fun String.toBareJidOrNull(): BareJid? {
        val bare = substringBefore('/')
        return runCatching { bare.toBareJid() }.getOrNull()
    }

    fun Any?.asStringListOrNull(): List<String>? = when (this) {
        null -> null

        is String -> split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null }

        is Iterable<*> -> mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }.ifEmpty { null }

        is Array<*> -> mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }.ifEmpty { null }

        else -> null
    }
}

object FunctionalUtils{
     fun newTraceId(prefix: String): String = buildString {
        append(prefix)
        append('-')
        repeat(12) { append(Random.nextInt(16).toString(16)) }
    }
}