package org.atoriapps.takina.core.connections

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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

    fun hash(input: ByteArray): ByteArray = MessageDigest.getInstance(hashAlgorithm).digest(input)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(hmacAlgorithm)
        mac.init(SecretKeySpec(key, hmacAlgorithm))
        return mac.doFinal(data)
    }

    fun hi(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, hashLengthBytes * 8)
        return SecretKeyFactory.getInstance(pbkdf2Algorithm).generateSecret(keySpec).encoded
    }

    fun isRuntimeSupported(): Boolean = runCatching {
        MessageDigest.getInstance(hashAlgorithm)
        Mac.getInstance(hmacAlgorithm)
        SecretKeyFactory.getInstance(pbkdf2Algorithm)
        true
    }.getOrDefault(false)

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

internal object JvmScram {
    private val random = SecureRandom()

    fun buildClientFirst(username: String, nonce: String = generateNonce()): ScramClientFirst {
        val escapedUser = saslNameEscape(username)
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
        val attrs = parseAttributes(serverFirstMessage)
        val nonce = attrs["r"] ?: error("SCRAM server-first missing nonce")
        val saltB64 = attrs["s"] ?: error("SCRAM server-first missing salt")
        val iterations = attrs["i"]?.toIntOrNull() ?: error("SCRAM server-first invalid iteration")
        require(iterations > 0) { "SCRAM iteration must be positive" }
        val clientNonce = parseAttributes(clientFirstBare)["r"] ?: error("SCRAM client-first missing nonce")
        require(nonce.startsWith(clientNonce)) { "SCRAM server nonce does not extend client nonce" }

        val salt = Base64.getDecoder().decode(saltB64)
        val channelBinding = "biws"
        val finalWithoutProof = "c=$channelBinding,r=$nonce"
        val authMessage = "$clientFirstBare,$serverFirstMessage,$finalWithoutProof"

        val saltedPassword = mechanism.hi(password, salt, iterations)
        val clientKey = mechanism.hmac(saltedPassword, "Client Key".toByteArray(Charsets.UTF_8))
        val storedKey = mechanism.hash(clientKey)
        val clientSignature = mechanism.hmac(storedKey, authMessage.toByteArray(Charsets.UTF_8))
        val clientProof = xor(clientKey, clientSignature)

        val serverKey = mechanism.hmac(saltedPassword, "Server Key".toByteArray(Charsets.UTF_8))
        val serverSignature = mechanism.hmac(serverKey, authMessage.toByteArray(Charsets.UTF_8))

        val proofB64 = Base64.getEncoder().encodeToString(clientProof)
        val serverSigB64 = Base64.getEncoder().encodeToString(serverSignature)
        return ScramClientFinal(
            messageWithoutProof = finalWithoutProof,
            fullMessage = "$finalWithoutProof,p=$proofB64",
            expectedServerSignatureBase64 = serverSigB64,
        )
    }

    fun extractServerVerifier(serverFinalMessage: String): String? = parseAttributes(serverFinalMessage)["v"]

    private fun generateNonce(): String {
        val bytes = ByteArray(18)
        random.nextBytes(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun parseAttributes(message: String): Map<String, String> = if (message.isBlank()) emptyMap()
    else message.split(',').mapNotNull { token ->
        val idx = token.indexOf('=')
        if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
    }.toMap()

    private fun saslNameEscape(value: String): String = value.replace("=", "=3D").replace(",", "=2C")

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "SCRAM xor operands length mismatch" }
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }
}

