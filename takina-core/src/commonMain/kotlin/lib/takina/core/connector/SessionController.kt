
package lib.takina.core.connector

import lib.takina.core.AbstractTakina
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition

interface SessionController {

	val takina: AbstractTakina

	sealed class SessionControllerEvents : Event(TYPE) {

		companion object : EventDefinition<SessionControllerEvents> {

			override val TYPE = "lib.takina.core.connector.SessionController.SessionControllerEvents"
		}

		data class ErrorStop(val message: String) : SessionControllerEvents()
		data class ErrorReconnect(
			val message: String, val immediately: Boolean = false, val force: Boolean = false,
		) : SessionControllerEvents()

		class Successful : SessionControllerEvents()
	}

	fun start()

	fun stop()

}