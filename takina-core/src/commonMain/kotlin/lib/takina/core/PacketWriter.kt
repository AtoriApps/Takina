
package lib.takina.core

import lib.takina.core.requests.Request
import lib.takina.core.xml.Element

interface PacketWriter {

    /**
     * Sends Request to server and register it for potential response or error handling.
     */
    fun write(request: Request<*, *>)

    /**
     * Sends prepared element directly to server, without registering response handlers.
     */
    fun writeDirectly(stanza: Element)

}