package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.controlling.ConfigRejectCode
import org.atoriapps.takina.core.error.TakinaError
import org.atoriapps.takina.core.models.BatchExecutionOutcome
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.pipeline.OutboundClassification
import org.atoriapps.takina.core.pipeline.PipelineDirection

data class TakinaStartedEvent(val reason: String = "startup") : BasicTakinaEvent("TakinaStartedEvent") {
    companion object : StaticEventProvider<TakinaStartedEvent>(TakinaStartedEvent::class)
}

data class TakinaShutdownCompletedEvent(val reason: String = "shutdown") : BasicTakinaEvent("TakinaShutdownCompletedEvent") {
    companion object : StaticEventProvider<TakinaShutdownCompletedEvent>(TakinaShutdownCompletedEvent::class)
}

data class AccountAddedEvent(val owner: BareJid) : BasicTakinaEvent("AccountAddedEvent") {
    companion object : StaticEventProvider<AccountAddedEvent>(AccountAddedEvent::class)
}

data class AccountRemovedEvent(val owner: BareJid) : BasicTakinaEvent("AccountRemovedEvent") {
    companion object : StaticEventProvider<AccountRemovedEvent>(AccountRemovedEvent::class)
}

data class GlobalConfigChangedEvent(val path: String) : BasicTakinaEvent("GlobalConfigChangedEvent") {
    companion object : StaticEventProvider<GlobalConfigChangedEvent>(GlobalConfigChangedEvent::class)
}

data class AccountConfigChangedEvent(val owner: BareJid, val path: String) : BasicTakinaEvent("AccountConfigChangedEvent") {
    companion object : StaticEventProvider<AccountConfigChangedEvent>(AccountConfigChangedEvent::class)
}

data class FeatureStateChangedEvent(val feature: String, val enabled: Boolean) : BasicTakinaEvent("FeatureStateChangedEvent") {
    companion object : StaticEventProvider<FeatureStateChangedEvent>(FeatureStateChangedEvent::class)
}

data class ConfigAppliedEvent(val path: String, val applyMode: String) : BasicTakinaEvent("ConfigAppliedEvent") {
    companion object : StaticEventProvider<ConfigAppliedEvent>(ConfigAppliedEvent::class)
}

data class ConfigApplyDeferredEvent(val path: String, val applyMode: String) : BasicTakinaEvent("ConfigApplyDeferredEvent") {
    companion object : StaticEventProvider<ConfigApplyDeferredEvent>(ConfigApplyDeferredEvent::class)
}

data class ConfigRejectedEvent(
    val path: String,
    val rejectCode: ConfigRejectCode,
    val reason: String,
) : BasicTakinaEvent("ConfigRejectedEvent") {
    companion object : StaticEventProvider<ConfigRejectedEvent>(ConfigRejectedEvent::class)
}

data class ConnectionStateChangedEvent(
    val owner: BareJid, val from: ConnectionState, val to: ConnectionState,
) : BasicTakinaEvent("ConnectionStateChangedEvent") {
    companion object : StaticEventProvider<ConnectionStateChangedEvent>(ConnectionStateChangedEvent::class)
}

data class UnexpectedDisconnectedEvent(val owner: BareJid, val reason: String?) : BasicTakinaEvent("UnexpectedDisconnectedEvent") {
    companion object : StaticEventProvider<UnexpectedDisconnectedEvent>(UnexpectedDisconnectedEvent::class)
}

data class ReconnectScheduledEvent(val owner: BareJid, val attempt: Int, val delayMillis: Long) : BasicTakinaEvent("ReconnectScheduledEvent") {
    companion object : StaticEventProvider<ReconnectScheduledEvent>(ReconnectScheduledEvent::class)
}

data class ReconnectExhaustedEvent(val owner: BareJid, val attempts: Int) : BasicTakinaEvent("ReconnectExhaustedEvent") {
    companion object : StaticEventProvider<ReconnectExhaustedEvent>(ReconnectExhaustedEvent::class)
}

data class AllConnectEvent(
    val outcome: BatchExecutionOutcome,
    val results: Map<BareJid, TakinaResult<Unit>>,
) : BasicTakinaEvent("AllConnectEvent") {
    companion object : StaticEventProvider<AllConnectEvent>(AllConnectEvent::class)
}

data class AllDisconnectEvent(
    val outcome: BatchExecutionOutcome,
    val results: Map<BareJid, TakinaResult<Unit>>,
) : BasicTakinaEvent("AllDisconnectEvent") {
    companion object : StaticEventProvider<AllDisconnectEvent>(AllDisconnectEvent::class)
}

