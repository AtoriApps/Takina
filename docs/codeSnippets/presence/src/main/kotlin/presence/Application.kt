package presence

import lib.takina.core.builder.createTakina
import lib.takina.core.xmpp.BareJID
import lib.takina.core.xmpp.modules.presence.PresenceModule
import lib.takina.core.xmpp.modules.presence.typeAndShow
import lib.takina.core.xmpp.stanzas.PresenceType
import lib.takina.core.xmpp.stanzas.Show
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

	// setting own presence
	takina.getModule(PresenceModule)
		.sendPresence(
			show = Show.Chat, status = "I'm ready for party!"
		)
		.send()

	// sending direct presence
	takina.getModule(PresenceModule)
		.sendPresence(
			jid = "mom@server.com".toJID(), show = Show.DnD, status = "I'm doing my homework!"
		)
		.send()

	// subscribe presence of buddy@somewhere.com
	takina.getModule(PresenceModule)
		.sendSubscriptionSet(jid = "buddy@somewhere.com".toJID(), presenceType = PresenceType.Subscribe)
		.send()

	val contactStatus = takina.getModule(PresenceModule)
		.getBestPresenceOf("dad@server.com".toBareJID())
		.typeAndShow()


	takina.waitForAllResponses()
	takina.disconnect()
}