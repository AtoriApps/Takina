package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza

class CapabilitiesComponent : TakinaComponent {
    companion object : TakinaComponentProvider<CapabilitiesComponent> {
        const val NAMESPACE: String = "http://jabber.org/protocol/caps"

        override fun getInstance(context: TakinaContext): CapabilitiesComponent = CapabilitiesComponent()

        override fun getComponentType() = CapabilitiesComponent::class
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

    fun parseFromPresenceXml(xml: String): EntityCapabilities? {
        val parsedRoot = runCatching { XmlParser.parseRoot(xml) }.getOrNull() ?: return null
        val rootLocalName = parsedRoot.rootName.substringAfter(':')
        if (rootLocalName != "presence") return null

        val tagMatch = CAPS_TAG_REGEX.find(xml) ?: return null
        val attrs = parseAttributes(tagMatch.groupValues[1])
        val namespace = attrs["xmlns"] ?: attrs.entries.firstOrNull { it.key.startsWith("xmlns:") }?.value
        if (namespace != NAMESPACE) return null

        val node = attrs["node"] ?: return null
        val ver = attrs["ver"] ?: return null
        val hash = attrs["hash"] ?: "sha-1"
        return EntityCapabilities(node = node, ver = ver, hash = hash)
    }

    private fun parseAttributes(raw: String): Map<String, String> = buildMap {
        ATTRIBUTE_REGEX.findAll(raw).forEach { match ->
            put(match.groupValues[1], match.groupValues[3])
        }
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

fun TakinaContext.capabilities(): CapabilitiesComponent = requireComponent(CapabilitiesComponent)

private val CAPS_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?c\b([^>]*)/?>""")
private val ATTRIBUTE_REGEX = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(['"])(.*?)\2""")
