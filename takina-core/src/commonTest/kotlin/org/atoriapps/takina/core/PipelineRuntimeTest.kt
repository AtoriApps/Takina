package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.atoriapps.takina.core.controlling.UnifiedPolicy
import org.atoriapps.takina.core.features.InboundFrameClaimer
import org.atoriapps.takina.core.features.InstalledFeatures
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.pipeline.InboundClassification
import org.atoriapps.takina.core.pipeline.InboundFrame
import org.atoriapps.takina.core.pipeline.InboundNode
import org.atoriapps.takina.core.pipeline.NodeOrderSpec
import org.atoriapps.takina.core.pipeline.NodeResult
import org.atoriapps.takina.core.pipeline.OutboundClassification
import org.atoriapps.takina.core.pipeline.OutboundFrame
import org.atoriapps.takina.core.pipeline.OutboundNode
import org.atoriapps.takina.core.pipeline.PipelineRuntime
import org.atoriapps.takina.core.pipeline.classifyInbound

class PipelineRuntimeTest {
    @Test
    fun `classifies inbound frame kinds`() {
        assertEquals(InboundClassification.STANZA_MESSAGE, classifyInbound("<message id='1'/>"))
        assertEquals(InboundClassification.STANZA_PRESENCE, classifyInbound("<presence/>"))
        assertEquals(InboundClassification.STANZA_IQ, classifyInbound("<iq/>"))
        assertEquals(InboundClassification.UNKNOWN, classifyInbound("<r xmlns='urn:xmpp:sm:3'/>"))
        assertEquals(InboundClassification.UNKNOWN, classifyInbound("<sm:r xmlns:sm='urn:xmpp:sm:3'/>"))
        assertEquals(InboundClassification.UNKNOWN, classifyInbound("<r/>"))
        assertEquals(InboundClassification.UNKNOWN, classifyInbound("<x/>"))
    }

    @Test
    fun `tracks node metrics for drop bypass and fail`() = runTest {
        val control = UnifiedPolicy(InstalledFeatures(emptyList()))
        val runtime = PipelineRuntime(control)

        runtime.registerInboundNode(object : InboundNode {
            override val key: String = "bypass"
            override suspend fun execute(frame: InboundFrame): NodeResult = NodeResult.Bypass
        })
        runtime.registerInboundNode(object : InboundNode {
            override val key: String = "fail"
            override suspend fun execute(frame: InboundFrame): NodeResult {
                error("boom")
            }
        })
        runtime.registerInboundNode(object : InboundNode {
            override val key: String = "drop"
            override suspend fun execute(frame: InboundFrame): NodeResult = NodeResult.Drop
        })
        control.setNodeOrder("bypass", Scope.Global, NodeOrderSpec(before = setOf("fail")))
        control.setNodeOrder("fail", Scope.Global, NodeOrderSpec(before = setOf("drop")))

        val result = runtime.executeInbound(
            frame = InboundFrame("<message/>", InboundClassification.STANZA_MESSAGE, owner = null),
            scope = Scope.Global,
        )
        assertNull(result)

        val metrics = runtime.metricsSnapshot().associateBy { it.key }
        assertEquals(1, metrics.getValue("bypass").bypassCount)
        assertEquals(1, metrics.getValue("fail").failCount)
        assertEquals(1, metrics.getValue("drop").dropCount)
    }

    @Test
    fun `control outbound skips user custom nodes by default`() = runTest {
        val control = UnifiedPolicy(InstalledFeatures(emptyList()))
        val runtime = PipelineRuntime(control)
        runtime.registerOutboundNode(object : OutboundNode {
            override val key: String = "custom-control"
            override suspend fun execute(frame: OutboundFrame): NodeResult = NodeResult.Drop
        }, controlOnly = true, userCustom = true)

        val result = runtime.executeOutbound(
            frame = OutboundFrame("<a/>", OutboundClassification.CONTROL),
            scope = Scope.Global,
        )
        assertTrue(result != null)
    }

    @Test
    fun `runtime classify uses feature claimers after builtin fallback`() {
        val control = UnifiedPolicy(InstalledFeatures(emptyList()))
        val runtime = PipelineRuntime(control)
        runtime.registerInboundClaimer(object : InboundFrameClaimer {
            override val key: String = "sm-claimer"
            override fun claim(raw: String): InboundClassification? = if (raw.contains("urn:xmpp:sm:3")) {
                InboundClassification.CONTROL
            } else {
                null
            }
        })

        val classification = runtime.classifyInbound("<r xmlns='urn:xmpp:sm:3'/>", Scope.Global)
        assertEquals(InboundClassification.CONTROL, classification)
    }
}
