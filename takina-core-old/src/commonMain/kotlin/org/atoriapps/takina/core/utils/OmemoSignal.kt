package org.atoriapps.takina.core.utils

data class OmemoSignalPreKeyMaterial(
    val id: Int,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val record: ByteArray,
)

data class OmemoSignalLocalMaterial(
    val registrationId: Int,
    val identityPublicKey: ByteArray,
    val identityPrivateKey: ByteArray,
    val identityKeyPair: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKeyPublicKey: ByteArray,
    val signedPreKeyPrivateKey: ByteArray,
    val signedPreKeySignature: ByteArray,
    val signedPreKeyRecord: ByteArray,
    val preKeys: List<OmemoSignalPreKeyMaterial>,
)

data class OmemoSignalEncryptedMessage(
    val message: ByteArray,
    val isPreKeyMessage: Boolean,
    val sessionRecord: ByteArray,
)

data class OmemoSignalDecryptedMessage(
    val plaintext: ByteArray,
    val sessionRecord: ByteArray,
)

expect object OmemoSignal {
    fun generateLocalMaterial(preKeyCount: Int): OmemoSignalLocalMaterial
    fun verifySignedPreKey(identityPublicKey: ByteArray, signedPreKeyPublicKey: ByteArray, signature: ByteArray): Boolean
    fun encryptKeyTransport(
        localRegistrationId: Int,
        localIdentityKeyPair: ByteArray,
        localPreKeyRecords: List<ByteArray>,
        localSignedPreKeyRecord: ByteArray,
        existingSessionRecord: ByteArray?,
        remoteAddress: String,
        remoteDeviceId: Int,
        remoteIdentityKey: ByteArray,
        remoteSignedPreKeyId: Int,
        remoteSignedPreKeyPublicKey: ByteArray,
        remoteSignedPreKeySignature: ByteArray,
        remotePreKeyId: Int,
        remotePreKeyPublicKey: ByteArray,
        plaintext: ByteArray,
    ): OmemoSignalEncryptedMessage

    fun decryptKeyTransport(
        localRegistrationId: Int,
        localIdentityKeyPair: ByteArray,
        localPreKeyRecords: List<ByteArray>,
        localSignedPreKeyRecord: ByteArray,
        existingSessionRecord: ByteArray?,
        remoteAddress: String,
        remoteDeviceId: Int,
        message: ByteArray,
        isPreKeyMessage: Boolean,
    ): OmemoSignalDecryptedMessage?
}
