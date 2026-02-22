package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.utils.Base64Codec
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.utils.OmemoCrypto
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import org.atoriapps.takina.core.xmpp.stanzas.MessageStanza
import org.atoriapps.takina.core.xmpp.stanzas.MessageType
import org.atoriapps.takina.core.xmpp.toJid
import kotlin.random.Random

class OmemoComponent internal constructor(private val takina: AbstractTakina) : TakinaComponent {
    companion object : TakinaComponentProvider<OmemoComponent> {
        private const val TAG = "OMEMO组件"

        const val PUBSUB_NAMESPACE: String = "http://jabber.org/protocol/pubsub"
        const val OMEMO_NAMESPACE: String = "urn:xmpp:omemo:2"
        const val DEVICES_NODE: String = "$OMEMO_NAMESPACE:devices"
        const val BUNDLES_NODE_PREFIX: String = "$OMEMO_NAMESPACE:bundles:"
        private const val PACKET_VERSION: Int = 1
        private const val WRAP_INFO: String = "takina-omemo-wrap-v1"
        private const val PAYLOAD_KEY_SIZE = 16
        private const val IV_SIZE = 12

        override fun getInstance(context: TakinaContext): OmemoComponent {
            val core = context as? AbstractTakina ?: error("OmemoComponent 只能安装在 Takina 核心上下文中")
            return OmemoComponent(core)
        }

        override fun getComponentType() = OmemoComponent::class
    }

    var store: OmemoStateStore = InMemoryOmemoStateStore()
    var localDeviceIdProvider: () -> Int = { Random.nextInt(1, Int.MAX_VALUE) }
    var preKeyCount: Int = 20

    fun bootstrapLocalDevice(account: BareJid): LocalDeviceState {
        val existing = store.loadLocalDevice(account)
        if (existing != null) return existing

        val identity = OmemoCrypto.generateEcKeyPair()
        val signedPreKey = OmemoCrypto.generateEcKeyPair()
        val signedPreKeyId = localDeviceIdProvider()
        val signedPreKeySignature = OmemoCrypto.sign(identity.privateKey, signedPreKey.publicKey)
        val preKeys = (1..preKeyCount).map { id ->
            val keyPair = OmemoCrypto.generateEcKeyPair()
            LocalPreKey(id = id, publicKey = keyPair.publicKey, privateKey = keyPair.privateKey)
        }
        val state = LocalDeviceState(
            account = account,
            deviceId = localDeviceIdProvider(),
            identityPublicKey = identity.publicKey,
            identityPrivateKey = identity.privateKey,
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublicKey = signedPreKey.publicKey,
            signedPreKeyPrivateKey = signedPreKey.privateKey,
            signedPreKeySignature = signedPreKeySignature,
            preKeys = preKeys,
        )
        store.saveLocalDevice(state)
        return state
    }

    fun publishOwnDeviceList(from: BareJid): PendingStanzaRequest {
        val local = bootstrapLocalDevice(from)
        return publishDeviceList(listOf(local.deviceId), from = from)
    }

    fun publishOwnBundle(from: BareJid): PendingStanzaRequest {
        val local = bootstrapLocalDevice(from)
        return publishBundle(deviceId = local.deviceId, bundle = local.toPublicBundle(), from = from)
    }

    suspend fun publishOwnMaterial(from: BareJid) {
        publishOwnDeviceList(from).send()
        publishOwnBundle(from).send()
    }

    fun fetchDeviceListAwait(owner: Jid, from: Jid? = null, timeoutMillis: Long = 15_000): PendingIqAwaitRequest = PendingIqAwaitRequest(
        takina = takina,
        timeoutMillis = timeoutMillis,
        stanza = IqStanza(
            id = IdUtils.newStanzaId("omemo-devices-get"),
            from = from,
            to = owner.bareJid,
            type = IqType.GET,
            payload = XmlElement(
                name = "pubsub",
                namespace = PUBSUB_NAMESPACE,
                children = listOf(XmlElement(name = "items", attributes = mapOf("node" to DEVICES_NODE))),
            ),
        ),
    )

