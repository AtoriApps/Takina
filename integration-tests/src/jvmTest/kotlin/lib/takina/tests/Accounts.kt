package lib.takina.tests

import lib.takina.core.Takina
import lib.takina.core.builder.socketConnector
import lib.takina.core.connector.ReceivedXMLElementEvent
import lib.takina.core.connector.SentXMLElementEvent
import lib.takina.core.connector.socket.BouncyCastleTLSProcessor
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.toBareJID
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.util.*

fun loadProperties() = Properties().let { prop ->
	val file = File("./local.properties")
	if (!file.exists()) {
		throw FileNotFoundException(file.absolutePath)
	}
	FileReader(file).use { prop.load(it) }
	Pair<BareJID, String>(
		prop.getProperty("userJID").toBareJID(), prop.getProperty("password")
	)
}

fun createTakina(): Takina {
	val (jid, password) = loadProperties()
	return lib.takina.core.builder.createTakina {
		auth {
			userJID = jid
			password { password }
		}
		socketConnector {
			tlsProcessorFactory = BouncyCastleTLSProcessor
		}
	}.apply {
		eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) {
			println(">> ${it.element.getAsString(showValue = false)}")
		}
		eventBus.register<SentXMLElementEvent>(SentXMLElementEvent.TYPE) {
			println("<< ${it.element.getAsString(showValue = false)}")
		}
	}
}