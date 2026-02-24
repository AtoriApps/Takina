package org.atoriapps.takina.core.requests

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaConfigDsl
import org.atoriapps.takina.core.components.OmemoComponent
import org.atoriapps.takina.core.components.OmemoComponent.OmemoProtocolPreference
import org.atoriapps.takina.core.connections.IqResult
import org.atoriapps.takina.core.exceptions.InvalidRequestException
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import org.atoriapps.takina.core.xmpp.stanzas.MessageStanza
import org.atoriapps.takina.core.xmpp.stanzas.MessageType
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza
import org.atoriapps.takina.core.xmpp.stanzas.PresenceType
import org.atoriapps.takina.core.xmpp.stanzas.XmppStanza
import org.atoriapps.takina.core.xml.XmlElement

class TakinaRequest internal constructor(
    private val takina: AbstractTakina,
) {
    fun message(init: MessageRequestBuilder.() -> Unit): PendingMessageRequest {
        val request = MessageRequestBuilder().apply(init).build()
        return PendingMessageRequest(
            takina = takina,
            stanza = request.stanza,
            sourceBody = request.sourceBody,
            encryption = request.encryption,
        )
    }

    fun presence(init: PresenceRequestBuilder.() -> Unit): PendingStanzaRequest {
        val stanza = PresenceRequestBuilder().apply(init).build()
        return PendingStanzaRequest(takina, stanza)
    }

    fun iq(init: IqRequestBuilder.() -> Unit): PendingStanzaRequest {
        val stanza = IqRequestBuilder().apply(init).build()
        return PendingStanzaRequest(takina, stanza)
    }

    fun iqAwait(
        timeoutMillis: Long = 10_000,
        init: IqRequestBuilder.() -> Unit,
    ): PendingIqAwaitRequest {
        val stanza = IqRequestBuilder().apply(init).build()
        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = stanza,
        )
    }

}

class PendingMessageRequest internal constructor(
    private val takina: AbstractTakina,
    val stanza: MessageStanza,
    private val sourceBody: String? = stanza.body,
    private val encryption: MessageEncryptionRequest? = null,
) {
    fun send() {
        val encryptionConfig = encryption

        if (encryptionConfig == null) {
            takina.sendMessage(stanza)
            return
        }

        when (val provider = encryptionConfig.provider) {
            is OmemoEncryptionProvider -> sendOmemo(encryptionConfig, provider)
        }
    }

    fun toXml(): String = stanza.toXml()

    private fun sendOmemo(config: MessageEncryptionRequest, provider: OmemoEncryptionProvider) {
        val plaintext = sourceBody?.takeIf { it.isNotBlank() }
            ?: throw InvalidRequestException("message.body is required when encryption.provider=Omemo")

        // TODO：自动解析
        val fromBare = stanza.from?.bareJid
            ?: throw InvalidRequestException("message.from is required when encryption.method=OMEMO")

        val omemo = takina.findComponent(OmemoComponent)

        if (omemo == null) {
            // CHECK：这个不属于加密失败吧...属于用户司马了不注册组件
            if (config.required) throw IllegalStateException("OMEMO 未注册：请在 createTakina 中 registerComponent(OmemoComponent)")
            takina.sendMessage(stanza.copy(body = config.fallbackBody))
            return
        }

        runCatching {
            val encrypted = omemo.encryptMessage(
                from = fromBare,
                to = stanza.to.bareJid,
                plaintext = plaintext,
                messageType = stanza.type,
                preference = provider.preferVersion,
            ).copy(
                id = stanza.id,
                from = stanza.from,
                to = stanza.to,
                body = config.fallbackBody,
                subject = null,
                thread = stanza.thread,
            )
            takina.sendMessage(encrypted)
        }.onFailure { error ->
            if (config.required) throw error
            takina.sendMessage(stanza.copy(body = config.fallbackBody))
        }
    }
}

class PendingStanzaRequest internal constructor(
    private val takina: AbstractTakina,
    val stanza: XmppStanza,
) {
    fun send() {
        takina.sendStanza(stanza)
    }

    fun toXml(): String = stanza.toXml()
}

