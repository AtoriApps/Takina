package org.atoriapps.takina.core.xmpp.stanzas

import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlEscaper
import org.atoriapps.takina.core.xmpp.Jid

sealed interface XmppStanza {
    val id: String

    fun toXml(): String
}

enum class MessageType(val wireValue: String) {
    CHAT("chat"),
    NORMAL("normal"),
    GROUPCHAT("groupchat"),
    HEADLINE("headline"),
    ERROR("error"),
}

enum class PresenceType(val wireValue: String) {
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
    SUBSCRIBE("subscribe"),
    SUBSCRIBED("subscribed"),
    UNSUBSCRIBE("unsubscribe"),
    UNSUBSCRIBED("unsubscribed"),
    PROBE("probe"),
    ERROR("error"),
}

enum class IqType(val wireValue: String) {
    GET("get"),
    SET("set"),
    RESULT("result"),
    ERROR("error"),
    ;

    companion object {
        fun fromWireValue(value: String): IqType? = entries.firstOrNull { it.wireValue == value }
    }
}

data class MessageStanza(
    override val id: String = IdUtils.newStanzaId("msg"),
    val from: Jid? = null,
    val to: Jid,
    val type: MessageType = MessageType.CHAT,
    val body: String? = null,
    val subject: String? = null,
    val thread: String? = null,
    val extensions: List<XmlElement> = emptyList(),
) : XmppStanza {
    override fun toXml(): String {
        val attrs = linkedMapOf(
            "id" to id,
            "to" to to.toString(),
            "type" to type.wireValue,
        )
        if (from != null) attrs["from"] = from.toString()

        val bodyXml = body?.let { "<body>${XmlEscaper.escape(it)}</body>" }.orEmpty()
        val subjectXml = subject?.let { "<subject>${XmlEscaper.escape(it)}</subject>" }.orEmpty()
        val threadXml = thread?.let { "<thread>${XmlEscaper.escape(it)}</thread>" }.orEmpty()
        val extensionsXml = extensions.joinToString(separator = "") { it.toXmlString() }

        return buildString {
            append("<message")
            attrs.forEach { (key, value) ->
                append(" ").append(key).append("='").append(XmlEscaper.escape(value)).append("'")
            }
            append(">")
            append(subjectXml)
            append(bodyXml)
            append(threadXml)
            append(extensionsXml)
            append("</message>")
        }
    }
}

data class PresenceStanza(
    override val id: String = IdUtils.newStanzaId("presence"),
    val from: Jid? = null,
    val to: Jid? = null,
    val type: PresenceType = PresenceType.AVAILABLE,
    val show: String? = null,
    val status: String? = null,
    val priority: Int? = null,
    val extensions: List<XmlElement> = emptyList(),
) : XmppStanza {
    override fun toXml(): String {
        val attrs = linkedMapOf("id" to id)
        if (from != null) attrs["from"] = from.toString()
        if (to != null) attrs["to"] = to.toString()
        if (type != PresenceType.AVAILABLE) attrs["type"] = type.wireValue

        val showXml = show?.let { "<show>${XmlEscaper.escape(it)}</show>" }.orEmpty()
        val statusXml = status?.let { "<status>${XmlEscaper.escape(it)}</status>" }.orEmpty()
        val priorityXml = priority?.let { "<priority>$it</priority>" }.orEmpty()
        val extensionsXml = extensions.joinToString(separator = "") { it.toXmlString() }

        return buildString {
            append("<presence")
            attrs.forEach { (k, v) -> append(" ").append(k).append("='").append(XmlEscaper.escape(v)).append("'") }
            append(">")
            append(showXml)
            append(statusXml)
            append(priorityXml)
            append(extensionsXml)
            append("</presence>")
        }
    }
}

data class IqStanza(
    override val id: String = IdUtils.newStanzaId("iq"),
    val from: Jid? = null,
    val to: Jid? = null,
    val type: IqType,
    val payload: XmlElement? = null,
) : XmppStanza {
    override fun toXml(): String {
        val attrs = linkedMapOf(
            "id" to id,
            "type" to type.wireValue,
        )
        if (from != null) attrs["from"] = from.toString()
        if (to != null) attrs["to"] = to.toString()

        return buildString {
            append("<iq")
            attrs.forEach { (k, v) -> append(" ").append(k).append("='").append(XmlEscaper.escape(v)).append("'") }
            append(">")
            append(payload?.toXmlString().orEmpty())
            append("</iq>")
        }
    }
}

data class EntityCapabilities(
    val node: String,
    val ver: String,
    val hash: String = "sha-1",
)

fun EntityCapabilities.toXmlElement(): XmlElement = XmlElement(
    name = "c",
    namespace = "http://jabber.org/protocol/caps",
    attributes = mapOf(
        "hash" to hash,
        "node" to node,
        "ver" to ver,
    ),
)

fun discoInfoIq(
    id: String = IdUtils.newStanzaId("disco"),
    from: Jid? = null,
    to: Jid? = null,
    node: String? = null,
): IqStanza {
    val attributes = if (node == null) emptyMap() else mapOf("node" to node)
    return IqStanza(
        id = id,
        from = from,
        to = to,
        type = IqType.GET,
        payload = XmlElement(
            name = "query",
            namespace = "http://jabber.org/protocol/disco#info",
            attributes = attributes,
        ),
    )
}

fun softwareVersionIq(
    id: String = IdUtils.newStanzaId("version"),
    from: Jid? = null,
    to: Jid? = null,
): IqStanza = IqStanza(
    id = id,
    from = from,
    to = to,
    type = IqType.GET,
    payload = XmlElement(
        name = "query",
        namespace = "jabber:iq:version",
    ),
)
