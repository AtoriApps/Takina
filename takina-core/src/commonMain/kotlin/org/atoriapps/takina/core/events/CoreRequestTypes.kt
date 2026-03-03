package org.atoriapps.takina.core.events

object CoreRequestTypes {
    const val CONNECT = "connect"
    const val DISCONNECT = "disconnect"
    const val CONNECT_ALL = "connect-all"
    const val DISCONNECT_ALL = "disconnect-all"
    const val MESSAGE = "message"
    const val PRESENCE = "presence"
    const val IQ = "iq"
    const val REQUEST_ROUTING = "request-routing"
    const val STATE_TRANSITION = "state-transition"
}

object OutboundSources {
    const val MESSAGE = "message"
    const val PRESENCE = "presence"
    const val IQ = "iq"
}
