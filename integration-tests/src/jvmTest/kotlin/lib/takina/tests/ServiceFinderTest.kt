package lib.takina.tests

import org.junit.Test
import lib.takina.core.xmpp.modules.serviceFinder.ServiceFinderModule
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.assertEquals

class ServiceFinderTest {

	init {
		val logger = Logger.getLogger("takina")
		val handler: Handler = ConsoleHandler()
		handler.level = Level.INFO
		logger.addHandler(handler)
		logger.level = Level.INFO

	}


	@Test
	fun testServiceFinder() {
		val takina = createTakina()
		takina.connectAndWait()

		takina.getModule(ServiceFinderModule).findComponents(predicate = {
			it.features.contains("urn:xmpp:http:upload:0")
//			it.identities.any { it.type=="file" && it.category=="store" }
		}, {
			println("""
				
				
				$it
				
				
				
			""".trimIndent())

			it.getOrThrow().first().forms.forEach {
				println("""
					${it.element.getAsString()}
					
					
				""".trimIndent())
			}


		})


		takina.waitForAllResponses()
		assertEquals(0, takina.requestsManager.getWaitingRequestsSize())
		takina.disconnect()
	}

}