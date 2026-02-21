package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.toJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.utils.LogUtils

class CarbonsComponent internal constructor(
    private val takina: AbstractTakina,
) : TakinaConnectionLifecycleComponent {
    companion object : TakinaComponentProvider<CarbonsComponent> {
        private const val TAG = "消息碳组件"

        const val NAMESPACE: String = "urn:xmpp:carbons:2"
        const val FORWARDED_NAMESPACE: String = "urn:xmpp:forward:0"

        override fun getInstance(context: TakinaContext): CarbonsComponent {
            val core = context as? AbstractTakina ?: error("消息碳组件只能安装在 Takina 核心上下文中")
            return CarbonsComponent(core)
        }

        override fun getComponentType() = CarbonsComponent::class
    }

    var autoEnableOnConnect: Boolean = true

    override fun onAfterConnected(connection: TakinaConnection, context: TakinaContext) {
        if (!autoEnableOnConnect) return
        runCatching {
            enable(from = connection.boundJid).send()
            LogUtils.debug(TAG, "已发送消息碳 enable", connection.boundJid)
        }.onFailure { error ->
            LogUtils.warn(TAG, "连接后自动启用消息碳失败", connection.boundJid, error.message ?: "未知错误")
        }
    }

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
        val wrapperAttrs = XmlRegexUtils.parseAttributes(wrapperMatch.groupValues[2])
        val wrapperNs = XmlRegexUtils.extractNamespace(wrapperAttrs)
        if (wrapperNs != NAMESPACE) return null

        val wrapperRange = XmlRegexUtils.findElementBounds(xml = messageXml, localName = wrapperType, fromIndex = wrapperMatch.range.first) ?: return null
        val wrapperXml = messageXml.substring(wrapperRange.first, wrapperRange.last + 1)

        val forwardedRange = XmlRegexUtils.findElementBounds(xml = wrapperXml, localName = "forwarded") ?: return null
        val forwardedXml = wrapperXml.substring(forwardedRange.first, forwardedRange.last + 1)
        val forwardedOpenEnd = forwardedXml.indexOf('>')
        if (forwardedOpenEnd <= 0) return null

        val forwardedOpenTag = forwardedXml.substring(0, forwardedOpenEnd + 1)
        val forwardedAttrsRaw = forwardedOpenTag.substringAfter("forwarded", missingDelimiterValue = "")
        val forwardedAttrs = XmlRegexUtils.parseAttributes(forwardedAttrsRaw)
        val forwardedNs = XmlRegexUtils.extractNamespace(forwardedAttrs)
        if (forwardedNs != FORWARDED_NAMESPACE) return null
        if (forwardedOpenTag.endsWith("/>")) return null

        val forwardedCloseStart = forwardedXml.lastIndexOf("</")
        if (forwardedCloseStart <= forwardedOpenEnd) return null
        val forwardedInner = forwardedXml.substring(forwardedOpenEnd + 1, forwardedCloseStart)

        val forwardedMessageRange = XmlRegexUtils.findElementBounds(xml = forwardedInner, localName = "message") ?: return null
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

val TakinaContext.carbons: CarbonsComponent get() = requireComponent(CarbonsComponent)

private val CARBON_WRAPPER_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?(sent|received)\b([^>]*)>""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()