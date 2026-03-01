package org.atoriapps.takina.core.utils

import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.toBareJid

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

