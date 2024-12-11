package discovery

import lib.takina.core.builder.createTakina
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.modules.caps.EntityCapabilitiesModule
import lib.takina.core.xmpp.modules.discovery.DiscoveryModule
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
		install(DiscoveryModule) {
			clientCategory = "client"
			clientType = "console"
			clientName = "Code Snippet Demo"
			clientVersion = "1.2.3"
		}
	}
	takina.connectAndWait()

	takina.getModule(DiscoveryModule)
		.info("tigase.org".toJID())
		.response { result ->
			result.onFailure { error -> println("Error $error") }
			result.onSuccess { info ->
				println("Received info from ${info.jid}:")
				println("Features " + info.features)
				println(info.identities.joinToString { identity ->
					"${identity.name} (${identity.category}, ${identity.type})"
				})
			}
		}
		.send()

	takina.getModule(DiscoveryModule)
		.items("tigase.org".toJID())
		.response { result ->
			result.onFailure { error -> println("Error $error") }
			result.onSuccess { items ->
				println("Received info from ${items.jid}:")
				println(items.items.joinToString { "${it.name} (${it.jid}, ${it.node})" })
			}
		}
		.send()

	takina.waitForAllResponses()
	takina.disconnect()
}