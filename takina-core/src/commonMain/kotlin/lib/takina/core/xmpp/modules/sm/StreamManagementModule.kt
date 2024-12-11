
package lib.takina.core.xmpp.modules.sm

import kotlinx.serialization.Serializable
import lib.takina.core.ClearedEvent
import lib.takina.core.Context
import lib.takina.core.Scope
import lib.takina.core.TickEvent
import lib.takina.core.builder.TakinaConfigDsl
import lib.takina.core.configuration.declaredUserJID
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.exceptions.TakinaException
import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.ModulesManager
import lib.takina.core.modules.XmppModule
import lib.takina.core.modules.XmppModuleProvider
import lib.takina.core.requests.Request
import lib.takina.core.utils.Lock
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.modules.auth.*
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.Message
import lib.takina.core.xmpp.stanzas.Presence
import kotlin.math.max


/**
 * Stream Management event.
 */
sealed class StreamManagementEvent : Event(TYPE) {

	companion object : EventDefinition<StreamManagementEvent> {

		override val TYPE = "lib.takina.core.xmpp.modules.sm.StreamManagementModule.StreamManagementEvent"
	}

	/**
	 * Fired when stream management is enabled.
	 * @param id generated SM id.
	 * @param resume `true` is server supports resumption.
	 * @param mx server's preferred maximum resumption time.
	 *
	 */
	class Enabled(val id: String, val resume: Boolean, val mx: Long?) : StreamManagementEvent()

	/**
	 * Fired when stream management operation failed.
	 * @param error error condition.
	 */
	class Failed(val error: ErrorCondition) : StreamManagementEvent()

	/**
	 * Fired when session is resumed.
	 * @param h sequen ce number of last handled stanza.
	 * @param prevId Stream Management ID.
	 */
	class Resumed(val h: Long, val prevId: String) : StreamManagementEvent()
}

@TakinaConfigDsl
interface StreamManagementModuleConfig

/**
 * Module is implementing Stream Management ([XEP-0198](https://xmpp.org/extensions/xep-0198.html)).
 */
class StreamManagementModule(override val context: Context) : XmppModule, InlineProtocol, StreamManagementModuleConfig {

	enum class State {
		disabled,
		activating,
		active,
		awaitingResumption,
		resuming
	}
	
	@Serializable
	class ResumptionContext {

		var state: State = State.disabled
		
		var resumptionTime: Long = 0
			internal set

		var incomingH: Long = 0L
			internal set

		var outgoingH: Long = 0L
			internal set

		var incomingLastSentH: Long = 0L
			internal set
		
		var resID: String? = null
			internal set

		var isResumeEnabled: Boolean = false
			internal set

		var location: String? = null
			internal set

		fun isResumptionAvailable() = resID != null && isResumeEnabled

	}

	/**
	 * Module is implementing Stream Management ([XEP-0198](https://xmpp.org/extensions/xep-0198.html)).
	 */
	companion object : XmppModuleProvider<StreamManagementModule, StreamManagementModuleConfig> {

		const val XMLNS = "urn:xmpp:sm:3"
		override val TYPE = XMLNS
		override fun instance(context: Context): StreamManagementModule = StreamManagementModule(context)

		override fun configure(module: StreamManagementModule, cfg: StreamManagementModuleConfig.() -> Unit) =
			module.cfg()

		override fun doAfterRegistration(module: StreamManagementModule, moduleManager: ModulesManager) =
			module.initialize()

	}

	private val resumptionContextLock = Lock();
	private var _resumptionContext: ResumptionContext by property(Scope.Session) { ResumptionContext() }

	fun <V> withResumptionContext(fn: (resumptionContext: ResumptionContext)->V): V {
		return resumptionContextLock.withLock {
			fn(_resumptionContext);
		}
	};

	override val type = TYPE
	override val features = arrayOf(XMLNS)
	override val criteria = null //Criterion.xmlns(XMLNS)

	private val log = LoggerFactory.logger("lib.takina.core.xmpp.modules.sm.StreamManagementModule")

	private val queue = ArrayList<Any>()

	val isActive: Boolean
		get() = withResumptionContext { it.state == State.active }

