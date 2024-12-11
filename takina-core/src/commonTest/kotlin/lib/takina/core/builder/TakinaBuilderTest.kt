package lib.takina.core.builder

import lib.takina.core.ReflectionModuleManager
import lib.takina.core.configuration.JIDPasswordSaslConfig
import lib.takina.core.configuration.declaredDomain
import lib.takina.core.xmpp.modules.BindModule
import lib.takina.core.xmpp.modules.MessageModule
import lib.takina.core.xmpp.modules.PingModule
import lib.takina.core.xmpp.modules.auth.AnonymousSaslConfig
import lib.takina.core.xmpp.modules.auth.SASL2Module
import lib.takina.core.xmpp.modules.auth.SASLModule
import lib.takina.core.xmpp.modules.auth.authAnonymous
import lib.takina.core.xmpp.modules.caps.EntityCapabilitiesModule
import lib.takina.core.xmpp.modules.chatmarkers.ChatMarkersModule
import lib.takina.core.xmpp.modules.chatmarkers.ChatMarkersModuleConfig
import lib.takina.core.xmpp.modules.discovery.DiscoveryModule
import lib.takina.core.xmpp.modules.mam.MAMModule
import lib.takina.core.xmpp.modules.mix.MIXModule
import lib.takina.core.xmpp.modules.presence.InMemoryPresenceStore
import lib.takina.core.xmpp.modules.pubsub.PubSubModule
import lib.takina.core.xmpp.modules.roster.InMemoryRosterStore
import lib.takina.core.xmpp.modules.roster.RosterModule
import lib.takina.core.xmpp.toBareJID
import kotlin.test.*

class TakinaBuilderTest {

	@Test
	fun simple_factory() {

		val takina = createTakina {
			auth {
				userJID = "a@localhost".toBareJID()
				password { "a" }
			}
			bind {
				resource = "test00909090"
			}
			presence {
				store = InMemoryPresenceStore()
			}
			roster {
				store = InMemoryRosterStore()
			}
			install(PingModule)
			install(ChatMarkersModule) {
				mode = ChatMarkersModuleConfig.Mode.All
			}
		}
		assertIs<JIDPasswordSaslConfig>(takina.config.sasl).let {
			assertEquals("a@localhost".toBareJID(), it.userJID)
			assertEquals("a", it.passwordCallback.invoke())
		}
		assertEquals("localhost", assertNotNull(takina.config).declaredDomain)
		assertEquals("test00909090", assertNotNull(takina.getModule(BindModule)).resource)
		assertEquals("https://github.com/AtoriApps/Takina", assertNotNull(takina.getModule(EntityCapabilitiesModule)).node)
	}

	@Test
	fun registration_factory() {

		val halyon = createTakina {
			register {
				domain = "localhost"
				registrationFormHandler { form ->
					form.getFieldByVar("username")!!.fieldValue = "user"
					form.getFieldByVar("password")!!.fieldValue = "password"
				}
				registrationHandler {
					it
				}
			}
		}


		assertNull(halyon.config.sasl)
		assertEquals("localhost", halyon.config.declaredDomain)
		assertNotNull(halyon.config.registration).let {
			assertEquals("localhost", it.domain)
			assertNotNull(it.formHandler)
		}

	}

	@Test
	fun anonymous_auth() {
		val cvg = createTakina {
			authAnonymous {
				domain = "example.com"
			}
		}
		assertEquals("example.com", cvg.config.declaredDomain)
		assertIs<AnonymousSaslConfig>(cvg.config.sasl)
	}

	@OptIn(ReflectionModuleManager::class)
	@Test
	fun modules_configuration() {
		val h = createTakina(false) {
			authAnonymous {
				domain = "example.com"
			}
			bind {
				resource = "blahblah"
			}
			install(PingModule)
			install(SASLModule) {
				enabled = false
			}
			install(SASL2Module)
			install(MIXModule)
		}


		assertNotNull(h.getModuleOrNull(DiscoveryModule))
		assertNotNull(h.getModuleOrNull(MIXModule))
		assertNotNull(h.getModuleOrNull(RosterModule))
		assertNotNull(h.getModuleOrNull(MAMModule))

		assertNull(h.getModuleOrNull(MessageModule))
		assertNotNull(h.getModuleOrNull(PingModule))
		assertNotNull(h.getModuleOrNull(PubSubModule))

		assertEquals("blahblah", assertNotNull(h.getModuleOrNull(BindModule)).resource)

		assertFalse(assertNotNull(h.getModuleOrNull(SASLModule)).enabled)
		assertTrue(assertNotNull(h.getModuleOrNull(SASL2Module)).enabled)

		assertEquals("blahblah", h.getModule<BindModule>().resource)
		assertFalse(h.getModule<SASLModule>().enabled)
		assertTrue(h.getModule<SASL2Module>().enabled)

		assertEquals("blahblah", h.getModule<BindModule>(BindModule.TYPE).resource)
		assertFalse(h.getModule<SASLModule>(SASLModule.TYPE).enabled)
		assertTrue(h.getModule<SASL2Module>(SASL2Module.TYPE).enabled)
	}

}