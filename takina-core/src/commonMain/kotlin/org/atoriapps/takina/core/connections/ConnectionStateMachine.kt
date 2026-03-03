package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.error.ErrorDomain
import org.atoriapps.takina.core.error.TakinaErrors

enum class AccountState {
    REGISTERED,
    CONNECTING,
    ONLINE,
    DEGRADED,
    RECONNECTING,
    OFFLINE,
    FAILED,
    REMOVED,
}

enum class ConnectionState {
    IDLE,
    TCP_CONNECTING,
    TLS_HANDSHAKING,
    STREAM_OPENING,
    AUTHENTICATING,
    BINDING_RESOURCE,
    ESTABLISHED,
    INTERRUPTED,
    RESUMING_SM,
    RECONNECT_WAIT,
    CLOSED,
}

data class TransitionResult(
    val accepted: Boolean,
    val from: ConnectionState,
    val to: ConnectionState,
    val errorCode: String? = null,
)

// TODO、CHECK：检查一下。另外就是具体XmppTp，这个会不会又存在什么耦合或者非聚合

class ConnectionStateMachine(
    initialState: ConnectionState = ConnectionState.IDLE,
) {
    private var current: ConnectionState = initialState

    fun currentState(): ConnectionState = current

    fun transitionTo(next: ConnectionState): TransitionResult {
        val allowed = allowedTransitions[current].orEmpty()
        if (next !in allowed) {
            val error = TakinaErrors.of(
                domain = ErrorDomain.STREAM,
                number = 101,
                message = "Illegal transition: $current -> $next",
                retryable = false,
            )
            return TransitionResult(
                accepted = false,
                from = current,
                to = next,
                errorCode = error.code,
            )
        }
        val from = current
        current = next
        return TransitionResult(accepted = true, from = from, to = next)
    }

    companion object {
        val allowedTransitions: Map<ConnectionState, Set<ConnectionState>> = mapOf(
            ConnectionState.IDLE to setOf(ConnectionState.TCP_CONNECTING, ConnectionState.CLOSED),
            ConnectionState.TCP_CONNECTING to setOf(ConnectionState.TLS_HANDSHAKING, ConnectionState.STREAM_OPENING, ConnectionState.CLOSED),
            ConnectionState.TLS_HANDSHAKING to setOf(ConnectionState.STREAM_OPENING, ConnectionState.CLOSED),
            ConnectionState.STREAM_OPENING to setOf(ConnectionState.TLS_HANDSHAKING, ConnectionState.AUTHENTICATING, ConnectionState.BINDING_RESOURCE, ConnectionState.CLOSED,),
            ConnectionState.AUTHENTICATING to setOf(ConnectionState.STREAM_OPENING, ConnectionState.BINDING_RESOURCE, ConnectionState.CLOSED),
            ConnectionState.BINDING_RESOURCE to setOf(ConnectionState.ESTABLISHED, ConnectionState.CLOSED),
            ConnectionState.ESTABLISHED to setOf(ConnectionState.INTERRUPTED, ConnectionState.CLOSED),
            ConnectionState.INTERRUPTED to setOf(ConnectionState.RESUMING_SM, ConnectionState.RECONNECT_WAIT, ConnectionState.CLOSED),
            ConnectionState.RESUMING_SM to setOf(ConnectionState.ESTABLISHED, ConnectionState.RECONNECT_WAIT, ConnectionState.CLOSED),
            ConnectionState.RECONNECT_WAIT to setOf(ConnectionState.TCP_CONNECTING, ConnectionState.CLOSED),
            ConnectionState.CLOSED to setOf(ConnectionState.IDLE, ConnectionState.RECONNECT_WAIT),
        )
    }
}
