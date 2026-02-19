package org.atoriapps.takina.core.requests

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaConfigDsl
import org.atoriapps.takina.core.connections.IqResult
import org.atoriapps.takina.core.exceptions.InvalidRequestException
import org.atoriapps.takina.core.xmpp.Jid
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
        val stanza = MessageRequestBuilder().apply(init).build()
        return PendingMessageRequest(takina, stanza)
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
) {
    fun send() {
        takina.sendMessage(stanza)
    }

    fun toXml(): String = stanza.toXml()
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

    fun build(): MessageStanza {
        val destination = to ?: throw InvalidRequestException("message.to is required")
        if (body.isNullOrBlank() && subject.isNullOrBlank()) {
            throw InvalidRequestException("message.body or message.subject is required")
        }
        return MessageStanza(
            id = id ?: org.atoriapps.takina.core.utils.IdUtils.newStanzaId("msg"),
            from = from,
            to = destination,
            type = type,
            body = body,
            subject = subject,
            thread = thread,
        )
    }
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
