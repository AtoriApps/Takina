package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.utils.Base64Codec
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.utils.OmemoCrypto
import org.atoriapps.takina.core.utils.OmemoSignal
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
        const val OMEMO_V2_NAMESPACE: String = "urn:xmpp:omemo:2"
        const val OMEMO_V1_NAMESPACE: String = "eu.siacs.conversations.axolotl"
        private const val PAYLOAD_KEY_SIZE = 16
        private const val IV_SIZE = 12
        private const val GCM_TAG_SIZE = 16

        override fun getInstance(context: TakinaContext): OmemoComponent {
            val core = context as? AbstractTakina ?: error("OmemoComponent 只能安装在 Takina 核心上下文中")
            return OmemoComponent(core)
        }

        override fun getComponentType() = OmemoComponent::class
    }

    enum class OmemoProtocolVersion { V1, V2 }

    enum class OmemoProtocolPreference { AUTO, V1, V2 }

    var store: OmemoStateStore = InMemoryOmemoStateStore()
    var localDeviceIdProvider: () -> Int = { Random.nextInt(1, Int.MAX_VALUE) }
    var preKeyCount: Int = 20
    var defaultProtocolPreference: OmemoProtocolPreference = OmemoProtocolPreference.AUTO
    var autoFallbackVersion: OmemoProtocolVersion = OmemoProtocolVersion.V1
    var strictBundleSignatureValidation: Boolean = false

    fun bootstrapLocalDevice(account: BareJid): LocalDeviceState {
        val existing = store.loadLocalDevice(account)
        if (existing != null) return existing

        val signal = OmemoSignal.generateLocalMaterial(preKeyCount)
        val preKeys = signal.preKeys.map { key -> LocalPreKey(id = key.id, publicKey = key.publicKey, privateKey = key.privateKey, record = key.record) }
        val state = LocalDeviceState(
            account = account,
            deviceId = localDeviceIdProvider(),
            identityPublicKey = signal.identityPublicKey,
            identityPrivateKey = signal.identityPrivateKey,
            signedPreKeyId = signal.signedPreKeyId,
            signedPreKeyPublicKey = signal.signedPreKeyPublicKey,
            signedPreKeyPrivateKey = signal.signedPreKeyPrivateKey,
            signedPreKeySignature = signal.signedPreKeySignature,
            registrationId = signal.registrationId,
            identityKeyPair = signal.identityKeyPair,
            signedPreKeyRecord = signal.signedPreKeyRecord,
            preKeys = preKeys,
        )
        store.saveLocalDevice(state)
        return state
    }

    fun publishOwnDeviceList(from: BareJid, preference: OmemoProtocolPreference = defaultProtocolPreference): PendingStanzaRequest {
        val local = bootstrapLocalDevice(from)
        val version = resolveProtocolVersion(from, from, preference)
        return publishDeviceList(deviceIds = listOf(local.deviceId), from = from, version = version)
    }

    fun publishOwnBundle(from: BareJid, preference: OmemoProtocolPreference = defaultProtocolPreference): PendingStanzaRequest {
        val local = bootstrapLocalDevice(from)
        val version = resolveProtocolVersion(from, from, preference)
        return publishBundle(deviceId = local.deviceId, bundle = local.toPublicBundle(), from = from, version = version)
    }

    suspend fun publishOwnMaterial(from: BareJid, preference: OmemoProtocolPreference = defaultProtocolPreference) {
        val local = bootstrapLocalDevice(from)
        val versions = when (preference) {
            OmemoProtocolPreference.V1 -> listOf(OmemoProtocolVersion.V1)
            OmemoProtocolPreference.V2 -> listOf(OmemoProtocolVersion.V2)
            OmemoProtocolPreference.AUTO -> listOf(OmemoProtocolVersion.V2, OmemoProtocolVersion.V1)
        }
        versions.forEach { version ->
            val mergedDevices = loadServerDeviceList(from, version).plus(local.deviceId).distinct().sorted()
            publishDeviceList(deviceIds = mergedDevices, from = from, version = version).send()
            publishBundle(deviceId = local.deviceId, bundle = local.toPublicBundle(), from = from, version = version).send()
            store.saveRemoteDeviceIds(account = from, owner = from, version = version, deviceIds = mergedDevices)
            store.saveRemoteBundle(account = from, owner = from, version = version, deviceId = local.deviceId, bundle = local.toPublicBundle())
            mergedDevices.filter { it != local.deviceId }.forEach { deviceId ->
                val parsedBundle = runCatching {
                    fetchBundleAwait(owner = from, deviceId = deviceId, from = from, version = version).awaitResult()
                }.getOrNull()?.let { parseBundleResult(it.xml) } ?: return@forEach
                if (parsedBundle.version != version) return@forEach
                cacheBundle(from, parsedBundle)
            }
        }
    }

    fun fetchDeviceListAwait(
        owner: Jid,
        from: Jid? = null,
        timeoutMillis: Long = 15_000,
        version: OmemoProtocolVersion = OmemoProtocolVersion.V2,
    ): PendingIqAwaitRequest = PendingIqAwaitRequest(
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
                children = listOf(XmlElement(name = "items", attributes = mapOf("node" to devicesNode(version)))),
            ),
        ),
    )

    fun fetchBundleAwait(
        owner: Jid,
        deviceId: Int,
        from: Jid? = null,
        timeoutMillis: Long = 15_000,
        version: OmemoProtocolVersion = OmemoProtocolVersion.V2,
    ): PendingIqAwaitRequest {
        require(deviceId > 0) { "deviceId 必须大于 0" }
        val items = XmlElement(
            name = "items",
            attributes = mapOf("node" to bundleNode(version, deviceId)),
            children = if (version == OmemoProtocolVersion.V2) listOf(XmlElement(name = "item", attributes = mapOf("id" to deviceId.toString()))) else emptyList(),
        )
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
                    children = listOf(items),
                ),
            ),
        )
    }

    fun publishDeviceList(
        deviceIds: List<Int>,
        from: Jid? = null,
        version: OmemoProtocolVersion = OmemoProtocolVersion.V2,
    ): PendingStanzaRequest {
        require(deviceIds.isNotEmpty()) { "deviceIds 不能为空" }
        val devicesElement = when (version) {
            OmemoProtocolVersion.V2 -> XmlElement(
                name = "devices",
                namespace = namespace(version),
                children = deviceIds.distinct().sorted().map { XmlElement(name = "device", attributes = mapOf("id" to it.toString())) },
            )

            OmemoProtocolVersion.V1 -> XmlElement(
                name = "list",
                namespace = namespace(version),
                children = deviceIds.distinct().sorted().map { XmlElement(name = "device", attributes = mapOf("id" to it.toString())) },
            )
        }
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
                            attributes = mapOf("node" to devicesNode(version)),
                            children = listOf(XmlElement(name = "item", attributes = mapOf("id" to "current"), children = listOf(devicesElement))),
                        ),
                    ),
                ),
            ),
        )
    }

    fun publishBundle(
        deviceId: Int,
        bundle: PublicBundle,
        from: Jid? = null,
        version: OmemoProtocolVersion = OmemoProtocolVersion.V2,
    ): PendingStanzaRequest {
        require(deviceId > 0) { "deviceId 必须大于 0" }
        val bundleElement = when (version) {
            OmemoProtocolVersion.V2 -> XmlElement(
                name = "bundle",
                namespace = namespace(version),
                children = listOf(
                    XmlElement(name = "spk", attributes = mapOf("id" to bundle.signedPreKeyId.toString()), text = Base64Codec.encode(bundle.signedPreKeyPublicKey)),
                    XmlElement(name = "spks", text = Base64Codec.encode(bundle.signedPreKeySignature)),
                    XmlElement(name = "ik", text = Base64Codec.encode(bundle.identityPublicKey)),
                    XmlElement(
                        name = "prekeys",
                        children = bundle.preKeys.map { key -> XmlElement(name = "pk", attributes = mapOf("id" to key.id.toString()), text = Base64Codec.encode(key.publicKey)) },
                    ),
                ),
            )

            OmemoProtocolVersion.V1 -> XmlElement(
                name = "bundle",
                namespace = namespace(version),
                children = listOf(
                    XmlElement(name = "signedPreKeyPublic", attributes = mapOf("signedPreKeyId" to bundle.signedPreKeyId.toString()), text = Base64Codec.encode(bundle.signedPreKeyPublicKey)),
                    XmlElement(name = "signedPreKeySignature", text = Base64Codec.encode(bundle.signedPreKeySignature)),
                    XmlElement(name = "identityKey", text = Base64Codec.encode(bundle.identityPublicKey)),
                    XmlElement(
                        name = "prekeys",
                        children = bundle.preKeys.map { key -> XmlElement(name = "preKeyPublic", attributes = mapOf("preKeyId" to key.id.toString()), text = Base64Codec.encode(key.publicKey)) },
                    ),
                ),
            )
        }
        val itemAttributes = if (version == OmemoProtocolVersion.V2) mapOf("id" to deviceId.toString()) else mapOf("id" to "current")
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
                            attributes = mapOf("node" to bundleNode(version, deviceId)),
                            children = listOf(XmlElement(name = "item", attributes = itemAttributes, children = listOf(bundleElement))),
                        ),
                    ),
                ),
            ),
        )
    }

    fun parseDeviceListResult(xml: String): ParsedDeviceList? {
        val context = parsePubsubItemsContext(xml) ?: return null
        val node = context.node
        
        val version = when (node) {
            devicesNode(OmemoProtocolVersion.V2) -> OmemoProtocolVersion.V2
            devicesNode(OmemoProtocolVersion.V1) -> OmemoProtocolVersion.V1
            else -> return null
        }
        val rootName = if (version == OmemoProtocolVersion.V2) "devices" else "list"
        val devicesBounds = XmlRegexUtils.findElementBounds(xml, rootName, context.itemsTagEnd + 1) ?: return null
        val devicesXml = xml.substring(devicesBounds)
        val openTag = DEVICES_OR_LIST_TAG_REGEX.find(devicesXml) ?: return null
        if (XmlRegexUtils.extractNamespace(XmlRegexUtils.parseAttributes(openTag.groupValues[1])) != namespace(version)) return null
        val ids = DEVICE_TAG_REGEX.findAll(devicesXml).mapNotNull { m -> XmlRegexUtils.parseAttributes(m.groupValues[1])["id"]?.toIntOrNull() }.filter { it > 0 }.distinct().sorted().toList()
        return ParsedDeviceList(owner = context.owner, devices = ids, version = version)
    }

    fun parseBundleResult(xml: String): ParsedBundle? {
        val context = parsePubsubItemsContext(xml) ?: return null
        val node = context.node

        val itemAttrs = ITEM_TAG_REGEX.find(xml.substring(context.itemsTagEnd + 1))?.let { XmlRegexUtils.parseAttributes(it.groupValues[1]) }
        val version: OmemoProtocolVersion
        val deviceId: Int
        when {
            node == "$OMEMO_V2_NAMESPACE:bundles" -> {
                version = OmemoProtocolVersion.V2
                deviceId = itemAttrs?.get("id")?.toIntOrNull() ?: return null
            }

            node.startsWith("${OMEMO_V2_NAMESPACE}:bundles:") -> {
                version = OmemoProtocolVersion.V2
                deviceId = node.substringAfterLast(':').toIntOrNull() ?: return null
            }

            node.startsWith("${OMEMO_V1_NAMESPACE}.bundles:") -> {
                version = OmemoProtocolVersion.V1
                deviceId = node.substringAfterLast(':').toIntOrNull() ?: return null
            }

            else -> return null
        }

        val bundleBounds = XmlRegexUtils.findElementBounds(xml, "bundle", context.itemsTagEnd + 1) ?: return null
        val bundleXml = xml.substring(bundleBounds)
        val bundleTag = BUNDLE_TAG_REGEX.find(bundleXml) ?: return null
        if (XmlRegexUtils.extractNamespace(XmlRegexUtils.parseAttributes(bundleTag.groupValues[1])) != namespace(version)) return null

        val parsedBundle = when (version) {
            OmemoProtocolVersion.V2 -> parseV2Bundle(bundleXml, deviceId = deviceId)
            OmemoProtocolVersion.V1 -> parseV1Bundle(bundleXml, deviceId = deviceId)
        } ?: return null

        return ParsedBundle(owner = context.owner, deviceId = deviceId, bundle = parsedBundle, version = version)
    }

    fun cacheDeviceList(account: BareJid, parsed: ParsedDeviceList) {
        val owner = parsed.owner ?: return
        store.saveRemoteDeviceIds(account, owner, parsed.version, parsed.devices)
    }

    fun cacheBundle(account: BareJid, parsed: ParsedBundle) {
        val owner = parsed.owner ?: return
        store.saveRemoteBundle(account, owner, parsed.version, parsed.deviceId, parsed.bundle)
    }

    suspend fun syncContactMaterial(
        from: BareJid,
        contact: BareJid,
        timeoutMillis: Long = 15_000,
        preference: OmemoProtocolPreference = defaultProtocolPreference,
    ): SyncResult {
        val versions = when (preference) {
            OmemoProtocolPreference.V1 -> listOf(OmemoProtocolVersion.V1)
            OmemoProtocolPreference.V2 -> listOf(OmemoProtocolVersion.V2)
            OmemoProtocolPreference.AUTO -> listOf(OmemoProtocolVersion.V2, OmemoProtocolVersion.V1)
        }

        val results = linkedMapOf<OmemoProtocolVersion, SyncResult>()
        var supportsV1 = false
        var supportsV2 = false

        versions.forEach { version ->
            val deviceListResult = runCatching {
                fetchDeviceListAwait(owner = contact, from = from, timeoutMillis = timeoutMillis, version = version).awaitResult()
            }.getOrNull() ?: return@forEach

            val parsedList = parseDeviceListResult(deviceListResult.xml)
            if (parsedList == null || parsedList.version != version) return@forEach

            cacheDeviceList(from, parsedList)
            var fetched = 0
            parsedList.devices.forEach { deviceId ->
                val bundleResult = runCatching {
                    fetchBundleAwait(owner = contact, deviceId = deviceId, from = from, timeoutMillis = timeoutMillis, version = version).awaitResult()
                }.getOrNull() ?: run {
                    LogUtils.warn(TAG, "OMEMO 拉取 bundle 失败", "contact=$contact", "version=$version", "deviceId=$deviceId")
                    return@forEach
                }
                val parsedBundle = parseBundleResult(bundleResult.xml)
                if (parsedBundle == null || parsedBundle.version != version) {
                    LogUtils.warn(TAG, "OMEMO 解析 bundle 失败", "contact=$contact", "version=$version", "deviceId=$deviceId")
                    return@forEach
                }
                cacheBundle(from, parsedBundle)
                fetched += 1
            }

            if (version == OmemoProtocolVersion.V1) supportsV1 = true
            if (version == OmemoProtocolVersion.V2) supportsV2 = true
            results[version] = SyncResult(version = version, devices = parsedList.devices, fetchedBundles = fetched)
        }

        val previous = store.loadRemoteSupport(from, contact)
        val support = RemoteOmemoSupport(
            supportsV2 = supportsV2 || previous?.supportsV2 == true,
            supportsV1 = supportsV1 || previous?.supportsV1 == true,
        )
        store.saveRemoteSupport(from, contact, support)

        val selectedVersion = when (preference) {
            OmemoProtocolPreference.V1 -> OmemoProtocolVersion.V1
            OmemoProtocolPreference.V2 -> OmemoProtocolVersion.V2
            OmemoProtocolPreference.AUTO -> when {
                results[OmemoProtocolVersion.V1]?.devices?.isNotEmpty() == true -> OmemoProtocolVersion.V1
                results[OmemoProtocolVersion.V2]?.devices?.isNotEmpty() == true -> OmemoProtocolVersion.V2
                support.supportsV1 -> OmemoProtocolVersion.V1
                support.supportsV2 -> OmemoProtocolVersion.V2
                else -> autoFallbackVersion
            }
        }

        return results[selectedVersion] ?: SyncResult(
            version = selectedVersion,
            devices = store.loadRemoteDeviceIds(from, contact, selectedVersion).orEmpty(),
            fetchedBundles = 0,
        )
    }

    private suspend fun loadServerDeviceList(owner: BareJid, version: OmemoProtocolVersion): List<Int> {
        val result = runCatching { fetchDeviceListAwait(owner = owner, from = owner, version = version).awaitResult() }.getOrNull()
            ?: return emptyList()
        val parsed = parseDeviceListResult(result.xml) ?: return emptyList()
        if (parsed.version != version) return emptyList()
        return parsed.devices
    }

    fun encryptMessage(
        from: BareJid,
        to: BareJid,
        plaintext: String,
        includeSenderDevice: Boolean = true,
        messageType: MessageType = MessageType.CHAT,
        preference: OmemoProtocolPreference = defaultProtocolPreference,
    ): MessageStanza {
        val local = bootstrapLocalDevice(from)
        val version = resolveProtocolVersion(from, to, preference)
        val keyEnvelopes = mutableListOf<EncryptedKeyEnvelope>()
        val recipientDevices = store.loadRemoteDeviceIds(from, to, version).orEmpty()
        require(recipientDevices.isNotEmpty()) { "未找到目标设备列表，请先拉取并缓存 $to 的 devices（version=$version）" }
        val payloadKey = OmemoCrypto.randomBytes(PAYLOAD_KEY_SIZE)
        val payloadIv = OmemoCrypto.randomBytes(IV_SIZE)
        val payloadCipherWithTag = OmemoCrypto.aesGcmEncrypt(payloadKey, payloadIv, plaintext.encodeToByteArray())
        val payloadTag = payloadCipherWithTag.copyOfRange(payloadCipherWithTag.size - GCM_TAG_SIZE, payloadCipherWithTag.size)
        val payloadCipher = payloadCipherWithTag.copyOfRange(0, payloadCipherWithTag.size - GCM_TAG_SIZE)
        val payloadPlain = payloadKey + payloadTag

        recipientDevices.forEach { deviceId ->
            val bundle = store.loadRemoteBundle(from, to, version, deviceId) ?: run {
                LogUtils.warn(TAG, "OMEMO 缺少目标设备 bundle，已跳过", "to=$to", "version=$version", "deviceId=$deviceId")
                return@forEach
            }
            runCatching {
                val encrypted = encryptPayloadKeyForDeviceV1(local = local, recipientDeviceId = deviceId, recipientBundle = bundle, recipientJid = to, payloadKeyAndTag = payloadPlain, version = version)
                keyEnvelopes += encrypted
            }.onFailure { error -> LogUtils.warn(TAG, "OMEMO 跳过不可用的目标设备", "to=$to", "version=$version", "deviceId=$deviceId", error.message ?: "未知错误") }
        }

        val ownDevices = store.loadRemoteDeviceIds(from, from, version).orEmpty().filter { it != local.deviceId }
        ownDevices.forEach { deviceId ->
            val bundle = store.loadRemoteBundle(from, from, version, deviceId) ?: run {
                LogUtils.warn(TAG, "OMEMO 缺少自身其它设备 bundle，已跳过", "from=$from", "version=$version", "deviceId=$deviceId")
                return@forEach
            }
            runCatching {
                val encrypted = encryptPayloadKeyForDeviceV1(local = local, recipientDeviceId = deviceId, recipientBundle = bundle, recipientJid = from, payloadKeyAndTag = payloadPlain, version = version)
                keyEnvelopes += encrypted
            }.onFailure { error -> LogUtils.warn(TAG, "OMEMO 跳过自身其它设备", "from=$from", "version=$version", "deviceId=$deviceId", error.message ?: "未知错误") }
        }

        if (includeSenderDevice) {
            runCatching {
                val selfEncrypted = encryptPayloadKeyForDeviceV1(local = local, recipientDeviceId = local.deviceId, recipientBundle = local.toPublicBundle(), recipientJid = from, payloadKeyAndTag = payloadPlain, version = version)
                keyEnvelopes += selfEncrypted
            }.onFailure { error -> LogUtils.warn(TAG, "OMEMO 自身设备加密失败，将不包含自身 key", "from=$from", "version=$version", error.message ?: "未知错误") }
        }

        require(keyEnvelopes.isNotEmpty()) { "未找到可用的加密目标设备，请先同步 bundle（version=$version）" }

        val encryptedElement = XmlElement(
            name = "encrypted",
            namespace = namespace(version),
            children = listOf(
                XmlElement(
                    name = "header",
                    attributes = mapOf("sid" to local.deviceId.toString()),
                    children = buildList {
                        addAll(keyEnvelopes.map { it.toXmlElement() })
                        add(XmlElement(name = "iv", text = Base64Codec.encode(payloadIv)))
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

    // TIPS：不建议用
    fun sendEncryptedMessage(
        from: BareJid,
        to: BareJid,
        plaintext: String,
        includeSenderDevice: Boolean = true,
        messageType: MessageType = MessageType.CHAT,
        preference: OmemoProtocolPreference = defaultProtocolPreference,
    ): MessageStanza = encryptMessage(from = from, to = to, plaintext = plaintext, includeSenderDevice = includeSenderDevice, messageType = messageType, preference = preference).also { takina.sendMessage(it) }

    fun decryptMessage(self: BareJid, messageXml: String): DecryptedOmemoMessage? {
        val local = store.loadLocalDevice(self) ?: return null
        val parsedRoot = runCatching { XmlParser.parseRoot(messageXml) }.getOrNull() ?: return null
        if (parsedRoot.rootName.substringAfter(':') != "message") return null
        val encryptedBounds = XmlRegexUtils.findElementBounds(messageXml, "encrypted") ?: return null
        val encryptedXml = messageXml.substring(encryptedBounds)
        val encryptedTag = ENCRYPTED_TAG_REGEX.find(encryptedXml) ?: return null
        val encryptedNs = XmlRegexUtils.extractNamespace(XmlRegexUtils.parseAttributes(encryptedTag.groupValues[1])) ?: return null
        val version = when (encryptedNs) {
            OMEMO_V2_NAMESPACE -> OmemoProtocolVersion.V2
            OMEMO_V1_NAMESPACE -> OmemoProtocolVersion.V1
            else -> return null
        }
        val headerBounds = XmlRegexUtils.findElementBounds(encryptedXml, "header") ?: return null
        val headerXml = encryptedXml.substring(headerBounds)
        val sid = HEADER_TAG_REGEX.find(headerXml)?.let { XmlRegexUtils.parseAttributes(it.groupValues[1])["sid"]?.toIntOrNull() } ?: return null
        val keyPacket = KEY_TAG_REGEX.findAll(headerXml).mapNotNull { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val rid = attrs["rid"]?.toIntOrNull() ?: return@mapNotNull null
            if (rid != local.deviceId) return@mapNotNull null
            val packet = runCatching { Base64Codec.decode(match.groupValues[2].trim()) }.getOrNull() ?: return@mapNotNull null
            val isPreKey = attrs["prekey"] == "true"
            packet to isPreKey
        }.firstOrNull()
        if (keyPacket == null) return null
        val payloadCipher = textOfTag(encryptedXml, "payload")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        val senderBare = parsedRoot.attributes["from"]?.toJidOrNull()?.bareJid ?: return null
        val payloadPlain = decryptPayloadKeyFromPacketV1(
            local = local,
            packet = keyPacket.first,
            senderJid = senderBare,
            senderDeviceId = sid,
            preKeyMessage = keyPacket.second,
            version = version,
        ) ?: return null
        if (payloadPlain.size < PAYLOAD_KEY_SIZE + GCM_TAG_SIZE) return null
        val payloadIv = textOfTag(headerXml, "iv")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        val payloadKey = payloadPlain.copyOfRange(0, PAYLOAD_KEY_SIZE)
        val tag = payloadPlain.copyOfRange(PAYLOAD_KEY_SIZE, PAYLOAD_KEY_SIZE + GCM_TAG_SIZE)
        val plaintext = runCatching { OmemoCrypto.aesGcmDecrypt(payloadKey, payloadIv, payloadCipher + tag) }.getOrNull() ?: return null
        return DecryptedOmemoMessage(from = parsedRoot.attributes["from"]?.toJidOrNull(), senderDeviceId = sid, plaintext = plaintext.decodeToString(), version = version)
    }

    fun saveRemoteOmemoSupport(account: BareJid, contact: BareJid, support: RemoteOmemoSupport) {
        store.saveRemoteSupport(account, contact, support)
    }

    private fun resolveProtocolVersion(account: BareJid, contact: BareJid, preference: OmemoProtocolPreference): OmemoProtocolVersion {
        return when (preference) {
            OmemoProtocolPreference.V1 -> OmemoProtocolVersion.V1
            OmemoProtocolPreference.V2 -> OmemoProtocolVersion.V2
            OmemoProtocolPreference.AUTO -> resolveProtocolAuto(account, contact)
        }
    }

    private fun resolveProtocolAuto(account: BareJid, contact: BareJid): OmemoProtocolVersion {
        val cached = store.loadRemoteSupport(account, contact)

        if (cached != null) {
            if (cached.supportsV1) return OmemoProtocolVersion.V1
            if (cached.supportsV2) return OmemoProtocolVersion.V2
        }

        val disco = takina.findComponent(DiscoveryComponent)?.latestDiscoInfoResult(contact)

        if (disco != null) {
            val features = parseDiscoFeatures(disco.xml)

            val support = RemoteOmemoSupport(
                supportsV2 = features.contains(OMEMO_V2_NAMESPACE),
                supportsV1 = features.contains(OMEMO_V1_NAMESPACE),
            )
            store.saveRemoteSupport(account, contact, support)

            if (support.supportsV1) return OmemoProtocolVersion.V1
            if (support.supportsV2) return OmemoProtocolVersion.V2
        }

        return autoFallbackVersion
    }

    private fun parseDiscoFeatures(xml: String): Set<String> =
        FEATURE_TAG_REGEX.findAll(xml).mapNotNull { match ->
            XmlRegexUtils.parseAttributes(match.groupValues[1])["var"]?.trim()?.takeIf { it.isNotBlank() }
        }.toSet()

    private fun parsePubsubItemsContext(xml: String): ParsedPubsubItemsContext? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "iq") return null
        if (root.attributes["type"] != IqType.RESULT.wireValue) return null
        val itemsTag = ITEMS_TAG_REGEX.find(xml) ?: return null
        val node = XmlRegexUtils.parseAttributes(itemsTag.groupValues[1])["node"] ?: return null
        return ParsedPubsubItemsContext(owner = root.attributes["from"]?.toJidOrNull()?.bareJid, node = node, itemsTagEnd = itemsTag.range.last)
    }

    private fun parseV2Bundle(bundleXml: String, deviceId: Int): PublicBundle? {
        val spkMatch = SPK_TAG_REGEX.find(bundleXml) ?: return null
        val spkAttrs = XmlRegexUtils.parseAttributes(spkMatch.groupValues[1])
        val signedPreKeyId = spkAttrs["id"]?.toIntOrNull() ?: return null
        val signedPreKeyPublic = runCatching { Base64Codec.decode(spkMatch.groupValues[2].trim()) }.getOrNull() ?: return null
        val signedPreKeySignature = textOfTag(bundleXml, "spks")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        val identityKey = textOfTag(bundleXml, "ik")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        if (!OmemoSignal.verifySignedPreKey(identityKey, signedPreKeyPublic, signedPreKeySignature)) {
            if (strictBundleSignatureValidation) return null
            LogUtils.warn(TAG, "OMEMO bundle 签名校验失败，兼容模式继续", "version=V2", "strict=false", "deviceId=$deviceId", "signedPreKeyId=$signedPreKeyId")
        }
        val prekeysBounds = XmlRegexUtils.findElementBounds(bundleXml, "prekeys") ?: return null
        val prekeysXml = bundleXml.substring(prekeysBounds)
        val preKeys = PK_TAG_REGEX.findAll(prekeysXml).mapNotNull { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val id = attrs["id"]?.toIntOrNull() ?: return@mapNotNull null
            val key = runCatching { Base64Codec.decode(match.groupValues[2].trim()) }.getOrNull() ?: return@mapNotNull null
            RemotePreKey(id = id, publicKey = key)
        }.sortedBy { it.id }.toList()
        if (preKeys.isEmpty()) return null
        return PublicBundle(
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublicKey = signedPreKeyPublic,
            signedPreKeySignature = signedPreKeySignature,
            identityPublicKey = identityKey,
            preKeys = preKeys,
        )
    }

    private fun parseV1Bundle(bundleXml: String, deviceId: Int): PublicBundle? {
        val spkMatch = SIGNED_PRE_KEY_PUBLIC_V1_REGEX.find(bundleXml) ?: return null
        val spkAttrs = XmlRegexUtils.parseAttributes(spkMatch.groupValues[1])
        val signedPreKeyId = spkAttrs["signedPreKeyId"]?.toIntOrNull() ?: return null
        val signedPreKeyPublic = runCatching { Base64Codec.decode(spkMatch.groupValues[2].trim()) }.getOrNull() ?: return null
        val signedPreKeySignature = textOfTag(bundleXml, "signedPreKeySignature")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        val identityKey = textOfTag(bundleXml, "identityKey")?.let { runCatching { Base64Codec.decode(it) }.getOrNull() } ?: return null
        if (!OmemoSignal.verifySignedPreKey(identityKey, signedPreKeyPublic, signedPreKeySignature)) {
            if (strictBundleSignatureValidation) return null
            LogUtils.warn(TAG, "OMEMO bundle 签名校验失败，兼容模式继续", "version=V1", "strict=false", "deviceId=$deviceId", "signedPreKeyId=$signedPreKeyId")
        }
        val prekeysBounds = XmlRegexUtils.findElementBounds(bundleXml, "prekeys") ?: return null
        val prekeysXml = bundleXml.substring(prekeysBounds)
        val preKeys = PRE_KEY_PUBLIC_V1_REGEX.findAll(prekeysXml).mapNotNull { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val id = attrs["preKeyId"]?.toIntOrNull() ?: return@mapNotNull null
            val key = runCatching { Base64Codec.decode(match.groupValues[2].trim()) }.getOrNull() ?: return@mapNotNull null
            RemotePreKey(id = id, publicKey = key)
        }.sortedBy { it.id }.toList()
        if (preKeys.isEmpty()) return null
        return PublicBundle(
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublicKey = signedPreKeyPublic,
            signedPreKeySignature = signedPreKeySignature,
            identityPublicKey = identityKey,
            preKeys = preKeys,
        )
    }

    private fun namespace(version: OmemoProtocolVersion): String = if (version == OmemoProtocolVersion.V2) OMEMO_V2_NAMESPACE else OMEMO_V1_NAMESPACE

    private fun devicesNode(version: OmemoProtocolVersion): String = if (version == OmemoProtocolVersion.V2) "$OMEMO_V2_NAMESPACE:devices" else "$OMEMO_V1_NAMESPACE.devicelist"

    private fun bundleNode(version: OmemoProtocolVersion, deviceId: Int): String = if (version == OmemoProtocolVersion.V2) "$OMEMO_V2_NAMESPACE:bundles" else "$OMEMO_V1_NAMESPACE.bundles:$deviceId"

    private fun encryptPayloadKeyForDeviceV1(
        local: LocalDeviceState,
        recipientDeviceId: Int,
        recipientBundle: PublicBundle,
        recipientJid: BareJid,
        payloadKeyAndTag: ByteArray,
        version: OmemoProtocolVersion,
    ): EncryptedKeyEnvelope {
        val preKey = recipientBundle.preKeys.firstOrNull() ?: error("OMEMO 目标 bundle 缺少 preKey")
        val existingSession = store.loadRemoteSession(local.account, recipientJid, version, recipientDeviceId)
        val encrypted = OmemoSignal.encryptKeyTransport(
            localRegistrationId = local.registrationId,
            localIdentityKeyPair = local.identityKeyPair,
            localPreKeyRecords = local.preKeys.map { it.record },
            localSignedPreKeyRecord = local.signedPreKeyRecord,
            existingSessionRecord = existingSession,
            remoteAddress = recipientJid.toString(),
            remoteDeviceId = recipientDeviceId,
            remoteIdentityKey = recipientBundle.identityPublicKey,
            remoteSignedPreKeyId = recipientBundle.signedPreKeyId,
            remoteSignedPreKeyPublicKey = recipientBundle.signedPreKeyPublicKey,
            remoteSignedPreKeySignature = recipientBundle.signedPreKeySignature,
            remotePreKeyId = preKey.id,
            remotePreKeyPublicKey = preKey.publicKey,
            plaintext = payloadKeyAndTag,
        )
        store.saveRemoteSession(local.account, recipientJid, version, recipientDeviceId, encrypted.sessionRecord)
        return EncryptedKeyEnvelope(recipientDeviceId = recipientDeviceId, preKey = encrypted.isPreKeyMessage, packet = encrypted.message)
    }

    private fun decryptPayloadKeyFromPacketV1(
        local: LocalDeviceState,
        packet: ByteArray,
        senderJid: BareJid,
        senderDeviceId: Int,
        preKeyMessage: Boolean,
        version: OmemoProtocolVersion,
    ): ByteArray? {
        val existingSession = store.loadRemoteSession(local.account, senderJid, version, senderDeviceId)
        val decrypted = OmemoSignal.decryptKeyTransport(
            localRegistrationId = local.registrationId,
            localIdentityKeyPair = local.identityKeyPair,
            localPreKeyRecords = local.preKeys.map { it.record },
            localSignedPreKeyRecord = local.signedPreKeyRecord,
            existingSessionRecord = existingSession,
            remoteAddress = senderJid.toString(),
            remoteDeviceId = senderDeviceId,
            message = packet,
            isPreKeyMessage = preKeyMessage,
        ) ?: return null
        store.saveRemoteSession(local.account, senderJid, version, senderDeviceId, decrypted.sessionRecord)
        return decrypted.plaintext
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
        val version: OmemoProtocolVersion,
    )

    data class ParsedBundle(
        val owner: BareJid?,
        val deviceId: Int,
        val bundle: PublicBundle,
        val version: OmemoProtocolVersion,
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
        val record: ByteArray,
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
        val registrationId: Int,
        val identityKeyPair: ByteArray,
        val signedPreKeyRecord: ByteArray,
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
        val version: OmemoProtocolVersion,
    )

    data class SyncResult(
        val version: OmemoProtocolVersion,
        val devices: List<Int>,
        val fetchedBundles: Int,
    )

    private data class ParsedPubsubItemsContext(
        val owner: BareJid?,
        val node: String,
        val itemsTagEnd: Int,
    )

    data class RemoteOmemoSupport(
        val supportsV2: Boolean,
        val supportsV1: Boolean,
    )

    interface OmemoStateStore {
        fun loadLocalDevice(account: BareJid): LocalDeviceState?
        fun saveLocalDevice(state: LocalDeviceState)
        fun loadRemoteDeviceIds(account: BareJid, owner: BareJid, version: OmemoProtocolVersion): List<Int>?
        fun saveRemoteDeviceIds(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceIds: List<Int>)
        fun loadRemoteBundle(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int): PublicBundle?
        fun saveRemoteBundle(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int, bundle: PublicBundle)
        fun loadRemoteSession(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int): ByteArray?
        fun saveRemoteSession(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int, session: ByteArray)
        fun clearRemoteSession(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int)
        fun loadRemoteSupport(account: BareJid, owner: BareJid): RemoteOmemoSupport?
        fun saveRemoteSupport(account: BareJid, owner: BareJid, support: RemoteOmemoSupport)
    }

    class InMemoryOmemoStateStore : OmemoStateStore {
        private val localDevices = linkedMapOf<BareJid, LocalDeviceState>()
        private val remoteDeviceIds = linkedMapOf<Triple<BareJid, BareJid, OmemoProtocolVersion>, List<Int>>()
        private val remoteBundles = linkedMapOf<List<Any>, PublicBundle>()
        private val remoteSessions = linkedMapOf<List<Any>, ByteArray>()
        private val remoteSupports = linkedMapOf<Pair<BareJid, BareJid>, RemoteOmemoSupport>()

        override fun loadLocalDevice(account: BareJid): LocalDeviceState? = synchronized(localDevices) { localDevices[account] }

        override fun saveLocalDevice(state: LocalDeviceState) {
            synchronized(localDevices) { localDevices[state.account] = state }
        }

        override fun loadRemoteDeviceIds(account: BareJid, owner: BareJid, version: OmemoProtocolVersion): List<Int>? =
            synchronized(remoteDeviceIds) { remoteDeviceIds[Triple(account, owner, version)] }

        override fun saveRemoteDeviceIds(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceIds: List<Int>) {
            synchronized(remoteDeviceIds) { remoteDeviceIds[Triple(account, owner, version)] = deviceIds.distinct().sorted() }
        }

        override fun loadRemoteBundle(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int): PublicBundle? =
            synchronized(remoteBundles) { remoteBundles[listOf(account, owner, version, deviceId)] }

        override fun saveRemoteBundle(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int, bundle: PublicBundle) {
            synchronized(remoteBundles) { remoteBundles[listOf(account, owner, version, deviceId)] = bundle }
        }

        override fun loadRemoteSession(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int): ByteArray? =
            synchronized(remoteSessions) { remoteSessions[listOf(account, owner, version, deviceId)]?.copyOf() }

        override fun saveRemoteSession(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int, session: ByteArray) {
            synchronized(remoteSessions) { remoteSessions[listOf(account, owner, version, deviceId)] = session.copyOf() }
        }

        override fun clearRemoteSession(account: BareJid, owner: BareJid, version: OmemoProtocolVersion, deviceId: Int) {
            synchronized(remoteSessions) { remoteSessions.remove(listOf(account, owner, version, deviceId)) }
        }

        override fun loadRemoteSupport(account: BareJid, owner: BareJid): RemoteOmemoSupport? =
            synchronized(remoteSupports) { remoteSupports[account to owner] }

        override fun saveRemoteSupport(account: BareJid, owner: BareJid, support: RemoteOmemoSupport) {
            synchronized(remoteSupports) { remoteSupports[account to owner] = support }
        }
    }

    private class EncryptedKeyEnvelope(
        val recipientDeviceId: Int,
        val preKey: Boolean,
        val packet: ByteArray,
    ) {
        fun toXmlElement(): XmlElement {
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
private val ITEM_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?item\b([^>]*)>""")
private val DEVICES_OR_LIST_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?(?:devices|list)\b([^>]*)>""")
private val DEVICE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?device\b([^>]*)/?>""")
private val BUNDLE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?bundle\b([^>]*)>""")
private val SPK_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?spk\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?spk\s*>""")
private val PK_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?pk\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?pk\s*>""")
private val SIGNED_PRE_KEY_PUBLIC_V1_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?signedPreKeyPublic\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?signedPreKeyPublic\s*>""")
private val PRE_KEY_PUBLIC_V1_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?preKeyPublic\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?preKeyPublic\s*>""")
private val ENCRYPTED_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?encrypted\b([^>]*)>""")
private val HEADER_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?header\b([^>]*)>""")
private val KEY_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?key\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?key\s*>""")
private val FEATURE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?feature\b([^>]*)/?>""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()
