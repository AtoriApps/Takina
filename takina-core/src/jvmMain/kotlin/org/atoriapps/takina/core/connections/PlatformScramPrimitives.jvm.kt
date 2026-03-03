package org.atoriapps.takina.core.connections

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal actual object PlatformScramPrimitives {
    private val secureRandom = SecureRandom()

    actual fun hash(algorithm: String, input: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(input)

    actual fun hmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key, algorithm))
        return mac.doFinal(data)
    }

    actual fun pbkdf2(
        algorithm: String,
        password: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBytes * 8)
        return SecretKeyFactory.getInstance(algorithm).generateSecret(keySpec).encoded
    }

    actual fun isAlgorithmSupported(hash: String, hmac: String, pbkdf2: String): Boolean = runCatching {
        MessageDigest.getInstance(hash)
        Mac.getInstance(hmac)
        SecretKeyFactory.getInstance(pbkdf2)
        true
    }.getOrDefault(false)

    actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    actual fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}
