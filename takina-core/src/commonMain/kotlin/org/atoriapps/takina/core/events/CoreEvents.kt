package org.atoriapps.takina.core.events

import org.atoriapps.takina.core.connections.ConnectionState
import org.atoriapps.takina.core.controlling.ConfigRejectCode
import org.atoriapps.takina.core.error.TakinaError
import org.atoriapps.takina.core.models.BatchExecutionOutcome
import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.models.TakinaResult
import org.atoriapps.takina.core.pipeline.OutboundClassification
import org.atoriapps.takina.core.pipeline.PipelineDirection

data class TakinaStartedEvent(val reason: String = "startup") : TakinaEvent("TakinaStartedEvent") {
    companion object : TakinaEventProvider<TakinaStartedEvent>(TakinaStartedEvent::class)
}

data class TakinaShutdownCompletedEvent(val reason: String = "shutdown") : TakinaEvent("TakinaShutdownCompletedEvent") {
    companion object : TakinaEventProvider<TakinaShutdownCompletedEvent>(TakinaShutdownCompletedEvent::class)
}

data class GlobalConfigChangedEvent(val path: String) : TakinaEvent("GlobalConfigChangedEvent") {
    companion object : TakinaEventProvider<GlobalConfigChangedEvent>(GlobalConfigChangedEvent::class)
}

data class AccountAddedEvent(val owner: BareJid) : TakinaEvent("AccountAddedEvent") {
    companion object : TakinaEventProvider<AccountAddedEvent>(AccountAddedEvent::class)
}

data class AccountRemovedEvent(val owner: BareJid) : TakinaEvent("AccountRemovedEvent") {
    companion object : TakinaEventProvider<AccountRemovedEvent>(AccountRemovedEvent::class)
}

data class AccountConfigChangedEvent(val owner: BareJid, val path: String) : TakinaEvent("AccountConfigChangedEvent") {
    companion object : TakinaEventProvider<AccountConfigChangedEvent>(AccountConfigChangedEvent::class)
}

data class FeatureStateChangedEvent(val feature: String, val enabled: Boolean) : TakinaEvent("FeatureStateChangedEvent") {
    companion object : TakinaEventProvider<FeatureStateChangedEvent>(FeatureStateChangedEvent::class)
}

data class ConfigAppliedEvent(val path: String, val applyMode: String) : TakinaEvent("ConfigAppliedEvent") {
    companion object : TakinaEventProvider<ConfigAppliedEvent>(ConfigAppliedEvent::class)
}

data class ConfigApplyDeferredEvent(val path: String, val applyMode: String) : TakinaEvent("ConfigApplyDeferredEvent") {
    companion object : TakinaEventProvider<ConfigApplyDeferredEvent>(ConfigApplyDeferredEvent::class)
}

data class ConfigRejectedEvent(
    val path: String,
    val rejectCode: ConfigRejectCode,
    val reason: String,
) : TakinaEvent("ConfigRejectedEvent") {
    companion object : TakinaEventProvider<ConfigRejectedEvent>(ConfigRejectedEvent::class)
}

data class ConnectionStateChangedEvent(
    val owner: BareJid, val from: ConnectionState, val to: ConnectionState,
) : TakinaEvent("ConnectionStateChangedEvent") {
    companion object : TakinaEventProvider<ConnectionStateChangedEvent>(ConnectionStateChangedEvent::class)
}

data class UnexpectedDisconnectedEvent(val owner: BareJid, val reason: String?) : TakinaEvent("UnexpectedDisconnectedEvent") {
    companion object : TakinaEventProvider<UnexpectedDisconnectedEvent>(UnexpectedDisconnectedEvent::class)
}

data class ReconnectScheduledEvent(val owner: BareJid, val attempt: Int, val delayMillis: Long) : TakinaEvent("ReconnectScheduledEvent") {
    companion object : TakinaEventProvider<ReconnectScheduledEvent>(ReconnectScheduledEvent::class)
}

data class ReconnectExhaustedEvent(val owner: BareJid, val attempts: Int) : TakinaEvent("ReconnectExhaustedEvent") {
    companion object : TakinaEventProvider<ReconnectExhaustedEvent>(ReconnectExhaustedEvent::class)
}

data class AllConnectEvent(
    val outcome: BatchExecutionOutcome,
    val results: Map<BareJid, TakinaResult<Unit>>,
) : TakinaEvent("AllConnectEvent") {
    companion object : TakinaEventProvider<AllConnectEvent>(AllConnectEvent::class)
}

