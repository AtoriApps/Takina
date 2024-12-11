
package lib.takina.core.connector

import lib.takina.core.AbstractTakina
import lib.takina.core.Scope
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventHandler
import lib.takina.core.exceptions.TakinaException
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.xmpp.JID
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.modules.*
import lib.takina.core.xmpp.modules.auth.SASL2Module
import lib.takina.core.xmpp.modules.auth.SASLEvent
import lib.takina.core.xmpp.modules.auth.SASLModule
import lib.takina.core.xmpp.modules.discovery.DiscoveryModule
import lib.takina.core.xmpp.modules.presence.PresenceModule
import lib.takina.core.xmpp.modules.roster.RosterModule
import lib.takina.core.xmpp.modules.sm.StreamManagementEvent
import lib.takina.core.xmpp.modules.sm.StreamManagementModule
import lib.takina.core.xmpp.toBareJID

abstract class AbstractSocketSessionController(final override val takina: AbstractTakina, loggerName: String) :
	SessionController {

	protected val log = LoggerFactory.logger(loggerName)

	private val eventsHandler: EventHandler<Event> = object : EventHandler<Event> {
		override fun onEvent(event: Event) {
			processEvent(event)
		}
	}

	protected open fun processStreamFeaturesEvent(event: StreamFeaturesEvent) {
		val authState = takina.authContext.state
		val isResumptionAvailable =
			takina.getModuleOrNull(StreamManagementModule)?.isResumptionAvailable() ?: false


		log.info { "authState=$authState; isResumptionAvailable=$isResumptionAvailable" }

		if (authState == lib.takina.core.xmpp.modules.auth.State.Unknown) {
			if (!isResumptionAvailable) {
				takina.getModuleOrNull(StreamManagementModule)?.reset()
			}

			val sasl1Module = takina.getModuleOrNull(SASLModule)
			val sasl2Module = takina.getModuleOrNull(SASL2Module)
			val registrationModule = takina.getModuleOrNull(InBandRegistrationModule)

			if (sasl2Module?.isAllowed(event.features) == true) {
				sasl2Module.startAuth(event.features)
			} else if (sasl1Module?.isAllowed(event.features) == true) {
				sasl1Module.startAuth(event.features)
			} else if (registrationModule?.isAllowed(event.features) == true && takina.config.registration != null) {
				processInBandRegistration()
			} else throw TakinaException("Cannot find supported auth or registration method.")
		}
		if (authState == lib.takina.core.xmpp.modules.auth.State.Success) {
			if (isResumptionAvailable) {
				takina.getModule(StreamManagementModule).resume()
			} else if (takina.getModuleOrNull(StreamFeaturesModule)
					?.isFeatureAvailable("bind", BindModule.XMLNS) == true
			) {
				bindResource()
			}
		}
	}

	private fun processInBandRegistration() {
		val registrationModule = takina.getModule(InBandRegistrationModule)
		val reg = takina.config.registration!!
		registrationModule.requestRegistrationForm(reg.domain.toBareJID()).response {
			it.onSuccess { requestForm ->
				reg.formHandler?.invoke(requestForm)
				reg.formHandlerWithResponse?.invoke(requestForm)?.let { resultForm ->
					registrationModule.submitRegistrationForm(reg.domain.toBareJID(), resultForm)
						.response { registrationResponse ->
							registrationResponse.onSuccess {
								log.info("Account registered")
								takina.disconnect()
							}
							registrationResponse.onFailure {
								log.info(it) { "Cannot register account." }
								throw TakinaException("Cannot register account", it)
							}
						}.send()
				}
			}
			it.onFailure {
				log.info(it) { "Cannot register account." }
				throw TakinaException("Cannot register account", it)
			}
		}.send()

	}

	private fun bindResource() {
		takina.getModuleOrNull(BindModule)?.bind()?.send() ?: throw TakinaException("BindModule is required.")
	}

	private fun processEvent(event: Event) {
		try {
			when (event) {
				is ParseErrorEvent -> processParseError(event)
				is SASLEvent.SASLError -> processAuthError(event)
				is StreamErrorEvent -> processStreamError(event)
				is ConnectionErrorEvent -> processConnectionError(event)
				is StreamFeaturesEvent -> processStreamFeaturesEvent(event)
				is SASLEvent.SASLSuccess -> processAuthSuccessfull(event)
				is StreamManagementEvent -> processStreamManagementEvent(event)
				is ConnectorStateChangeEvent -> processConnectorStateChangeEvent(event)
				is BindEvent.Success -> processBindSuccess(event.jid, event.inlineProtocol)
				is BindEvent.Failure -> processBindError()
			}
		} catch (e: XMPPException) {
			log.severe(e) { "Cannot establish connection" }
			takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Error in session processing"))
		} catch (e: TakinaException) {
			log.severe(e) { "Cannot establish connection" }
			takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Error in session processing"))
		}
	}

	private fun processConnectorStateChangeEvent(event: ConnectorStateChangeEvent) {
		if (event.oldState == State.Connected && (event.newState == State.Disconnected || event.newState == State.Disconnecting)) {
			log.fine { "Checking conditions to force timeout" }
			val isResumptionAvailable =
				takina.getModuleOrNull(StreamManagementModule)?.isResumptionAvailable()?:false
			if (!isResumptionAvailable) {
				takina.requestsManager.timeoutAll()
			}
		}
	}

	private fun processStreamManagementEvent(event: StreamManagementEvent) {
		when (event) {
			is StreamManagementEvent.Resumed -> takina.eventBus.fire(SessionController.SessionControllerEvents.Successful())
			is StreamManagementEvent.Failed -> {
				takina.requestsManager.timeoutAll()
				bindResource()
			}

			is StreamManagementEvent.Enabled -> {}
		}
	}

	private fun processBindSuccess(jid: JID, inlineProtocol: Boolean) {
		log.info("Binded")
		takina.getModuleOrNull(DiscoveryModule)?.let {
			it.discoverServerFeatures()
			it.discoverAccountFeatures()
		}
		takina.eventBus.fire(SessionController.SessionControllerEvents.Successful())
		takina.getModuleOrNull(PresenceModule)?.sendInitialPresence()
		takina.getModuleOrNull(RosterModule)?.rosterGet()?.send()
		if (!inlineProtocol) {
			takina.modules.getModuleOrNull(StreamManagementModule)?.enable()
		}
	}

	protected abstract fun processAuthSuccessfull(event: SASLEvent.SASLSuccess)

	protected abstract fun processConnectionError(event: ConnectionErrorEvent)

	protected open fun processParseError(event: ParseErrorEvent) {
		takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Parse error"))
	}

	protected open fun processBindError() {
		takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Session bind error"))
	}

	protected open fun processAuthError(event: SASLEvent.SASLError) {
		takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Authentication error."))
	}

	protected open fun processStreamError(event: StreamErrorEvent) {
		takina.clear(Scope.Connection)
		when (event.errorElement.name) {
			else -> takina.eventBus.fire(SessionController.SessionControllerEvents.ErrorReconnect("Stream error: ${event.condition}"))
		}
	}

	override fun start() {
		takina.eventBus.register(handler = eventsHandler)
		log.info { "Started session controller" }
	}

	override fun stop() {
		takina.eventBus.unregister(eventsHandler)
		log.info { "Stopped session controller" }
	}
}