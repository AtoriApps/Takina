
package lib.takina.core.connector.socket

import jdk.net.ExtendedSocketOptions
import org.minidns.dnssec.DnssecValidationFailedException
import lib.takina.core.Takina
import lib.takina.core.configuration.declaredUserJID
import lib.takina.core.connector.*
import lib.takina.core.exceptions.TakinaException
import lib.takina.core.excutor.TickExecutor
import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xml.parser.StreamParser
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.modules.sm.StreamManagementModule
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.seconds

sealed class SocketConnectionErrorEvent : ConnectionErrorEvent() {

	class TLSFailureEvent : SocketConnectionErrorEvent()
	class HostNotFount : SocketConnectionErrorEvent()
	class Unknown(val caught: Throwable) : SocketConnectionErrorEvent() {

		override fun toString(): String {
			caught.printStackTrace()
			return "lib.takina.core.connector.socket.SocketConnectionErrorEvent.Unknown: " + caught.message
		}
	}

}

class HostNotFound : TakinaException()

typealias HostPort = Pair<String, Int>

var extendedSocketOptionsConfigurer: ((Socket)->Unit)? = null;

fun JDK11ExtendedSocketOptionConfigurer(socket: Socket) {
	socket.setOption(ExtendedSocketOptions.TCP_KEEPIDLE, 60)
	socket.setOption(ExtendedSocketOptions.TCP_KEEPCOUNT, 3)
	socket.setOption(ExtendedSocketOptions.TCP_KEEPINTERVAL, 90)
	socket.keepAlive = true
}

