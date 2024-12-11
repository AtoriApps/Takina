
package lib.takina.core.xmpp.stanzas

import kotlinx.serialization.Serializable
import lib.takina.core.xml.*
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException

/**
 * Availability sub-state
 */
enum class Show(val value: String) {

    /**
     * The entity or resource is actively interested in chatting.
     */
    Chat("chat"),

    /**
     * The entity or resource is temporarily away.
     */
    Away("away"),

    /**
     * The entity or resource is away for an extended period (xa =
     * "eXtended Away").
     */
    XA("xa"),

    /**
     * The entity or resource is busy (dnd = "Do Not Disturb").
     */
    DnD("dnd"),

}
@Serializable
enum class PresenceType(val value: String) {

    Error("error"),
    Probe("probe"),
    Subscribe("subscribe"),
    Subscribed("subscribed"),
    Unavailable("unavailable"),
    Unsubscribe("unsubscribe"),
    Unsubscribed("unsubscribed"),
}

@Serializable(with = PresenceStanzaSerializer::class)
class Presence(wrappedElement: Element) : Stanza<PresenceType?>(wrappedElement) {

    init {
        require(wrappedElement.name == NAME) { "Presence stanza requires element $NAME." }
    }

    companion object {

        const val NAME = "presence"
    }

    override var type: PresenceType? by attributeProp(
        valueToString = { v -> v?.value },
        stringToValue = { s ->
            s?.let {
                PresenceType.values()
                    .firstOrNull { te -> te.value == it } ?: throw XMPPException(
                    ErrorCondition.BadRequest, "Unknown stanza type '$it'"
                )
            }
        })

    var show: Show? by elementProperty(stringToValue = { s ->
        s?.let {
            Show.values()
                .firstOrNull { s -> s.value == it } ?: throw XMPPException(
                ErrorCondition.BadRequest, "Unknown show value: '$it'"
            )
        }

    }, valueToString = { v -> v?.value })
    var priority: Int by intWithDefaultElementProperty(defaultValue = 0)
    var status: String? by stringElementProperty()

}
