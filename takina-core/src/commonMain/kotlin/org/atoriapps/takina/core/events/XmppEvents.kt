package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.connections.ConnectionLifecycleStage
import org.atoriapps.takina.core.xmpp.BareJid

class ConnectionConnectedEvent(val jid: BareJid) : TakinaEvent() {
    override val description = "账号=$jid"

    companion object : TakinaEventDescriber<ConnectionConnectedEvent> {
        override val eventTokens = listOf("connection", "connected")
        override val eventType = ConnectionConnectedEvent::class
    }
}

class ConnectionFailedEvent(
    val jid: BareJid,
    val reason: String,
) : TakinaEvent() {
    override val description = "账号=$jid 原因=$reason"

    companion object : TakinaEventDescriber<ConnectionFailedEvent> {
        override val eventTokens = listOf("connection", "failed")
        override val eventType = ConnectionFailedEvent::class
    }
}

class AllConnectedEvent(
    val connectedCount: Int,
    val configuredCount: Int,
) : TakinaEvent() {
    override val description: String = "成功=$connectedCount/$configuredCount"

    companion object : TakinaEventDescriber<AllConnectedEvent> {
        override val eventTokens = listOf("all", "connected")
        override val eventType = AllConnectedEvent::class
    }
}

class AllDisconnectedEvent(val connectionCount: Int) : TakinaEvent() {
    override val description: String = "连接数=$connectionCount"

    companion object : TakinaEventDescriber<AllDisconnectedEvent> {
        override val eventTokens = listOf("all", "disconnected")
        override val eventType = AllDisconnectedEvent::class
    }
}

class StanzaReceivedEvent(
    val jid: BareJid,
    val stanzaType: String,
    val xml: String,
) : TakinaEvent() {
    override val description: String = "账号=$jid 类型=$stanzaType"

    companion object : TakinaEventDescriber<StanzaReceivedEvent> {
        override val eventTokens = listOf("stanza", "received")
        override val eventType = StanzaReceivedEvent::class
    }
}

class FrameOutboundEvent(
    val jid: BareJid,
    val xml: String,
) : TakinaEvent() {
    override val description: String = "账号=$jid"

    companion object : TakinaEventDescriber<FrameOutboundEvent> {
        override val eventTokens = listOf("frame", "outbound")
        override val eventType = FrameOutboundEvent::class
    }
}

class ConnectionClosedEvent(
    val jid: BareJid,
    val reason: String,
) : TakinaEvent() {
    override val description: String = "账号=$jid 原因=$reason"

    companion object : TakinaEventDescriber<ConnectionClosedEvent> {
        override val eventTokens = listOf("connection", "closed")
        override val eventType = ConnectionClosedEvent::class
    }
}

class ConnectionStageChangedEvent(
    val jid: BareJid,
    val oldStage: ConnectionLifecycleStage,
    val newStage: ConnectionLifecycleStage,
) : TakinaEvent() {
    override val description: String = "账号=$jid 阶段=$oldStage -> $newStage"

    companion object : TakinaEventDescriber<ConnectionStageChangedEvent> {
        override val eventTokens = listOf("connection", "stage", "changed")
        override val eventType = ConnectionStageChangedEvent::class
    }
}
