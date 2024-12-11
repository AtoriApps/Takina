
package lib.takina.core

import lib.takina.core.builder.ConfigurationBuilder
import lib.takina.core.configuration.Configuration
import lib.takina.core.connector.*
import lib.takina.core.eventbus.Event
import lib.takina.core.eventbus.EventBus
import lib.takina.core.eventbus.EventDefinition
import lib.takina.core.eventbus.EventHandler
import lib.takina.core.exceptions.TakinaException
import lib.takina.core.logger.Level
import lib.takina.core.logger.LoggerFactory
import lib.takina.core.modules.*
import lib.takina.core.requests.Request
import lib.takina.core.requests.RequestBuilderFactory
import lib.takina.core.requests.RequestsManager
import lib.takina.core.utils.Lock
import lib.takina.core.xml.Element
import lib.takina.core.xml.element
import lib.takina.core.xmpp.ErrorCondition
import lib.takina.core.xmpp.FullJID
import lib.takina.core.xmpp.XMPPException
import lib.takina.core.xmpp.modules.auth.SASLContext
import lib.takina.core.xmpp.modules.sm.StreamManagementModule
import lib.takina.core.xmpp.stanzas.IQ
import lib.takina.core.xmpp.stanzas.IQType

data class TakinaStateChangeEvent(val oldState: AbstractTakina.State, val newState: AbstractTakina.State) :
    Event(TYPE) {

    companion object : EventDefinition<TakinaStateChangeEvent> {

        override val TYPE = "lib.takina.core.TakinaStateChangeEvent"
    }
}

data class TickEvent(val counter: Long) : Event(TYPE) {

    companion object : EventDefinition<TickEvent> {

        override val TYPE = "lib.takina.core.TickEvent"
    }
}

@Suppress("LeakingThis")
abstract class AbstractTakina(configurator: ConfigurationBuilder) : Context, PacketWriter {

    var running: Boolean = false
        private set

    private val log = LoggerFactory.logger("lib.takina.core.AbstractTakina")

    enum class State {

        Connecting, Connected, Disconnecting, Disconnected, Stopped
    }

    internal var connector: AbstractConnector? = null
    protected var sessionController: SessionController? = null
    final override val eventBus: EventBus = EventBus(this)
    override val authContext: SASLContext by property(Scope.Connection) { SASLContext() }
    override var boundJID: FullJID? by propertySimple(Scope.Session, null)

    var autoReconnect: Boolean = true

    override val request = RequestBuilderFactory(this)

    override val writer: PacketWriter
        get() = this
    final override val modules: ModulesManager = ModulesManager()
    val internalDataStore = InternalDataStore()
    val requestsManager: RequestsManager = RequestsManager()
    private val executor = lib.takina.core.excutor.Executor()
    override val config: Configuration
    var state = State.Stopped
        internal set(value) {
            val old = field
            field = value
            if (old != field) eventBus.fire(TakinaStateChangeEvent(old, field))
        }

    init {
        modules.context = this
        this.config = configurator.build()

        eventBus.register(ReceivedXMLElementEvent, ::processReceivedXmlElementEvent)
        eventBus.register(SessionController.SessionControllerEvents, ::onSessionControllerEvent)
        eventBus.register<TickEvent>(TickEvent) { requestsManager.findOutdated() }

        configurator.modulesConfigBuilder.initializeModules(modules)
    }

    protected open fun onSessionControllerEvent(event: SessionController.SessionControllerEvents) {
        when (event) {
            is SessionController.SessionControllerEvents.ErrorStop, is SessionController.SessionControllerEvents.ErrorReconnect -> processControllerErrorEvent(
                event
            )

            is SessionController.SessionControllerEvents.Successful -> onSessionEstablished()
        }
    }

    private fun onSessionEstablished() {
        log.info("Session established")
        state = State.Connected
        requestsManager.boundJID = boundJID
    }

    private fun processControllerErrorEvent(event: SessionController.SessionControllerEvents) {
        if (event is SessionController.SessionControllerEvents.ErrorReconnect && (this.autoReconnect || event.force)) {
            state = State.Disconnected
            stopConnector {
                reconnect(event.immediately)
            }
        } else {
            disconnect()
        }
    }

    abstract fun reconnect(immediately: Boolean = false)

    protected fun processReceivedXmlElementEvent(event: ReceivedXMLElementEvent) {
        processReceivedXmlElement(event.element)
    }

