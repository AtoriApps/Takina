package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.exceptions.TakinaConnectionException

enum class ConnectionLifecycleStage {
    DISCONNECTED,
    TCP_CONNECTED,
    STREAM_OPENED,
    TLS_NEGOTIATED,
    AUTHENTICATED,
    RESOURCE_BOUND,
    ONLINE,
    DISCONNECTING,
}

class ConnectionStateMachine(
    initial: ConnectionLifecycleStage = ConnectionLifecycleStage.DISCONNECTED,
) {
    private var _stage: ConnectionLifecycleStage = initial

    val stage: ConnectionLifecycleStage
        @Synchronized get() = _stage

    @Synchronized
    fun moveTo(
        target: ConnectionLifecycleStage,
        allowedFrom: Set<ConnectionLifecycleStage>,
        reason: String,
    ): ConnectionLifecycleStage {
        val current = _stage
        if (current !in allowedFrom) {
            throw TakinaConnectionException(
                "invalid stage transition $current -> $target: $reason",
            )
        }
        _stage = target
        return current
    }

    @Synchronized
    fun forceTo(target: ConnectionLifecycleStage): ConnectionLifecycleStage {
        val previous = _stage
        _stage = target
        return previous
    }
}
