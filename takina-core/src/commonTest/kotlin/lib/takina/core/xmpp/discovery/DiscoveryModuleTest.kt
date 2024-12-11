package lib.takina.core.xmpp.discovery

import lib.takina.DummyTakina
import lib.takina.assertContains
import lib.takina.core.xmpp.modules.discovery.DiscoveryModule
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq
import lib.takina.core.xmpp.toJID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DiscoveryModuleTest {

	val takina = DummyTakina().apply {
		connect()
	}

	@Test
	fun info_function() {
		val disco = takina.getModule(DiscoveryModule)

		var response: DiscoveryModule.Info? = null
		val reqId = disco.info("benvolio@capulet.lit/230193".toJID())
			.response {
				it.onSuccess { response = it }
			}
			.send().id

		assertContains(iq {
			type = IQType.Get
			to = "benvolio@capulet.lit/230193".toJID()
			"query" {
				xmlns = "http://jabber.org/protocol/disco#info"
			}
		}, takina.peekLastSend(), "Invalid output stanza,")


		takina.addReceived(iq {
			type = IQType.Result
			attributes["id"] = reqId
			to = "juliet@capulet.lit/chamber".toJID()
			from = "benvolio@capulet.lit/230193".toJID()
			query("http://jabber.org/protocol/disco#info") {
				attribute("node", "http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w=")
				"identity" {
					attributes["xml:lang"] = "en"
					attributes["name"] = "Psi 0.11"
					attributes["category"] = "client"
					attributes["type"] = "pc"
				}
				"identity" {
					attributes["xml:lang"] = "el"
					attributes["name"] = "Ψ 0.11"
					attributes["category"] = "client"
					attributes["type"] = "pc"
				}
				"feature" { attributes["var"] = "http://jabber.org/protocol/caps" }
				"feature" { attributes["var"] = "http://jabber.org/protocol/disco#info" }
				"feature" { attributes["var"] = "http://jabber.org/protocol/disco#items" }
				"feature" { attributes["var"] = "http://jabber.org/protocol/muc" }
				"x" {
					xmlns = "jabber:x:data"
					attribute("type", "result")
					"field" {
						attribute("type", "hidden")
						attribute("var", "FORM_TYPE")
						"value" { +"urn:xmpp:dataforms:softwareinfo" }
					}
					"field" {
						attribute("type", "text-multi")
						attribute("var", "ip_version")
						"value" { +"ipv4" }
						"value" { +"ipv6" }
					}
					"field" {
						attribute("var", "os")
						"value" { +"Mac" }
					}
					"field" {
						attribute("var", "os_version")
						"value" { +"10.5.1" }
					}
					"field" {
						attribute("var", "software")
						"value" { +"Psi" }
					}
					"field" {
						attribute("var", "software_version")
						"value" { +"0.11" }
					}
				}
			}
		})

		assertNotNull(response).let { info ->
			assertNotNull(info.identities.get(0)).let {
				assertEquals("en", it.lang)
				assertEquals("pc", it.type)
				assertEquals("client", it.category)
				assertEquals("Psi 0.11", it.name)
			}
			assertNotNull(info.identities.get(1)).let {
				assertEquals("el", it.lang)
				assertEquals("pc", it.type)
				assertEquals("client", it.category)
				assertEquals("Ψ 0.11", it.name)
			}
			assertContentEquals(
				listOf(
					"http://jabber.org/protocol/caps",
					"http://jabber.org/protocol/disco#info",
					"http://jabber.org/protocol/disco#items",
					"http://jabber.org/protocol/muc"
				), info.features
			)
			assertNotNull(info.forms.firstOrNull(), "Disco extension is missing") {
				assertEquals("urn:xmpp:dataforms:softwareinfo", it.getFieldByVar("FORM_TYPE")?.fieldValue)
			}
		}

	}
}