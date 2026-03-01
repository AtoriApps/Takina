package org.atoriapps.takina.core.bootstrap

import org.atoriapps.takina.core.feature.FeatureKey
import org.atoriapps.takina.core.feature.TakinaFeature

data class BootstrapPlan(
    val installSet: List<TakinaFeature>,
    val frozenAtBuildTime: Set<FeatureKey>,
)

object BootstrapPlanner {
    fun plan(
        presetFeatures: List<TakinaFeature>,
        configuredFeatures: List<TakinaFeature>,
    ): BootstrapPlan {
        val merged = (presetFeatures + configuredFeatures).distinctBy { it.key }
        return BootstrapPlan(
            installSet = merged,
            frozenAtBuildTime = merged.map { it.key }.toSet(),
        )
    }
}
