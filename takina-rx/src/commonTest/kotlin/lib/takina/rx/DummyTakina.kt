package lib.takina.rx

import lib.takina.core.AbstractTakina
import lib.takina.core.builder.createConfiguration
import lib.takina.core.connector.AbstractConnector
import lib.takina.core.connector.SessionController
import lib.takina.core.xmpp.toBareJID

class DummyTakina : AbstractTakina(createConfiguration {
	auth {
		userJID = "test@tester.com".toBareJID()
		passwordCallback = { "test" }
	}
}) {

	inner class DummySessionController : SessionController {

		override val takina: AbstractTakina
			get() = TODO("Not yet implemented")

		override fun start() {
		}

		override fun stop() {
		}
	}

	inner class DummyConnector : AbstractConnector(this) {

		override fun createSessionController(): SessionController = DummySessionController()

		override fun send(data: CharSequence) {
		}

		override fun start() {
			state = lib.takina.core.connector.State.Connected
		}

		override fun stop() {
			state = lib.takina.core.connector.State.Disconnected
		}

	}

	override fun reconnect(immediately: Boolean) {
		TODO("Not yet implemented")
	}

	override fun createConnector(): AbstractConnector = DummyConnector()
}