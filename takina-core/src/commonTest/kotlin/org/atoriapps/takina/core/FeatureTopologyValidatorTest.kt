package org.atoriapps.takina.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.atoriapps.takina.core.bootstrap.FeatureTopologyValidator
import org.atoriapps.takina.core.control.ApplyMode
import org.atoriapps.takina.core.feature.FeatureKey
import org.atoriapps.takina.core.feature.TakinaFeature
import org.atoriapps.takina.core.models.ScopeKind

class FeatureTopologyValidatorTest {
    @Test
    fun `reports missing required feature`() {
        val omemo = testFeature("omemo", requires = setOf(FeatureKey("sm")))

        val errors = FeatureTopologyValidator.validate(listOf(omemo))
        assertEquals(1, errors.size)
        assertTrue(errors.first().missing.contains(FeatureKey("sm")))
    }

    @Test
    fun `reports conflict`() {
        val a = testFeature("a", conflicts = setOf(FeatureKey("b")))
        val b = testFeature("b")

        val errors = FeatureTopologyValidator.validate(listOf(a, b))
        assertEquals(1, errors.count { it.conflicts.isNotEmpty() })
    }

    @Test
    fun `reports cycle`() {
        val a = testFeature("a", requires = setOf(FeatureKey("b")))
        val b = testFeature("b", requires = setOf(FeatureKey("a")))
        val errors = FeatureTopologyValidator.validate(listOf(a, b))
        assertTrue(errors.any { it.cycle != null })
    }

    private fun testFeature(
        key: String,
        requires: Set<FeatureKey> = emptySet(),
        conflicts: Set<FeatureKey> = emptySet(),
    ): TakinaFeature = object : TakinaFeature {
        override val key: FeatureKey = FeatureKey(key)
        override val supportedScopes: Set<ScopeKind> = setOf(ScopeKind.GLOBAL, ScopeKind.ACCOUNT)
        override val applyMode: ApplyMode = ApplyMode.IMMEDIATE
        override val requires: Set<FeatureKey> = requires
        override val conflictsWith: Set<FeatureKey> = conflicts
    }
}
