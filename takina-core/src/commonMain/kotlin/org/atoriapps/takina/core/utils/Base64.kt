package org.atoriapps.takina.core.utils

expect object Base64Codec {
    fun encode(value: ByteArray): String

    fun decode(value: String): ByteArray
}
