package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.atoriapps.takina.core.bootstrap.FeatureTopologyValidator
import org.atoriapps.takina.core.controlling.ApplyMode
import org.atoriapps.takina.core.features.InstalledFeature
import org.atoriapps.takina.core.features.TakinaFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider
import org.atoriapps.takina.core.models.ScopeKind

class FeatureTopologyValidatorTest {
    @Test
    fun `reports missing required feature`() {
        val sm = provider("sm")
        val omemo = testFeature("omemo", requires = setOf(sm))

        val errors = FeatureTopologyValidator.validate(listOf(omemo))
        assertEquals(1, errors.size)
        assertTrue(errors.first().missing.contains("sm"))
    }

    @Test
    fun `reports conflict`() {
        val providerA = provider("a")
        val providerB = provider("b")
        val a = testFeature("a", provider = providerA, conflicts = setOf(providerB))
        val b = testFeature("b", provider = providerB)

        val errors = FeatureTopologyValidator.validate(listOf(a, b))
        assertEquals(1, errors.count { it.conflicts.isNotEmpty() })
    }

    @Test
    fun `reports cycle`() {
        val providerA = provider("a")
        val providerB = provider("b")
        val a = testFeature("a", provider = providerA, requires = setOf(providerB))
        val b = testFeature("b", provider = providerB, requires = setOf(providerA))
        val errors = FeatureTopologyValidator.validate(listOf(a, b))
        assertTrue(errors.any { it.cycle != null })
    }

    private fun provider(providerId: String): TakinaFeatureProvider<TakinaFeature> = object : TakinaFeatureProvider<TakinaFeature> {
        override val id: String = providerId
        override val featureType = TakinaFeature::class
        override fun create(): TakinaFeature = error("not used in tests")
    }

    private fun testFeature(
        id: String,
        provider: TakinaFeatureProvider<TakinaFeature> = provider(id),
        requires: Set<TakinaFeatureProvider<out TakinaFeature>> = emptySet(),
        conflicts: Set<TakinaFeatureProvider<out TakinaFeature>> = emptySet(),
    ): InstalledFeature = InstalledFeature(provider, object : TakinaFeature {
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL, ScopeKind.ACCOUNT)
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE
        override val requires: Set<TakinaFeatureProvider<out TakinaFeature>> = requires
        override val conflictsWith: Set<TakinaFeatureProvider<out TakinaFeature>> = conflicts
    })
}
