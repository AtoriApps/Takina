package org.atoriapps.takina.examples

import kotlinx.coroutines.runBlocking
import org.atoriapps.takina.core.components.OmemoComponent
import org.atoriapps.takina.core.components.omemo
import org.atoriapps.takina.core.createTakina
import org.atoriapps.takina.core.events.FrameInboundEvent
import org.atoriapps.takina.core.events.FrameOutboundEvent
import org.atoriapps.takina.core.events.StanzaReceivedEvent
import org.atoriapps.takina.core.requests.omemoProvider
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.toBareJid
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * 环境变量：
 * - TAKINA_A_JID / TAKINA_A_PASSWORD
 * - TAKINA_B_JID / TAKINA_B_PASSWORD
 * - TAKINA_OMEMO_MSG_A2B (可选，默认 "hello from A")
 * - TAKINA_OMEMO_MSG_B2A (可选，默认 "hello from B")
 * - TAKINA_OMEMO_STORE_FILE (可选，默认 "./build/omemo-dm-smoke.store")
 */
fun main() = runBlocking {
    val aJid = (System.getenv("TAKINA_A_JID") ?: error("缺少 TAKINA_A_JID")).toBareJid()
    val aPassword = System.getenv("TAKINA_A_PASSWORD") ?: error("缺少 TAKINA_A_PASSWORD")
    val bJid = (System.getenv("TAKINA_B_JID") ?: error("缺少 TAKINA_B_JID")).toBareJid()
    val bPassword = System.getenv("TAKINA_B_PASSWORD") ?: error("缺少 TAKINA_B_PASSWORD")
    val msgA2B = System.getenv("TAKINA_OMEMO_MSG_A2B") ?: "hello from A"
    val msgB2A = System.getenv("TAKINA_OMEMO_MSG_B2A") ?: "hello from B"
    val storeFile = File(System.getenv("TAKINA_OMEMO_STORE_FILE") ?: "./build/omemo-dm-smoke.store")

    val takina = createTakina {
        addAccount {
            jid = aJid
            password = aPassword
            resource = "takina-omemo-a"
        }

        addAccount {
            jid = bJid
            password = bPassword
            resource = "takina-omemo-b"
        }

        onConfigureComponent(OmemoComponent){
            store = FileOmemoStateStore(storeFile)
            println("OMEMO状态存储：${storeFile.absolutePath}")
        }
    }
    takina.events.enableEventLog = false

    takina.events.on(FrameInboundEvent) { println("${it.jid} 入站：${it.xml}") }
    takina.events.on(FrameOutboundEvent) { println("${it.jid} 出站：${it.xml}") }
    takina.events.on(StanzaReceivedEvent) { event ->
        if (event.stanzaType != "message") return@on
        val target = event.jid
        val decrypted = takina.omemo.decryptMessage(self = target, messageXml = event.xml) ?: return@on
        println("OMEMO解密成功：receiver=$target senderDevice=${decrypted.senderDeviceId} text=${decrypted.plaintext}")
    }

    takina.connectAll()

    takina.request.presence {
        from = aJid
        status = "骄傲地宣告：本账号正在扮演A方，进行Takina客户端OmemoDm冒烟测试，爱来自中国"
    }.send()
    takina.request.presence {
        from = bJid
        status = "骄傲地宣告：本账号正在扮演A方，进行Takina客户端OmemoDm冒烟测试，爱来自中国"
    }.send()

    Thread.sleep(500L) // 等待上线完成

    println("作事前准备")
    takina.omemo.bootstrapLocalDevice(aJid)
    takina.omemo.bootstrapLocalDevice(bJid)
    takina.omemo.publishOwnMaterial(aJid)
    takina.omemo.publishOwnMaterial(bJid)

    val syncAB = takina.omemo.syncContactMaterial(from = aJid, contact = bJid)
    val syncBA = takina.omemo.syncContactMaterial(from = bJid, contact = aJid)
    println("同步完成：A->B devices=${syncAB.devices.size} bundles=${syncAB.fetchedBundles}；B->A devices=${syncBA.devices.size} bundles=${syncBA.fetchedBundles}")

    println("发加密消息")
    takina.request.message {
        from = aJid
        to = bJid
        body = msgA2B
        encryption {
            provider = omemoProvider()
            fallbackBody = "这这不能2B"
        }
    }.send()
    takina.request.message {
        from = bJid
        to = aJid
        body = msgB2A
        encryption {
            provider = omemoProvider()
            fallbackBody = "这这不能2A"
        }
    }.send()

    Thread.sleep(120_000L) // 久等一下
    takina.disconnectAll()
}

