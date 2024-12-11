
package lib.takina.core.xmpp.modules.pubsub

import lib.takina.DummyTakina
import lib.takina.core.xml.element
import lib.takina.core.xmpp.stanzas.message
import lib.takina.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PubSubTest {

	@Test
	fun testProcessAndEvents() {
		val takina = DummyTakina().apply {
			connect()
		}
		val pubsub = assertNotNull(takina.getModule<PubSubModule>(PubSubModule.TYPE))

		val published = mutableMapOf<String, Any>()
		val retracted = mutableSetOf<String>()

		takina.eventBus.register<PubSubItemEvent>(PubSubItemEvent.TYPE) {
			when (it) {
				is PubSubItemEvent.Published -> published.put(it.itemId!!, it.content ?: true)
				is PubSubItemEvent.Retracted -> retracted.add(it.itemId!!)
			}
		}

		pubsub.process(message {
			from = "pubsub.shakespeare.lit".toJID()
			to = "francisco@denmark.lit".toJID()
			"event" {
				xmlns = "http://jabber.org/protocol/pubsub#event"
				"items" {
					attribute("node", "princely_musings")
					"item" {
						attribute("id", "item-1")
						"data" {
							+"test"
						}
					}
					"item" {
						attribute("id", "item-2")
					}
				}
			}
		})
		assertEquals(2, published.size)
		assertEquals(0, retracted.size)

		assertEquals(element("data") { +"test" }, published["item-1"]!!)
		assertEquals(true, published["item-2"]!!)

		pubsub.process(message {
			from = "pubsub.shakespeare.lit".toJID()
			to = "bernardo@denmark.lit".toJID()
			"event" {
				xmlns = "http://jabber.org/protocol/pubsub#event"
				"items" {
					attribute("node", "princely_musings")
					"retract" {
						attribute("id", "item-3")
					}
				}
			}
		})
		assertEquals(2, published.size)
		assertEquals(1, retracted.size)

		assertTrue(retracted.contains("item-3"))

	}

}