package org.atoriapps.takina.core.connections

internal expect object PlatformScramPrimitives {
    fun hash(algorithm: String, input: ByteArray): ByteArray
    fun hmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray
    fun pbkdf2(algorithm: String, password: String, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray
    fun isAlgorithmSupported(hash: String, hmac: String, pbkdf2: String): Boolean
    fun secureRandomBytes(size: Int): ByteArray
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
}