	val resumptionLocation: String?
		get() = withResumptionContext { it.location }

	fun isResumptionAvailable(): Boolean = withResumptionContext { it.isResumptionAvailable() }

	private fun initialize() {
		context.eventBus.register(ClearedEvent) {
			if (it.scopes.contains(Scope.Connection)) {
				log.fine { "Disabling ACK" }
				withResumptionContext { resumptionContext ->
					resumptionContext.state = State.awaitingResumption
				}
			}
		}
		context.eventBus.register(TickEvent) { onTick() }
	}

	private fun onTick() {
		if (isActive) {
			if (queue.size > 0) request()
			sendAck(false)
		}
	}

	private fun isElementCounted(element: Element) =
		when (element.name) {
			IQ.NAME, Message.NAME, Presence.NAME -> true
			else -> false
		}

	/**
	 * Processes received element and if it is handled (it shouldn't be processed by other modules) it returns true.
	 */
	fun processElementReceived(element: Element): Boolean {
		if (element.xmlns == XMLNS) {
			process(element)
			return true;
		} else {
			withResumptionContext { resumptionContext ->
				if (!isActive) return@withResumptionContext
				if (!isElementCounted(element)) return@withResumptionContext

				++resumptionContext.incomingH
			}
			return false
		}
	}

	/**
	 * Processes sending element and if it is queued for confirmation from the server it returns true.
	 */
	fun processElementSent(element: Element, request: Request<*, *>?): Boolean = withResumptionContext { resumptionContext ->
		when(resumptionContext.state) {
			State.disabled ->  {
				log.finest { "SMM: for account ${context.config.declaredUserJID}, queuing disabled: ${element.getAsString()}"}
				if (element.name == "enable" && element.xmlns == XMLNS) {
					resumptionContext.state = State.activating
				}
				return@withResumptionContext false
			}
			State.activating, State.active -> {
				if (!isElementCounted(element)) {
					log.finest { "SMM: for account ${context.config.declaredUserJID} skipping queuing nonza: ${element.getAsString()}"}
					return@withResumptionContext false
				};

				if (request != null) {
					queue.add(request)
				} else {
					queue.add(element)
				}
				val newOutgoing = ++resumptionContext.outgoingH
				log.finest { "SMM: for account ${context.config.declaredUserJID} new value $newOutgoing after queuing: ${element.getAsString()}"}
				return@withResumptionContext true;
			}
			else -> {
				log.finest { "SMM: for account ${context.config.declaredUserJID}, not queuing in state: ${resumptionContext.state}, ${element.getAsString()}"}
				return@withResumptionContext false
			}
		}
	}

	/**
	 * Clear the outgoing request queue.
	 */
	fun reset() {
		resumptionContextLock.withLock {
			queue.clear()
			_resumptionContext = ResumptionContext();
		}
	}

	private fun processFailed(element: Element) {
		reset()
		val e = ErrorCondition.getByElementName(
			element.getChildrenNS(XMPPException.XMLNS).first().name
		)
		context.eventBus.fire(StreamManagementEvent.Failed(e))
	}

	private fun processEnabled(element: Element) {
		val id = element.attributes["id"]!!
		val location = element.attributes["location"]
		val resume = element.attributes["resume"]?.toBoolean() ?: false
		// server's preferred maximum resumption time
		val mx = element.attributes["max"]?.toLong() ?: 0

		withResumptionContext { ctx ->
			ctx.resID = id
			ctx.isResumeEnabled = resume
			ctx.location = location
			ctx.resumptionTime = mx
			ctx.state = State.active
		}

		context.eventBus.fire(StreamManagementEvent.Enabled(id, resume, mx))
	}

	/**
	 * Process ACK answer from server.
	 */
	private fun processAckResponse(element: Element) {
		val h = element.attributes["h"]?.toLong() ?: 0
		withResumptionContext { resumptionContext ->
			val lh = resumptionContext.outgoingH

			log.finest { "SMM: for account ${context.config.declaredUserJID} expected h=$lh, received h=$h, queue=${queue.size}"}
			if(log.isLoggable(Level.FINE)){
				log.fine { "queue=$queue" }
			}

			val left = max(lh - h, 0)
			removeFromQueue(left)
		}.forEach(this::markAsAcked)
	}

