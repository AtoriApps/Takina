package org.atoriapps.takina.core.bootstrap

import org.atoriapps.takina.core.feature.FeatureKey
import org.atoriapps.takina.core.feature.TakinaFeature

data class FeatureTopologyError(
    val code: String,
    val feature: FeatureKey,
    val missing: Set<FeatureKey> = emptySet(),
    val conflicts: Set<FeatureKey> = emptySet(),
    val cycle: List<FeatureKey>? = null,
)

class FeatureTopologyException(
    val errors: List<FeatureTopologyError>,
) : IllegalStateException("Feature topology validation failed: ${errors.size} error(s)")

object FeatureTopologyValidator {
    fun validate(features: List<TakinaFeature>): List<FeatureTopologyError> {
        val byKey = features.associateBy { it.key }
        val errors = mutableListOf<FeatureTopologyError>()

        for (feature in features) {
            val missing = feature.requires.filterNot { byKey.containsKey(it) }.toSet()
            val conflicts = feature.conflictsWith.filter { byKey.containsKey(it) }.toSet()
            if (missing.isNotEmpty() || conflicts.isNotEmpty()) errors += FeatureTopologyError(
                code = "TAKINA-FEATURE-001",
                feature = feature.key,
                missing = missing,
                conflicts = conflicts,
            )
        }

        val cycle = detectCycle(byKey.values.toList())
        cycle?.forEach { key ->
            errors += FeatureTopologyError(
                code = "TAKINA-FEATURE-002",
                feature = key,
                cycle = cycle,
            )
        }

        return errors
    }

    fun validateOrThrow(features: List<TakinaFeature>) {
        val errors = validate(features)
        if (errors.isNotEmpty()) throw FeatureTopologyException(errors)
    }

    private fun detectCycle(features: List<TakinaFeature>): List<FeatureKey>? {
        val byKey = features.associateBy { it.key }
        val visited = mutableSetOf<FeatureKey>()
        val active = mutableSetOf<FeatureKey>()
        val stack = mutableListOf<FeatureKey>()

        fun dfs(key: FeatureKey): List<FeatureKey>? {
            if (key !in visited) {
                visited += key
                active += key
                stack += key

                val feature = byKey[key] ?: return null
                for (next in feature.requires) {
                    if (next !in byKey) continue
                    if (next !in visited) {
                        val cycle = dfs(next)
                        if (cycle != null) return cycle
                    } else if (next in active) {
                        val start = stack.indexOf(next)
                        return stack.subList(start, stack.size).toList() + next
                    }
                }
            }

            active.remove(key)
            if (stack.isNotEmpty() && stack.last() == key) stack.removeAt(stack.lastIndex)
            return null
        }

        for (feature in features) {
            val cycle = dfs(feature.key)
            if (cycle != null) return cycle
        }
        return null
    }
}
