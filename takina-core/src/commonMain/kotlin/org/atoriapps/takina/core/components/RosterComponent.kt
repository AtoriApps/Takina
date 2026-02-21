package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.utils.LogUtils
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza
import org.atoriapps.takina.core.xmpp.stanzas.PresenceType
import org.atoriapps.takina.core.xmpp.toJid

class RosterComponent internal constructor(private val takina: AbstractTakina) : TakinaInboundStanzaInterceptor, TakinaConnectionLifecycleComponent {
    companion object : TakinaComponentProvider<RosterComponent> {
        private const val TAG = "RosterComponent"

        const val ROSTER_NAMESPACE: String = "jabber:iq:roster"

        override fun getInstance(context: TakinaContext): RosterComponent {
            val core = context as? AbstractTakina ?: error("RosterComponent 只能安装在 Takina 核心上下文中")
            return RosterComponent(core)
        }

        override fun getComponentType() = RosterComponent::class
    }

    var captureInboundRosterPush: Boolean = true
    private val snapshots = linkedMapOf<BareJid, RosterSnapshot>()

    override fun onAfterDisconnected(jid: BareJid, reason: String?, context: TakinaContext) {
        synchronized(snapshots) { snapshots.remove(jid) }
    }

    override fun interceptInboundStanza(
        connection: TakinaConnection,
        stanzaType: String,
        xml: String,
        context: TakinaContext,
    ): TakinaInboundStanzaInterceptResult {
        if (!captureInboundRosterPush || stanzaType != "iq") return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        val parsed = parseRosterResult(xml) ?: return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        if (parsed.type != IqType.SET && parsed.type != IqType.RESULT) return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        if (parsed.type == IqType.SET && !isAuthorizedRosterPush(connection.boundJid, parsed.from)) {
            LogUtils.warn(TAG, "收到未授权 roster push，已忽略", connection.boundJid, "from=${parsed.from}")
            return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        }

        synchronized(snapshots) {
            val previous = snapshots[connection.boundJid]
            val merged = if (previous == null || parsed.type == IqType.RESULT) parsed.items.associateBy { it.jid.bareJid }.toMutableMap()
            else previous.items.toMutableMap().apply {
                parsed.items.forEach { item ->
                    if (item.subscription == "remove") remove(item.jid.bareJid)
                    else put(item.jid.bareJid, item)
                }
            }
            snapshots[connection.boundJid] = RosterSnapshot(items = merged, version = parsed.version ?: previous?.version)
        }
        LogUtils.debug(TAG, "已更新 roster 快照", connection.boundJid, "items=${parsed.items.size}")
        return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
    }

