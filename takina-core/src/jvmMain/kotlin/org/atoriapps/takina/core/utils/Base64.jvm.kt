package org.atoriapps.takina.core.utils

import java.util.Base64

actual object Base64Codec {
    actual fun encodeToString(value: ByteArray): String =
        Base64.getEncoder().encodeToString(value)
}
