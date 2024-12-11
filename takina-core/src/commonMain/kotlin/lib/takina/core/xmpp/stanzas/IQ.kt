
package lib.takina.core.xmpp.stanzas

import kotlinx.serialization.Serializable
import lib.takina.core.xml.Element
import lib.takina.core.xml.IQStanzaSerializer
import lib.takina.core.xml.attributeProp
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException

@Serializable
enum class IQType(val value: String) {
    Error("error"),
    Get("get"),
    Result("result"),
    Set("set")
}

@Serializable(with = IQStanzaSerializer::class)
class IQ(wrappedElement: Element) : Stanza<IQType>(wrappedElement) {

    init {
        require(wrappedElement.name == NAME) { "IQ stanza requires element $NAME." }
    }

    companion object {

        const val NAME = "iq"
    }

    override var type: IQType by attributeProp(stringToValue = { v ->
        v?.let {
            IQType.values()
                .firstOrNull { te -> te.value == it }
        } ?: throw XMPPException(ErrorCondition.BadRequest, "Unknown stanza type '$v'")
    }, valueToString = { v -> v.value })

}
