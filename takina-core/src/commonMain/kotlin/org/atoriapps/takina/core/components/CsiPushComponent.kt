package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlRegexUtils
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.bareJid
import org.atoriapps.takina.core.xmpp.stanzas.IqStanza
import org.atoriapps.takina.core.xmpp.stanzas.IqType

class CsiPushComponent internal constructor(private val takina: AbstractTakina) : TakinaComponent {
    companion object : TakinaComponentProvider<CsiPushComponent> {
        const val CSI_NAMESPACE: String = "urn:xmpp:csi:0"
        const val PUSH_NAMESPACE: String = "urn:xmpp:push:0"
        const val DISCO_INFO_NAMESPACE: String = "http://jabber.org/protocol/disco#info"

        override fun getInstance(context: TakinaContext): CsiPushComponent {
            val core = context as? AbstractTakina ?: error("CsiPushComponent 只能安装在 Takina 核心上下文中")
            return CsiPushComponent(core)
        }

        override fun getComponentType() = CsiPushComponent::class
    }

    fun activeElement(): XmlElement = XmlElement(name = "active", namespace = CSI_NAMESPACE)

    fun inactiveElement(): XmlElement = XmlElement(name = "inactive", namespace = CSI_NAMESPACE)

    fun sendActive(from: Jid? = null) = takina.sendRaw(from, activeElement().toXmlString())

    fun sendInactive(from: Jid? = null) = takina.sendRaw(from, inactiveElement().toXmlString())

    fun enablePushAwait(
        pushServiceJid: Jid,
        node: String?,
        secret: String? = null,
        from: Jid? = null,
        to: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        val xDataFields = mutableListOf<XmlElement>()
        if (!secret.isNullOrBlank()) xDataFields += XmlElement(name = "field", attributes = mapOf("var" to "secret"), children = listOf(XmlElement(name = "value", text = secret)))

        val publishOptions = if (xDataFields.isEmpty()) null
        else XmlElement(
            name = "x",
            namespace = "jabber:x:data",
            attributes = mapOf("type" to "submit"),
            children = xDataFields,
        )

        val attrs = linkedMapOf("jid" to pushServiceJid.bareJid.toString())
        if (!node.isNullOrBlank()) attrs["node"] = node
        val payload = XmlElement(
            name = "enable",
            namespace = PUSH_NAMESPACE,
            attributes = attrs,
            children = listOfNotNull(publishOptions),
        )

        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("push-enable"),
                from = from,
                to = to,
                type = IqType.SET,
                payload = payload,
            ),
        )
    }

    fun disablePushAwait(
        pushServiceJid: Jid,
        node: String? = null,
        from: Jid? = null,
        to: Jid? = null,
        timeoutMillis: Long = 10_000,
    ): PendingIqAwaitRequest {
        val attrs = linkedMapOf("jid" to pushServiceJid.bareJid.toString())
        if (!node.isNullOrBlank()) attrs["node"] = node
        val payload = XmlElement(
            name = "disable",
            namespace = PUSH_NAMESPACE,
            attributes = attrs,
        )
        return PendingIqAwaitRequest(
            takina = takina,
            timeoutMillis = timeoutMillis,
            stanza = IqStanza(
                id = IdUtils.newStanzaId("push-disable"),
                from = from,
                to = to,
                type = IqType.SET,
                payload = payload,
            ),
        )
    }

    fun parseDiscoFeatures(discoInfoResultXml: String): PushDiscovery {
        val features = FEATURE_TAG_REGEX.findAll(discoInfoResultXml).mapNotNull { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            attrs["var"]
        }.toSet()
        return PushDiscovery(
            supportsCsi = features.contains(CSI_NAMESPACE),
            supportsPush = features.contains(PUSH_NAMESPACE),
            rawFeatures = features,
        )
    }

    data class PushDiscovery(
        val supportsCsi: Boolean,
        val supportsPush: Boolean,
        val rawFeatures: Set<String>,
    )
}

val TakinaContext.csiPush: CsiPushComponent get() = requireComponent(CsiPushComponent)

private val FEATURE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?feature\b([^>]*)/?>""")
