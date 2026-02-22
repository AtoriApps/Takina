package org.atoriapps.takina.core.utils

data class OmemoKeyPairBytes(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
)

expect object OmemoCrypto {
    fun generateEcKeyPair(): OmemoKeyPairBytes

    fun sign(privateKey: ByteArray, data: ByteArray): ByteArray

    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean

    fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray

    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray

    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray

    fun randomBytes(size: Int): ByteArray
}
