package org.atoriapps.takina.core.utils

import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.InvalidKeyIdException
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.state.IdentityKeyStore
import org.whispersystems.libsignal.util.KeyHelper
import java.util.concurrent.ConcurrentHashMap

actual object OmemoSignal {
    actual fun generateLocalMaterial(preKeyCount: Int): OmemoSignalLocalMaterial {
        require(preKeyCount > 0) { "preKeyCount 必须大于 0" }
        val identity = KeyHelper.generateIdentityKeyPair()
        val registrationId = KeyHelper.generateRegistrationId(false)
        val signedPreKeyId = KeyHelper.getRandomSequence(Integer.MAX_VALUE - 1) + 1
        val signedPreKey = KeyHelper.generateSignedPreKey(identity, signedPreKeyId)
        val preKeys = KeyHelper.generatePreKeys(1, preKeyCount)
        return OmemoSignalLocalMaterial(
            registrationId = registrationId,
            identityPublicKey = identity.publicKey.serialize(),
            identityPrivateKey = identity.privateKey.serialize(),
            identityKeyPair = identity.serialize(),
            signedPreKeyId = signedPreKey.id,
            signedPreKeyPublicKey = signedPreKey.keyPair.publicKey.serialize(),
            signedPreKeyPrivateKey = signedPreKey.keyPair.privateKey.serialize(),
            signedPreKeySignature = signedPreKey.signature,
            signedPreKeyRecord = signedPreKey.serialize(),
            preKeys = preKeys.map { record ->
                OmemoSignalPreKeyMaterial(
                    id = record.id,
                    publicKey = record.keyPair.publicKey.serialize(),
                    privateKey = record.keyPair.privateKey.serialize(),
                    record = record.serialize(),
                )
            },
        )
    }

    actual fun verifySignedPreKey(identityPublicKey: ByteArray, signedPreKeyPublicKey: ByteArray, signature: ByteArray): Boolean =
        runCatching { Curve.verifySignature(Curve.decodePoint(identityPublicKey, 0), signedPreKeyPublicKey, signature) }.getOrDefault(false)

    actual fun encryptKeyTransport(
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
    ): OmemoSignalEncryptedMessage {
        val address = SignalProtocolAddress(remoteAddress, remoteDeviceId)
        val store = InMemorySignalProtocolStore(
            registrationId = localRegistrationId,
            identityKeyPairSerialized = localIdentityKeyPair,
            preKeyRecords = localPreKeyRecords,
            signedPreKeyRecord = localSignedPreKeyRecord,
            sessionRecord = existingSessionRecord,
            sessionAddress = address,
        )
        if (!store.containsSession(address)) {
            val sessionBuilder = SessionBuilder(store, address)
            val bundle = PreKeyBundle(
                0,
                remoteDeviceId,
                remotePreKeyId,
                Curve.decodePoint(remotePreKeyPublicKey, 0),
                remoteSignedPreKeyId,
                Curve.decodePoint(remoteSignedPreKeyPublicKey, 0),
                remoteSignedPreKeySignature,
                IdentityKey(remoteIdentityKey, 0),
            )
            sessionBuilder.process(bundle)
        }
        val cipher = SessionCipher(store, address)
        val encrypted = cipher.encrypt(plaintext)
        return OmemoSignalEncryptedMessage(message = encrypted.serialize(), isPreKeyMessage = encrypted.type == CiphertextMessage.PREKEY_TYPE, sessionRecord = store.loadSession(address).serialize())
    }

    actual fun decryptKeyTransport(
        localRegistrationId: Int,
        localIdentityKeyPair: ByteArray,
        localPreKeyRecords: List<ByteArray>,
        localSignedPreKeyRecord: ByteArray,
        existingSessionRecord: ByteArray?,
        remoteAddress: String,
        remoteDeviceId: Int,
        message: ByteArray,
        isPreKeyMessage: Boolean,
    ): OmemoSignalDecryptedMessage? {
        val address = SignalProtocolAddress(remoteAddress, remoteDeviceId)
        val store = InMemorySignalProtocolStore(
            registrationId = localRegistrationId,
            identityKeyPairSerialized = localIdentityKeyPair,
            preKeyRecords = localPreKeyRecords,
            signedPreKeyRecord = localSignedPreKeyRecord,
            sessionRecord = existingSessionRecord,
            sessionAddress = address,
        )
        val cipher = SessionCipher(store, address)
        return runCatching {
            if (isPreKeyMessage) cipher.decrypt(PreKeySignalMessage(message))
            else cipher.decrypt(SignalMessage(message))
        }.map { plaintext ->
            OmemoSignalDecryptedMessage(
                plaintext = plaintext,
                sessionRecord = store.loadSession(address).serialize(),
            )
        }.getOrNull()
    }
}

private class InMemorySignalProtocolStore(
    private val registrationId: Int,
    identityKeyPairSerialized: ByteArray,
    preKeyRecords: List<ByteArray>,
    signedPreKeyRecord: ByteArray,
    sessionRecord: ByteArray?,
    sessionAddress: SignalProtocolAddress,
) : SignalProtocolStore {
    private val identity = IdentityKeyPair(identityKeyPairSerialized)
    private val preKeys = ConcurrentHashMap<Int, PreKeyRecord>().apply {
        preKeyRecords.forEach { serialized ->
            val record = PreKeyRecord(serialized)
            put(record.id, record)
        }
    }
    private val signedPreKeys = ConcurrentHashMap<Int, SignedPreKeyRecord>().apply {
        val record = SignedPreKeyRecord(signedPreKeyRecord)
        put(record.id, record)
    }
    private val sessions = ConcurrentHashMap<SignalProtocolAddress, SessionRecord>().apply {
        if (sessionRecord != null) put(sessionAddress, SessionRecord(sessionRecord))
    }
    private val trusted = ConcurrentHashMap<SignalProtocolAddress, IdentityKey>()

    override fun getIdentityKeyPair(): IdentityKeyPair = identity

    override fun getLocalRegistrationId(): Int = registrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey?): Boolean {
        if (identityKey == null) return false
        trusted[address] = identityKey
        return true
    }

    override fun isTrustedIdentity(address: SignalProtocolAddress, identityKey: IdentityKey?, direction: IdentityKeyStore.Direction): Boolean {
        if (identityKey == null) return false
        val known = trusted[address] ?: return true
        return known == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = trusted[address]

    override fun loadPreKey(preKeyId: Int): PreKeyRecord = preKeys[preKeyId] ?: throw InvalidKeyIdException("prekey not found: $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys[preKeyId] = record
    }

    override fun containsPreKey(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)

    override fun removePreKey(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        signedPreKeys[signedPreKeyId] ?: throw InvalidKeyIdException("signed prekey not found: $signedPreKeyId")

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = signedPreKeys.values.toMutableList()

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[signedPreKeyId] = record
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = signedPreKeys.containsKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord = sessions[address] ?: SessionRecord()

    override fun getSubDeviceSessions(name: String): MutableList<Int> =
        sessions.keys.filter { it.name == name }.map { it.deviceId }.toMutableList()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessions[address] = record
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = sessions.containsKey(address)

    override fun deleteSession(address: SignalProtocolAddress) {
        sessions.remove(address)
    }

    override fun deleteAllSessions(name: String) {
        sessions.keys.filter { it.name == name }.forEach { sessions.remove(it) }
    }
}
