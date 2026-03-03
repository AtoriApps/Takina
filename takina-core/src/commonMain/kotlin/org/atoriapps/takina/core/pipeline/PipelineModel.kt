package org.atoriapps.takina.core.pipeline

import org.atoriapps.takina.core.models.BareJid
import org.atoriapps.takina.core.xml.XmlElement
import org.atoriapps.takina.core.xml.XmlParser
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

enum class PipelineDirection {
    INBOUND,
    OUTBOUND
}

// CHECK：会有除此之外的类型吗？

enum class InboundClassification {
    STANZA_MESSAGE,
    STANZA_PRESENCE,
    STANZA_IQ,
    CONTROL,
    STREAM_META,
    STREAM_END,
    UNKNOWN
}

enum class OutboundClassification {
    BUSINESS,
    CONTROL
}

private const val XMPP_SM_NAMESPACE = "urn:xmpp:sm:3"
private val smControlLocalNames = setOf("r", "a", "resume", "enabled", "resumed", "failed")

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

// HACK、TODO：会不会过于简单，是否应建立`类型认领管线`，由Features提供认领节点
fun classifyInbound(raw: String): InboundClassification {
    val trimmed = raw.trim()
    if (trimmed.startsWith("</stream:stream")) return InboundClassification.STREAM_END

    val element = XmlParser.parseStartTagOrNull(trimmed) ?: return InboundClassification.UNKNOWN
    return when (element.localName) {
        "message" -> InboundClassification.STANZA_MESSAGE
        "presence" -> InboundClassification.STANZA_PRESENCE
        "iq" -> InboundClassification.STANZA_IQ
        // HACK：下面不该这样，在认领管线时重做
        in smControlLocalNames -> if (element.isSmControlFrame()) InboundClassification.CONTROL else InboundClassification.UNKNOWN
        "stream", "features" -> InboundClassification.STREAM_META
        else -> InboundClassification.UNKNOWN
    }
}

private fun XmlElement.isSmControlFrame(): Boolean {
    val defaultNs = attribute("xmlns")
    if (defaultNs == XMPP_SM_NAMESPACE) return true

    val prefix = name.substringBefore(':', missingDelimiterValue = "")
    if (prefix.isBlank()) return false
    return attribute("xmlns:$prefix") == XMPP_SM_NAMESPACE
}