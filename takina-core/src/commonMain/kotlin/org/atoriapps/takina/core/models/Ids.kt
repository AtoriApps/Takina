package org.atoriapps.takina.core.models

import kotlin.random.Random

internal fun newId(prefix: String): String = buildString {
    append(prefix)
    append('-')
    repeat(12) { append(Random.nextInt(16).toString(16)) }
}
