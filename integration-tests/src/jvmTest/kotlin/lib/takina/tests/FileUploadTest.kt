package lib.takina.tests

import lib.takina.core.AbstractTakina
import lib.takina.core.ReflectionModuleManager
import lib.takina.core.eventbus.Event
import lib.takina.core.xmpp.modules.fileupload.FileUploadModule
import lib.takina.core.xmpp.modules.fileupload.uploadFile
import lib.takina.core.xmpp.toJID
import java.io.File
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

class FileUploadTest {

	init {
		val logger = Logger.getLogger("takina")
		val handler: Handler = ConsoleHandler()
		handler.level = Level.INFO
		logger.addHandler(handler)
		logger.level = Level.INFO

	}

	@OptIn(ReflectionModuleManager::class)
	@Test
	fun uploadFileTest() {
		val takina = createTakina()

		takina.eventBus.register<Event> {
			println("EVENT: $it")
		}

		takina.connectAndWait()
		println("Connected!")
		assertEquals(AbstractTakina.State.Connected, takina.state, "Client should be connected to server.")


		val file = File("/Users/bmalkow/Downloads/IMG20230428164155.jpg")
		takina.getModule(FileUploadModule).requestSlot(
			"upload.sure.im".toJID(), "testfile.jpg", file.length(), "image/jpg"
		).response {
			println("!!!!>$it")

			uploadFile(file, it.getOrThrow())

		}.send()


		takina.waitForAllResponses()
		assertEquals(0, takina.requestsManager.getWaitingRequestsSize())
		takina.disconnect()
		assertEquals(AbstractTakina.State.Stopped, takina.state, "Client should be connected to server.")
	}


}