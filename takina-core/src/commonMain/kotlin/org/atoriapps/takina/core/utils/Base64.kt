package org.atoriapps.takina.core.utils

expect object Base64Codec {
    fun encodeToString(value: ByteArray): String
}
