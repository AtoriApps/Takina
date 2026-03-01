package org.atoriapps.takina.core.pipeline

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeSource
import org.atoriapps.takina.core.controlling.ControlPlane
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.Scope

// TODO、CHECK：管线about的前后依赖型排序好像没做，另外这个API（about）是否要更名？另外这里面是不是也有Key？再看看

class PipelineRuntime(
    private val controlPlane: ControlPlane,
) {
    private data class InboundRegistration(
        val node: InboundNode,
        val featureProvider: TakinaFeatureProvider<*>?,
        val controlOnly: Boolean,
    )

    private data class OutboundRegistration(
        val node: OutboundNode,
        val featureProvider: TakinaFeatureProvider<*>?,
        val controlOnly: Boolean,
        val userCustom: Boolean,
    )

    private data class MutableMetrics(
        var duration: Duration = ZERO,
        var failCount: Long = 0,
        var dropCount: Long = 0,
        var bypassCount: Long = 0,
    )

    private val inboundNodes = mutableListOf<InboundRegistration>()
    private val outboundNodes = mutableListOf<OutboundRegistration>()
    private val nodeMetrics = mutableMapOf<String, MutableMetrics>()

    fun registerInboundNode(
        node: InboundNode,
        featureProvider: TakinaFeatureProvider<*>? = null,
        controlOnly: Boolean = false,
    ) {
        inboundNodes += InboundRegistration(node = node, featureProvider = featureProvider, controlOnly = controlOnly)
    }

    fun registerOutboundNode(
        node: OutboundNode,
        featureProvider: TakinaFeatureProvider<*>? = null,
        controlOnly: Boolean = false,
        userCustom: Boolean = true,
    ) {
        outboundNodes += OutboundRegistration(
            node = node,
            featureProvider = featureProvider,
            controlOnly = controlOnly,
            userCustom = userCustom,
        )
    }

    suspend fun executeInbound(frame: InboundFrame, scope: Scope): String? {
        val candidates = when (frame.classification) {
            InboundClassification.CONTROL, InboundClassification.STREAM_META, InboundClassification.STREAM_END -> inboundNodes.filter { it.controlOnly }

            else -> inboundNodes.filterNot { it.controlOnly }
        }

        val sorted = controlPlane.sortNodesWithVisibilityConflict(
            nodeKeys = candidates.map { it.node.key },
            featureProviderOfNode = { key -> candidates.first { it.node.key == key }.featureProvider },
            scope = scope,
        ).filter { it.enabled }

        var current = frame.raw
        for (state in sorted) {
            val registration = candidates.first { it.node.key == state.nodeKey }
            val metric = nodeMetrics.getOrPut(state.nodeKey) { MutableMetrics() }
            val mark = TimeSource.Monotonic.markNow()
            try {
                when (val result = registration.node.execute(frame.copy(raw = current))) {
                    is NodeResult.Continue -> current = result.value
                    NodeResult.Drop -> {
                        metric.dropCount += 1
                        return null
                    }

                    NodeResult.Bypass -> metric.bypassCount += 1
                }
            } catch (_: Throwable) {
                metric.failCount += 1
            } finally {
                metric.duration += mark.elapsedNow()
            }
        }
        return current
    }

    suspend fun executeOutbound(frame: OutboundFrame, scope: Scope): String? {
        val candidates = when (frame.classification) {
            OutboundClassification.BUSINESS -> outboundNodes.filterNot { it.controlOnly }

            OutboundClassification.CONTROL -> outboundNodes.filter { it.controlOnly }
        }

        val sorted = controlPlane.sortNodesWithVisibilityConflict(
            nodeKeys = candidates.map { it.node.key },
            featureProviderOfNode = { key -> candidates.first { it.node.key == key }.featureProvider },
            scope = scope,
        ).filter { it.enabled }

        var current = frame.raw
        for (state in sorted) {
            val registration = candidates.first { it.node.key == state.nodeKey }
            if (frame.classification == OutboundClassification.CONTROL && registration.userCustom) continue

            val metric = nodeMetrics.getOrPut(state.nodeKey) { MutableMetrics() }
            val mark = TimeSource.Monotonic.markNow()
            try {
                when (val result = registration.node.execute(frame.copy(raw = current))) {
                    is NodeResult.Continue -> current = result.value
                    NodeResult.Drop -> {
                        metric.dropCount += 1
                        return null
                    }

                    NodeResult.Bypass -> metric.bypassCount += 1
                }
            } catch (_: Throwable) {
                metric.failCount += 1
            } finally {
                metric.duration += mark.elapsedNow()
            }
        }
        return current
    }

    fun describeActivePipeline(direction: PipelineDirection, scope: Scope): PipelineDescription {
        val nodeKeys = when (direction) {
            PipelineDirection.INBOUND -> inboundNodes.map { it.node.key }
            PipelineDirection.OUTBOUND -> outboundNodes.map { it.node.key }
        }
        val registrationsFeatureMap = when (direction) {
            PipelineDirection.INBOUND -> inboundNodes.associate { it.node.key to it.featureProvider }
            PipelineDirection.OUTBOUND -> outboundNodes.associate { it.node.key to it.featureProvider }
        }
        val sorted = controlPlane.sortNodesWithVisibilityConflict(
            nodeKeys = nodeKeys,
            featureProviderOfNode = { registrationsFeatureMap[it] },
            scope = scope,
        )
        return PipelineDescription(
            direction = direction,
            scope = scope.toString(),
            nodes = sorted.map { NodeActivation(it.nodeKey, it.enabled, it.order) },
        )
    }

    fun metricsSnapshot(): List<NodeMetrics> = nodeMetrics.map { (key, value) ->
        NodeMetrics(
            key = key,
            duration = value.duration,
            failCount = value.failCount,
            dropCount = value.dropCount,
            bypassCount = value.bypassCount,
        )
    }.sortedBy { it.key }
}
