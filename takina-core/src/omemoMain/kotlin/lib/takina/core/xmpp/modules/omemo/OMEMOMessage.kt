package lib.takina.core.xmpp.modules.omemo

import lib.takina.core.xml.Element
import lib.takina.core.xmpp.stanzas.Message


/**
 * Message stanza proceeded by [OMEMOModule].
 */
sealed class OMEMOMessage(wrappedElement: Element) : Message(wrappedElement) {

    /**
     * Successfully decrypted stanza.
     * @param senderAddress address of sender
     * @param fingerprint fingerprint of sender public key.
     */
    class Decrypted(wrappedElement: Element, val senderAddress: SignalProtocolAddress, val fingerprint: String) :
        OMEMOMessage(wrappedElement)

    /**
     * Unsuccessfully decrypted stanza.
     * @param condition error condition.
     */
    class Error(wrappedElement: Element, val condition: OMEMOErrorCondition) :
        OMEMOMessage(wrappedElement)

}

enum class OMEMOErrorCondition {
    /**
     * Message is not encrypted for this device.
     */
    DeviceKeyNotFound,

    /**
     * Cannot decrypt message, because of errors.
     */
    CannotDecrypt
}