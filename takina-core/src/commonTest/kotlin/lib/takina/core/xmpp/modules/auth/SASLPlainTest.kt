package lib.takina.core.xmpp.modules.auth

import lib.takina.DummyTakina
import lib.takina.core.builder.createConfiguration
import lib.takina.core.fromBase64
import lib.takina.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SASLPlainTest {

	@Test
	fun test_simple_noauthcid() {
		val cfg = createConfiguration {
			auth {
				userJID = "user01@example.com".toBareJID()
				password { "secret" }
			}
		}.build()
		val ctx = SASLContext()
		val sasl = SASLPlain()


		val resp = sasl.evaluateChallenge(null, DummyTakina(), cfg, ctx)
		assertEquals(
			"\u0000user01\u0000secret",
			assertNotNull(resp).fromBase64()
				.decodeToString()
		)
	}

	@Test
	fun test_simple_with_authcid_equals_localpart() {
		val cfg = createConfiguration {
			auth {
				userJID = "user01@example.com".toBareJID()
				authenticationName = "user01"
				password { "secret" }
			}
		}.build()
		val ctx = SASLContext()
		val sasl = SASLPlain()

		val resp = sasl.evaluateChallenge(null,DummyTakina(), cfg, ctx)
		assertEquals(
			"user01@example.com\u0000user01\u0000secret",
			assertNotNull(resp).fromBase64()
				.decodeToString()
		)
	}

	@Test
	fun test_simple_withauthcid() {
		val cfg = createConfiguration {
			auth {
				userJID = "user01@example.com".toBareJID()
				authenticationName = "differentusername"
				password { "secret" }
			}
		}.build()
		val ctx = SASLContext()
		val sasl = SASLPlain()

		val resp = sasl.evaluateChallenge(null,DummyTakina(), cfg, ctx)
		assertEquals(
			"user01@example.com\u0000differentusername\u0000secret",
			assertNotNull(resp).fromBase64()
				.decodeToString()
		)
	}

}