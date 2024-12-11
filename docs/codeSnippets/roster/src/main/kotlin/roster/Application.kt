package roster

import lib.takina.core.builder.createTakina
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.modules.roster.RosterItem
import lib.takina.core.xmpp.modules.roster.RosterModule
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
	}
	takina.connectAndWait()

	// Add new contact
	takina.getModule(RosterModule)
		.addItem(
			RosterItem(
				jid = "contact@somewhere.com".toBareJID(),
				name = "My friend",
			)
		)
		.send()

	// Update contact
	takina.getModule(RosterModule)
		.addItem(
			RosterItem(
				jid = "contact@somewhere.com".toBareJID(),
				name = "My best friend!",
			)
		)
		.send()

	// Update contact
	takina.getModule(RosterModule)
		.deleteItem("contact@somewhere.com".toBareJID())
		.send()



	takina.waitForAllResponses()
	takina.disconnect()
}