package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.toJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import org.atoriapps.takina.core.utils.IdUtils

class CarbonsComponent internal constructor(
    private val takina: AbstractTakina,
) : TakinaComponent {
    companion object : TakinaComponentProvider<CarbonsComponent> {
        const val NAMESPACE: String = "urn:xmpp:carbons:2"
        const val FORWARDED_NAMESPACE: String = "urn:xmpp:forward:0"

        override fun getInstance(context: TakinaContext): CarbonsComponent {
            val core = context as? AbstractTakina
                ?: error("CarbonsComponent 只能安装在 Takina 核心上下文中")
            return CarbonsComponent(core)
        }

        override fun getComponentType() = CarbonsComponent::class
    }

    var autoEnableOnConnect: Boolean = true

    fun enable(
        from: Jid? = null,
    ): PendingStanzaRequest = PendingStanzaRequest(
        takina = takina,
        stanza = commandIq(
            enabled = true,
            from = from,
        ),
    )

    fun disable(
        from: Jid? = null,
    ): PendingStanzaRequest = PendingStanzaRequest(
        takina = takina,
        stanza = commandIq(
            enabled = false,
            from = from,
        ),
    )

    fun enableAwait(
        from: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest = PendingIqAwaitRequest(
        takina = takina,
        timeoutMillis = timeoutMillis,
        stanza = commandIq(
            enabled = true,
            from = from,
        ),
    )

    fun disableAwait(
        from: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest = PendingIqAwaitRequest(
        takina = takina,
        timeoutMillis = timeoutMillis,
        stanza = commandIq(
            enabled = false,
            from = from,
        ),
    )

    fun parseEnvelope(messageXml: String): CarbonEnvelope? {
        val parsedRoot = runCatching { XmlParser.parseRoot(messageXml) }.getOrNull() ?: return null
        if (parsedRoot.rootName.substringAfter(':') != "message") return null

        val wrapperMatch = CARBON_WRAPPER_REGEX.find(messageXml) ?: return null
        val wrapperType = wrapperMatch.groupValues[1]
        val wrapperAttrs = parseAttributes(wrapperMatch.groupValues[2])
        val wrapperNs = extractNamespace(wrapperAttrs)
        if (wrapperNs != NAMESPACE) return null

        val wrapperRange = findElementBounds(
            xml = messageXml,
            localName = wrapperType,
            fromIndex = wrapperMatch.range.first,
        ) ?: return null
        val wrapperXml = messageXml.substring(wrapperRange.first, wrapperRange.last + 1)

        val forwardedRange = findElementBounds(
            xml = wrapperXml,
            localName = "forwarded",
        ) ?: return null
        val forwardedXml = wrapperXml.substring(forwardedRange.first, forwardedRange.last + 1)
        val forwardedOpenEnd = forwardedXml.indexOf('>')
        if (forwardedOpenEnd <= 0) return null

        val forwardedOpenTag = forwardedXml.substring(0, forwardedOpenEnd + 1)
        val forwardedAttrsRaw = forwardedOpenTag.substringAfter("forwarded", missingDelimiterValue = "")
        val forwardedAttrs = parseAttributes(forwardedAttrsRaw)
        val forwardedNs = extractNamespace(forwardedAttrs)
        if (forwardedNs != FORWARDED_NAMESPACE) return null
        if (forwardedOpenTag.endsWith("/>")) return null

        val forwardedCloseStart = forwardedXml.lastIndexOf("</")
        if (forwardedCloseStart <= forwardedOpenEnd) return null
        val forwardedInner = forwardedXml.substring(forwardedOpenEnd + 1, forwardedCloseStart)

        val forwardedMessageRange = findElementBounds(
            xml = forwardedInner,
            localName = "message",
        ) ?: return null
        val forwardedMessageXml = forwardedInner.substring(forwardedMessageRange.first, forwardedMessageRange.last + 1)

        return CarbonEnvelope(
            frame = if (wrapperType == "sent") CarbonFrame.Sent else CarbonFrame.Received,
            from = parsedRoot.attributes["from"]?.toJidOrNull(),
            to = parsedRoot.attributes["to"]?.toJidOrNull(),
            messageId = parsedRoot.attributes["id"],
            type = parsedRoot.attributes["type"],
            forwardedMessageXml = forwardedMessageXml,
        )
    }

    fun parseFromMessageXml(messageXml: String): CarbonFrame? = parseEnvelope(messageXml)?.frame

    private fun commandIq(
        enabled: Boolean,
        from: Jid?,
    ): IqStanza = IqStanza(
        id = IdUtils.newStanzaId(if (enabled) "carbons-enable" else "carbons-disable"),
        type = IqType.SET,
        from = from,
        payload = XmlElement(
            name = if (enabled) "enable" else "disable",
            namespace = NAMESPACE,
        ),
    )

    private fun parseAttributes(raw: String): Map<String, String> = buildMap {
        ATTRIBUTE_REGEX.findAll(raw).forEach { match ->
            put(match.groupValues[1], match.groupValues[3])
        }
    }

    private fun extractNamespace(attributes: Map<String, String>): String? {
        if (attributes["xmlns"] != null) return attributes["xmlns"]
        return attributes.entries.firstOrNull { it.key.startsWith("xmlns:") }?.value
    }

    private fun findElementBounds(
        xml: String,
        localName: String,
        fromIndex: Int = 0,
    ): IntRange? {
        val openRegex = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?$localName\b[^>]*>""")
        val closeRegex = Regex("""</\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?$localName\s*>""")

        val open = openRegex.find(xml, fromIndex) ?: return null
        if (open.value.endsWith("/>")) return open.range

        var depth = 1
        var index = open.range.last + 1
        while (depth > 0) {
            val nextOpen = openRegex.find(xml, index)
            val nextClose = closeRegex.find(xml, index) ?: return null
            if (nextOpen != null && nextOpen.range.first < nextClose.range.first) {
                if (!nextOpen.value.endsWith("/>")) depth += 1
                index = nextOpen.range.last + 1
                continue
            }
            depth -= 1
            if (depth == 0) {
                return open.range.first..nextClose.range.last
            }
            index = nextClose.range.last + 1
        }
        return null
    }

    enum class CarbonFrame {
        Sent,
        Received,
    }

    data class CarbonEnvelope(
        val frame: CarbonFrame,
        val from: Jid?,
        val to: Jid?,
        val messageId: String?,
        val type: String?,
        val forwardedMessageXml: String,
    )
}

fun TakinaContext.carbons(): CarbonsComponent = requireComponent(CarbonsComponent)

private val CARBON_WRAPPER_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?(sent|received)\b([^>]*)>""")
private val ATTRIBUTE_REGEX = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(['"])(.*?)\2""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()
