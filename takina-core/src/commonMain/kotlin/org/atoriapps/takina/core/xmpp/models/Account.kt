package org.atoriapps.takina.core.xmpp.models

import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.xmpp.BareJid

data class XmppAccountProfile(
    val jid: BareJid,
    val host: String = jid.domain,
    val port: Int,
    val securityMode: SecurityMode,
    val resource: String = "takina",
)
