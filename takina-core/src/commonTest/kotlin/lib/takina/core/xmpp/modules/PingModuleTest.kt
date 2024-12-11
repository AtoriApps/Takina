package lib.takina.core.xmpp.modules

import lib.takina.DummyTakina
import lib.takina.core.xmpp.stanzas.IQType
import lib.takina.core.xmpp.stanzas.iq
import lib.takina.core.xmpp.toJID
import lib.takina.requestResponse
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail

class PingModuleTest {

	val takina = DummyTakina().apply {
		connect()
	}

	@Test
	fun test_ping() {
		takina.requestResponse<PingModule.Pong> {
			request {
				it.getModule<PingModule>(PingModule.TYPE)
					.ping("entity@faraway.com".toJID())
			}
			expectedRequest {
				iq {
					type = IQType.Get
					to = "entity@faraway.com".toJID()
					"ping" {
						xmlns = "urn:xmpp:ping"
					}
				}
			}
			response {
				iq {
					from = "entity@faraway.com".toJID()
					to = "user@example.scom/1234".toJID()
					type = IQType.Result
				}
			}
			validate {
				assertNotNull(it).let {
					it.onFailure { fail(cause = it) }
					it.onSuccess { }
				}
			}
		}
	}

}