package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.xml.XmlRegexUtils

class ConnectionDiscoveryComponent internal constructor(private val takina: AbstractTakina) : TakinaComponent {
    companion object : TakinaComponentProvider<ConnectionDiscoveryComponent> {
        const val ALT_WEBSOCKET_NAMESPACE: String = "urn:xmpp:alt-connections:websocket"
        const val ALT_XBOSH_NAMESPACE: String = "urn:xmpp:alt-connections:xbosh"
        const val XEP0368_DIRECT_TLS_FEATURE: String = "urn:xmpp:features:tls"

        override fun getInstance(context: TakinaContext): ConnectionDiscoveryComponent {
            val core = context as? AbstractTakina ?: error("ConnectionDiscoveryComponent 只能安装在 Takina 核心上下文中")

            return ConnectionDiscoveryComponent(core)
        }

        override fun getComponentType() = ConnectionDiscoveryComponent::class
    }

    fun parseDiscoFeatures(discoInfoResultXml: String): ConnectionDiscoveryResult {
        val features = FEATURE_TAG_REGEX.findAll(discoInfoResultXml).mapNotNull { match ->
            val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
            attrs["var"]
        }.toSet()

        return ConnectionDiscoveryResult(
            supportsDirectTls = features.contains(XEP0368_DIRECT_TLS_FEATURE),
            supportsWebSocket = features.contains(ALT_WEBSOCKET_NAMESPACE),
            supportsBosh = features.contains(ALT_XBOSH_NAMESPACE),
            rawFeatures = features,
        )
    }

    fun parseHostMetaLinks(hostMetaXml: String): List<AlternativeEndpoint> = LINK_TAG_REGEX.findAll(hostMetaXml).mapNotNull { match ->
        val attrs = XmlRegexUtils.parseAttributes(match.groupValues[1])
        val rel = attrs["rel"] ?: return@mapNotNull null
        val href = attrs["href"] ?: return@mapNotNull null

        when (rel) {
            ALT_WEBSOCKET_NAMESPACE -> if (href.startsWith("wss://", ignoreCase = true))
                AlternativeEndpoint(type = EndpointType.WEBSOCKET, url = href, securityMode = SecurityMode.DIRECT_TLS)
            else null

            ALT_XBOSH_NAMESPACE -> if (href.startsWith("https://", ignoreCase = true))
                AlternativeEndpoint(type = EndpointType.BOSH, url = href, securityMode = SecurityMode.START_TLS)
            else null

            else -> null
        }
    }.toList()

    fun parseHostMetaJsonLinks(hostMetaJson: String): List<AlternativeEndpoint> {
        val linksBlock = LINKS_BLOCK_REGEX.find(hostMetaJson)?.groupValues?.get(1) ?: return emptyList()
        return LINK_OBJECT_REGEX.findAll(linksBlock).mapNotNull { match ->
            val body = match.groupValues[1]
            val rel = JSON_REL_REGEX.find(body)?.groupValues?.get(1) ?: return@mapNotNull null
            val href = JSON_HREF_REGEX.find(body)?.groupValues?.get(1) ?: return@mapNotNull null
            when (rel) {
                ALT_WEBSOCKET_NAMESPACE -> if (href.startsWith("wss://", ignoreCase = true))
                    AlternativeEndpoint(type = EndpointType.WEBSOCKET, url = href, securityMode = SecurityMode.DIRECT_TLS)
                else null

                ALT_XBOSH_NAMESPACE -> if (href.startsWith("https://", ignoreCase = true))
                    AlternativeEndpoint(type = EndpointType.BOSH, url = href, securityMode = SecurityMode.START_TLS)
                else null

                else -> null
            }
        }.toList()
    }

    fun choosePreferredEndpoint(
        endpoints: List<AlternativeEndpoint>,
        preferWebSocket: Boolean = true,
        allowBoshFallback: Boolean = true,
    ): AlternativeEndpoint? {
        if (endpoints.isEmpty()) return null
        val websockets = endpoints.filter { it.type == EndpointType.WEBSOCKET }
        val bosh = endpoints.filter { it.type == EndpointType.BOSH }
        if (preferWebSocket && websockets.isNotEmpty()) return websockets.first()
        if (allowBoshFallback && bosh.isNotEmpty()) return bosh.first()
        if (!preferWebSocket && bosh.isNotEmpty()) return bosh.first()
        return websockets.firstOrNull() ?: bosh.firstOrNull()
    }

    data class ConnectionDiscoveryResult(
        val supportsDirectTls: Boolean,
        val supportsWebSocket: Boolean,
        val supportsBosh: Boolean,
        val rawFeatures: Set<String>,
    )

    enum class EndpointType {
        WEBSOCKET,
        BOSH,
    }

    data class AlternativeEndpoint(
        val type: EndpointType,
        val url: String,
        val securityMode: SecurityMode,
    )
}

val TakinaContext.connectionDiscovery: ConnectionDiscoveryComponent get() = requireComponent(ConnectionDiscoveryComponent)

private val FEATURE_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?feature\b([^>]*)/?>""")
private val LINK_TAG_REGEX = Regex("""<\s*(?:[A-Za-z_:][A-Za-z0-9_.:-]*:)?Link\b([^>]*)/?>""")
private val LINKS_BLOCK_REGEX = Regex(""""links"\s*:\s*\[(.*?)]""", setOf(RegexOption.DOT_MATCHES_ALL))
private val LINK_OBJECT_REGEX = Regex("""\{(.*?)\}""", setOf(RegexOption.DOT_MATCHES_ALL))
private val JSON_REL_REGEX = Regex(""""rel"\s*:\s*"([^"]+)"""")
private val JSON_HREF_REGEX = Regex(""""href"\s*:\s*"([^"]+)"""")
