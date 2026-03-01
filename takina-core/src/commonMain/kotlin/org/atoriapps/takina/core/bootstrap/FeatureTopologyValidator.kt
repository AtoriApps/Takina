package org.atoriapps.takina.core.bootstrap

import org.atoriapps.takina.core.features.InstalledFeature
import org.atoriapps.takina.core.features.TakinaFeatureProvider

data class FeatureTopologyError(
    val code: String,
    val featureId: String,
    val missing: Set<String> = emptySet(),
    val conflicts: Set<String> = emptySet(),
    val cycle: List<String>? = null,
)

class FeatureTopologyException(
    val errors: List<FeatureTopologyError>,
) : IllegalStateException("Feature topology validation failed: ${errors.size} error(s)")

object FeatureTopologyValidator {
    fun validate(features: List<InstalledFeature>): List<FeatureTopologyError> {
        val byProvider = features.associateBy { it.provider }
        val errors = mutableListOf<FeatureTopologyError>()

        for (installed in features) {
            val feature = installed.feature
            val missing = feature.requires.filterNot { byProvider.containsKey(it) }.map { it.id }.toSet()
            val conflicts = feature.conflictsWith.filter { byProvider.containsKey(it) }.map { it.id }.toSet()
            if (missing.isNotEmpty() || conflicts.isNotEmpty()) errors += FeatureTopologyError(
                code = "TAKINA-FEATURE-001",
                featureId = installed.provider.id,
                missing = missing,
                conflicts = conflicts,
            )
        }

        val cycle = detectCycle(features)
        cycle?.forEach { id ->
            errors += FeatureTopologyError(
                code = "TAKINA-FEATURE-002",
                featureId = id,
                cycle = cycle,
            )
        }

        return errors
    }

    fun validateOrThrow(features: List<InstalledFeature>) {
        val errors = validate(features)
        if (errors.isNotEmpty()) throw FeatureTopologyException(errors)
    }

    private fun detectCycle(features: List<InstalledFeature>): List<String>? {
        val byProvider = features.associateBy { it.provider }
        val visited = mutableSetOf<TakinaFeatureProvider<*>>()
        val active = mutableSetOf<TakinaFeatureProvider<*>>()
        val stack = mutableListOf<TakinaFeatureProvider<*>>()

        fun dfs(provider: TakinaFeatureProvider<*>): List<String>? {
            if (provider !in visited) {
                visited += provider
                active += provider
                stack += provider

                val feature = byProvider[provider]?.feature ?: return null
                for (next in feature.requires) {
                    if (next !in byProvider) continue
                    if (next !in visited) {
                        val cycle = dfs(next)
                        if (cycle != null) return cycle
                    } else if (next in active) {
                        val start = stack.indexOf(next)
                        return stack.subList(start, stack.size).map { it.id } + next.id
                    }
                }
            }

            active.remove(provider)
            if (stack.isNotEmpty() && stack.last() == provider) stack.removeAt(stack.lastIndex)
            return null
        }

        for (installed in features) {
            val cycle = dfs(installed.provider)
            if (cycle != null) return cycle
        }

        return null
    }
}