	private fun markAsAcked(x: Request<*,*>) {
		x.markAsSent()
		log.finest { "Marked as 'delivered to server': $x" }
	}

	/**
	 * Process ACK request from server.
	 */
	fun sendAck(force: Boolean) {
		val h = withResumptionContext { resumptionContext ->
			val h = resumptionContext.incomingH
			val lastH = resumptionContext.incomingLastSentH

			if (!force && h == lastH) {
				null
			} else {
				resumptionContext.incomingLastSentH = h
				h
			}
		} ?: return;
		
		context.writer.writeDirectly(element("a") {
			xmlns = XMLNS
			attribute("h", h.toString())
		})
	}

	private fun removeFromQueue(left: Long): List<Request<*,*>> {
		var result = mutableListOf<Request<*, *>>();
		while (left < queue.size) {
			val x = queue.removeFirst()
			if (x is Request<*, *>) {
				result.add(x);
			}
		}
		return result;
	}

	private fun processResumed(element: Element) {
		val h = element.attributes["h"]?.toLong() ?: 0
		val id = element.attributes["previd"] ?: ""

		val (sent, unacked) = withResumptionContext { ctx ->
			val unacked = mutableListOf<Any>()
			val lh = ctx.outgoingH
			val left = lh - h
			val sent = if (left > 0) removeFromQueue(left) else emptyList();
			ctx.outgoingH = h
			unacked.addAll(queue)
			queue.clear()
			ctx.state = State.active
			Pair(sent, unacked)
		}

		sent.forEach(this::markAsAcked)
		
		unacked.forEach {
			when (it) {
				is Request<*, *> -> context.writer.write(it)
				is Element -> context.writer.writeDirectly(it)
			}
		}
		
		context.eventBus.fire(StreamManagementEvent.Resumed(h, id))
	}

	/**
	 * Enable stream management.
	 */
	fun enable() {
		context.writer.writeDirectly(element("enable") {
			xmlns = XMLNS
			attribute("resume", "true")
		})
	}

	override fun process(element: Element) {
		when (element.name) {
			"r" -> sendAck(true)
			"a" -> processAckResponse(element)
			"enabled" -> processEnabled(element)
			"resumed" -> processResumed(element)
			"failed" -> processFailed(element)
			else -> throw XMPPException(ErrorCondition.FeatureNotImplemented)
		}
	}

	/**
	 * Send ACK request to server
	 */
	fun request() {
		if (isActive) {
			log.fine { "Sending ACK request" }
			context.writer.writeDirectly(element("r") { xmlns = XMLNS })
		}
	}

	/**
	 * Start session resumption.
	 */
	fun resume() {
		val (h,id) = withResumptionContext { resumptionContext ->
			val h = resumptionContext.incomingH
			val id = resumptionContext.resID ?: throw TakinaException("Cannot resume session: no resumption ID")
			Pair(h, id)
		}

		withResumptionContext {
			it.state = State.resuming
		}

		context.writer.writeDirectly(element("resume") {
			xmlns = XMLNS
			attribute("h", h.toString())
			attribute("previd", id)
		})
	}

	override fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element? {
		return when (stage) {
			InlineProtocolStage.AfterSasl -> {
				withResumptionContext { resumptionContext ->
					if (resumptionContext.isResumptionAvailable() && features.supports("sm", XMLNS)) {
						val h = resumptionContext.incomingH
						val id =
							resumptionContext.resID ?: throw TakinaException("Cannot resume session: no resumption ID")
						element("resume") {
							xmlns = XMLNS
							attribute("h", h.toString())
							attribute("previd", id)
						}
					} else null
				}
			}

			InlineProtocolStage.AfterBind -> {
				if (features.supports(XMLNS)) element("enable") {
					xmlns = XMLNS
					attribute("resume", "true")
				} else null
			}
		}
	}

	override fun process(response: InlineResponse) {
		response.whenExists(InlineProtocolStage.AfterSasl, "resumed", XMLNS) { processResumed(it) }
		response.whenExists(InlineProtocolStage.AfterBind, "enabled", XMLNS) { processEnabled(it) }
	}

}