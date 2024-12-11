
package lib.takina.core.xmpp.modules.mix

import lib.takina.DummyTakina
import lib.takina.core.xmpp.modules.roster.RosterItem
import lib.takina.core.xmpp.modules.roster.RosterModule
import lib.takina.core.xmpp.stanzas.message
import lib.takina.core.xmpp.toBareJID
import lib.takina.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertFalse

class MIXModuleTest {

	val takina = DummyTakina().apply {
		connect()
	}

	/**
	 * Problem TAKINA-51
	 */
	@Test
	fun testMIXMessageEventCalling() {
		takina.getModule<RosterModule>(RosterModule.TYPE).store.addItem(
			RosterItem(
				"arturs@mix.tigase.org".toBareJID(), "MIX", annotations = arrayOf(
					MIXRosterItemAnnotation("123")
				)
			)
		)

		val module = takina.getModule<MIXModule>(MIXModule.TYPE)

		var eventCalled = false

		takina.eventBus.register<MIXMessageEvent>(MIXMessageEvent.TYPE) {
			eventCalled = true
		}

		val stanza = message {
			from = "arturs@mix.tigase.org".toJID()
			to = "kobit@tigase.org".toJID()
			attributes["id"] = "4"
			"event" {
				xmlns = "http://jabber.org/protocol/pubsub#event"
				"items" {
					attributes["node"] = "urn:xmpp:mix:nodes:participants"
				}
			}
			"stanza-id" {
				xmlns = "urn:xmpp:sid:0"
				attributes["id"] = "2b40219a-8f51-419f-ab0f-71ee03d278e9"
				attributes["by"] = "kobit@tigase.org"
			}
		}

		if (module.criteria.match(stanza)) module.process(stanza)

		assertFalse(stanza.isMixMessage(), "This is not MIX Message")
		assertFalse(eventCalled, "Event should not be called")
	}
}