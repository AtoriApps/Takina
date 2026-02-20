package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.AbstractTakina
import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.requests.PendingIqAwaitRequest
import org.atoriapps.takina.core.requests.PendingStanzaRequest
import org.atoriapps.takina.core.xmpp.Jid
import org.atoriapps.takina.core.xmpp.stanzas.discoInfoIq
import org.atoriapps.takina.core.xmpp.stanzas.softwareVersionIq

class DiscoveryComponent internal constructor(
    private val takina: AbstractTakina,
) : TakinaComponent {
    companion object : TakinaComponentProvider<DiscoveryComponent> {
        override fun getInstance(context: TakinaContext): DiscoveryComponent {
            val core = context as? AbstractTakina ?: error("DiscoveryComponent 只能安装在 Takina 核心上下文中")

            return DiscoveryComponent(core)
        }

        override fun getComponentType() = DiscoveryComponent::class
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

    fun discoInfoAwait(
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
}

val TakinaContext.discovery: DiscoveryComponent get() = requireComponent(DiscoveryComponent)
