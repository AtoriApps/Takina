package lib.takina.core.xmpp.modules.omemo

import lib.takina.core.xml.Element
import lib.takina.core.xmpp.stanzas.Message

expect object OMEMOEncryptor {

    fun decrypt(store: SignalProtocolStore, session: OMEMOSession, stanza: Message): Pair<Message,Boolean>
    fun encrypt(session: OMEMOSession, plaintext: String): Element
    
}