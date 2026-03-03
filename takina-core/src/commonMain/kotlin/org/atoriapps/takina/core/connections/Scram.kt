package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.utils.CodecUtils

internal enum class ScramMechanism(
    val mechanismName: String,
    private val hashAlgorithm: String,
    private val hmacAlgorithm: String,
    private val pbkdf2Algorithm: String,
    private val hashLengthBytes: Int,
) {
    SHA_1(
        mechanismName = "SCRAM-SHA-1",
        hashAlgorithm = "SHA-1",
        hmacAlgorithm = "HmacSHA1",
        pbkdf2Algorithm = "PBKDF2WithHmacSHA1",
        hashLengthBytes = 20,
    ),
    SHA_256(
        mechanismName = "SCRAM-SHA-256",
        hashAlgorithm = "SHA-256",
        hmacAlgorithm = "HmacSHA256",
        pbkdf2Algorithm = "PBKDF2WithHmacSHA256",
        hashLengthBytes = 32,
    );

    fun hash(input: ByteArray): ByteArray = PlatformScramPrimitives.hash(hashAlgorithm, input)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray = PlatformScramPrimitives.hmac(hmacAlgorithm, key, data)

    fun hi(password: String, salt: ByteArray, iterations: Int): ByteArray =
        PlatformScramPrimitives.pbkdf2(
            algorithm = pbkdf2Algorithm,
            password = password,
            salt = salt,
            iterations = iterations,
            keyLengthBytes = hashLengthBytes,
        )

    fun isRuntimeSupported(): Boolean = PlatformScramPrimitives.isAlgorithmSupported(
        hash = hashAlgorithm,
        hmac = hmacAlgorithm,
        pbkdf2 = pbkdf2Algorithm,
    )

    companion object {
        fun fromNameOrNull(value: String): ScramMechanism? = entries.firstOrNull { it.mechanismName == value }
    }
}

internal data class ScramClientFirst(
    val messageBare: String,
    val fullMessage: String,
)

internal data class ScramClientFinal(
    val messageWithoutProof: String,
    val fullMessage: String,
    val expectedServerSignatureBase64: String,
)

internal object Scram {
    fun buildClientFirst(username: String, nonce: String = generateNonce()): ScramClientFirst {
        val escapedUser = username.saslNameEscape()
        val bare = "n=$escapedUser,r=$nonce"
        return ScramClientFirst(
            messageBare = bare,
            fullMessage = "n,,$bare",
        )
    }

    fun buildClientFinal(
        mechanism: ScramMechanism,
        password: String,
        clientFirstBare: String,
        serverFirstMessage: String,
    ): ScramClientFinal {
        val attrs = serverFirstMessage.parseAttributes()
        val nonce = attrs["r"] ?: error("SCRAM server-first missing nonce")
        val saltB64 = attrs["s"] ?: error("SCRAM server-first missing salt")
        val iterations = attrs["i"]?.toIntOrNull() ?: error("SCRAM server-first invalid iteration")
        require(iterations > 0) { "SCRAM iteration must be positive" }
        val clientNonce = clientFirstBare.parseAttributes()["r"] ?: error("SCRAM client-first missing nonce")
        require(nonce.startsWith(clientNonce)) { "SCRAM server nonce does not extend client nonce" }

        val salt = CodecUtils.base64Decode(saltB64)
        val channelBinding = "biws"
        val finalWithoutProof = "c=$channelBinding,r=$nonce"
        val authMessage = "$clientFirstBare,$serverFirstMessage,$finalWithoutProof"

        val saltedPassword = mechanism.hi(password, salt, iterations)
        val clientKey = mechanism.hmac(saltedPassword, "Client Key".encodeToByteArray())
        val storedKey = mechanism.hash(clientKey)
        val clientSignature = mechanism.hmac(storedKey, authMessage.encodeToByteArray())
        val clientProof = clientKey.xor(clientSignature)

        val serverKey = mechanism.hmac(saltedPassword, "Server Key".encodeToByteArray())
        val serverSignature = mechanism.hmac(serverKey, authMessage.encodeToByteArray())

        val proofB64 = CodecUtils.base64Encode(clientProof)
        val serverSigB64 = CodecUtils.base64Encode(serverSignature)
        return ScramClientFinal(
            messageWithoutProof = finalWithoutProof,
            fullMessage = "$finalWithoutProof,p=$proofB64",
            expectedServerSignatureBase64 = serverSigB64,
        )
    }

    fun extractServerVerifier(serverFinalMessage: String): String? = serverFinalMessage.parseAttributes()["v"]

    fun encodeUtf8Base64(value: String): String = CodecUtils.base64Encode(value.encodeToByteArray())

    fun decodeUtf8Base64(value: String): String = CodecUtils.base64Decode(value).decodeToString()

    fun constantTimeEqualsUtf8(a: String, b: String): Boolean = PlatformScramPrimitives.constantTimeEquals(
        a.encodeToByteArray(),
        b.encodeToByteArray(),
    )

    private fun generateNonce(): String {
        val bytes = PlatformScramPrimitives.secureRandomBytes(size = 18)
        return CodecUtils.base64Encode(bytes, true)
    }

    private fun String.parseAttributes(): Map<String, String> = if (isBlank()) emptyMap()
    else split(',').mapNotNull { token ->
        val idx = token.indexOf('=')
        if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
    }.toMap()

    private fun String.saslNameEscape(): String = replace("=", "=3D").replace(",", "=2C")

    private fun ByteArray.xor(other: ByteArray): ByteArray {
        require(size == other.size) { "SCRAM xor operands length mismatch" }
        return ByteArray(size) { i -> (this[i].toInt() xor other[i].toInt()).toByte() }
    }
}
