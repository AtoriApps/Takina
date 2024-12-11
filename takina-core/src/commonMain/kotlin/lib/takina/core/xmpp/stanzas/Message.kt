
package lib.takina.core.xmpp.stanzas

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import lib.takina.core.parseISO8601
import lib.takina.core.xml.Element
import lib.takina.core.xml.MessageStanzaSerializer
import lib.takina.core.xml.attributeProp
import lib.takina.core.xml.stringElementProperty
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException

@Serializable
enum class MessageType(val value: String) {
    Chat("chat"),
    Error("error"),
    Groupchat("groupchat"),
    Headline("headline"),
    Normal("normal")
}

@Serializable(with = MessageStanzaSerializer::class)
open class Message(wrappedElement: Element) : Stanza<MessageType?>(wrappedElement) {

    init {
        require(wrappedElement.name == NAME) { "Message stanza requires element $NAME." }
    }

    companion object {

        const val NAME = "message"
    }

    override var type: MessageType? by attributeProp(
        valueToString = { v -> v?.value },
        stringToValue = { s: String? ->
            s?.let {
                MessageType.values()
                    .firstOrNull { te -> te.value == it } ?: throw XMPPException(
                    ErrorCondition.BadRequest, "Unknown stanza type '$it'"
                )
            }
        })

    var body: String? by stringElementProperty()

}

fun Message.getTimestampOrNull(): Instant? {
    return this.getChildrenNS("delay", "urn:xmpp:delay")
        ?.let {
            it.attributes["stamp"]?.let { stamp -> parseISO8601(stamp) }
        }
}