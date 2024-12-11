package lib.takina.core.xmpp.modules.auth

import lib.takina.DummyTakina
import lib.takina.core.builder.createConfiguration
import lib.takina.core.xml.element
import lib.takina.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SASLModuleTest {

    @Test
    fun configurationBasicTest() {
        val features = element("stream:features") {
            "mechanisms" {
                xmlns = "urn:ietf:params:xml:ns:xmpp-sasl"
                "mechanism" { +"SCRAM-SHA-1" }
                "mechanism" { +"PLAIN" }
            }
            "authentication" {
                xmlns = "urn:xmpp:sasl:2"
                "mechanism" { +"PLAIN" }
            }
        }
        val takina = DummyTakina(createConfiguration {
            auth {
                userJID = "user01@example.com".toBareJID()
                authenticationName = "differentusername"
                password { "secret" }
            }
            install(SASLModule) {}
            install(SASL2Module) {}
        })
        assertNotNull(takina.getModuleOrNull(SASLModule)) { module ->
            assertTrue { module.isAllowed(features) }
        }
        assertNotNull(takina.getModuleOrNull(SASL2Module)) { module ->
            assertTrue { module.isAllowed(features) }
        }
    }

    @Test
    fun configurationEmptyMechanismsTest() {
        val features = element("stream:features") {
            "mechanisms" {
                xmlns = "urn:ietf:params:xml:ns:xmpp-sasl"
                "mechanism" { +"SCRAM-SHA-1" }
                "mechanism" { +"PLAIN" }
            }
            "authentication" {
                xmlns = "urn:xmpp:sasl:2"
                "mechanism" { +"PLAIN" }
            }
        }
        val takina = DummyTakina(createConfiguration {
            auth {
                userJID = "user01@example.com".toBareJID()
                authenticationName = "differentusername"
                password { "secret" }
            }
            install(SASLModule) {
                mechanisms(clear = true) {
                }
            }
            install(SASL2Module) {
                mechanisms(clear = true) {
                }
            }
        })
        assertNotNull(takina.getModuleOrNull(SASLModule)) { module ->
            assertFalse { module.isAllowed(features) }
        }
        assertNotNull(takina.getModuleOrNull(SASL2Module)) { module ->
            assertFalse { module.isAllowed(features) }
        }
    }

    @Test
    fun configurationSingleAddedMechanismsTest() {
        val features = element("stream:features") {
            "mechanisms" {
                xmlns = "urn:ietf:params:xml:ns:xmpp-sasl"
                "mechanism" { +"SCRAM-SHA-1" }
                "mechanism" { +"PLAIN" }
            }
            "authentication" {
                xmlns = "urn:xmpp:sasl:2"
                "mechanism" { +"PLAIN" }
            }
        }
        val takina = DummyTakina(createConfiguration {
            auth {
                userJID = "user01@example.com".toBareJID()
                authenticationName = "differentusername"
                password { "secret" }
            }
            install(SASLModule) {
                mechanisms(clear = true) {
                    install(SASLScramSHA1)
                    install(SASLPlain)
                }
            }
            install(SASL2Module) {
                mechanisms(clear = true) {
                    install(SASLPlain)
                    install(SASLScramSHA1)
                }
            }

        })
        assertNotNull(takina.getModuleOrNull(SASLModule)) { module ->
            assertTrue { module.isAllowed(features) }
        }
        assertNotNull(takina.getModuleOrNull(SASL2Module)) { module ->
            assertTrue { module.isAllowed(features) }
        }
    }

    @Test
    fun configurationSingleAddedMechanismsSecondTest() {
        val features = element("stream:features") {
            "mechanisms" {
                xmlns = "urn:ietf:params:xml:ns:xmpp-sasl"
                "mechanism" { +"SCRAM-SHA-1" }
            }
            "authentication" {
                xmlns = "urn:xmpp:sasl:2"
                "mechanism" { +"PLAIN" }
            }
        }
        val takina = DummyTakina(createConfiguration {
            auth {
                userJID = "user01@example.com".toBareJID()
                authenticationName = "differentusername"
                password { "secret" }
            }
            install(SASLModule) {
                mechanisms(clear = true) {
                    install(SASLScramSHA256)
                }
            }
            install(SASL2Module) {
                mechanisms(clear = true) {
                    install(SASLScramSHA256)
                }
            }

        })
        assertNotNull(takina.getModuleOrNull(SASLModule)) { module ->
            assertFalse { module.isAllowed(features) }
        }
        assertNotNull(takina.getModuleOrNull(SASL2Module)) { module ->
            assertFalse { module.isAllowed(features) }
        }
    }


}