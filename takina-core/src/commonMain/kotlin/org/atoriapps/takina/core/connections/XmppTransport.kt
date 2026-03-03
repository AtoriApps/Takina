package org.atoriapps.takina.core.connections

internal interface XmppTransportCallbacks {
    suspend fun onFrame(frame: String)
    suspend fun onFrameParseFailed(raw: String, reason: String)
    suspend fun onClosed(reason: String?, authHardFailure: Boolean)
}

internal enum class XmppConnectPhase {
    TCP_CONNECTING,
    TLS_HANDSHAKING,
    STREAM_OPENING,
    AUTHENTICATING,
    BINDING_RESOURCE,
}

internal data class XmppSession(
    val boundJid: String,
)

internal interface XmppTransport {
    suspend fun connect(password: String, onPhase: suspend (XmppConnectPhase) -> Unit): XmppSession
    suspend fun sendRaw(xml: String)
    suspend fun disconnect()
}

internal typealias XmppTransportFactory = (ConnectionConfig, XmppTransportCallbacks) -> XmppTransport

internal object XmppTransportFactoryRegistry {
    var factory: XmppTransportFactory = ::createPlatformXmppTransport
}

internal expect fun createPlatformXmppTransport(
    config: ConnectionConfig,
    callbacks: XmppTransportCallbacks,
): XmppTransport
