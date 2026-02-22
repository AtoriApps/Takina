package org.atoriapps.takina.core.connections

import org.atoriapps.takina.core.utils.Base64Codec
import org.atoriapps.takina.core.utils.IdUtils
import org.atoriapps.takina.core.xml.XmlEscaper
import org.atoriapps.takina.core.xmpp.BareJid

object XmppNamespaces {
    const val CLIENT = "jabber:client"
    const val STREAM = "http://etherx.jabber.org/streams"
    const val SASL = "urn:ietf:params:xml:ns:xmpp-sasl"
    const val BIND = "urn:ietf:params:xml:ns:xmpp-bind"
    const val STANZAS = "urn:ietf:params:xml:ns:xmpp-stanzas"
}

object XmppStream {
    fun openingStream(config: ConnectionConfig): String {
        val to = XmlEscaper.escape(config.jid.domain)
        val lang = XmlEscaper.escape(config.streamLanguage)
        return "<stream:stream to='$to' xml:lang='$lang' version='1.0' " +
            "xmlns='${XmppNamespaces.CLIENT}' xmlns:stream='${XmppNamespaces.STREAM}'>"
    }

    fun closingStream(): String = "</stream:stream>"

    fun startTlsRequest(): String =
        "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"

    fun authPlain(jid: BareJid, password: String): String {
        val username = jid.userName ?: ""
        val payload = "\u0000$username\u0000$password".encodeToByteArray()
        val encoded = Base64Codec.encode(payload)
        return "<auth xmlns='${XmppNamespaces.SASL}' mechanism='PLAIN'>$encoded</auth>"
    }

    fun bindResource(resource: String, id: String = IdUtils.newStanzaId("bind")): String {
        val escapedResource = XmlEscaper.escape(resource)
        val escapedId = XmlEscaper.escape(id)
        return "<iq id='$escapedId' type='set'><bind xmlns='${XmppNamespaces.BIND}'>" +
            "<resource>$escapedResource</resource></bind></iq>"
    }
}
