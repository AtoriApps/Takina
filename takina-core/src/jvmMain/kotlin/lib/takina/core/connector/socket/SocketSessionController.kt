
package lib.takina.core.connector.socket

import lib.takina.core.AbstractTakina
import lib.takina.core.Scope
import lib.takina.core.connector.AbstractSocketSessionController
import lib.takina.core.connector.ConnectionErrorEvent
import lib.takina.core.connector.SessionController
import lib.takina.core.connector.socket.SocketConnector.Companion.SEE_OTHER_HOST_KEY
import lib.takina.core.connector.socket.SocketConnector.Companion.XMLNS_START_TLS
import lib.takina.core.xml.Element
import lib.takina.core.xmpp.StreamError
import lib.takina.core.xmpp.modules.StreamErrorEvent
import lib.takina.core.xmpp.modules.StreamFeaturesEvent
import lib.takina.core.xmpp.modules.auth.SASLEvent

class SocketSessionController(takina: AbstractTakina, private val connector: SocketConnector) :
	AbstractSocketSessionController(takina, "lib.takina.core.connector.socket.SocketSessionController") {

	var seeOtherHostUrl: String? = null
		private set

	override fun processAuthSuccessfull(event: SASLEvent.SASLSuccess) {
		connector.restartStream()
	}

	private fun isTLSAvailable(features: Element): Boolean = features.getChildrenNS("starttls", XMLNS_START_TLS) != null

	override fun processConnectionError(event: ConnectionErrorEvent) {
		log.fine { "Received connector exception: $event" }

		takina.clear(Scope.Connection)

//		context.modules.getModuleOrNull<StreamManagementModule>(StreamManagementModule.TYPE)?.reset()
//		context.modules.getModuleOrNull<SASLModule>(SASLModule.TYPE)?.clear()

		when (event) {
			is SocketConnectionErrorEvent.HostNotFount -> {
				log.info { "Cannot find server in DNS" }
				takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Cannot find server in DNS"))
			}

			else -> {
				takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Connection error"))
			}
		}
	}

	override fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		val connectionSecured = connector.secured
		val tlsAvailable: Boolean = isTLSAvailable(event.features)

		if (!connectionSecured && tlsAvailable) {
			connector.startTLS()
		} else super.processStreamFeaturesEvent(event)
	}

	override fun processStreamError(event: StreamErrorEvent) {
		when (event.condition) {
			StreamError.SEE_OTHER_HOST -> processSeeOtherHost(event)
			else -> super.processStreamError(event)
		}
	}

	private fun processSeeOtherHost(event: StreamErrorEvent) {
		val url = event.errorElement.value
		takina.internalDataStore.setData(Scope.Session, SEE_OTHER_HOST_KEY, url)

		takina.eventBus.fire(
			SessionController.SessionControllerEvents.ErrorReconnect(
				"see-other-host: $url", immediately = true, force = true
			)
		)
	}

}