    fun rosterGet(
        from: Jid? = null,
        to: Jid? = null,
        version: String? = null,
        requestVersioning: Boolean = false,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        val attrs = when {
            !version.isNullOrBlank() -> mapOf("ver" to version)
            requestVersioning -> mapOf("ver" to "")
            else -> emptyMap()
        }
        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("roster-get"),
                from = from,
                to = to,
                type = IqType.GET,
                payload = XmlElement(name = "query", namespace = ROSTER_NAMESPACE, attributes = attrs),
            ),
        )
    }

    fun rosterSetItem(
        jid: Jid,
        name: String? = null,
        groups: List<String> = emptyList(),
        from: Jid? = null,
        to: Jid? = null,
    ): PendingStanzaRequest {
        val groupChildren = groups.filter { it.isNotBlank() }.map { XmlElement(name = "group", text = it) }
        val itemAttrs = linkedMapOf("jid" to jid.bareJid.toString())
        if (!name.isNullOrBlank()) itemAttrs["name"] = name
        val item = XmlElement(name = "item", attributes = itemAttrs, children = groupChildren)
        val query = XmlElement(name = "query", namespace = ROSTER_NAMESPACE, children = listOf(item))
        return PendingStanzaRequest(
            takina = takina,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("roster-set"),
                from = from,
                to = to,
                type = IqType.SET,
                payload = query,
            ),
        )
    }

    fun rosterRemoveItem(
        jid: Jid,
        from: Jid? = null,
        to: Jid? = null,
    ): PendingStanzaRequest {
        val item = XmlElement(name = "item", attributes = mapOf("jid" to jid.bareJid.toString(), "subscription" to "remove"))
        val query = XmlElement(name = "query", namespace = ROSTER_NAMESPACE, children = listOf(item))
        return PendingStanzaRequest(
            takina = takina,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("roster-remove"),
                from = from,
                to = to,
                type = IqType.SET,
                payload = query,
            ),
        )
    }

    fun requestSubscription(to: Jid, from: Jid? = null): PendingStanzaRequest = presenceAction(to = to, from = from, type = PresenceType.SUBSCRIBE)

    fun approveSubscription(to: Jid, from: Jid? = null): PendingStanzaRequest = presenceAction(to = to, from = from, type = PresenceType.SUBSCRIBED)

    fun rejectSubscription(to: Jid, from: Jid? = null): PendingStanzaRequest = presenceAction(to = to, from = from, type = PresenceType.UNSUBSCRIBED)

    fun unsubscribe(to: Jid, from: Jid? = null): PendingStanzaRequest = presenceAction(to = to, from = from, type = PresenceType.UNSUBSCRIBE)

    fun parseRosterResult(xml: String): ParsedRosterResult? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "iq") return null
        val type = root.attributes["type"]?.let(IqType::fromWireValue) ?: return null
        val queryMatch = ROSTER_QUERY_TAG_REGEX.find(xml) ?: return null
        val queryAttrs = XmlRegexUtils.parseAttributes(queryMatch.groupValues[1])
        if (XmlRegexUtils.extractNamespace(queryAttrs) != ROSTER_NAMESPACE) return null
        val items = parseRosterItems(xml, queryMatch.range.last + 1)
        return ParsedRosterResult(
            type = type,
            id = root.attributes["id"],
            from = root.attributes["from"]?.toJidOrNull(),
            version = queryAttrs["ver"],
            items = items,
        )
    }

    fun parseSubscriptionEvent(xml: String): SubscriptionEvent? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "presence") return null
        val type = root.attributes["type"] ?: return null
        val action = when (type) {
            "subscribe" -> SubscriptionAction.REQUEST
            "subscribed" -> SubscriptionAction.APPROVED
            "unsubscribe" -> SubscriptionAction.UNSUBSCRIBE
            "unsubscribed" -> SubscriptionAction.REJECTED
            else -> return null
        }
        val from = root.attributes["from"]?.toJidOrNull() ?: return null
        return SubscriptionEvent(action = action, from = from, to = root.attributes["to"]?.toJidOrNull(), id = root.attributes["id"])
    }

    fun snapshot(jid: BareJid): RosterSnapshot? = synchronized(snapshots) { snapshots[jid] }

    private fun presenceAction(to: Jid, from: Jid?, type: PresenceType): PendingStanzaRequest = PendingStanzaRequest(
        takina = takina,
        stanza = PresenceStanza(
            id = IdUtils.newStanzaId("presence"),
            from = from,
            to = to,
            type = type,
        ),
    )

    private fun parseRosterItems(xml: String, fromIndex: Int): List<RosterItem> {
        val results = mutableListOf<RosterItem>()
        var searchFrom = fromIndex
        while (true) {
            val bounds = XmlRegexUtils.findElementBounds(xml, "item", searchFrom) ?: break
            val itemXml = xml.substring(bounds)
            parseRosterItem(itemXml)?.let { results += it }
            searchFrom = bounds.last + 1
        }
        return results
    }

    private fun parseRosterItem(itemXml: String): RosterItem? {
        val openTag = ITEM_OPEN_TAG_REGEX.find(itemXml) ?: return null
        val attrs = XmlRegexUtils.parseAttributes(openTag.groupValues[1])
        val groups = GROUP_TAG_REGEX.findAll(itemXml).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()
        val jid = attrs["jid"]?.toJidOrNull() ?: return null
        return RosterItem(
            jid = jid,
            name = attrs["name"],
            subscription = attrs["subscription"],
            ask = attrs["ask"],
            groups = groups,
        )
    }

    private fun isAuthorizedRosterPush(selfJid: BareJid, from: Jid?): Boolean {
        if (from == null) return true
        if (from.bareJid == selfJid) return true
        if (from.userName == null && from.domain == selfJid.domain) return true
        return false
    }

    data class ParsedRosterResult(
        val type: IqType,
        val id: String?,
        val from: Jid?,
        val version: String?,
        val items: List<RosterItem>,
    )

    data class RosterItem(
        val jid: Jid,
        val name: String?,
        val subscription: String?,
        val ask: String?,
        val groups: List<String>,
    )

    data class RosterSnapshot(
        val items: Map<BareJid, RosterItem>,
        val version: String?,
    )

    enum class SubscriptionAction {
        REQUEST,
        APPROVED,
        UNSUBSCRIBE,
        REJECTED,
    }

    data class SubscriptionEvent(
        val action: SubscriptionAction,
        val from: Jid,
        val to: Jid?,
        val id: String?,
    )
}

val TakinaContext.roster: RosterComponent get() = requireComponent(RosterComponent)

private val ROSTER_QUERY_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?query\b([^>]*)/?>""")
private val ITEM_OPEN_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?item\b([^>]*)/?>""")
private val GROUP_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?group\b[^>]*>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?group\s*>""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()
