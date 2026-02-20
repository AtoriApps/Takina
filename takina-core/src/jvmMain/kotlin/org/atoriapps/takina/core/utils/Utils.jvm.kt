package org.atoriapps.takina.core.utils

import java.util.Base64

actual fun platformPrint(method: LogUtils.Method, tag: String, message: String) {
    val msg = "[${method.displayName}] $tag > $message"

    if (method == LogUtils.Method.ERROR || method == LogUtils.Method.WARN) System.err.println(msg)
    else println(msg)
}

actual object Base64Codec {
    actual fun encodeToString(value: ByteArray): String =
        Base64.getEncoder().encodeToString(value)
}