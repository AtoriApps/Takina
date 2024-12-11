package capabilities

import lib.takina.core.builder.createTakina
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.modules.caps.EntityCapabilitiesModule
import lib.takina.core.xmpp.toBareJID
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
		install(EntityCapabilitiesModule) {
			node = "http://mycompany.com/bestclientever"
		}
	}
	takina.connectAndWait()

	// We have to slow down application, because it needs time to retrieve discover information about server.
	Thread.sleep(1000)

	val caps = takina.getModule(EntityCapabilitiesModule)
		.getServerCapabilities()
	println(caps)


	takina.waitForAllResponses()
	takina.disconnect()
}