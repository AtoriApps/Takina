package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.xmpp.stanzas.IqType

data class IqResult(
    val id: String,
    val type: IqType,
    val xml: String,
)
