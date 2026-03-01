package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.BareJid
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.toJid
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza

class CapabilitiesComponent : TakinaConnectionLifecycleComponent, TakinaInboundStanzaInterceptor, TakinaOutboundFrameInterceptor {
    companion object : TakinaComponentProvider<CapabilitiesComponent> {
        const val NAMESPACE: String = "http://jabber.org/protocol/caps"

        override fun getInstance(context: TakinaContext): CapabilitiesComponent = CapabilitiesComponent()

        override fun getComponentType() = CapabilitiesComponent::class
    }

    var autoAppendToOutboundPresence: Boolean = false
    var cacheInboundPresenceCapabilities: Boolean = true
    var defaultOutboundCapabilities: EntityCapabilities? = null

    private val inboundCapabilitiesCache = linkedMapOf<BareJid, EntityCapabilities>()

    override fun onAfterDisconnected(jid: BareJid, reason: String?, context: TakinaContext) = clearCachedInboundCapabilities(jid)

    override fun onShutdown(context: TakinaContext) = clearCachedInboundCapabilities()

    override fun interceptInboundStanza(
        connection: TakinaConnection,
        stanzaType: String,
        xml: String,
        context: TakinaContext,
    ): TakinaInboundStanzaInterceptResult {
        if (!cacheInboundPresenceCapabilities || stanzaType != "presence") return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        val from = runCatching { XmlParser.parseRoot(xml).attributes["from"]?.toJidOrNull() }.getOrNull() ?: return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        val capabilities = parseFromPresenceXml(xml) ?: return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
        synchronized(inboundCapabilitiesCache) { inboundCapabilitiesCache[from.bareJid] = capabilities }
        return TakinaInboundStanzaInterceptResult(stanzaType = stanzaType, xml = xml)
    }

    override fun interceptOutboundFrame(connection: TakinaConnection, xml: String, context: TakinaContext): TakinaFrameInterceptResult {
        if (!autoAppendToOutboundPresence) return TakinaFrameInterceptResult(xml = xml)
        val capabilities = defaultOutboundCapabilities ?: return TakinaFrameInterceptResult(xml = xml)
        val root = runCatching { XmlParser.parseRoot(xml).rootName.substringAfter(':') }.getOrNull() ?: return TakinaFrameInterceptResult(xml = xml)
        if (root != "presence") return TakinaFrameInterceptResult(xml = xml)
        if (parseFromPresenceXml(xml) != null) return TakinaFrameInterceptResult(xml = xml)
        return TakinaFrameInterceptResult(xml = appendCapsToPresenceXml(xml, capabilities))
    }

    fun build(
        node: String,
        ver: String,
        hash: String = "sha-1",
    ): EntityCapabilities = EntityCapabilities(
        node = node,
        ver = ver,
        hash = hash,
    )

    fun toXmlElement(capabilities: EntityCapabilities): XmlElement = capabilities.toCapsXmlElement()

    fun appendToPresence(
        presence: PresenceStanza,
        capabilities: EntityCapabilities,
    ): PresenceStanza = presence.copy(
        extensions = presence.extensions + capabilities.toCapsXmlElement(),
    )

    fun cachedInboundCapabilities(jid: Jid): EntityCapabilities? = synchronized(inboundCapabilitiesCache) { inboundCapabilitiesCache[jid.bareJid] }

    fun cachedInboundCapabilitiesSnapshot(): Map<BareJid, EntityCapabilities> = synchronized(inboundCapabilitiesCache) { inboundCapabilitiesCache.toMap() }

    fun clearCachedInboundCapabilities(jid: BareJid? = null) {
        synchronized(inboundCapabilitiesCache) { if (jid == null) inboundCapabilitiesCache.clear() else inboundCapabilitiesCache.remove(jid) }
    }

    fun parseFromPresenceXml(xml: String): EntityCapabilities? {
        val parsedRoot = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        val rootLocalName = parsedRoot.rootName.substringAfter(':')
        if (rootLocalName != "presence") return null

        val tagMatch = CAPS_TAG_REGEX.find(xml) ?: return null
        val attrs = XmlRegexUtils.parseAttributes(tagMatch.groupValues[1])
        val namespace = XmlRegexUtils.extractNamespace(attrs)
        if (namespace != NAMESPACE) return null

        val node = attrs["node"] ?: return null
        val ver = attrs["ver"] ?: return null
        val hash = attrs["hash"] ?: "sha-1"
        return EntityCapabilities(node = node, ver = ver, hash = hash)
    }

    private fun appendCapsToPresenceXml(xml: String, capabilities: EntityCapabilities): String {
        val range = XmlRegexUtils.findElementBounds(xml = xml, localName = "presence") ?: return xml
        val presenceXml = xml.substring(range.first, range.last + 1)
        val openEnd = presenceXml.indexOf('>')
        if (openEnd <= 0) return xml
        val openTag = presenceXml.substring(0, openEnd + 1)
        val tagName = openTag.substring(1).trimStart().takeWhile { !it.isWhitespace() && it != '>' && it != '/' }
        if (tagName.isBlank()) return xml
        val capsXml = capabilities.toCapsXmlElement().toXmlString()
        val patchedPresence = if (openTag.dropLast(1).trimEnd().endsWith("/")) {
            val normalizedOpenTag = openTag.dropLast(1).trimEnd().removeSuffix("/").trimEnd() + ">"
            "$normalizedOpenTag$capsXml</$tagName>"
        } else {
            val closeStart = presenceXml.lastIndexOf("</")
            if (closeStart <= openEnd) return xml
            presenceXml.substring(0, closeStart) + capsXml + presenceXml.substring(closeStart)
        }
        return xml.substring(0, range.first) + patchedPresence + xml.substring(range.last + 1)
    }
}

data class EntityCapabilities(
    val node: String,
    val ver: String,
    val hash: String = "sha-1",
)

fun EntityCapabilities.toCapsXmlElement(): XmlElement = XmlElement(
    name = "c",
    namespace = CapabilitiesComponent.NAMESPACE,
    attributes = mapOf(
        "hash" to hash,
        "node" to node,
        "ver" to ver,
    ),
)

val TakinaContext.capabilities: CapabilitiesComponent get() = requireComponent(CapabilitiesComponent)

private val CAPS_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?c\b([^>]*)/?>""")

private fun String.toJidOrNull(): Jid? = runCatching { toJid() }.getOrNull()