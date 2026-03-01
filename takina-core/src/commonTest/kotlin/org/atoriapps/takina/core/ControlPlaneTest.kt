package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.controlling.ControlPlane
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.features.FeatureRegistry
import org.atoriapps.takina.core.features.InstalledFeature
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.toBareJid

class ControlPlaneTest {
    private val omemoProvider = provider("omemo")
    private val omemo = feature()
    private val registry = FeatureRegistry(listOf(InstalledFeature(omemoProvider, omemo)))
    private val controlPlane = ControlPlane(registry)
    private val owner = "alice@example.com".toBareJid()
    private val peer = "bob@example.com".toBareJid()

    @Test
    fun `scope priority prefers message over global`() {
        controlPlane.setFeatureEnabled(omemoProvider, Scope.Global, true)
        controlPlane.setFeatureEnabled(omemoProvider, Scope.Message(owner, peer, "m1"), false)

        val result = controlPlane.explainFeature(omemoProvider, Scope.Message(owner, peer, "m1"))
        assertFalse(result.enabled)
        assertTrue(result.reasonChain.first().contains("MESSAGE"))
    }

    @Test
    fun `node order conflict is visible`() {
        controlPlane.setNodeEnabled("decrypt", Scope.Global, true)
        controlPlane.setNodeEnabled("normalize", Scope.Global, true)
        controlPlane.setNodeOrder("decrypt", Scope.Global, 10)
        controlPlane.setNodeOrder("normalize", Scope.Global, 10)

        val sorted = controlPlane.sortNodesWithVisibilityConflict(
            nodeKeys = listOf("decrypt", "normalize"),
            featureProviderOfNode = { null },
            scope = Scope.Global,
        )
        assertEquals(2, sorted.size)
        assertTrue(sorted.all { it.explanation.reasonChain.any { r -> r.contains("order-conflict-visible") } })
    }

    @Test
    fun `config apply modes follow v1 behavior`() {
        val immediate = controlPlane.applyConfig("reconnect.enabled", false)
        assertTrue(immediate.applied)

        val nextItem = controlPlane.applyConfig("pipeline.businessInbound.enabled", false)
        assertFalse(nextItem.applied)
        controlPlane.onNextItemBoundary()
        assertEquals(false, controlPlane.currentConfig("pipeline.businessInbound.enabled"))

        val immutable = controlPlane.applyConfig("features.installSet", "abc")
        assertFalse(immutable.applied)
        assertTrue(immutable.rejectedReason != null)

        val badSecurityMode = controlPlane.applyConfig("securityMode", "START_TLS")
        assertFalse(badSecurityMode.applied)
        assertTrue(badSecurityMode.rejectedReason != null)

        val goodSecurityMode = controlPlane.applyConfig("securityMode", SecurityMode.START_TLS)
        assertFalse(goodSecurityMode.applied)
        assertEquals(goodSecurityMode.rejectedReason, null)
    }

    @Test
    fun `config resolves by scope chain with account override over global and preset`() {
        controlPlane.applyConfig("reconnect.delay", 1_000L, Scope.Preset)
        controlPlane.applyConfig("reconnect.delay", 2_000L, Scope.Global)
        controlPlane.applyConfig("reconnect.delay", 3_000L, Scope.Account(owner))

        assertEquals(3_000L, controlPlane.currentConfig("reconnect.delay", Scope.Account(owner)))
        assertEquals(2_000L, controlPlane.currentConfig("reconnect.delay", Scope.Global))
    }

    private fun provider(featureId: String): TakinaFeatureProvider<TakinaFeature> = object : TakinaFeatureProvider<TakinaFeature> {
        override val id: String = featureId
        override val featureType = TakinaFeature::class
        override fun create(): TakinaFeature = error("not used in tests")
    }

    private fun feature(): TakinaFeature = object : TakinaFeature {
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL, ScopeKind.ACCOUNT, ScopeKind.CONVERSATION, ScopeKind.MESSAGE)
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE
    }
}
