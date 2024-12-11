package org.example

import lib.takina.core.AbstractTakina
import lib.takina.core.builder.createTakina
import lib.takina.core.xmpp.modules.MessageReceivedEvent
import lib.takina.core.xmpp.toBareJID
import lib.takina.core.xmpp.toJID

fun main() {
	val takina = createTakina {
		auth {
			userJID = "yourjid@server.com".toBareJID()
			password { "secretpassword" }
		}
	}
	takina.connectAndWait()

	takina.eventBus.register(MessageReceivedEvent) {
		if (!it.stanza.body.isNullOrEmpty()) {
			takina.request.message {
				to = it.fromJID
				body = "Echo: ${it.stanza.body}"
			}.send()
		}
	}
	takina.eventBus.register(MessageReceivedEvent) {
		if (it.stanza.body == "/stop") {
			println("Stopped")
			takina.disconnect()
		}
	}

	// waiting while client is connected
	while (takina.state == AbstractTakina.State.Connected) Thread.sleep(1000)
}