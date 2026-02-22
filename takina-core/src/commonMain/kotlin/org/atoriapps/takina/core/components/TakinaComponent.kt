@file:Suppress("NonAsciiCharacters")

package org.atoriapps.takina.core.components

import org.atoriapps.takina.core.TakinaContext
import org.atoriapps.takina.core.connections.ConnectionLifecycleStage
import org.atoriapps.takina.core.connections.PreBindNegotiationResult
import org.atoriapps.takina.core.connections.PreBindNegotiationTransport
import org.atoriapps.takina.core.connections.TakinaConnection
import org.atoriapps.takina.core.xmpp.BareJid
import kotlin.reflect.KClass

interface TakinaComponent {
    val priority: Int get() = 0

    fun onInstall(context: TakinaContext) {}

    fun onShutdown(context: TakinaContext) {}
}

interface TakinaConnectionLifecycleComponent : TakinaComponent {
    fun onBeforeConnect(jid: BareJid, context: TakinaContext) {}

    fun onAfterConnected(connection: TakinaConnection, context: TakinaContext) {}

    fun onAfterConnectFailed(jid: BareJid, reason: String, context: TakinaContext) {}

    fun onBeforeDisconnect(jid: BareJid, context: TakinaContext) {}

    fun onAfterDisconnected(jid: BareJid, reason: String?, context: TakinaContext) {}

    fun onConnectionStageChanged(
        jid: BareJid,
        oldStage: ConnectionLifecycleStage,
        newStage: ConnectionLifecycleStage,
        context: TakinaContext,
    ) {}
}

interface TakinaPreBindNegotiationComponent : TakinaComponent {
    fun tryPreBindNegotiation(
        connection: TakinaConnection,
        featuresXml: String,
        transport: PreBindNegotiationTransport,
        context: TakinaContext,
    ): PreBindNegotiationResult = PreBindNegotiationResult.SKIPPED
}

enum class TakinaFrameInterceptAction {
    CONTINUE,
    DROP,
}

data class TakinaFrameInterceptResult(
    val action: TakinaFrameInterceptAction = TakinaFrameInterceptAction.CONTINUE,
    val xml: String,
)

data class TakinaInboundStanzaInterceptResult(
    val action: TakinaFrameInterceptAction = TakinaFrameInterceptAction.CONTINUE,
    val stanzaType: String,
    val xml: String,
)

interface TakinaOutboundFrameInterceptor : TakinaComponent {
    fun interceptOutboundFrame(connection: TakinaConnection, xml: String, context: TakinaContext): TakinaFrameInterceptResult =
        TakinaFrameInterceptResult(action = TakinaFrameInterceptAction.CONTINUE, xml = xml)
}

interface TakinaOutboundFrameObserver : TakinaComponent {
    fun onOutboundFrameSent(connection: TakinaConnection, xml: String, context: TakinaContext) {}
}

interface TakinaInboundFrameInterceptor : TakinaComponent {
    fun interceptInboundFrame(connection: TakinaConnection, xml: String, context: TakinaContext): TakinaFrameInterceptResult =
        TakinaFrameInterceptResult(action = TakinaFrameInterceptAction.CONTINUE, xml = xml)
}

interface TakinaInboundStanzaInterceptor : TakinaComponent {
    fun interceptInboundStanza(
        connection: TakinaConnection,
        stanzaType: String,
        xml: String,
        context: TakinaContext,
    ): TakinaInboundStanzaInterceptResult = TakinaInboundStanzaInterceptResult(
        action = TakinaFrameInterceptAction.CONTINUE,
        stanzaType = stanzaType,
        xml = xml,
    )
}

interface TakinaComponentProvider<COMPONENT : TakinaComponent> {
    fun getInstance(context: TakinaContext): COMPONENT

    fun configure(context: TakinaContext, component: @UnsafeVariance COMPONENT) {}

    fun getComponentType(): KClass<COMPONENT>
}