data class SessionReadyEvent(val owner: BareJid) : BasicTakinaEvent("SessionReadyEvent") {
    companion object : StaticEventProvider<SessionReadyEvent>(SessionReadyEvent::class)
}

data class RawFrameInboundEvent(val owner: BareJid, val xml: String) : BasicTakinaEvent("RawFrameInboundEvent") {
    companion object : StaticEventProvider<RawFrameInboundEvent>(RawFrameInboundEvent::class)
}

data class FinalFrameOutboundEvent(
    val owner: BareJid,
    val xml: String,
    val classification: OutboundClassification,
    val source: String,
) : BasicTakinaEvent("FinalFrameOutboundEvent") {
    companion object : StaticEventProvider<FinalFrameOutboundEvent>(FinalFrameOutboundEvent::class)
}

data class FrameInboundParseFailedEvent(val owner: BareJid?, val raw: String, val reason: String) : BasicTakinaEvent("FrameInboundParseFailedEvent") {
    companion object : StaticEventProvider<FrameInboundParseFailedEvent>(FrameInboundParseFailedEvent::class)
}

data class UnknownFrameInboundEvent(val owner: BareJid?, val raw: String) : BasicTakinaEvent("UnknownFrameInboundEvent") {
    companion object : StaticEventProvider<UnknownFrameInboundEvent>(UnknownFrameInboundEvent::class)
}

data class MessageReceivedEvent(val owner: BareJid, val from: BareJid?, val body: String?) : BasicTakinaEvent("MessageReceivedEvent") {
    companion object : StaticEventProvider<MessageReceivedEvent>(MessageReceivedEvent::class)
}

data class PresenceReceivedEvent(val owner: BareJid, val from: BareJid?) : BasicTakinaEvent("PresenceReceivedEvent") {
    companion object : StaticEventProvider<PresenceReceivedEvent>(PresenceReceivedEvent::class)
}

data class IqReceivedEvent(val owner: BareJid, val from: BareJid?) : BasicTakinaEvent("IqReceivedEvent") {
    companion object : StaticEventProvider<IqReceivedEvent>(IqReceivedEvent::class)
}

data class MessageSentEvent(val owner: BareJid, val to: BareJid, val body: String?) : BasicTakinaEvent("MessageSentEvent") {
    companion object : StaticEventProvider<MessageSentEvent>(MessageSentEvent::class)
}

data class MessageSendFailedEvent(val owner: BareJid, val to: BareJid?, val error: TakinaError) : BasicTakinaEvent("MessageSendFailedEvent") {
    companion object : StaticEventProvider<MessageSendFailedEvent>(MessageSendFailedEvent::class)
}

data class RequestFailedEvent(
    val owner: BareJid?,
    val requestType: String,
    val error: TakinaError,
) : BasicTakinaEvent("RequestFailedEvent") {
    companion object : StaticEventProvider<RequestFailedEvent>(RequestFailedEvent::class)
}

data class PipelineNodeFailedEvent(
    val owner: BareJid?,
    val direction: PipelineDirection,
    val nodeKey: String,
    val error: TakinaError,
) : BasicTakinaEvent("PipelineNodeFailedEvent") {
    companion object : StaticEventProvider<PipelineNodeFailedEvent>(PipelineNodeFailedEvent::class)
}

data class RequestTimeoutEvent(val owner: BareJid?, val requestType: String) : BasicTakinaEvent("RequestTimeoutEvent") {
    companion object : StaticEventProvider<RequestTimeoutEvent>(RequestTimeoutEvent::class)
}

data class EncryptionFailedEvent(val owner: BareJid?, val reason: String) : BasicTakinaEvent("EncryptionFailedEvent") {
    companion object : StaticEventProvider<EncryptionFailedEvent>(EncryptionFailedEvent::class)
}

data class DecryptionFailedEvent(val owner: BareJid?, val reason: String, val raw: String) : BasicTakinaEvent("DecryptionFailedEvent") {
    companion object : StaticEventProvider<DecryptionFailedEvent>(DecryptionFailedEvent::class)
}

data class StoreOperationFailedEvent(val owner: BareJid?, val store: String, val reason: String) : BasicTakinaEvent("StoreOperationFailedEvent") {
    companion object : StaticEventProvider<StoreOperationFailedEvent>(StoreOperationFailedEvent::class)
}