    internal fun processReceivedXmlElement(receivedElement: Element) {
        modules.processReceiveInterceptors(receivedElement) {
            it.onSuccess { element ->
                if (element == null) return@onSuccess
                val handled = requestsManager.findAndExecute(element)
                if (element.name == IQ.NAME && (handled || (element.attributes["type"] == IQType.Result.value || element.attributes["type"] == IQType.Error.value))) return@onSuccess


                val modules = modules.getModulesFor(element)
                if (modules.isEmpty()) {
                    log.fine { "Unsupported stanza: " + element.getAsString() }
                    sendErrorBack(element, XMPPException(ErrorCondition.FeatureNotImplemented))
                    return@onSuccess
                }

                executor.execute {
                    try {
                        modules.forEach {
                            it.process(element)
                        }
                    } catch (e: XMPPException) {
                        log.finest(e) { "Error ${e.condition} during processing stanza ${element.getAsString()}" }
                        sendErrorBack(element, e)
                    } catch (e: Exception) {
                        log.finest(e) { "Problem on processing element ${element.getAsString()}" }
                        sendErrorBack(element, e)
                    }
                }
            }
            it.onFailure { e ->
                log.info(e) { "Problem on processing element ${receivedElement.getAsString()}" }
                sendErrorBack(receivedElement, e)
            }
        }
    }

    private fun createError(element: Element, caught: Throwable): Element? {
        if (caught is XMPPException) {
            return createError(element, caught.condition, caught.message)
        } else {
            return null
        }
    }

    private fun createError(element: Element, errorCondition: ErrorCondition, msg: String?): Element {
        val resp = element(element.name) {
            attribute("type", "error")
            element.attributes["id"]?.apply {
                attribute("id", this)
            }
            element.attributes["from"]?.apply {
                attribute("to", this)
            }

            "error" {
                errorCondition.type?.let {
                    attribute("type", it)
                }
                errorCondition.errorCode?.let {
                    attribute("code", it.toString())
                }

                errorCondition.elementName {
                    attribute("xmlns", XMPPException.XMLNS)
                }

                msg?.let {
                    "text" {
                        attribute("xmlns", XMPPException.XMLNS)
                        +it
                    }
                }
            }
        }
        return resp
    }

    private fun sendErrorBack(element: Element, exception: Throwable) {
        when (element.name) {
            "iq", "presence", "message" -> {
                if (element.attributes["type"] == "error") {
                    log.fine { "Ignoring unexpected error response" }
                    return
                }
                createError(element, exception)?.apply {
                    writeDirectly(this)
                }
            }

            else -> {
                writeDirectly(element("stream:error") {
                    "unsupported-stanza-type" {
                        xmlns = "urn:ietf:params:xml:ns:xmpp-streams"
                    }
                })
                connector?.stop()
            }
        }
    }

    protected abstract fun createConnector(): AbstractConnector

    protected fun getConnectorState(): lib.takina.core.connector.State =
        this.connector?.state ?: lib.takina.core.connector.State.Disconnected

    private fun logSendingStanza(element: Element) {
        when {
            log.isLoggable(Level.FINEST) -> log.finest("Sending: ${element.getAsString()}")
            log.isLoggable(Level.FINER) -> log.finer("Sending: ${element.getAsString()}")
            log.isLoggable(Level.FINE) -> log.fine("Sending: ${element.getAsString()}")
        }
    }

    override fun writeDirectly(stanza: Element) {
        val c = this.connector ?: return run {
            log.fine {"skipping sending stanza ${stanza}, connector is not initialized!" }
        }
        if (c.state != lib.takina.core.connector.State.Connected) {
            log.fine {"skipping sending stanza ${stanza}, connector is not connected!" }
            return;
        }
        modules.processOutgoingFilters(stanza) {
            it.onSuccess { toSend ->
                if (toSend == null) return@onSuccess
                logSendingStanza(toSend)
                senderLock.withLock {
                    getModuleOrNull(StreamManagementModule)?.processElementSent(toSend, null)
                    c.send(toSend.getAsString())
                }
                eventBus.fire(SentXMLElementEvent(toSend, null))
            }
            it.onFailure {
                log.warning(it) { "Problem on filtering stanza ${stanza.getAsString()}" }
            }
        }
    }

