package org.atoriapps.takina.features.streammanagement

import org.atoriapps.takina.core.events.TakinaEvent
import org.atoriapps.takina.core.events.TakinaEventProvider
import org.atoriapps.takina.core.models.BareJid

data class StreamManagementResumedEvent(
    val owner: BareJid,
    val handledByServer: Long,
) : TakinaEvent("StreamManagementResumedEvent") {
    companion object : TakinaEventProvider<StreamManagementResumedEvent>(StreamManagementResumedEvent::class)
}

data class StreamManagementResumeFailedEvent(
    val owner: BareJid,
    val reason: String,
) : TakinaEvent("StreamManagementResumeFailedEvent") {
    companion object : TakinaEventProvider<StreamManagementResumeFailedEvent>(StreamManagementResumeFailedEvent::class)
}

data class StreamManagementGapDetectedEvent(
    val owner: BareJid,
    val expectedAck: Long,
    val actualAck: Long,
) : TakinaEvent("StreamManagementGapDetectedEvent") {
    companion object : TakinaEventProvider<StreamManagementGapDetectedEvent>(StreamManagementGapDetectedEvent::class)
}

