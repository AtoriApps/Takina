package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xml.xDataField
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.toJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType

class MamComponent internal constructor(private val takina: AbstractTakina) : TakinaComponent {
    companion object : TakinaComponentProvider<MamComponent> {
        const val MAM2_NAMESPACE: String = "urn:xmpp:mam:2"
        const val RSM_NAMESPACE: String = "http://jabber.org/protocol/rsm"
        const val FORWARDED_NAMESPACE: String = "urn:xmpp:forward:0"
        const val STANZA_ID_NAMESPACE: String = "urn:xmpp:sid:0"

        override fun getInstance(context: TakinaContext): MamComponent {
            val core = context as? AbstractTakina ?: error("MamComponent 只能安装在 Takina 核心上下文中")
            return MamComponent(core)
        }

        override fun getComponentType() = MamComponent::class
    }

    fun queryArchiveAwait(
        from: Jid? = null,
        to: Jid? = null,
        with: Jid? = null,
        startIso8601: String? = null,
        endIso8601: String? = null,
        pageAfter: String? = null,
        pageBefore: String? = null,
        pageMax: Int? = null,
        queryId: String = IdUtils.newStanzaId("mam-q"),
        timeoutMillis: Long = 15_000,
    ): PendingIqAwaitRequest {
        require(pageMax == null || pageMax > 0) { "pageMax 必须大于 0" }

        val fields = mutableListOf(xDataField(varName = "FORM_TYPE", values = listOf(MAM2_NAMESPACE), fieldType = "hidden"))
        if (with != null) fields += xDataField(varName = "with", values = listOf(with.bareJid.toString()))
        if (!startIso8601.isNullOrBlank()) fields += xDataField(varName = "start", values = listOf(startIso8601))
        if (!endIso8601.isNullOrBlank()) fields += xDataField(varName = "end", values = listOf(endIso8601))

        val queryChildren = mutableListOf(XmlElement(name = "x", namespace = "jabber:x:data", attributes = mapOf("type" to "submit"), children = fields),)

        val rsm = buildRsmSet(after = pageAfter, before = pageBefore, max = pageMax)
        if (rsm != null) queryChildren += rsm

        val query = XmlElement(name = "query", namespace = MAM2_NAMESPACE, attributes = mapOf("queryid" to queryId), children = queryChildren)

        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("mam"),
                from = from,
                to = to,
                type = IqType.SET,
                payload = query,
            ),
        )
    }

    fun parseResultEnvelope(xml: String): MamResultEnvelope? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "message") return null

        val resultMatch = RESULT_TAG_REGEX.find(xml) ?: return null
        val resultAttrs = XmlRegexUtils.parseAttributes(resultMatch.groupValues[1])
        if (XmlRegexUtils.extractNamespace(resultAttrs) != MAM2_NAMESPACE) return null

        val forwardedBounds = XmlRegexUtils.findElementBounds(xml, "forwarded", resultMatch.range.last + 1) ?: return null
        val forwardedXml = xml.substring(forwardedBounds)
        val forwardedOpenTag = FORWARDED_OPEN_TAG_REGEX.find(forwardedXml) ?: return null
        val forwardedAttrs = XmlRegexUtils.parseAttributes(forwardedOpenTag.groupValues[1])
        if (XmlRegexUtils.extractNamespace(forwardedAttrs) != FORWARDED_NAMESPACE) return null

        val delayStamp = DELAY_TAG_REGEX.find(forwardedXml)?.let { XmlRegexUtils.parseAttributes(it.groupValues[1])["stamp"] }
        val messageBounds = XmlRegexUtils.findElementBounds(forwardedXml, "message") ?: return null
        val forwardedMessageXml = forwardedXml.substring(messageBounds)

        return MamResultEnvelope(
            queryId = resultAttrs["queryid"],
            resultId = resultAttrs["id"],
            delayStamp = delayStamp,
            forwardedMessageXml = forwardedMessageXml,
            stanzaIds = parseStanzaIds(forwardedMessageXml),
        )
    }

    fun parseFin(xml: String): MamFin? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "iq") return null
        val type = root.attributes["type"]?.let(IqType::fromWireValue) ?: return null
        if (type != IqType.RESULT) return null

        val finMatch = FIN_TAG_REGEX.find(xml) ?: return null
        val finAttrs = XmlRegexUtils.parseAttributes(finMatch.groupValues[1])
        if (XmlRegexUtils.extractNamespace(finAttrs) != MAM2_NAMESPACE) return null

        val rsm = XmlRegexUtils.findElementBounds(xml, "set", finMatch.range.last + 1)?.let { parseRsmSet(xml.substring(it)) }

        return MamFin(
            queryId = finAttrs["queryid"],
            complete = finAttrs["complete"].toBooleanLike(),
            stable = finAttrs["stable"].toBooleanLike(defaultValue = true),
            rsm = rsm,
        )
    }

    fun parseStanzaIds(messageXml: String): List<StanzaId> = STANZA_ID_TAG_REGEX.findAll(messageXml).mapNotNull { match ->
        val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
        if (XmlRegexUtils.extractNamespace(attrs) != STANZA_ID_NAMESPACE) return@mapNotNull null
        val id = attrs["id"] ?: return@mapNotNull null
        val by = attrs["by"]?.toJidOrNull()
        StanzaId(id = id, by = by)
    }.toList()

    fun createAggregator(queryId: String? = null): MamResultAggregator = MamResultAggregator(queryId)

    fun nextPageAfter(fin: MamFin?): String? = fin?.rsm?.last

    fun nextPageBefore(fin: MamFin?): String? = fin?.rsm?.first

    private fun buildRsmSet(after: String?, before: String?, max: Int?): XmlElement? {
        if (after.isNullOrBlank() && before.isNullOrBlank() && max == null) return null
        val children = mutableListOf<XmlElement>()
        if (!after.isNullOrBlank()) children += XmlElement(name = "after", text = after)
        if (!before.isNullOrBlank()) children += XmlElement(name = "before", text = before)
        if (max != null) children += XmlElement(name = "max", text = max.toString())
        return XmlElement(name = "set", namespace = RSM_NAMESPACE, children = children)
    }

    private fun parseRsmSet(xml: String): RsmSet? {
        val setTag = SET_TAG_REGEX.find(xml) ?: return null
        val attrs = XmlRegexUtils.parseAttributes(setTag.groupValues[1])
        if (XmlRegexUtils.extractNamespace(attrs) != RSM_NAMESPACE) return null
        val first = FIRST_TAG_REGEX.find(xml)?.groupValues?.get(2)
        val last = LAST_TAG_REGEX.find(xml)?.groupValues?.get(2)
        val count = COUNT_TAG_REGEX.find(xml)?.groupValues?.get(2)?.toIntOrNull()
        return RsmSet(first = first, last = last, count = count)
    }

    inner class MamResultAggregator(private val queryId: String?) {
        private val envelopes = mutableListOf<MamResultEnvelope>()
        private var fin: MamFin? = null

        fun ingest(xml: String): Boolean {
            val result = parseResultEnvelope(xml)
            if (result != null) {
                if (queryId == null || result.queryId == queryId) envelopes += result
                return true
            }
            val parsedFin = parseFin(xml)
            if (parsedFin != null) {
                if (queryId == null || parsedFin.queryId == queryId) fin = parsedFin
                return true
            }
            return false
        }

        fun snapshot(): AggregatedMamResult = AggregatedMamResult(
            results = envelopes.toList(),
            fin = fin,
            nextPageAfter = nextPageAfter(fin),
            nextPageBefore = nextPageBefore(fin),
            isComplete = fin?.complete ?: false,
        )

        fun clear() {
            envelopes.clear()
            fin = null
        }
    }

    data class MamResultEnvelope(
        val queryId: String?,
        val resultId: String?,
        val delayStamp: String?,
        val forwardedMessageXml: String,
        val stanzaIds: List<StanzaId>,
    )

    data class AggregatedMamResult(
        val results: List<MamResultEnvelope>,
        val fin: MamFin?,
        val nextPageAfter: String?,
        val nextPageBefore: String?,
        val isComplete: Boolean,
    )

    data class StanzaId(
        val id: String,
        val by: Jid?,
    )

    data class MamFin(
        val queryId: String?,
        val complete: Boolean,
        val stable: Boolean,
        val rsm: RsmSet?,
    )

    data class RsmSet(
        val first: String?,
        val last: String?,
        val count: Int?,
    )
}

val TakinaContext.mam: MamComponent get() = requireComponent(MamComponent)

private val RESULT_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?result\b([^>]*)>""")
private val FORWARDED_OPEN_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?forwarded\b([^>]*)>""")
private val DELAY_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?delay\b([^>]*)/?>""")
private val FIN_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?fin\b([^>]*)>""")
private val SET_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?set\b([^>]*)>""")
private val FIRST_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?first\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?first\s*>""")
private val LAST_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?last\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?last\s*>""")
private val COUNT_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?count\b([^>]*)>(.*?)</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?count\s*>""")
private val STANZA_ID_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?stanza-id\b([^>]*)/?>""")

private fun String?.toBooleanLike(defaultValue: Boolean = false): Boolean {
    if (this == null) return defaultValue
    return equals("true", ignoreCase = true) || this == "1"
}

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()
