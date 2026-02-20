package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.toJid
import org.atoriapps.takina.core.xmpp.stanzas.MessageStanza
import org.atoriapps.takina.core.xmpp.stanzas.MessageType

class MessageReceiptsComponent internal constructor(private val takina: AbstractTakina) : TakinaComponent {
    companion object : TakinaComponentProvider<MessageReceiptsComponent> {
        const val NAMESPACE: String = "urn:xmpp:receipts"

        override fun getInstance(context: TakinaContext): MessageReceiptsComponent {
            val core = context as? AbstractTakina
                ?: error("MessageReceiptsComponent 只能安装在 Takina 核心上下文中")
            return MessageReceiptsComponent(core)
        }

        override fun getComponentType() = MessageReceiptsComponent::class
    }

    var autoReplyEnabled: Boolean = true

    fun requestElement(): XmlElement = XmlElement(
        name = "request",
        namespace = NAMESPACE,
    )

    fun receivedElement(messageId: String): XmlElement {
        require(messageId.isNotBlank()) { "messageId 不能为空" }
        return XmlElement(
            name = "received",
            namespace = NAMESPACE,
            attributes = mapOf("id" to messageId),
        )
    }

    fun appendRequest(stanza: MessageStanza): MessageStanza = stanza.copy(
        extensions = stanza.extensions + requestElement(),
    )

    fun appendReceived(stanza: MessageStanza, messageId: String): MessageStanza = stanza.copy(
        extensions = stanza.extensions + receivedElement(messageId),
    )

    fun parseFromMessageXml(xml: String): ReceiptFrame? {
        return parseEnvelope(xml)?.frame
    }

    fun parseEnvelope(xml: String): ParsedReceiptEnvelope? {
        val parsedRoot = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        val rootLocalName = parsedRoot.rootName.substringAfter(':')
        if (rootLocalName != "message") return null

        val received = RECEIVED_TAG_REGEX.find(xml)?.let { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val namespace = XmlRegexUtils.extractNamespace(attrs)
            if (namespace == NAMESPACE) {
                val id = attrs["id"] ?: return@let null
                return@let ParsedReceiptEnvelope(
                    frame = ReceiptFrame.Received(id),
                    from = parsedRoot.attributes["from"]?.toJidOrNull(),
                    to = parsedRoot.attributes["to"]?.toJidOrNull(),
                    messageId = parsedRoot.attributes["id"],
                    type = parsedRoot.attributes["type"],
                )
            }
            null
        }
        if (received != null) return received

        val request = REQUEST_TAG_REGEX.find(xml)?.let { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val namespace = XmlRegexUtils.extractNamespace(attrs)
            if (namespace == NAMESPACE) ParsedReceiptEnvelope(
                frame = ReceiptFrame.Request,
                from = parsedRoot.attributes["from"]?.toJidOrNull(),
                to = parsedRoot.attributes["to"]?.toJidOrNull(),
                messageId = parsedRoot.attributes["id"],
                type = parsedRoot.attributes["type"],
            ) else null
        }
        return request
    }

    fun buildAutoReply(
        selfJid: Jid,
        inboundMessageXml: String,
    ): MessageStanza? = if (!autoReplyEnabled) null
    else buildReceivedReply(selfJid, inboundMessageXml)

    fun buildReceivedReply(
        selfJid: Jid,
        inboundMessageXml: String,
    ): MessageStanza? {
        val envelope = parseEnvelope(inboundMessageXml) ?: return null
        if (envelope.frame !is ReceiptFrame.Request) return null

        val requestId = envelope.messageId ?: return null
        val sender = envelope.from ?: return null
        val replyType = envelope.type?.let { parseMessageType(it) } ?: MessageType.NORMAL

        return MessageStanza(
            from = selfJid,
            to = sender,
            type = replyType,
            extensions = listOf(receivedElement(requestId)),
        )
    }

    fun sendReceived(
        from: Jid,
        to: Jid,
        messageId: String,
        type: MessageType = MessageType.CHAT,
    ) {
        val stanza = MessageStanza(
            from = from,
            to = to,
            type = type,
            extensions = listOf(receivedElement(messageId)),
        )
        takina.sendMessage(stanza)
    }

    fun sendReceivedReply(
        selfJid: Jid,
        inboundMessageXml: String,
    ): Boolean {
        val stanza = buildReceivedReply(selfJid, inboundMessageXml) ?: return false
        takina.sendMessage(stanza)
        return true
    }

    private fun parseMessageType(value: String): MessageType = MessageType.entries.firstOrNull { it.wireValue == value } ?: MessageType.NORMAL

    sealed interface ReceiptFrame {
        data object Request : ReceiptFrame

        data class Received(
            val id: String,
        ) : ReceiptFrame
    }

    data class ParsedReceiptEnvelope(
        val frame: ReceiptFrame,
        val from: Jid?,
        val to: Jid?,
        val messageId: String?,
        val type: String?,
    )
}

val TakinaContext.receipts: MessageReceiptsComponent get() = requireComponent(MessageReceiptsComponent)

private val REQUEST_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?request\b([^>]*)/?>""")
private val RECEIVED_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?received\b([^>]*)/?>""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()
