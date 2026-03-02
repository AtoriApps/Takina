package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.controlling.ControlPlane
import org.atoriapps.takina.core.connections.ConnectionConfigPaths
import org.atoriapps.takina.core.connections.ReconnectConfigPaths
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
    fun `config apply validates value type and rejects connection identity keys`() {
        val immediate = controlPlane.applyConfig(ReconnectConfigPaths.ENABLED, false)
        assertTrue(immediate.applied)

        val badReconnect = controlPlane.applyConfig(ReconnectConfigPaths.ENABLED, "false")
        assertFalse(badReconnect.applied)
        assertTrue(badReconnect.rejectedReason != null)

        val connectionRejected = controlPlane.applyConfig(ConnectionConfigPaths.HOST, "next.example.com")
        assertFalse(connectionRejected.applied)
        assertTrue(connectionRejected.rejectedReason?.contains("account-definition-only") == true)
    }

    @Test
    fun `config resolves by scope chain with account override over global and preset`() {
        controlPlane.applyConfig(ReconnectConfigPaths.DELAY, 1_000L, Scope.Preset)
        controlPlane.applyConfig(ReconnectConfigPaths.DELAY, 2_000L, Scope.Global)
        controlPlane.applyConfig(ReconnectConfigPaths.DELAY, 3_000L, Scope.Account(owner))

        assertEquals(3_000L, controlPlane.currentConfig(ReconnectConfigPaths.DELAY, Scope.Account(owner)))
        assertEquals(2_000L, controlPlane.currentConfig(ReconnectConfigPaths.DELAY, Scope.Global))
    }

    @Test
    fun `config null unsets scoped value and falls back to broader scope`() {
        controlPlane.applyConfig(ReconnectConfigPaths.DELAY, 1_000L, Scope.Preset)
        controlPlane.applyConfig(ReconnectConfigPaths.DELAY, 2_000L, Scope.Global)
        controlPlane.applyConfig(ReconnectConfigPaths.DELAY, 3_000L, Scope.Account(owner))

        controlPlane.applyConfig(ReconnectConfigPaths.DELAY, null, Scope.Account(owner))

        assertEquals(2_000L, controlPlane.currentConfig(ReconnectConfigPaths.DELAY, Scope.Account(owner)))
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
