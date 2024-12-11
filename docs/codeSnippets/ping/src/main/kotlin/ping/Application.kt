package ping

import lib.takina.core.builder.createTakina
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.modules.PingModule
import lib.takina.core.xmpp.toBareJID
import lib.takina.core.xmpp.toJID
import java.io.FileReader
import java.util.*

fun main() {
	val (jid, password) = Properties().let { prop ->
		FileReader("../local.properties").use { prop.load(it) }
		Pair<BareJID, String>(
			prop.getProperty("userJID")
				.toBareJID(), prop.getProperty("password")
		)
	}
	val takina = createTakina {
		auth {
			userJID = jid
			password { password }
		}
	}
	takina.connectAndWait()

	takina.getModule(PingModule)
		.ping("tigase.org".toJID())
		.response { result ->
			result.onSuccess { pong -> println("Pong: ${pong.time}ms") }
			result.onFailure { error -> println("Error $error") }
		}
		.send()

	takina.waitForAllResponses()
	takina.disconnect()
}