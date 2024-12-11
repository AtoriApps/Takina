
package lib.takina.core.connector

import lib.takina.core.AbstractTakina
import lib.takina.core.Scope
import lib.takina.core.xmpp.modules.auth.SASLEvent

class WebSocketSessionController(takina: AbstractTakina, private val connector: WebSocketConnector) :
	AbstractSocketSessionController(takina, "lib.takina.core.connector.WebSocketSessionController") {

	override fun processAuthSuccessfull(event: SASLEvent.SASLSuccess) {
		connector.restartStream()
	}

	override fun processConnectionError(event: ConnectionErrorEvent) {
		log.fine { "Received connector exception: $event" }
		takina.clear(Scope.Connection)
		takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Connection error"))
	}

}