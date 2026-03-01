package org.atoriapps.takina.core.pipeline

import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.xml.XmlParser
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

enum class PipelineDirection {
    INBOUND,
    OUTBOUND,
}

enum class InboundClassification {
    STANZA_MESSAGE,
    STANZA_PRESENCE,
    STANZA_IQ,
    CONTROL_SM,
    STREAM_META,
    STREAM_END,
    UNKNOWN,
}

enum class OutboundClassification {
    BUSINESS,
    CONTROL,
}

data class PipelineScopeContext(
    val owner: BareJid?,
    val conversationPeer: BareJid? = null,
    val messageId: String? = null,
)

data class InboundFrame(
    val raw: String,
    val classification: InboundClassification,
    val owner: BareJid? = null,
)

data class OutboundFrame(
    val raw: String,
    val classification: OutboundClassification,
    val owner: BareJid? = null,
)

sealed interface NodeResult {
    data class Continue(val value: String) : NodeResult
    data object Drop : NodeResult
    data object Bypass : NodeResult
}

interface PipelineNode {
    val key: String
}

interface InboundNode : PipelineNode {
    suspend fun execute(frame: InboundFrame): NodeResult
}

interface OutboundNode : PipelineNode {
    suspend fun execute(frame: OutboundFrame): NodeResult
}

data class NodeMetrics(
    val key: String,
    val duration: Duration = ZERO,
    val failCount: Long = 0,
    val dropCount: Long = 0,
    val bypassCount: Long = 0,
)

data class PipelineDescription(
    val direction: PipelineDirection,
    val scope: String,
    val nodes: List<NodeActivation>,
)

data class NodeActivation(
    val key: String,
    val enabled: Boolean,
    val order: Int,
)

fun classifyInbound(raw: String): InboundClassification {
    val trimmed = raw.trim()
    if (trimmed.startsWith("</stream:stream")) return InboundClassification.STREAM_END
    val element = XmlParser.parseStartTagOrNull(trimmed) ?: return InboundClassification.UNKNOWN
    return when (element.localName) {
        "message" -> InboundClassification.STANZA_MESSAGE
        "presence" -> InboundClassification.STANZA_PRESENCE
        "iq" -> InboundClassification.STANZA_IQ
        "r", "a", "resume", "enabled", "resumed", "failed" -> InboundClassification.CONTROL_SM
        "stream", "features" -> InboundClassification.STREAM_META
        else -> InboundClassification.UNKNOWN
    }
}
