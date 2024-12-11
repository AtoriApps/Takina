
package lib.takina.core.xmpp.stanzas

import kotlinx.serialization.Serializable
import lib.takina.core.xml.Element
import lib.takina.core.xml.StanzaSerializer
import lib.takina.core.xml.jidAttributeProperty
import lib.takina.core.xml.stringAttributeProperty
import lib.takina.core.xmpp.JID

@Serializable(with = StanzaSerializer::class)
sealed class Stanza<STANZA_TYPE>(protected val element: Element) : Element by element {

    var to: JID? by jidAttributeProperty()
    var from: JID? by jidAttributeProperty()
    var id: String? by stringAttributeProperty()

    abstract var type: STANZA_TYPE

    override fun equals(other: Any?): Boolean {
        return element.equals(other)
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }

    override fun toString(): String {
        return buildString {
            append(name.uppercase()).append("[")
            attributes["type"]?.let {
                append("type=").append(it)
                    .append(" ")
            }
            attributes["id"]?.let {
                append("id=").append(it)
                    .append(" ")
            }
            attributes["to"]?.let {
                append("to=").append(it)
                    .append(" ")
            }
            attributes["from"]?.let {
                append("from=").append(it)
                    .append(" ")
            }
            append("]")
        }
    }

}