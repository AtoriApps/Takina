
package lib.takina.core.connector

import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event
import lib.takina.core.Takina
import lib.takina.core.configuration.declaredUserJID
import lib.takina.core.excutor.TickExecutor
import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.xml.Element
import lib.takina.core.xml.parser.StreamParser
import lib.takina.core.xmpp.modules.discoaltconn.AlternativeConnectionMethodModule
import lib.takina.core.xmpp.modules.discoaltconn.HostLink
import kotlin.time.Duration.Companion.seconds

class WebSocketConnectionErrorEvent(@Suppress("unused") val description: String) : ConnectionErrorEvent()

class WebSocketConnector(takina: Takina) : AbstractConnector(takina) {

	private val log = LoggerFactory.logger("lib.takina.core.connector.WebSocketConnector")

	private var config: WebSocketConnectorConfig = takina.config.connection as WebSocketConnectorConfig

	private val whitespacePingExecutor = TickExecutor(takina.eventBus, 25.seconds) { onTick() }

	private val parser = object : StreamParser() {
		private fun logReceivedStanza(element: Element) {
			when {
				log.isLoggable(Level.FINEST) -> log.finest("Received element ${element.getAsString()}")
				log.isLoggable(Level.FINER) -> log.finer(
					"Received element ${
						element.getAsString()
					}"
				)

				log.isLoggable(Level.FINE) -> log.fine(
					"Received element ${
						element.getAsString()
					}"
				)
			}
		}

		override fun onNextElement(element: Element) {
			logReceivedStanza(element)
			handleReceivedElement(element);
		}

		override fun onStreamClosed() {
			log.finest { "Stream closed" }
			takina.eventBus.fire(StreamTerminatedEvent())
		}

		override fun onStreamStarted(attrs: Map<String, String>) {
			log.finest { "Stream started: $attrs" }
			takina.eventBus.fire(StreamStartedEvent(attrs))
		}

		override fun onParseError(errorMessage: String) {
			log.finest { "Parse error: $errorMessage" }
			takina.eventBus.fire(ParseErrorEvent(errorMessage))
		}
	}

	private lateinit var ws: WebSocket

	override fun createSessionController(): SessionController = WebSocketSessionController(takina, this)

	override fun send(data: CharSequence) {
		log.finest { "Sending: $data" }
		try {
			this.ws.send(data.toString())
		} catch (e: Throwable) {
			log.warning(e) { "Cannot send data." }
			takina.eventBus.fire(WebSocketConnectionErrorEvent("Cannot send data"))
			throw e
		}
	}

	override fun start() {
		state = State.Connecting
		log.fine { "Starting WebSocket connector" }

		createSocket { sckt ->
			log.finest { "Created WS: $sckt" }

			this.ws = sckt
			ws.onmessage = this::onSocketMessageEvent
			ws.onerror = this::onSocketError
			ws.onopen = this::onSocketOpen
			ws.onclose = this::onSocketClose
		}
	}

	private fun createSocket(completionHandler: (WebSocket) -> Unit) {
		resolveTarget { urls ->
			for (url in urls) {
				if (!config.allowUnsecureConnection && url.startsWith("ws:")) {
					throw ConnectorException("Unsecure connection is not allowed.")
				}
				try {
					log.info { "Opening WebSocket connection to $url" }
					val s = WebSocket(url, "xmpp")
					completionHandler(s)
					return@resolveTarget
				} catch (e: Throwable) {
					log.fine(e) { "Websocket $url is unreachable. ${e.message}" }
				}
			}
			throw ConnectorException("Cannot open WebSocket connection.")
		}
	}

	private fun resolveTarget(completionHandler: (List<String>) -> Unit) {
		config.webSocketUrl?.let {
			log.fine("WebSocket URL is declared: $it")
			completionHandler(listOf(it))
			return
		}

		val result = mutableListOf<String>()

		val md = takina.getModuleOrNull(AlternativeConnectionMethodModule)
		if (md != null) {
			log.fine("Checking alternative connection methods...")
			md.discovery(config.domain) {
				log.fine("Found connection methods: $it")
				it.searchForProtocol("wss:").forEach { url ->
					log.fine("Found secure WebSocket endpoint: $url")
					result.add(url)
				}
				if (config.allowUnsecureConnection) {
					it.searchForProtocol("ws:").forEach { url ->
						log.fine("Found unsecure WebSocket endpoint: $url")
						result.add(url)
					}
				}

				result += "wss://${config.domain}:5291/"
				completionHandler(result)
			}
		} else {
			log.fine("Creating default WebSocket endpoints.")
			result.add("wss://${config.domain}:5291/")

			if (config.allowUnsecureConnection) {
				result.add("ws://${config.domain}:5290/")
			}

			completionHandler(result)
		}
	}

	private fun onSocketClose(event: Event): dynamic {
		log.fine { "Socket is closed: $event" }
		if (state == State.Connected) takina.eventBus.fire(WebSocketConnectionErrorEvent("Socket unexpectedly disconnected."))
		state = State.Disconnected
		eventsEnabled = false
		return true
	}

	private fun onSocketOpen(event: Event): dynamic {
		log.fine { "Socket opened $event" }
		state = State.Connected
		whitespacePingExecutor.start()

		restartStream()

		return true
	}

	private fun onSocketError(event: Event): dynamic {
		log.warning { "Socket error: $event   ${event.type}   ${JSON.stringify(event)}" }

		takina.eventBus.fire(WebSocketConnectionErrorEvent("Unknown error"))
		state = when (state) {
			State.Connecting -> State.Disconnected
			State.Connected -> State.Disconnecting
			State.Disconnecting -> State.Disconnected
			State.Disconnected -> State.Disconnected
		}
		if (state == State.Disconnected) eventsEnabled = false
		return true
	}

	private fun onSocketMessageEvent(event: MessageEvent): dynamic {
		log.fine { "Received: ${event.data}" }
		parser.parse(event.data.toString())

		return true
	}

	override fun stop() {
		log.info { "Stopping WebSocket connector" }
		whitespacePingExecutor.stop()
		if (state == State.Connected) closeStream()
		state = State.Disconnecting
		this.ws.close()
	}

	private fun closeStream() {
		send("</stream:stream>")
	}

	fun restartStream() {
		log.finest { "Send new stream" }
		val userJid = takina.config.declaredUserJID
		val domain = (takina.config.connection as WebSocketConnectorConfig).domain

		val sb = buildString {
			append("<stream:stream ")
			append("xmlns='jabber:client' ")
			append("xmlns:stream='http://etherx.jabber.org/streams' ")
			append("version='1.0' ")
			if (userJid != null) append("from='${userJid}' ")
			append("to='${domain}' ")
			append(">")
		}

		send(sb)
	}

	private fun onTick() {
		if (state == State.Connected) {
			log.finer { "Whitespace ping" }
			this.ws.send("")
		}
	}

}

private fun List<HostLink>.searchForProtocol(protocolPrefix: String) =
	this.filter { it.rel == "urn:xmpp:alt-connections:websocket" }.filter { it.href.startsWith(protocolPrefix) }
		.map { it.href }