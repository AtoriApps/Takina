package org.atoriapps.takina.core.pipeline

data class NodeOrderSpec(
    val before: Set<String> = emptySet(),
    val after: Set<String> = emptySet(),
) {
    companion object {
        val Empty: NodeOrderSpec = NodeOrderSpec()
    }
}