    fun fetchBundleAwait(owner: Jid, deviceId: Int, from: Jid? = null, timeoutMillis: Long = 15_000): PendingIqAwaitRequest {
        require(deviceId > 0) { "deviceId 必须大于 0" }
        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("omemo-bundle-get"),
                from = from,
                to = owner.bareJid,
                type = IqType.GET,
                payload = XmlElement(
                    name = "pubsub",
                    namespace = PUBSUB_NAMESPACE,
                    children = listOf(XmlElement(name = "items", attributes = mapOf("node" to "$BUNDLES_NODE_PREFIX$deviceId"))),
                ),
            ),
        )
    }

    fun publishDeviceList(deviceIds: List<Int>, from: Jid? = null): PendingStanzaRequest {
        require(deviceIds.isNotEmpty()) { "deviceIds 不能为空" }
        val list = XmlElement(
            name = "devices",
            namespace = OMEMO_NAMESPACE,
            children = deviceIds.distinct().sorted().map { XmlElement(name = "device", attributes = mapOf("id" to it.toString())) },
        )
        return PendingStanzaRequest(
            takina = takina,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("omemo-devices-pub"),
                from = from,
                type = IqType.SET,
                payload = XmlElement(
                    name = "pubsub",
                    namespace = PUBSUB_NAMESPACE,
                    children = listOf(
                        XmlElement(
                            name = "publish",
                            attributes = mapOf("node" to DEVICES_NODE),
                            children = listOf(XmlElement(name = "item", attributes = mapOf("id" to "current"), children = listOf(list))),
                        ),
                    ),
                ),
            ),
        )
    }

    fun publishBundle(deviceId: Int, bundle: PublicBundle, from: Jid? = null): PendingStanzaRequest {
        require(deviceId > 0) { "deviceId 必须大于 0" }
        val bundleElement = XmlElement(
            name = "bundle",
            namespace = OMEMO_NAMESPACE,
            children = listOf(
                XmlElement(name = "spk", attributes = mapOf("id" to bundle.signedPreKeyId.toString()), text = Base64Codec.encode(bundle.signedPreKeyPublicKey)),
                XmlElement(name = "spks", text = Base64Codec.encode(bundle.signedPreKeySignature)),
                XmlElement(name = "ik", text = Base64Codec.encode(bundle.identityPublicKey)),
                XmlElement(
                    name = "prekeys",
                    children = bundle.preKeys.map { key ->
                        XmlElement(name = "pk", attributes = mapOf("id" to key.id.toString()), text = Base64Codec.encode(key.publicKey))
                    },
                ),
            ),
        )
        return PendingStanzaRequest(
            takina = takina,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("omemo-bundle-pub"),
                from = from,
                type = IqType.SET,
                payload = XmlElement(
                    name = "pubsub",
                    namespace = PUBSUB_NAMESPACE,
                    children = listOf(
                        XmlElement(
                            name = "publish",
                            attributes = mapOf("node" to "$BUNDLES_NODE_PREFIX$deviceId"),
                            children = listOf(XmlElement(name = "item", attributes = mapOf("id" to "current"), children = listOf(bundleElement))),
                        ),
                    ),
                ),
            ),
        )
    }

    fun parseDeviceListResult(xml: String): ParsedDeviceList? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "iq" || root.attributes["type"] != IqType.RESULT.wireValue) return null
        val itemsTag = ITEMS_TAG_REGEX.find(xml) ?: return null
        val itemsAttrs = XmlRegexUtils.parseAttributes(itemsTag.groupValues[1])
        if (itemsAttrs["node"] != DEVICES_NODE) return null
        val devicesBounds = XmlRegexUtils.findElementBounds(xml, "devices", itemsTag.range.last + 1) ?: return null
        val devicesXml = xml.substring(devicesBounds)
        val devicesTag = DEVICES_TAG_REGEX.find(devicesXml) ?: return null
        if (XmlRegexUtils.extractNamespace(XmlRegexUtils.parseAttributes(devicesTag.groupValues[1])) != OMEMO_NAMESPACE) return null
        val ids = DEVICE_TAG_REGEX.findAll(devicesXml).mapNotNull { m -> XmlRegexUtils.parseAttributes(m.groupValues[1])["id"]?.toIntOrNull() }.filter { it > 0 }.distinct().sorted().toList()
        return ParsedDeviceList(owner = root.attributes["from"]?.toJidOrNull()?.bareJid, devices = ids)
    }

    fun parseBundleResult(xml: String): ParsedBundle? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "iq" || root.attributes["type"] != IqType.RESULT.wireValue) return null
        val itemsTag = ITEMS_TAG_REGEX.find(xml) ?: return null
        val itemsAttrs = XmlRegexUtils.parseAttributes(itemsTag.groupValues[1])
        val node = itemsAttrs["node"] ?: return null
        if (!node.startsWith(BUNDLES_NODE_PREFIX)) return null
        val deviceId = node.removePrefix(BUNDLES_NODE_PREFIX).toIntOrNull() ?: return null
        val bundleBounds = XmlRegexUtils.findElementBounds(xml, "bundle", itemsTag.range.last + 1) ?: return null
        val bundleXml = xml.substring(bundleBounds)
        val bundleTag = BUNDLE_TAG_REGEX.find(bundleXml) ?: return null
        if (XmlRegexUtils.extractNamespace(XmlRegexUtils.parseAttributes(bundleTag.groupValues[1])) != OMEMO_NAMESPACE) return null

        val spkMatch = SPK_TAG_REGEX.find(bundleXml) ?: return null
        val spkAttrs = XmlRegexUtils.parseAttributes(spkMatch.groupValues[1])
        val signedPreKeyId = spkAttrs["id"]?.toIntOrNull() ?: return null
        val signedPreKeyPublic = runCatching { Base64Codec.decode(spkMatch.groupValues[2].trim()) }.getOrNull() ?: return null
        val signedPreKeySignature = textOfTag(bundleXml, "spks")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        val identityKey = textOfTag(bundleXml, "ik")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        if (!OmemoCrypto.verify(identityKey, signedPreKeyPublic, signedPreKeySignature)) return null
        val prekeysBounds = XmlRegexUtils.findElementBounds(bundleXml, "prekeys") ?: return null
        val prekeysXml = bundleXml.substring(prekeysBounds)
        val preKeys = PK_TAG_REGEX.findAll(prekeysXml).mapNotNull { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val id = attrs["id"]?.toIntOrNull() ?: return@mapNotNull null
            val key = runCatching { Base64Codec.decode(match.groupValues[2].trim()) }.getOrNull() ?: return@mapNotNull null
            RemotePreKey(id = id, publicKey = key)
        }.sortedBy { it.id }.toList()
        if (preKeys.isEmpty()) return null
        return ParsedBundle(
            owner = root.attributes["from"]?.toJidOrNull()?.bareJid,
            deviceId = deviceId,
            bundle = PublicBundle(
                signedPreKeyId = signedPreKeyId,
                signedPreKeyPublicKey = signedPreKeyPublic,
                signedPreKeySignature = signedPreKeySignature,
                identityPublicKey = identityKey,
                preKeys = preKeys,
            ),
        )
    }

    fun cacheDeviceList(account: BareJid, parsed: ParsedDeviceList) {
        val owner = parsed.owner ?: return
        store.saveRemoteDeviceIds(account, owner, parsed.devices)
    }

    fun cacheBundle(account: BareJid, parsed: ParsedBundle) {
        val owner = parsed.owner ?: return
        store.saveRemoteBundle(account, owner, parsed.deviceId, parsed.bundle)
    }

    suspend fun syncContactMaterial(from: BareJid, contact: BareJid, timeoutMillis: Long = 15_000): SyncResult {
        val deviceListResult = fetchDeviceListAwait(owner = contact, from = from, timeoutMillis = timeoutMillis).awaitResult()
        val parsedList = parseDeviceListResult(deviceListResult.xml)
        if (parsedList == null) return SyncResult(devices = emptyList(), fetchedBundles = 0)
        cacheDeviceList(from, parsedList)
        var fetched = 0
        parsedList.devices.forEach { deviceId ->
            val bundleResult = fetchBundleAwait(owner = contact, deviceId = deviceId, from = from, timeoutMillis = timeoutMillis).awaitResult()
            val parsedBundle = parseBundleResult(bundleResult.xml) ?: return@forEach
            cacheBundle(from, parsedBundle)
            fetched += 1
        }
        return SyncResult(devices = parsedList.devices, fetchedBundles = fetched)
    }

    fun encryptMessage(from: BareJid, to: BareJid, plaintext: String, includeSenderDevice: Boolean = true, messageType: MessageType = MessageType.CHAT): MessageStanza {
        val local = bootstrapLocalDevice(from)
        val keysByJid = linkedMapOf<BareJid, MutableList<EncryptedKeyEnvelope>>()
        val recipientDevices = store.loadRemoteDeviceIds(from, to).orEmpty()
        require(recipientDevices.isNotEmpty()) { "未找到目标设备列表，请先拉取并缓存 $to 的 devices" }
        val payloadKey = OmemoCrypto.randomBytes(PAYLOAD_KEY_SIZE)
        val payloadIv = OmemoCrypto.randomBytes(IV_SIZE)
        val payloadCipher = OmemoCrypto.aesGcmEncrypt(payloadKey, payloadIv, plaintext.encodeToByteArray())
        val payloadPlain = payloadKey + payloadIv

        recipientDevices.forEach { deviceId ->
            val bundle = store.loadRemoteBundle(from, to, deviceId) ?: return@forEach
            val encrypted = encryptPayloadKeyForDevice(senderDeviceId = local.deviceId, recipientDeviceId = deviceId, recipientSignedPreKey = bundle.signedPreKeyPublicKey, payloadPlain = payloadPlain)
            keysByJid.getOrPut(to) { mutableListOf() } += encrypted
        }

        if (includeSenderDevice) {
            val selfEncrypted = encryptPayloadKeyForDevice(senderDeviceId = local.deviceId, recipientDeviceId = local.deviceId, recipientSignedPreKey = local.signedPreKeyPublicKey, payloadPlain = payloadPlain)
            keysByJid.getOrPut(from) { mutableListOf() } += selfEncrypted
        }

        require(keysByJid.values.any { it.isNotEmpty() }) { "未找到可用的加密目标设备，请先同步 bundle" }

        val encryptedElement = XmlElement(
            name = "encrypted",
            namespace = OMEMO_NAMESPACE,
            children = listOf(
                XmlElement(
                    name = "header",
                    attributes = mapOf("sid" to local.deviceId.toString()),
                    children = buildList {
                        keysByJid.forEach { (jid, keys) ->
                            add(
                                XmlElement(
                                    name = "keys",
                                    attributes = mapOf("jid" to jid.toString()),
                                    children = keys.map { it.toXmlElement() },
                                ),
                            )
                        }
                    },
                ),
                XmlElement(name = "payload", text = Base64Codec.encode(payloadCipher)),
            ),
        )

        return MessageStanza(
            id = IdUtils.newStanzaId("omemo-msg"),
            from = from,
            to = to,
            type = messageType,
            body = null,
            extensions = listOf(encryptedElement),
        )
    }

    fun sendEncryptedMessage(from: BareJid, to: BareJid, plaintext: String, includeSenderDevice: Boolean = true, messageType: MessageType = MessageType.CHAT): MessageStanza =
        encryptMessage(from = from, to = to, plaintext = plaintext, includeSenderDevice = includeSenderDevice, messageType = messageType).also { takina.sendMessage(it) }

    fun decryptMessage(self: BareJid, messageXml: String): DecryptedOmemoMessage? {
        val local = store.loadLocalDevice(self) ?: return null
        val parsedRoot = runCatching { XmlParser.parseRoot(messageXml) }.getOrNull() ?: return null
        if (parsedRoot.rootName.substringAfter(':') != "message") return null
        val encryptedBounds = XmlRegexUtils.findElementBounds(messageXml, "encrypted") ?: return null
        val encryptedXml = messageXml.substring(encryptedBounds)
        val encryptedTag = ENCRYPTED_TAG_REGEX.find(encryptedXml) ?: return null
        if (XmlRegexUtils.extractNamespace(XmlRegexUtils.parseAttributes(encryptedTag.groupValues[1])) != OMEMO_NAMESPACE) return null
        val headerBounds = XmlRegexUtils.findElementBounds(encryptedXml, "header") ?: return null
        val headerXml = encryptedXml.substring(headerBounds)
        val sid = HEADER_TAG_REGEX.find(headerXml)?.let { XmlRegexUtils.parseAttributes(it.groupValues[1])["sid"]?.toIntOrNull() } ?: return null
        var searchFrom = 0
        var keyPacket: ByteArray? = null
        while (true) {
            val keysBounds = XmlRegexUtils.findElementBounds(headerXml, "keys", searchFrom) ?: break
            val keysXml = headerXml.substring(keysBounds)
            keyPacket = KEY_TAG_REGEX.findAll(keysXml).mapNotNull { match ->
                val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
                val rid = attrs["rid"]?.toIntOrNull() ?: return@mapNotNull null
                if (rid != local.deviceId) return@mapNotNull null
                runCatching { Base64Codec.decode(match.groupValues[2].trim()) }.getOrNull()
            }.firstOrNull()
            if (keyPacket != null) break
            searchFrom = keysBounds.last + 1
        }
        if (keyPacket == null) return null

        val payloadCipher = textOfTag(encryptedXml, "payload")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        val payloadPlain = decryptPayloadKeyFromPacket(local, keyPacket, sid, local.deviceId) ?: return null
        if (payloadPlain.size < PAYLOAD_KEY_SIZE + IV_SIZE) return null
        val payloadKey = payloadPlain.copyOfRange(0, PAYLOAD_KEY_SIZE)
        val payloadIv = payloadPlain.copyOfRange(PAYLOAD_KEY_SIZE, PAYLOAD_KEY_SIZE + IV_SIZE)
        val plaintext = runCatching { OmemoCrypto.aesGcmDecrypt(payloadKey, payloadIv, payloadCipher) }.getOrNull() ?: return null

        return DecryptedOmemoMessage(
            from = parsedRoot.attributes["from"]?.toJidOrNull(),
            senderDeviceId = sid,
            plaintext = plaintext.decodeToString(),
        )
    }

    private fun encryptPayloadKeyForDevice(senderDeviceId: Int, recipientDeviceId: Int, recipientSignedPreKey: ByteArray, payloadPlain: ByteArray): EncryptedKeyEnvelope {
        val ephemeral = OmemoCrypto.generateEcKeyPair()
        val shared = OmemoCrypto.deriveSharedSecret(ephemeral.privateKey, recipientSignedPreKey)
        val wrapKey = OmemoCrypto.hkdfSha256(shared, salt = ByteArray(0), info = WRAP_INFO.encodeToByteArray(), size = 32)
        val wrapIv = OmemoCrypto.randomBytes(IV_SIZE)
        val aad = "$senderDeviceId:$recipientDeviceId".encodeToByteArray()
        val wrappedPayload = OmemoCrypto.aesGcmEncrypt(wrapKey, wrapIv, payloadPlain, aad)
        return EncryptedKeyEnvelope(
            recipientDeviceId = recipientDeviceId,
            preKey = true,
            ephemeralPublic = ephemeral.publicKey,
            wrapIv = wrapIv,
            wrappedPayload = wrappedPayload,
        )
    }

    private fun decryptPayloadKeyFromPacket(local: LocalDeviceState, packet: ByteArray, senderDeviceId: Int, recipientDeviceId: Int): ByteArray? {
        if (packet.size < 1 + 2 + IV_SIZE + 1) return null
        val version = packet[0].toInt() and 0xFF
        if (version != PACKET_VERSION) return null
        val pubSize = ((packet[1].toInt() and 0xFF) shl 8) or (packet[2].toInt() and 0xFF)
        val pubStart = 3
        val pubEnd = pubStart + pubSize
        if (packet.size <= pubEnd + IV_SIZE) return null
        val ephemeralPublic = packet.copyOfRange(pubStart, pubEnd)
        val wrapIv = packet.copyOfRange(pubEnd, pubEnd + IV_SIZE)
        val wrapped = packet.copyOfRange(pubEnd + IV_SIZE, packet.size)
        val shared = OmemoCrypto.deriveSharedSecret(local.signedPreKeyPrivateKey, ephemeralPublic)
        val wrapKey = OmemoCrypto.hkdfSha256(shared, salt = ByteArray(0), info = WRAP_INFO.encodeToByteArray(), size = 32)
        val aad = "$senderDeviceId:$recipientDeviceId".encodeToByteArray()
        return runCatching { OmemoCrypto.aesGcmDecrypt(wrapKey, wrapIv, wrapped, aad) }.getOrNull()
    }

    private fun textOfTag(xml: String, localName: String): String? {
        val bounds = XmlRegexUtils.findElementBounds(xml, localName) ?: return null
        val element = xml.substring(bounds)
        val openEnd = element.indexOf('>')
        val closeStart = element.lastIndexOf("</")
        if (openEnd < 0 || closeStart <= openEnd) return null
        return element.substring(openEnd + 1, closeStart).trim().takeIf { it.isNotEmpty() }
    }

    data class ParsedDeviceList(
        val owner: BareJid?,
        val devices: List<Int>,
    )

    data class ParsedBundle(
        val owner: BareJid?,
        val deviceId: Int,
        val bundle: PublicBundle,
    )

    data class PublicBundle(
        val signedPreKeyId: Int,
        val signedPreKeyPublicKey: ByteArray,
        val signedPreKeySignature: ByteArray,
        val identityPublicKey: ByteArray,
        val preKeys: List<RemotePreKey>,
    )

    data class RemotePreKey(
        val id: Int,
        val publicKey: ByteArray,
    )

    data class LocalPreKey(
        val id: Int,
        val publicKey: ByteArray,
        val privateKey: ByteArray,
    )

    data class LocalDeviceState(
        val account: BareJid,
        val deviceId: Int,
        val identityPublicKey: ByteArray,
        val identityPrivateKey: ByteArray,
        val signedPreKeyId: Int,
        val signedPreKeyPublicKey: ByteArray,
        val signedPreKeyPrivateKey: ByteArray,
        val signedPreKeySignature: ByteArray,
        val preKeys: List<LocalPreKey>,
    ) {
        fun toPublicBundle(): PublicBundle = PublicBundle(
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublicKey = signedPreKeyPublicKey,
            signedPreKeySignature = signedPreKeySignature,
            identityPublicKey = identityPublicKey,
            preKeys = preKeys.map { RemotePreKey(id = it.id, publicKey = it.publicKey) },
        )
    }

    data class DecryptedOmemoMessage(
        val from: Jid?,
        val senderDeviceId: Int,
        val plaintext: String,
    )

    data class SyncResult(
        val devices: List<Int>,
        val fetchedBundles: Int,
    )

    interface OmemoStateStore {
        fun loadLocalDevice(account: BareJid): LocalDeviceState?
        fun saveLocalDevice(state: LocalDeviceState)
        fun loadRemoteDeviceIds(account: BareJid, owner: BareJid): List<Int>?
        fun saveRemoteDeviceIds(account: BareJid, owner: BareJid, deviceIds: List<Int>)
        fun loadRemoteBundle(account: BareJid, owner: BareJid, deviceId: Int): PublicBundle?
        fun saveRemoteBundle(account: BareJid, owner: BareJid, deviceId: Int, bundle: PublicBundle)
    }

    class InMemoryOmemoStateStore : OmemoStateStore {
        private val localDevices = linkedMapOf<BareJid, LocalDeviceState>()
        private val remoteDeviceIds = linkedMapOf<Pair<BareJid, BareJid>, List<Int>>()
        private val remoteBundles = linkedMapOf<Triple<BareJid, BareJid, Int>, PublicBundle>()

        override fun loadLocalDevice(account: BareJid): LocalDeviceState? = synchronized(localDevices) { localDevices[account] }

        override fun saveLocalDevice(state: LocalDeviceState) {
            synchronized(localDevices) { localDevices[state.account] = state }
        }

        override fun loadRemoteDeviceIds(account: BareJid, owner: BareJid): List<Int>? =
            synchronized(remoteDeviceIds) { remoteDeviceIds[account to owner] }

        override fun saveRemoteDeviceIds(account: BareJid, owner: BareJid, deviceIds: List<Int>) {
            synchronized(remoteDeviceIds) { remoteDeviceIds[account to owner] = deviceIds.distinct().sorted() }
        }

        override fun loadRemoteBundle(account: BareJid, owner: BareJid, deviceId: Int): PublicBundle? =
            synchronized(remoteBundles) { remoteBundles[Triple(account, owner, deviceId)] }

        override fun saveRemoteBundle(account: BareJid, owner: BareJid, deviceId: Int, bundle: PublicBundle) {
            synchronized(remoteBundles) { remoteBundles[Triple(account, owner, deviceId)] = bundle }
        }
    }

    private class EncryptedKeyEnvelope(
        val recipientDeviceId: Int,
        val preKey: Boolean,
        val ephemeralPublic: ByteArray,
        val wrapIv: ByteArray,
        val wrappedPayload: ByteArray,
    ) {
        fun toXmlElement(): XmlElement {
            val packet = ByteArray(1 + 2 + ephemeralPublic.size + wrapIv.size + wrappedPayload.size)
            packet[0] = PACKET_VERSION.toByte()
            packet[1] = ((ephemeralPublic.size ushr 8) and 0xFF).toByte()
            packet[2] = (ephemeralPublic.size and 0xFF).toByte()
            ephemeralPublic.copyInto(packet, destinationOffset = 3)
            wrapIv.copyInto(packet, destinationOffset = 3 + ephemeralPublic.size)
            wrappedPayload.copyInto(packet, destinationOffset = 3 + ephemeralPublic.size + wrapIv.size)
            return XmlElement(
                name = "key",
                attributes = buildMap {
                    put("rid", recipientDeviceId.toString())
                    if (preKey) put("prekey", "true")
                },
                text = Base64Codec.encode(packet),
            )
        }
    }
}

val TakinaContext.omemo: OmemoComponent get() = requireComponent(OmemoComponent)

private val ITEMS_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?items\b([^>]*)>""")
private val DEVICES_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?devices\b([^>]*)>""")
private val DEVICE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?device\b([^>]*)/?>""")
private val BUNDLE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?bundle\b([^>]*)>""")
private val SPK_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?spk\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?spk\s*>""")
private val PK_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?pk\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?pk\s*>""")
private val ENCRYPTED_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?encrypted\b([^>]*)>""")
private val HEADER_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?header\b([^>]*)>""")
private val KEY_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?key\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?key\s*>""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()