class PendingIqAwaitRequest internal constructor(
    private val takina: AbstractTakina,
    private val timeoutMillis: Long,
    val stanza: IqStanza,
) {
    suspend fun awaitResult(): IqResult =
        takina.sendIqAndAwaitResult(
            from = stanza.from,
            stanza = stanza,
            timeoutMillis = timeoutMillis,
        )

    fun send() {
        takina.sendStanza(stanza)
    }

    fun toXml(): String = stanza.toXml()
}

@TakinaConfigDsl
class MessageRequestBuilder {
    var id: String? = null
    var from: Jid? = null
    var to: Jid? = null
    var type: MessageType = MessageType.CHAT
    var body: String? = null
    var subject: String? = null
    var thread: String? = null
    private var encryptionConfig: MessageEncryptionRequest? = null

    fun encryption(init: MessageEncryptionBuilder.() -> Unit) {
        encryptionConfig = MessageEncryptionBuilder().apply(init).build()
    }

    fun build(): BuiltMessageRequest {
        val destination = to ?: throw InvalidRequestException("message.to is required")
        if (body.isNullOrBlank() && subject.isNullOrBlank()) {
            throw InvalidRequestException("message.body or message.subject is required")
        }
        if (encryptionConfig?.provider is OmemoEncryptionProvider && body.isNullOrBlank()) {
            throw InvalidRequestException("message.body is required when encryption.provider=Omemo")
        }
        return BuiltMessageRequest(
            stanza = MessageStanza(
                id = id ?: org.atoriapps.takina.core.utils.IdUtils.newStanzaId("msg"),
                from = from,
                to = destination,
                type = type,
                body = body,
                subject = subject,
                thread = thread,
            ),
            sourceBody = body,
            encryption = encryptionConfig,
        )
    }
}

data class BuiltMessageRequest(
    val stanza: MessageStanza,
    val sourceBody: String?,
    val encryption: MessageEncryptionRequest?,
)

sealed interface MessageEncryptionProvider

data class OmemoEncryptionProvider(
    val preferVersion: OmemoProtocolPreference = OmemoProtocolPreference.AUTO,
) : MessageEncryptionProvider

fun omemoProvider(preferVersion: OmemoProtocolPreference = OmemoProtocolPreference.AUTO): OmemoEncryptionProvider =
    OmemoEncryptionProvider(preferVersion = preferVersion)

data class MessageEncryptionRequest(
    val provider: MessageEncryptionProvider,
    val fallbackBody: String,
    val required: Boolean,
)

@TakinaConfigDsl
class MessageEncryptionBuilder {
    var provider: MessageEncryptionProvider? = null
    var fallbackBody: String = "信息已加密，请使用支持 OMEMO 的客户端查看"
    var required: Boolean = true

    fun build(): MessageEncryptionRequest = MessageEncryptionRequest(
        provider = provider ?: throw InvalidRequestException("message.encryption.provider is required"),
        fallbackBody = fallbackBody,
        required = required,
    )
}

@TakinaConfigDsl
class PresenceRequestBuilder {
    var id: String? = null
    var from: Jid? = null
    var to: Jid? = null
    var type: PresenceType = PresenceType.AVAILABLE
    var show: String? = null
    var status: String? = null
    var priority: Int? = null

    fun build(): PresenceStanza = PresenceStanza(
        id = id ?: org.atoriapps.takina.core.utils.IdUtils.newStanzaId("presence"),
        from = from,
        to = to,
        type = type,
        show = show,
        status = status,
        priority = priority,
    )
}

@TakinaConfigDsl
class IqRequestBuilder {
    var id: String? = null
    var from: Jid? = null
    var to: Jid? = null
    var type: IqType? = null
    var payload: XmlElement? = null

    fun build(): IqStanza = IqStanza(
        id = id ?: org.atoriapps.takina.core.utils.IdUtils.newStanzaId("iq"),
        from = from,
        to = to,
        type = type ?: throw InvalidRequestException("iq.type is required"),
        payload = payload,
    )
}
