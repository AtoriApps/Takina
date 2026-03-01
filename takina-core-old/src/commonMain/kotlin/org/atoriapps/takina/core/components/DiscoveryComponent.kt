package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.toJid
import org.atoriapps.takina.core.xmpp.stanzas.discoInfoIq
import org.atoriapps.takina.core.xmpp.stanzas.softwareVersionIq

class DiscoveryComponent internal constructor(
    private val takina: AbstractTakina,
) : TakinaConnectionLifecycleComponent, TakinaInboundStanzaInterceptor {
    companion object : TakinaComponentProvider<DiscoveryComponent> {
        const val DISCO_INFO_NAMESPACE: String = "http://jabber.org/protocol/disco#info"
        const val SOFTWARE_VERSION_NAMESPACE: String = "jabber:iq:version"

        override fun getInstance(context: TakinaContext): DiscoveryComponent {
            val core = context as? AbstractTakina ?: error("DiscoveryComponent 只能安装在 Takina 核心上下文中")

            return DiscoveryComponent(core)
        }

        override fun getComponentType() = DiscoveryComponent::class
    }

    var captureInboundIqResults: Boolean = true

    private val latestResults = linkedMapOf<ResultKey, CapturedIqResult>()

    override fun onAfterDisconnected(jid: BareJid, reason: String?, context: TakinaContext) = clearCapturedResults(jid)

    override fun onShutdown(context: TakinaContext) = clearCapturedResults()

    override fun interceptInboundStanza(
        connection: TakinaConnection,
        stanzaType: String,
        xml: String,
        context: TakinaContext,
    ): TakinaInboundStanzaInterceptResult {
        if (!captureInboundIqResults || stanzaType != "iq") return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        val captured = parseCapturedIqResult(xml) ?: return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        val from = captured.from ?: return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        synchronized(latestResults) { latestResults[ResultKey(namespace = captured.namespace, from = from.bareJid)] = captured }
        return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
    }

    fun discoInfo(
        to: Jid? = null,
        from: Jid? = null,
        node: String? = null,
    ): PendingStanzaRequest = PendingStanzaRequest(
        takina = takina,
        stanza = discoInfoIq(
            from = from,
            to = to,
            node = node,
        ),
    )

    fun softwareVersion(
        to: Jid? = null,
        from: Jid? = null,
    ): PendingStanzaRequest = PendingStanzaRequest(
        takina = takina,
        stanza = softwareVersionIq(
            from = from,
            to = to,
        ),
    )

    fun getAwait(
        to: Jid? = null,
        from: Jid? = null,
        node: String? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        val stanza = discoInfoIq(
            from = from,
            to = to,
            node = node,
        )

        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = stanza,
        )
    }

    fun softwareVersionAwait(
        to: Jid? = null,
        from: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        val stanza = softwareVersionIq(
            from = from,
            to = to,
        )

        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = stanza,
        )
    }

    fun latestDiscoInfoResult(from: Jid): CapturedIqResult? = latestResult(DISCO_INFO_NAMESPACE, from.bareJid)

    fun latestSoftwareVersionResult(from: Jid): CapturedIqResult? = latestResult(SOFTWARE_VERSION_NAMESPACE, from.bareJid)

    fun capturedResultsSnapshot(): List<CapturedIqResult> = synchronized(latestResults) { latestResults.values.toList() }

    fun clearCapturedResults(jid: BareJid? = null) {
        synchronized(latestResults) { if (jid == null) latestResults.clear() else latestResults.keys.removeAll { it.from == jid } }
    }

    private fun latestResult(namespace: String, from: BareJid): CapturedIqResult? = synchronized(latestResults) { latestResults[ResultKey(namespace = namespace, from = from)] }

    private fun parseCapturedIqResult(xml: String): CapturedIqResult? {
        val root = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        if (root.rootName.substringAfter(':') != "iq") return null
        if (root.attributes["type"] != "result") return null
        val queryMatch = QUERY_TAG_REGEX.find(xml) ?: return null
        val queryAttrs = XmlRegexUtils.parseAttributes(queryMatch.groupValues[1])
        val namespace = XmlRegexUtils.extractNamespace(queryAttrs) ?: return null
        if (namespace != DISCO_INFO_NAMESPACE && namespace != SOFTWARE_VERSION_NAMESPACE) return null
        return CapturedIqResult(
            namespace = namespace,
            id = root.attributes["id"],
            from = root.attributes["from"]?.toJidOrNull(),
            to = root.attributes["to"]?.toJidOrNull(),
            xml = xml,
        )
    }

    private data class ResultKey(
        val namespace: String,
        val from: BareJid,
    )

    data class CapturedIqResult(
        val namespace: String,
        val id: String?,
        val from: Jid?,
        val to: Jid?,
        val xml: String,
    )
}

val TakinaContext.discovery: DiscoveryComponent get() = requireComponent(DiscoveryComponent)

private val QUERY_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?query\b([^>]*)/?>""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()