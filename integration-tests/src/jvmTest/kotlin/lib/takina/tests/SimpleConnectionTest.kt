package lib.takina.tests

import lib.takina.core.AbstractTakina
import lib.takina.core.ReflectionModuleManager
import lib.takina.core.configuration.declaredUserJID
import lib.takina.core.connector.ReceivedXMLElementEvent
import lib.takina.core.connector.SentXMLElementEvent
import lib.takina.core.eventbus.Event
import lib.takina.core.exceptions.AuthenticationException
import lib.takina.core.xmpp.modules.auth.SASLModule
import lib.takina.core.xmpp.modules.auth.State
import lib.takina.core.xmpp.toBareJID
import java.util.*
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SimpleConnectionTest {

	init {
		val logger = Logger.getLogger("takina")
		val handler: Handler = ConsoleHandler()
		handler.level = Level.ALL
		logger.addHandler(handler)
		logger.level = Level.ALL

	}

	@OptIn(ReflectionModuleManager::class)
	@Test
	fun simpleConnectionAndDisconnection() {
		val takina = createTakina()

		takina.eventBus.register<Event> {
			println("EVENT: $it")
		}

		takina.connectAndWait()
		println("Connected!")
		assertEquals(AbstractTakina.State.Connected, takina.state, "Client should be connected to server.")
		assertEquals(
			State.Success, takina.modules.getModule<SASLModule>().saslContext.state, "Client should be authenticated."
		)
		assertEquals(takina.config.declaredUserJID, assertNotNull(takina.boundJID).bareJID)


		takina.waitForAllResponses()
		assertEquals(0, takina.requestsManager.getWaitingRequestsSize())
		takina.disconnect()
		assertEquals(AbstractTakina.State.Stopped, takina.state, "Client should be connected to server.")
	}

	@Test
	fun notExistingUserLogin() {
		val (user, password) = loadProperties()
		val takina = lib.takina.core.builder.createTakina {
			auth {
				userJID = "NOT-EXISTING-${UUID.randomUUID()}@${user.domain}".toBareJID()
				password { "sdfasdfsdf${UUID.randomUUID()}90273864trfiuydhjks" }
			}
		}
			.apply {
				eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { println(">> ${it.element.getAsString()}") }
				eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) { println("<< ${it.element.getAsString()}") }
			}

		takina.eventBus.register<Event> {
			println("EVENT: $it")
		}

		assertFailsWith(AuthenticationException::class) {
			takina.connectAndWait()
		}
	}

}