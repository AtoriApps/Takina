package lib.takina

import lib.takina.core.AbstractTakina
import lib.takina.core.builder.ConfigurationBuilder
import lib.takina.core.builder.createConfiguration
import lib.takina.core.configuration.JIDPasswordSaslConfig
import lib.takina.core.connector.AbstractConnector
import lib.takina.core.connector.ReceivedXMLElementEvent
import lib.takina.core.connector.SessionController
import lib.takina.core.xml.Element
import lib.takina.core.xml.parser.parseXML
import lib.takina.core.xmpp.FullJID
import lib.takina.core.xmpp.modules.sm.StreamManagementModule
import lib.takina.core.xmpp.toBareJID

val dummyConfig = createConfiguration {
    auth {
        userJID = "user@example.com".toBareJID()
        password { "pencil" }
    }
    install(StreamManagementModule)
}

class DummyTakina(cf: ConfigurationBuilder = dummyConfig) : AbstractTakina(cf) {

    val sentElements = mutableListOf<Element>()

    inner class DummySessionController : SessionController {

        override val takina: AbstractTakina = this@DummyTakina

        override fun start() {
        }

        override fun stop() {
        }
    }

    inner class MockConnector : AbstractConnector(this) {

        override fun createSessionController(): SessionController = DummySessionController()

        override fun send(data: CharSequence) {
            try {
                val pr = parseXML(data.toString())
                pr.let {
                    sentElements.add(it)
                }
            } catch (ignore: Throwable) {
            }
        }

        override fun start() {
            state = lib.takina.core.connector.State.Connected
        }

        override fun stop() {
            state = lib.takina.core.connector.State.Disconnected
        }
    }

    override fun reconnect(immediately: Boolean) = throw NotImplementedError()

    override fun onConnecting() {
        boundJID = (config.sasl as JIDPasswordSaslConfig?)?.userJID?.let { FullJID(it, "1234") }
            ?: throw RuntimeException("No UserJID to bind!")
        requestsManager.boundJID = boundJID
    }

    override fun createConnector(): AbstractConnector = MockConnector()
    fun peekLastSend(): Element? = sentElements.removeLastOrNull()
    fun addReceived(stanza: Element) {
        eventBus.fire(ReceivedXMLElementEvent(stanza))
    }
}