private class FileOmemoStateStore(private val file: File) : OmemoComponent.OmemoStateStore {
    private val lock = Any()
    private val state = loadState()

    override fun loadLocalDevice(account: BareJid): OmemoComponent.LocalDeviceState? = synchronized(lock) {
        state.localDevices[account.toString()]?.toLocalDevice()
    }

    override fun saveLocalDevice(state: OmemoComponent.LocalDeviceState) = synchronized(lock) {
        this.state.localDevices[state.account.toString()] = PersistedLocalDevice.from(state)
        saveState()
    }

    override fun loadRemoteDeviceIds(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion): List<Int>? = synchronized(lock) {
        state.remoteDeviceIds[deviceKey(account, owner, version)]?.toList()
    }

    override fun saveRemoteDeviceIds(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion, deviceIds: List<Int>) = synchronized(lock) {
        state.remoteDeviceIds[deviceKey(account, owner, version)] = deviceIds.distinct().sorted()
        saveState()
    }

    override fun loadRemoteBundle(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion, deviceId: Int): OmemoComponent.PublicBundle? = synchronized(lock) {
        state.remoteBundles[bundleKey(account, owner, version, deviceId)]?.toPublicBundle()
    }

    override fun saveRemoteBundle(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion, deviceId: Int, bundle: OmemoComponent.PublicBundle) = synchronized(lock) {
        state.remoteBundles[bundleKey(account, owner, version, deviceId)] = PersistedPublicBundle.from(bundle)
        saveState()
    }

    override fun loadRemoteSession(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion, deviceId: Int): ByteArray? = synchronized(lock) {
        state.remoteSessions[bundleKey(account, owner, version, deviceId)]?.copyOf()
    }

    override fun saveRemoteSession(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion, deviceId: Int, session: ByteArray) = synchronized(lock) {
        state.remoteSessions[bundleKey(account, owner, version, deviceId)] = session.copyOf()
        saveState()
    }

    override fun clearRemoteSession(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion, deviceId: Int) = synchronized(lock) {
        state.remoteSessions.remove(bundleKey(account, owner, version, deviceId))
        saveState()
    }

    override fun loadRemoteSupport(account: BareJid, owner: BareJid): OmemoComponent.RemoteOmemoSupport? = synchronized(lock) {
        state.remoteSupports[supportKey(account, owner)]?.toRemoteSupport()
    }

    override fun saveRemoteSupport(account: BareJid, owner: BareJid, support: OmemoComponent.RemoteOmemoSupport) = synchronized(lock) {
        state.remoteSupports[supportKey(account, owner)] = PersistedRemoteSupport.from(support)
        saveState()
    }

    private fun loadState(): PersistedOmemoState = if (!file.exists()) PersistedOmemoState()
    else runCatching {
        ObjectInputStream(file.inputStream().buffered()).use { stream -> stream.readObject() as? PersistedOmemoState ?: PersistedOmemoState() }
    }.onFailure { println("OMEMO状态读取失败，将使用空状态：${it.message}") }.getOrDefault(PersistedOmemoState())

    private fun saveState() {
        runCatching {
            file.parentFile?.mkdirs()
            ObjectOutputStream(file.outputStream().buffered()).use { stream -> stream.writeObject(state) }
        }.onFailure { println("OMEMO状态保存失败：${it.message}") }
    }

    private fun deviceKey(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion) = "${account}|${owner}|${version}"
    private fun bundleKey(account: BareJid, owner: BareJid, version: OmemoComponent.OmemoProtocolVersion, deviceId: Int) = "${account}|${owner}|${version}|${deviceId}"
    private fun supportKey(account: BareJid, owner: BareJid) = "${account}|${owner}"
}

private data class PersistedOmemoState(
    val localDevices: MutableMap<String, PersistedLocalDevice> = linkedMapOf(),
    val remoteDeviceIds: MutableMap<String, List<Int>> = linkedMapOf(),
    val remoteBundles: MutableMap<String, PersistedPublicBundle> = linkedMapOf(),
    val remoteSessions: MutableMap<String, ByteArray> = linkedMapOf(),
    val remoteSupports: MutableMap<String, PersistedRemoteSupport> = linkedMapOf(),
) : Serializable

