package org.atoriapps.takina.core.connections

internal interface XmppTransportCallbacks {
    suspend fun onStateChanged(to: ConnectionState)
    suspend fun onFrame(frame: String)
    suspend fun onFrameParseFailed(raw: String, reason: String)
    suspend fun onClosed(reason: String?, authHardFailure: Boolean)
}

internal interface XmppTransport {
    val isConnected: Boolean
    val boundJid: String?

    suspend fun connect(password: String)
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
