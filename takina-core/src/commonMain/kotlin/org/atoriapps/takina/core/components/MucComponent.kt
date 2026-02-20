package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingMessageRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.toJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import org.atoriapps.takina.core.xmpp.stanzas.MessageStanza
import org.atoriapps.takina.core.xmpp.stanzas.MessageType
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza
import org.atoriapps.takina.core.xmpp.stanzas.PresenceType

class MucComponent internal constructor(private val takina: AbstractTakina) : TakinaComponent {
    companion object : TakinaComponentProvider<MucComponent> {
        const val MUC_NAMESPACE: String = "http://jabber.org/protocol/muc"
        const val CONFERENCE_NAMESPACE: String = "jabber:x:conference"
        const val BOOKMARKS2_NAMESPACE: String = "urn:xmpp:bookmarks:1"
        const val PUBSUB_NAMESPACE: String = "http://jabber.org/protocol/pubsub"

        override fun getInstance(context: TakinaContext): MucComponent {
            val core = context as? AbstractTakina ?: error("MucComponent 只能安装在 Takina 核心上下文中")
            return MucComponent(core)
        }

        override fun getComponentType() = MucComponent::class
    }

    fun joinRoom(
        roomJid: Jid,
        nick: String,
        from: Jid? = null,
        password: String? = null,
        historyMaxStanzas: Int? = null,
    ): PendingStanzaRequest {
        require(nick.isNotBlank()) { "nick 不能为空" }
        require(historyMaxStanzas == null || historyMaxStanzas >= 0) { "historyMaxStanzas 不能小于 0" }
        val to = org.atoriapps.takina.core.xmpp.createFullJid(
            userName = roomJid.userName,
            domain = roomJid.domain,
            resource = nick,
        )
        val mucChildren = mutableListOf<XmlElement>()
        if (!password.isNullOrBlank()) mucChildren += XmlElement(name = "password", text = password)
        if (historyMaxStanzas != null) mucChildren += XmlElement(name = "history", attributes = mapOf("maxstanzas" to historyMaxStanzas.toString()))
        val presence = PresenceStanza(
            id = IdUtils.newStanzaId("muc-join"),
            from = from,
            to = to,
            type = PresenceType.AVAILABLE,
            extensions = listOf(XmlElement(name = "x", namespace = MUC_NAMESPACE, children = mucChildren)),
        )
        return PendingStanzaRequest(takina = takina, stanza = presence)
    }

    fun leaveRoom(roomFullJid: Jid, from: Jid? = null): PendingStanzaRequest = PendingStanzaRequest(
        takina = takina,
        stanza = PresenceStanza(
            id = IdUtils.newStanzaId("muc-leave"),
            from = from,
            to = roomFullJid,
            type = PresenceType.UNAVAILABLE,
        ),
    )

    fun groupMessage(
        roomJid: Jid,
        body: String,
        from: Jid? = null,
        subject: String? = null,
        thread: String? = null,
    ): PendingMessageRequest {
        val stanza = MessageStanza(
            id = IdUtils.newStanzaId("muc-msg"),
            from = from,
            to = roomJid,
            type = MessageType.GROUPCHAT,
            body = body,
            subject = subject,
            thread = thread,
        )
        return PendingMessageRequest(takina = takina, stanza = stanza)
    }

    fun directInvite(
        invitee: Jid,
        roomJid: Jid,
        from: Jid? = null,
        reason: String? = null,
        continueThread: Boolean = false,
    ): PendingMessageRequest {
        val attrs = linkedMapOf("jid" to roomJid.bareJid.toString())
        if (!reason.isNullOrBlank()) attrs["reason"] = reason
        if (continueThread) attrs["continue"] = "true"
        val stanza = MessageStanza(
            id = IdUtils.newStanzaId("muc-invite"),
            from = from,
            to = invitee,
            type = MessageType.NORMAL,
            extensions = listOf(XmlElement(name = "x", namespace = CONFERENCE_NAMESPACE, attributes = attrs)),
        )
        return PendingMessageRequest(takina = takina, stanza = stanza)
    }

    fun publishBookmarks2Await(
        bookmarks: List<BookmarkRoom>,
        from: Jid? = null,
        to: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        val publishItems = bookmarks.map { bookmark ->
            val attrs = linkedMapOf(
                "name" to bookmark.name,
                "autojoin" to if (bookmark.autoJoin) "true" else "false",
            )
            XmlElement(
                name = "item",
                attributes = mapOf("id" to bookmark.jid.bareJid.toString()),
                children = listOf(
                    XmlElement(
                        name = "conference",
                        namespace = BOOKMARKS2_NAMESPACE,
                        attributes = attrs,
                        children = listOfNotNull(bookmark.nick?.let { XmlElement(name = "nick", text = it) }),
                    ),
                ),
            )
        }

        val publish = XmlElement(name = "publish", attributes = mapOf("node" to BOOKMARKS2_NAMESPACE), children = publishItems)
        val pubsub = XmlElement(name = "pubsub", namespace = PUBSUB_NAMESPACE, children = listOf(publish))
        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("bookmark-set"),
                from = from,
                to = to,
                type = IqType.SET,
                payload = pubsub,
            ),
        )
    }

    fun getBookmarks2Await(
        from: Jid? = null,
        to: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        val items = XmlElement(name = "items", attributes = mapOf("node" to BOOKMARKS2_NAMESPACE))
        val pubsub = XmlElement(name = "pubsub", namespace = PUBSUB_NAMESPACE, children = listOf(items))
        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("bookmark-get"),
                from = from,
                to = to,
                type = IqType.GET,
                payload = pubsub,
            ),
        )
    }

    fun parseDirectInvite(xml: String): DirectInvite? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "message") return null
        val xTag = X_TAG_REGEX.find(xml) ?: return null
        val attrs = XmlRegexUtils.parseAttributes(xTag.groupValues[1])
        if (XmlRegexUtils.extractNamespace(attrs) != CONFERENCE_NAMESPACE) return null
        val room = attrs["jid"] ?: return null
        return DirectInvite(
            roomJid = room,
            reason = attrs["reason"],
            continueThread = attrs["continue"].toBoolean(),
        )
    }

    fun parseBookmarks2Result(xml: String): List<BookmarkRoom> {
        val results = mutableListOf<BookmarkRoom>()
        BOOKMARK_ITEM_WITH_CONFERENCE_REGEX.findAll(xml).forEach { match ->
            val itemAttrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            val conferenceAttrs = XmlRegexUtils.parseAttributes(match.groupValues[2])
            if (XmlRegexUtils.extractNamespace(conferenceAttrs) != BOOKMARKS2_NAMESPACE) return@forEach
            val jid = itemAttrs["id"] ?: return@forEach
            results += BookmarkRoom(
                jid = jid.toJid(),
                name = conferenceAttrs["name"] ?: jid,
                autoJoin = conferenceAttrs["autojoin"].toBooleanLike(),
                nick = null,
            )
        }
        return results
    }

    data class BookmarkRoom(
        val jid: Jid,
        val name: String,
        val autoJoin: Boolean,
        val nick: String? = null,
    )

    data class DirectInvite(
        val roomJid: String,
        val reason: String?,
        val continueThread: Boolean,
    )
}

val TakinaContext.muc: MucComponent get() = requireComponent(MucComponent)

private val X_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?x\b([^>]*)/?>""")
private val BOOKMARK_ITEM_WITH_CONFERENCE_REGEX = Regex(
    """<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?item\b([^>]*)>\s*<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?conference\b([^>]*)""",
)

private fun String?.toBooleanLike(): Boolean = this != null && (equals("true", ignoreCase = true) || this == "1")