data class AllDisconnectEvent(
    val outcome: BatchExecutionOutcome,
    val results: Map<BareJid, TakinaResult<Unit>>,
) : TakinaEvent("AllDisconnectEvent") {
    companion object : TakinaEventProvider<AllDisconnectEvent>(AllDisconnectEvent::class)
}

data class SessionReadyEvent(val owner: BareJid) : TakinaEvent("SessionReadyEvent") {
    companion object : TakinaEventProvider<SessionReadyEvent>(SessionReadyEvent::class)
}

data class RawFrameInboundEvent(val owner: BareJid, val xml: String) : TakinaEvent("RawFrameInboundEvent") {
    companion object : TakinaEventProvider<RawFrameInboundEvent>(RawFrameInboundEvent::class)
}

data class FinalFrameOutboundEvent(
    val owner: BareJid,
    val xml: String,
    val classification: OutboundClassification,
    val source: String,
) : TakinaEvent("FinalFrameOutboundEvent") {
    companion object : TakinaEventProvider<FinalFrameOutboundEvent>(FinalFrameOutboundEvent::class)
}

data class FrameInboundParseFailedEvent(val owner: BareJid?, val raw: String, val reason: String) : TakinaEvent("FrameInboundParseFailedEvent") {
    companion object : TakinaEventProvider<FrameInboundParseFailedEvent>(FrameInboundParseFailedEvent::class)
}

data class UnknownFrameInboundEvent(val owner: BareJid?, val raw: String) : TakinaEvent("UnknownFrameInboundEvent") {
    companion object : TakinaEventProvider<UnknownFrameInboundEvent>(UnknownFrameInboundEvent::class)
}

data class MessageReceivedEvent(val owner: BareJid, val from: BareJid?, val body: String?) : TakinaEvent("MessageReceivedEvent") {
    companion object : TakinaEventProvider<MessageReceivedEvent>(MessageReceivedEvent::class)
}

data class PresenceReceivedEvent(val owner: BareJid, val from: BareJid?) : TakinaEvent("PresenceReceivedEvent") {
    companion object : TakinaEventProvider<PresenceReceivedEvent>(PresenceReceivedEvent::class)
}

data class IqReceivedEvent(val owner: BareJid, val from: BareJid?) : TakinaEvent("IqReceivedEvent") {
    companion object : TakinaEventProvider<IqReceivedEvent>(IqReceivedEvent::class)
}

data class MessageSentEvent(val owner: BareJid, val to: BareJid, val body: String?) : TakinaEvent("MessageSentEvent") {
    companion object : TakinaEventProvider<MessageSentEvent>(MessageSentEvent::class)
}

data class MessageSendFailedEvent(val owner: BareJid, val to: BareJid?, val error: TakinaError) : TakinaEvent("MessageSendFailedEvent") {
    companion object : TakinaEventProvider<MessageSendFailedEvent>(MessageSendFailedEvent::class)
}

data class RequestFailedEvent(
    val owner: BareJid?,
    val requestType: String,
    val error: TakinaError,
) : TakinaEvent("RequestFailedEvent") {
    companion object : TakinaEventProvider<RequestFailedEvent>(RequestFailedEvent::class)
}

data class PipelineNodeFailedEvent(
    val owner: BareJid?,
    val direction: PipelineDirection,
    val nodeKey: String,
    val error: TakinaError,
) : TakinaEvent("PipelineNodeFailedEvent") {
    companion object : TakinaEventProvider<PipelineNodeFailedEvent>(PipelineNodeFailedEvent::class)
}

data class RequestTimeoutEvent(val owner: BareJid?, val requestType: String) : TakinaEvent("RequestTimeoutEvent") {
    companion object : TakinaEventProvider<RequestTimeoutEvent>(RequestTimeoutEvent::class)
}

data class EncryptionFailedEvent(val owner: BareJid?, val reason: String) : TakinaEvent("EncryptionFailedEvent") {
    companion object : TakinaEventProvider<EncryptionFailedEvent>(EncryptionFailedEvent::class)
}

data class DecryptionFailedEvent(val owner: BareJid?, val reason: String, val raw: String) : TakinaEvent("DecryptionFailedEvent") {
    companion object : TakinaEventProvider<DecryptionFailedEvent>(DecryptionFailedEvent::class)
}

data class StoreOperationFailedEvent(val owner: BareJid?, val store: String, val reason: String) : TakinaEvent("StoreOperationFailedEvent") {
    companion object : TakinaEventProvider<StoreOperationFailedEvent>(StoreOperationFailedEvent::class)
}