private data class PersistedRemotePreKey(
    val id: Int,
    val publicKey: ByteArray,
) : Serializable {
    fun toRemotePreKey(): OmemoComponent.RemotePreKey = OmemoComponent.RemotePreKey(id = id, publicKey = publicKey)

    companion object {
        fun from(key: OmemoComponent.RemotePreKey) = PersistedRemotePreKey(id = key.id, publicKey = key.publicKey)
    }
}

private data class PersistedPublicBundle(
    val signedPreKeyId: Int,
    val signedPreKeyPublicKey: ByteArray,
    val signedPreKeySignature: ByteArray,
    val identityPublicKey: ByteArray,
    val preKeys: List<PersistedRemotePreKey>,
) : Serializable {
    fun toPublicBundle(): OmemoComponent.PublicBundle = OmemoComponent.PublicBundle(
        signedPreKeyId = signedPreKeyId,
        signedPreKeyPublicKey = signedPreKeyPublicKey,
        signedPreKeySignature = signedPreKeySignature,
        identityPublicKey = identityPublicKey,
        preKeys = preKeys.map { it.toRemotePreKey() },
    )

    companion object {
        fun from(bundle: OmemoComponent.PublicBundle) = PersistedPublicBundle(
            signedPreKeyId = bundle.signedPreKeyId,
            signedPreKeyPublicKey = bundle.signedPreKeyPublicKey,
            signedPreKeySignature = bundle.signedPreKeySignature,
            identityPublicKey = bundle.identityPublicKey,
            preKeys = bundle.preKeys.map { PersistedRemotePreKey.from(it) },
        )
    }
}

private data class PersistedLocalPreKey(
    val id: Int,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val record: ByteArray,
) : Serializable {
    fun toLocalPreKey(): OmemoComponent.LocalPreKey =
        OmemoComponent.LocalPreKey(id = id, publicKey = publicKey, privateKey = privateKey, record = record)

    companion object {
        fun from(key: OmemoComponent.LocalPreKey) =
            PersistedLocalPreKey(id = key.id, publicKey = key.publicKey, privateKey = key.privateKey, record = key.record)
    }
}

private data class PersistedLocalDevice(
    val account: String,
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
    val preKeys: List<PersistedLocalPreKey>,
) : Serializable {
    fun toLocalDevice(): OmemoComponent.LocalDeviceState = OmemoComponent.LocalDeviceState(
        account = account.toBareJid(),
        deviceId = deviceId,
        identityPublicKey = identityPublicKey,
        identityPrivateKey = identityPrivateKey,
        signedPreKeyId = signedPreKeyId,
        signedPreKeyPublicKey = signedPreKeyPublicKey,
        signedPreKeyPrivateKey = signedPreKeyPrivateKey,
        signedPreKeySignature = signedPreKeySignature,
        registrationId = registrationId,
        identityKeyPair = identityKeyPair,
        signedPreKeyRecord = signedPreKeyRecord,
        preKeys = preKeys.map { it.toLocalPreKey() },
    )

    companion object {
        fun from(device: OmemoComponent.LocalDeviceState) = PersistedLocalDevice(
            account = device.account.toString(),
            deviceId = device.deviceId,
            identityPublicKey = device.identityPublicKey,
            identityPrivateKey = device.identityPrivateKey,
            signedPreKeyId = device.signedPreKeyId,
            signedPreKeyPublicKey = device.signedPreKeyPublicKey,
            signedPreKeyPrivateKey = device.signedPreKeyPrivateKey,
            signedPreKeySignature = device.signedPreKeySignature,
            registrationId = device.registrationId,
            identityKeyPair = device.identityKeyPair,
            signedPreKeyRecord = device.signedPreKeyRecord,
            preKeys = device.preKeys.map { PersistedLocalPreKey.from(it) },
        )
    }
}

private data class PersistedRemoteSupport(
    val supportsV2: Boolean,
    val supportsV1: Boolean,
) : Serializable {
    fun toRemoteSupport(): OmemoComponent.RemoteOmemoSupport = OmemoComponent.RemoteOmemoSupport(supportsV2 = supportsV2, supportsV1 = supportsV1)

    companion object {
        fun from(support: OmemoComponent.RemoteOmemoSupport) = PersistedRemoteSupport(supportsV2 = support.supportsV2, supportsV1 = support.supportsV1)
    }
}