    override fun write(request: Request<*, *>) {
        val c = this.connector ?: return run {
            log.fine("Returning remote_server_timeout error, connector is not initialized!")
            request.markTimeout()
        }
        if (c.state != lib.takina.core.connector.State.Connected) {
            log.fine("Returning remote_server_timeout error, connector is not connected!")
            request.markTimeout()
            return;
        }
        modules.processOutgoingFilters(request.stanza) { it ->
            it.onSuccess { element ->
                if (element == null) return@onSuccess
                requestsManager.register(request)
                logSendingStanza(element)
                if (!senderLock.withLock {
                    val smHandled = getModuleOrNull(StreamManagementModule)?.processElementSent(element, request) ?: false;
                    c.send(element.getAsString())
                    smHandled
                }) {
                    request.markAsSent()
                }
                eventBus.fire(SentXMLElementEvent(request.stanza, request))
            }
            it.onFailure {
                log.warning(it) { "Problem on filtering stanza $request" }
            }

        }
    }

    private val senderLock = Lock();

    protected open fun onConnecting() {}

    protected open fun onDisconnecting() {}

    fun <T : TakinaModule> getModule(type: String): T = modules.getModule(type)
    fun <T : TakinaModule> getModule(provider: TakinaModuleProvider<T, out Any>): T = modules.getModule(provider.TYPE)

    fun <T : TakinaModule> getModuleOrNull(type: String): T? = modules.getModuleOrNull(type)
    fun <T : TakinaModule> getModuleOrNull(provider: TakinaModuleProvider<T, out Any>): T? =
        modules.getModuleOrNull(provider.TYPE)

    @ReflectionModuleManager
    inline fun <reified T : TakinaModule> getModule(): T = modules.getModule(T::class)

    protected fun startConnector() {
        if (running) {
            log.fine { "Starting connector ($this)" }

            stopConnector()

            sessionController?.stop()
            sessionController = null
            connector = createConnector()
            sessionController = connector!!.createSessionController()

            sessionController!!.start()
            connector!!.start()
        } else throw TakinaException("Client is not running")
    }

    protected fun stopConnector(doAfterDisconnected: (() -> Unit)? = null) {
        if (connector != null || sessionController != null) {
            log.fine { "Stopping connector${if (doAfterDisconnected != null) " (with action after disconnect)" else ""}" }
            if (doAfterDisconnected != null) connector?.let {
                waitForDisconnect(it, doAfterDisconnected)
            }
            if (!running) {
                sessionController?.stop()
                sessionController = null
            }
            connector?.stop()
            connector = null
        }
    }

    protected fun waitForDisconnect(connector: AbstractConnector?, handler: () -> Unit) {
        log.finer { "Waiting for disconnection ($this)" }
        if (connector == null) {
            log.finest { "No connector. Calling handler. ($this)" }
            handler.invoke()
        } else {
            var fired = false
            val h: EventHandler<ConnectorStateChangeEvent> = object : EventHandler<ConnectorStateChangeEvent> {
                override fun onEvent(event: ConnectorStateChangeEvent) {
                    if (!fired && event.newState == lib.takina.core.connector.State.Disconnected) {
                        connector.takina.eventBus.unregister(this)
                        fired = true
                        log.finest { "State changed. Calling handler. ($this)" }
                        handler.invoke()
                    }
                }
            }
            try {
                connector.takina.eventBus.register(ConnectorStateChangeEvent, h)
                if (!fired && connector.state == lib.takina.core.connector.State.Disconnected) {
                    connector.takina.eventBus.unregister(h)
                    fired = true
                    log.finest { "State is Disconnected already. Calling handler. ($this)" }
                    handler.invoke()
                }
            } finally {
            }
        }
    }

    fun connect() {
        clear(Scope.Session)
        this.running = true
        log.info { "Connecting" }
        state = State.Connecting
        onConnecting()
        try {
            startConnector()
        } catch (e: Exception) {
            requestsManager.timeoutAll()
            state = State.Stopped
            throw e
        }
    }

    fun disconnect() {
        try {
            this.running = false
            log.info { "Disconnecting: $this" }

            modules.getModuleOrNull(StreamManagementModule)?.let { module ->
                val ackEnabled =
                    module.isActive
                if (ackEnabled && getConnectorState() == lib.takina.core.connector.State.Connected) {
                    module.sendAck(true)
                }
            }

            state = State.Disconnecting
            onDisconnecting()
            stopConnector()
        } finally {
            clear(Scope.Session)
            requestsManager.timeoutAll()
            state = State.Stopped
        }
    }

    internal fun clear(scope: Scope) {
        internalDataStore.clear(scope)
        val scopes = Scope.values().filter { s -> s.ordinal <= scope.ordinal }.toTypedArray()
        eventBus.fire(ClearedEvent(scopes))
    }

    override fun toString(): String {
        return "AbstractTakina(boundJID=$boundJID, state=$state, running=$running)"
    }
}