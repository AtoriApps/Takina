package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.connection.ConnectionState
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.pipeline.OutboundClassification

data class TakinaStartedEvent(val reason: String = "startup") : SimpleTakinaEvent("TakinaStartedEvent") {
    companion object : StaticEventType<TakinaStartedEvent>(TakinaStartedEvent::class)
}

data class TakinaShutdownCompletedEvent(val reason: String = "shutdown") : SimpleTakinaEvent("TakinaShutdownCompletedEvent") {
    companion object : StaticEventType<TakinaShutdownCompletedEvent>(TakinaShutdownCompletedEvent::class)
}

data class AccountAddedEvent(val owner: BareJid) : SimpleTakinaEvent("AccountAddedEvent") {
    companion object : StaticEventType<AccountAddedEvent>(AccountAddedEvent::class)
}

data class AccountRemovedEvent(val owner: BareJid) : SimpleTakinaEvent("AccountRemovedEvent") {
    companion object : StaticEventType<AccountRemovedEvent>(AccountRemovedEvent::class)
}

data class GlobalConfigChangedEvent(val path: String) : SimpleTakinaEvent("GlobalConfigChangedEvent") {
    companion object : StaticEventType<GlobalConfigChangedEvent>(GlobalConfigChangedEvent::class)
}

data class AccountConfigChangedEvent(val owner: BareJid, val path: String) : SimpleTakinaEvent("AccountConfigChangedEvent") {
    companion object : StaticEventType<AccountConfigChangedEvent>(AccountConfigChangedEvent::class)
}

data class FeatureStateChangedEvent(val feature: String, val enabled: Boolean) : SimpleTakinaEvent("FeatureStateChangedEvent") {
    companion object : StaticEventType<FeatureStateChangedEvent>(FeatureStateChangedEvent::class)
}

data class ConfigAppliedEvent(val path: String, val applyMode: String) : SimpleTakinaEvent("ConfigAppliedEvent") {
    companion object : StaticEventType<ConfigAppliedEvent>(ConfigAppliedEvent::class)
}

data class ConfigApplyDeferredEvent(val path: String, val applyMode: String) : SimpleTakinaEvent("ConfigApplyDeferredEvent") {
    companion object : StaticEventType<ConfigApplyDeferredEvent>(ConfigApplyDeferredEvent::class)
}

data class ConfigRejectedEvent(val path: String, val reason: String) : SimpleTakinaEvent("ConfigRejectedEvent") {
    companion object : StaticEventType<ConfigRejectedEvent>(ConfigRejectedEvent::class)
}

data class ConnectionStateChangedEvent(
    val owner: BareJid,
    val from: ConnectionState,
    val to: ConnectionState,
) : SimpleTakinaEvent("ConnectionStateChangedEvent") {
    companion object : StaticEventType<ConnectionStateChangedEvent>(ConnectionStateChangedEvent::class)
}

data class UnexpectedDisconnectedEvent(val owner: BareJid, val reason: String?) : SimpleTakinaEvent("UnexpectedDisconnectedEvent") {
    companion object : StaticEventType<UnexpectedDisconnectedEvent>(UnexpectedDisconnectedEvent::class)
}

data class ReconnectScheduledEvent(val owner: BareJid, val attempt: Int, val delayMillis: Long) : SimpleTakinaEvent("ReconnectScheduledEvent") {
    companion object : StaticEventType<ReconnectScheduledEvent>(ReconnectScheduledEvent::class)
}

data class ReconnectExhaustedEvent(val owner: BareJid, val attempts: Int) : SimpleTakinaEvent("ReconnectExhaustedEvent") {
    companion object : StaticEventType<ReconnectExhaustedEvent>(ReconnectExhaustedEvent::class)
}

data class SessionReadyEvent(val owner: BareJid) : SimpleTakinaEvent("SessionReadyEvent") {
    companion object : StaticEventType<SessionReadyEvent>(SessionReadyEvent::class)
}

data class RawFrameInboundEvent(val owner: BareJid, val xml: String) : SimpleTakinaEvent("RawFrameInboundEvent") {
    companion object : StaticEventType<RawFrameInboundEvent>(RawFrameInboundEvent::class)
}

data class FinalFrameOutboundEvent(
    val owner: BareJid,
    val xml: String,
    val classification: OutboundClassification,
    val source: String,
) : SimpleTakinaEvent("FinalFrameOutboundEvent") {
    companion object : StaticEventType<FinalFrameOutboundEvent>(FinalFrameOutboundEvent::class)
}

data class FrameInboundParseFailedEvent(val owner: BareJid?, val raw: String, val reason: String) : SimpleTakinaEvent("FrameInboundParseFailedEvent") {
    companion object : StaticEventType<FrameInboundParseFailedEvent>(FrameInboundParseFailedEvent::class)
}

data class UnknownFrameInboundEvent(val owner: BareJid?, val raw: String) : SimpleTakinaEvent("UnknownFrameInboundEvent") {
    companion object : StaticEventType<UnknownFrameInboundEvent>(UnknownFrameInboundEvent::class)
}

data class MessageReceivedEvent(val owner: BareJid, val from: BareJid?, val body: String?) : SimpleTakinaEvent("MessageReceivedEvent") {
    companion object : StaticEventType<MessageReceivedEvent>(MessageReceivedEvent::class)
}

data class PresenceReceivedEvent(val owner: BareJid, val from: BareJid?) : SimpleTakinaEvent("PresenceReceivedEvent") {
    companion object : StaticEventType<PresenceReceivedEvent>(PresenceReceivedEvent::class)
}

data class IqReceivedEvent(val owner: BareJid, val from: BareJid?) : SimpleTakinaEvent("IqReceivedEvent") {
    companion object : StaticEventType<IqReceivedEvent>(IqReceivedEvent::class)
}

data class MessageSentEvent(val owner: BareJid, val to: BareJid, val body: String?) : SimpleTakinaEvent("MessageSentEvent") {
    companion object : StaticEventType<MessageSentEvent>(MessageSentEvent::class)
}

data class MessageSendFailedEvent(val owner: BareJid, val to: BareJid?, val reason: String) : SimpleTakinaEvent("MessageSendFailedEvent") {
    companion object : StaticEventType<MessageSendFailedEvent>(MessageSendFailedEvent::class)
}

data class RequestFailedEvent(val owner: BareJid?, val requestType: String, val reason: String) : SimpleTakinaEvent("RequestFailedEvent") {
    companion object : StaticEventType<RequestFailedEvent>(RequestFailedEvent::class)
}

data class RequestTimeoutEvent(val owner: BareJid?, val requestType: String) : SimpleTakinaEvent("RequestTimeoutEvent") {
    companion object : StaticEventType<RequestTimeoutEvent>(RequestTimeoutEvent::class)
}

data class EncryptionFailedEvent(val owner: BareJid?, val reason: String) : SimpleTakinaEvent("EncryptionFailedEvent") {
    companion object : StaticEventType<EncryptionFailedEvent>(EncryptionFailedEvent::class)
}

data class DecryptionFailedEvent(val owner: BareJid?, val reason: String, val raw: String) : SimpleTakinaEvent("DecryptionFailedEvent") {
    companion object : StaticEventType<DecryptionFailedEvent>(DecryptionFailedEvent::class)
}

data class StoreOperationFailedEvent(val owner: BareJid?, val store: String, val reason: String) : SimpleTakinaEvent("StoreOperationFailedEvent") {
    companion object : StaticEventType<StoreOperationFailedEvent>(StoreOperationFailedEvent::class)
}

