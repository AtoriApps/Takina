package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.connections.ConnectionLifecycleStage
import org.atoriapps.takina.core.xmpp.BareJid
import kotlin.reflect.KClass

class ConnectionConnectedEvent(val jid: BareJid) : TakinaEvent() {
    companion object : TakinaEventDescriber<ConnectionConnectedEvent> {
        override fun getEventTokens(): List<String> = listOf("connection", "connected")
        override fun getEventType(): KClass<ConnectionConnectedEvent> = ConnectionConnectedEvent::class
    }
}

class ConnectionFailedEvent(
    val jid: BareJid,
    val reason: String,
) : TakinaEvent() {
    companion object : TakinaEventDescriber<ConnectionFailedEvent> {
        override fun getEventTokens(): List<String> = listOf("connection", "failed")
        override fun getEventType(): KClass<ConnectionFailedEvent> = ConnectionFailedEvent::class
    }
}

class AllConnectedEvent(
    val connectedCount: Int,
    val configuredCount: Int,
) : TakinaEvent() {
    companion object : TakinaEventDescriber<AllConnectedEvent> {
        override fun getEventTokens(): List<String> = listOf("all", "connected")
        override fun getEventType(): KClass<AllConnectedEvent> = AllConnectedEvent::class
    }
}

class AllDisconnectedEvent(val connectionCount: Int) : TakinaEvent() {
    companion object : TakinaEventDescriber<AllDisconnectedEvent> {
        override fun getEventTokens(): List<String> = listOf("all", "disconnected")
        override fun getEventType(): KClass<AllDisconnectedEvent> = AllDisconnectedEvent::class
    }
}

class StanzaReceivedEvent(
    val jid: BareJid,
    val stanzaType: String,
    val xml: String,
) : TakinaEvent() {
    companion object : TakinaEventDescriber<StanzaReceivedEvent> {
        override fun getEventTokens(): List<String> = listOf("stanza", "received")
        override fun getEventType(): KClass<StanzaReceivedEvent> = StanzaReceivedEvent::class
    }
}

class ConnectionClosedEvent(
    val jid: BareJid,
    val reason: String,
) : TakinaEvent() {
    companion object : TakinaEventDescriber<ConnectionClosedEvent> {
        override fun getEventTokens(): List<String> = listOf("connection", "closed")
        override fun getEventType(): KClass<ConnectionClosedEvent> = ConnectionClosedEvent::class
    }
}

class ConnectionStageChangedEvent(
    val jid: BareJid,
    val oldStage: ConnectionLifecycleStage,
    val newStage: ConnectionLifecycleStage,
) : TakinaEvent() {
    companion object : TakinaEventDescriber<ConnectionStageChangedEvent> {
        override fun getEventTokens(): List<String> = listOf("connection", "stage", "changed")
        override fun getEventType(): KClass<ConnectionStageChangedEvent> = ConnectionStageChangedEvent::class
    }
}
