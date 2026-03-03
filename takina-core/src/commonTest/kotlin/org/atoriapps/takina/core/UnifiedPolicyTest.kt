package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.controlling.ConnectionConfigPaths
import org.atoriapps.takina.core.controlling.CoreConfigCatalog
import org.atoriapps.takina.core.controlling.ConfigRejectCode
import org.atoriapps.takina.core.controlling.ReconnectConfigPaths
import org.atoriapps.takina.core.controlling.UnifiedPolicy
import org.atoriapps.takina.core.features.FeatureRegistry
import org.atoriapps.takina.core.features.InstalledFeature
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.Scope
import org.atoriapps.takina.core.models.ScopeKind
import org.atoriapps.takina.core.models.toBareJid

class UnifiedPolicyTest {
    private val omemoProvider = provider("omemo")
    private val omemo = feature()
    private val registry = FeatureRegistry(listOf(InstalledFeature(omemoProvider, omemo)))
    private val unifiedPolicy = UnifiedPolicy(registry)
    private val owner = "alice@example.com".toBareJid()
    private val peer = "bob@example.com".toBareJid()

    @Test
    fun `scope priority prefers message over global`() {
        unifiedPolicy.setFeatureEnabled(omemoProvider, Scope.Global, true)
        unifiedPolicy.setFeatureEnabled(omemoProvider, Scope.Message(owner, peer, "m1"), false)

        val result = unifiedPolicy.explainFeature(omemoProvider, Scope.Message(owner, peer, "m1"))
        assertFalse(result.enabled)
        assertTrue(result.reasonChain.first().contains("MESSAGE"))
    }

    @Test
    fun `node order conflict is visible`() {
        unifiedPolicy.setNodeEnabled("decrypt", Scope.Global, true)
        unifiedPolicy.setNodeEnabled("normalize", Scope.Global, true)
        unifiedPolicy.setNodeOrder("decrypt", Scope.Global, 10)
        unifiedPolicy.setNodeOrder("normalize", Scope.Global, 10)

        val sorted = unifiedPolicy.sortNodesWithVisibilityConflict(
            nodeKeys = listOf("decrypt", "normalize"),
            featureProviderOfNode = { null },
            scope = Scope.Global,
        )
        assertEquals(2, sorted.size)
        assertTrue(sorted.all { it.explanation.reasonChain.any { r -> r.contains("order-conflict-visible") } })
    }

    @Test
    fun `config apply validates value type and rejects connection identity keys`() {
        val immediate = unifiedPolicy.applyConfig(ReconnectConfigPaths.ENABLED, false)
        assertTrue(immediate.applied)

        val badReconnect = unifiedPolicy.applyConfig(ReconnectConfigPaths.ENABLED, "false")
        assertFalse(badReconnect.applied)
        assertTrue(badReconnect.rejectedReason != null)

        val connectionRejected = unifiedPolicy.applyConfig(ConnectionConfigPaths.HOST, "next.example.com")
        assertFalse(connectionRejected.applied)
        assertEquals(connectionRejected.rejectedReason?.contains("account-definition-only"), true)

        val unknownRejected = unifiedPolicy.applyConfig("unknown.path", true)
        assertFalse(unknownRejected.applied)
        assertEquals("Unknown config path: unknown.path", unknownRejected.rejectedReason)
    }

    @Test
    fun `config resolves by scope chain with account override over global and preset`() {
        unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, 1_000L, Scope.Preset)
        unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, 2_000L, Scope.Global)
        unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, 3_000L, Scope.Account(owner))

        assertEquals(3_000L, unifiedPolicy.currentConfig(ReconnectConfigPaths.DELAY, Scope.Account(owner)))
        assertEquals(2_000L, unifiedPolicy.currentConfig(ReconnectConfigPaths.DELAY, Scope.Global))
    }

    @Test
    fun `config null unsets scoped value and falls back to broader scope`() {
        unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, 1_000L, Scope.Preset)
        unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, 2_000L, Scope.Global)
        unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, 3_000L, Scope.Account(owner))

        unifiedPolicy.unsetConfig(ReconnectConfigPaths.DELAY, Scope.Account(owner))

        assertEquals(2_000L, unifiedPolicy.currentConfig(ReconnectConfigPaths.DELAY, Scope.Account(owner)))
    }

    @Test
    fun `config values are coerced to canonical types`() {
        val accepted = unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, 1500, Scope.Global)
        assertTrue(accepted.applied)
        assertEquals(1500L, unifiedPolicy.currentConfigOrDefault(CoreConfigCatalog.Reconnect.DELAY, Scope.Global))
    }

    @Test
    fun `set null no longer implies unset`() {
        val result = unifiedPolicy.applyConfig(ReconnectConfigPaths.DELAY, null, Scope.Global)
        assertFalse(result.applied)
        assertEquals(CoreConfigCatalog.Reconnect.DELAY.path, result.path)
    }

    @Test
    fun `reconnect config rejects message scope`() {
        val result = unifiedPolicy.applyConfig(
            ReconnectConfigPaths.DELAY,
            1000L,
            Scope.Message(owner = owner, peer = peer, messageId = "m1"),
        )
        assertFalse(result.applied)
        assertEquals(ConfigRejectCode.UNSUPPORTED_SCOPE, result.rejectCode)
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
