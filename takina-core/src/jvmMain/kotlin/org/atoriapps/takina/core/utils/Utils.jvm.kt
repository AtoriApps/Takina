package org.atoriapps.takina.core.utils

import java.util.Base64

actual object CodecUtils {
    actual fun base64Encode(input: ByteArray, padding: Boolean): String = Base64.getEncoder().run {
        if (padding) this else withoutPadding()
    }.encodeToString(input)

    actual fun base64Decode(input: String): ByteArray = Base64.getDecoder().decode(input)
}