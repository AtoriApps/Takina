package org.atoriapps.takina.core.utils

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object OmemoCrypto {
    private val random = SecureRandom()

    actual fun generateEcKeyPair(): OmemoKeyPairBytes {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = generator.generateKeyPair()
        return OmemoKeyPairBytes(
            publicKey = pair.public.encoded,
            privateKey = pair.private.encoded,
        )
    }

    actual fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKey)))
        signature.update(data)
        return signature.sign()
    }

    actual fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey)))
        verifier.update(data)
        return verifier.verify(signature)
    }

    actual fun deriveSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKey)))
        agreement.doPhase(keyFactory.generatePublic(X509EncodedKeySpec(publicKey)), true)
        return agreement.generateSecret()
    }

    actual fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
        require(size > 0) { "size 必须大于 0" }
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = hmac.doFinal(ikm)
        var t = ByteArray(0)
        val output = ByteArray(size)
        var offset = 0
        var counter = 1
        while (offset < size) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copySize = minOf(t.size, size - offset)
            t.copyInto(output, destinationOffset = offset, endIndex = copySize)
            offset += copySize
            counter += 1
        }
        return output
    }

    actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    actual fun randomBytes(size: Int): ByteArray {
        require(size > 0) { "size 必须大于 0" }
        return ByteArray(size).also { random.nextBytes(it) }
    }
}