class SocketConnector(takina: Takina, val tlsProcesorFactory: TLSProcessorFactory) : AbstractConnector(takina),
	ChannelBindingDataProvider {

	companion object {

		const val SEE_OTHER_HOST_KEY = "lib.takina.core.connector.socket.SocketConnector#seeOtherHost"

		const val XMLNS_START_TLS = "urn:ietf:params:xml:ns:xmpp-tls"
	}

	private var tlsProcesor: TLSProcessor? = null

	val secured: Boolean
		get() = tlsProcesor?.isConnectionSecure() ?: false

	private var started = false

	private val log = LoggerFactory.logger("lib.takina.core.connector.socket.SocketConnector")

	private var socket: Socket? = null

	private var worker: SocketWorker? = null

	private val whitespacePingExecutor = TickExecutor(takina.eventBus, 30.seconds) { onTick() }

	private var whiteSpaceEnabled: Boolean = true

	private var config: SocketConnectorConfig = takina.config.connection as SocketConnectorConfig

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
			processReceivedElement(element)
		}

		override fun onStreamClosed() {
			log.finest { "Stream closed" }
			this@SocketConnector.fire(StreamTerminatedEvent())
		}

		override fun onStreamStarted(attrs: Map<String, String>) {
			log.finest { "Stream started: $attrs" }
			this@SocketConnector.fire(StreamStartedEvent(attrs))
		}

		override fun onParseError(errorMessage: String) {
			log.finest { "Parse error: $errorMessage" }
			this@SocketConnector.fire(ParseErrorEvent(errorMessage))
		}
	}

	private fun processReceivedElement(element: Element) {
		when (element.xmlns) {
			XMLNS_START_TLS -> processTLSStanza(element)
			else -> handleReceivedElement(element)
		}
	}

	private fun processTLSStanza(element: Element) {
		when (element.name) {
			"proceed" -> {
				proceedTLS()
			}

			"failure" -> {
				log.warning { "Cannot establish TLS connection!" }
				fire(SocketConnectionErrorEvent.TLSFailureEvent())
			}

			else -> throw XMPPException(ErrorCondition.BadRequest)
		}
	}


	private fun proceedTLS() {
		log.info { "Proceeding TLS" }
		try {
			log.finest { "Disabling whitespace ping" }
			whiteSpaceEnabled = false

			tlsProcesor?.proceedTLS { inputStream, outputStream ->
				worker?.setReaderAndWriter(
					InputStreamReader(inputStream), OutputStreamWriter(outputStream)
				) ?: throw TakinaException("Socket worker not initialized")
			} ?: throw TakinaException("TLS Processor not initialized")

			restartStream()
		} catch (e: Throwable) {
			state = State.Disconnecting
			fire(createSocketConnectionErrorEvent(e))
		} finally {
			log.finest { "Enabling whitespace ping" }
			whiteSpaceEnabled = true
		}
	}

	override fun createSessionController(): SessionController = SocketSessionController(takina, this)

	private fun resolveTarget(completionHandler: (List<HostPort>) -> Unit) {
		val hosts = mutableListOf<HostPort>()

		val location = takina.getModuleOrNull(StreamManagementModule)?.resumptionLocation
		if (location != null) {
			hosts += HostPort(location, config.port)
			log.fine { "Using host ${location}:${config.port}" }
			completionHandler(hosts)
			return
		}

		val seeOther = takina.internalDataStore.getData<String>(SEE_OTHER_HOST_KEY)
		if (seeOther != null) {
			hosts += HostPort(seeOther, config.port)
			log.fine { "Using host ${seeOther}:${config.port}" }
			completionHandler(hosts)
			return
		}

		if (config.hostname != null) {
			hosts += HostPort(config.hostname!!, config.port)
			log.fine { "Using host ${config.hostname}:${config.port}" }
			completionHandler(hosts)
			return
		}

		log.fine { "Resolving DNS of ${config.domain}" }
		config.dnsResolver.resolve(config.domain) { result ->
			result.onFailure {
				hosts += HostPort(config.domain, config.port)
			}
			result.onSuccess {
				hosts.addAll(it.shuffled().map { HostPort(it.target, it.port.toInt()) })
			}

			completionHandler(hosts)
		}
	}

	private fun createSocket(completionHandler: (Socket) -> Unit) {
		resolveTarget { hosts ->
			hosts.forEach { hp ->
				try {
					log.fine { "Opening connection to ${hp.first}:${hp.second}" }
					val s = Socket(hp.first, hp.second)
					completionHandler(s)
					return@resolveTarget
				} catch (e: Throwable) {
					log.fine { "Host ${hp.first}:${hp.second} is unreachable." }
				}
			}
			throw HostNotFound()
		}
	}

	override fun start() {
		started = true
		state = State.Connecting

		val userJid = takina.config.declaredUserJID
		val domain = (takina.config.connection as SocketConnectorConfig).domain
		try {
			createSocket { sckt ->
				this.socket = sckt
				sckt.soTimeout = 20 * 1000
				sckt.tcpNoDelay = true
				extendedSocketOptionsConfigurer?.invoke(sckt);
				log.fine { "Opening socket connection to ${sckt.inetAddress}" }
				this.worker = SocketWorker(parser).apply {
					setReaderAndWriter(
						InputStreamReader(sckt.getInputStream()), OutputStreamWriter(sckt.getOutputStream())
					)
				}.apply {
					onError = { exception -> onWorkerException(exception) }
				}
				this.tlsProcesor = tlsProcesorFactory.create(sckt, config)
				worker?.start() ?: throw TakinaException("Socket Worker not created properly.")
				val sb = buildString {
					append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
					append("version='1.0' ")
					if (userJid != null) append("from='$userJid' ")
					append("to='${domain}'")
					append(">")

				}
				send(sb)

				state = State.Connected
				whitespacePingExecutor.start()
			}
		} catch (e: HostNotFound) {
			state = State.Disconnected
			fire(SocketConnectionErrorEvent.HostNotFount())
		} catch (e: Exception) {
			state = State.Disconnected
			fire(createSocketConnectionErrorEvent(e))
			eventsEnabled = false
		}
	}

	private fun onWorkerException(cause: Exception) {
		cause.printStackTrace()
		fire(createSocketConnectionErrorEvent(cause))
		state = when (state) {
			State.Connecting -> State.Disconnected
			State.Connected -> State.Disconnecting
			State.Disconnecting -> State.Disconnected
			State.Disconnected -> State.Disconnected
		}
		if (state == State.Disconnected) eventsEnabled = false
	}

	private fun createSocketConnectionErrorEvent(cause: Throwable): SocketConnectionErrorEvent = when (cause) {
		is UnknownHostException, is DnssecValidationFailedException -> SocketConnectionErrorEvent.HostNotFount()
		else -> SocketConnectionErrorEvent.Unknown(cause)
	}

	override fun stop() {
		started = false
		if ((state != State.Disconnected)) {
			log.fine { "Stopping..." }
			try {
				if (state == State.Connected) closeStream()
				state = State.Disconnecting
				whitespacePingExecutor.stop()
				Thread.sleep(175)
				if (this.socket?.isClosed == false) {
					worker?.writer?.close()
					this.socket?.close()
				}
				worker?.interrupt()
				tlsProcesor?.clear()
				while (worker?.isActive == true) Thread.sleep(32)
			} finally {
				log.fine { "Stopped" }
				this.state = State.Disconnected
				this.worker = null
				this.socket = null
				this.eventsEnabled = false
			}
		}
	}

	private fun closeStream() {
		if (state == State.Connected) send("</stream:stream>")
	}

	override fun send(data: CharSequence) {
		try {
			log.finest { "Sending (${socket?.isConnected}, ${!(socket?.isOutputShutdown ?: true)}): $data" }
			worker?.let {
				it.writer.write(data.toString())
				it.writer.flush()
			} ?: throw TakinaException("Socket Worker not initialized.")
		} catch (e: Exception) {
			log.warning(e) { "Cannot send data to server" }
			state = State.Disconnecting
			fire(createSocketConnectionErrorEvent(e))
			throw e
		}
	}

	fun restartStream() {
		val userJid = takina.config.declaredUserJID
		val domain = (takina.config.connection as SocketConnectorConfig).domain

		val sb = buildString {
			append("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' ")
			append("version='1.0' ")
			if (userJid != null) append("from='$userJid' ")
			append("to='${domain}'")
			append(">")
		}
		send(sb)
	}

	private fun onTick() {
		if (state == State.Connected && whiteSpaceEnabled) {
			log.finer { "Whitespace ping" }
			worker?.writer?.write(' '.code)
			worker?.writer?.flush()
		}
	}

	fun startTLS() {
		log.info { "Running StartTLS" }
		whiteSpaceEnabled = false
		val element = element("starttls") {
			xmlns = XMLNS_START_TLS
		}
		takina.writer.writeDirectly(element)
	}

	override fun isConnectionSecure(): Boolean = tlsProcesor?.isConnectionSecure() ?: false

	override fun getTlsUnique(): ByteArray? = tlsProcesor?.getTlsUnique()

	override fun getTlsServerEndpoint(): ByteArray? = tlsProcesor?.getTlsServerEndpoint()

	override fun getTlsExporter(): ByteArray? = tlsProcesor?.getTlsExporter()

}