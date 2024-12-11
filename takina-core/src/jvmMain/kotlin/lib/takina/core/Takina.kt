
package lib.takina.core

import lib.takina.core.builder.ConfigurationBuilder
import lib.takina.core.connector.AbstractConnector
import lib.takina.core.connector.socket.SocketConnector
import lib.takina.core.connector.socket.SocketConnectorConfig
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventBus
import lib.takina.core.eventbus.EventHandler
import lib.takina.core.exceptions.AuthenticationException
import lib.takina.core.exceptions.TakinaException
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.xmpp.modules.auth.SASLEvent

actual class Takina actual constructor(configuration: ConfigurationBuilder) : AbstractTakina(configuration) {

	private val log = LoggerFactory.logger("lib.takina.core.Takina")

	override fun createConnector(): AbstractConnector {
		val tlsProcessorFactory = (config.connection as SocketConnectorConfig).tlsProcessorFactory
		log.fine("Selected TLS Processor: ${tlsProcessorFactory.NAME}")
		return SocketConnector(this, tlsProcessorFactory)
	}

	override fun reconnect(immediately: Boolean) {
		log.finer { "Called reconnect. immediately=$immediately" }
		if (!immediately) Thread.sleep(3000)
		state = State.Connecting
		startConnector()
	}

	private val lock = Object()

	init {
		eventBus.mode = EventBus.Mode.ThreadPerHandler
//		this.config.connectorConfig = SocketConnectorConfig()
	}

	fun waitForAllResponses() {
		while (requestsManager.getWaitingRequestsSize() > 0) {
			synchronized(lock) {
				lock.wait(100)
			}
		}
	}

	fun connectAndWait() {
		var exceptionToThrow: Throwable? = null
		val handler = object : EventHandler<Event> {
			override fun onEvent(event: Event) {
				if (event is SASLEvent.SASLError) {
					exceptionToThrow = AuthenticationException(event.error, event.description ?: "Authentication error")
				} else if (event is TakinaStateChangeEvent) {
					if (event.newState == State.Connected || event.newState == State.Stopped) {
						synchronized(lock) {
							lock.notify()
						}
					}
				}
			}
		}
		try {
			eventBus.register(handler = handler)
			super.connect()
			synchronized(lock) {
				lock.wait(30000)
			}
			exceptionToThrow?.let { throw it }
			if (state != State.Connected && state != State.Stopped) {
				throw TakinaException("Cannot connect to XMPP server.")
			}
		} finally {
			eventBus.unregister(handler)
		}
	}
}