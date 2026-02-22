package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.CapabilitiesComponent
import org.atoriapps.takina.core.components.CarbonsComponent
import org.atoriapps.takina.core.components.ConnectionDiscoveryComponent
import org.atoriapps.takina.core.components.CsiPushComponent
import org.atoriapps.takina.core.components.DiscoveryComponent
import org.atoriapps.takina.core.components.HttpUploadComponent
import org.atoriapps.takina.core.components.MamComponent
import org.atoriapps.takina.core.components.MessageReceiptsComponent
import org.atoriapps.takina.core.components.MucComponent
import org.atoriapps.takina.core.components.OmemoComponent
import org.atoriapps.takina.core.components.RosterComponent
import org.atoriapps.takina.core.components.StreamManagementComponent
import org.atoriapps.takina.core.components.discovery
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.exceptions.AmbiguousAccountException
import org.atoriapps.takina.core.exceptions.InvalidRequestException
import org.atoriapps.takina.core.requests.EncryptionMethod
import org.atoriapps.takina.core.xmpp.createBareJid
import org.atoriapps.takina.core.xmpp.toBareJid
import org.atoriapps.takina.core.xmpp.toJid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommonInitTest {
    @Test
    fun parseJid_shouldNormalizeDomain() {
        val jid = "Alice@Example.COM/Phone".toJid()
        assertEquals("example.com", jid.domain)
        assertEquals("Phone", jid.resource)
    }

    @Test
    fun messageRequest_shouldBuildXml() {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }

        val xml = takina.request.message {
            from = "alice@example.com".toBareJid()
            to = "bob@example.com".toBareJid()
            body = "Hello"
        }.toXml()

        assertTrue(xml.contains("<message"))
        assertTrue(xml.contains("to='bob@example.com'"))
        assertTrue(xml.contains("<body>Hello</body>"))
    }

    @Test
    fun messageRequest_withoutTo_shouldFail() {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }
        assertFailsWith<InvalidRequestException> {
            takina.request.message {
                body = "no destination"
            }
        }
    }

    @Test
    fun messageRequest_encryptionWithoutMethod_shouldFail() {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }

        assertFailsWith<InvalidRequestException> {
            takina.request.message {
                to = "bob@example.com".toBareJid()
                body = "secret"
                encryption {
                    fallbackBody = "encrypted"
                }
            }
        }
    }

    @Test
    fun messageRequest_omemoWithoutBody_shouldFail() {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }

        assertFailsWith<InvalidRequestException> {
            takina.request.message {
                to = "bob@example.com".toBareJid()
                subject = "only-subject"
                encryption {
                    method = EncryptionMethod.OMEMO
                }
            }
        }
    }

    @Test
    fun sendWithoutFrom_whenMultipleAccounts_shouldFailBeforeNetwork() {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
                endpoint(securityMode = SecurityMode.PLAIN, host = "example.com")
            }
            addAccount {
                jid = "carol@example.com".toBareJid()
                password { "password" }
                endpoint(securityMode = SecurityMode.PLAIN, host = "example.com")
            }
        }

        val request = takina.request.message {
            to = "bob@example.com".toBareJid()
            body = "Hello"
        }

        assertFailsWith<AmbiguousAccountException> {
            request.send()
        }
    }

    @Test
    fun connectionConfig_shouldUseProvidedPort() {
        val config = ConnectionConfig(
            jid = "demo@example.com".toBareJid(),
            host = "xmpp.example.com",
            securityMode = SecurityMode.DIRECT_TLS,
            port = 443,
        )
        assertEquals(443, config.port)
    }

    @Test
    fun accountFields_shouldSupportLazyProviderStyle() {
        var suffix = "one"
        val cfg = TakinaConfiguration()
        cfg.addAccount {
            jid { "alice@$suffix.example.com".toBareJid() }
            password { "pw-$suffix" }
            endpoint {
                host { "xmpp.$suffix.example.com" }
                port { 6000 }
                securityMode { SecurityMode.DIRECT_TLS }
            }
            resource { "resource-$suffix" }
            connectTimeoutMillis { 20_000 }
            streamLanguage { "zh" }
        }
        suffix = "two"

        val account = cfg.accountConfigurations.single()
        val resolved = account.resolveConnectionConfig()
        assertEquals("xmpp.two.example.com", resolved.host)
        assertEquals(6000, resolved.port)
        assertEquals(SecurityMode.DIRECT_TLS, resolved.securityMode)
        assertEquals("resource-two", resolved.resource)
        assertEquals(20_000, resolved.connectTimeoutMillis)
        assertEquals("zh", resolved.streamLanguage)
        assertEquals(createBareJid("alice", "two.example.com"), resolved.jid)
    }

    @Test
    fun endpoint_shouldSupportReceiverDslAndOverrideFields() {
        val cfg = TakinaConfiguration()
        cfg.addAccount {
            jid = "alice@example.com".toBareJid()
            password { "pw" }
            endpoint {
                host { "custom.example.com" }
                port { 7443 }
                securityMode { SecurityMode.DIRECT_TLS }
            }
        }

        val resolved = cfg.accountConfigurations.single().resolveConnectionConfig()
        assertEquals("custom.example.com", resolved.host)
        assertEquals(7443, resolved.port)
        assertEquals(SecurityMode.DIRECT_TLS, resolved.securityMode)
    }

    @Test
    fun accountPassword_shouldSupportDirectFieldAssignment() {
        val cfg = TakinaConfiguration()
        cfg.addAccount {
            jid = "alice@example.com".toBareJid()
            password = "plain-password"
        }

        val provider = cfg.accountConfigurations.single().requirePasswordProvider()
        assertEquals("plain-password", provider())
    }

    @Test
    fun optionalComponent_shouldNotLoadInCoreOnlyMode() {
        val takina = createTakina(registerAllComponents = false) {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }

        assertNull(takina.findComponent(DiscoveryComponent))
        assertFailsWith<IllegalStateException> {
            takina.discovery
        }
    }

    @Test
    fun optionalComponent_shouldLoadWhenRegisteredManually() {
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(DiscoveryComponent)
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }

        assertNotNull(takina.findComponent(DiscoveryComponent))
    }

    @Test
    fun optionalComponent_shouldLoadInRegisterAllMode() {
        val takina = createTakina {
            addAccount {
                jid = "alice@example.com".toBareJid()
                password { "password" }
            }
        }

        assertNotNull(takina.findComponent(CapabilitiesComponent))
        assertNotNull(takina.findComponent(CarbonsComponent))
        assertNotNull(takina.findComponent(DiscoveryComponent))
        assertNotNull(takina.findComponent(MessageReceiptsComponent))
        assertNotNull(takina.findComponent(StreamManagementComponent))
        assertNotNull(takina.findComponent(RosterComponent))
        assertNotNull(takina.findComponent(MamComponent))
        assertNotNull(takina.findComponent(MucComponent))
        assertNotNull(takina.findComponent(OmemoComponent))
        assertNotNull(takina.findComponent(CsiPushComponent))
        assertNotNull(takina.findComponent(HttpUploadComponent))
        assertNotNull(takina.findComponent(ConnectionDiscoveryComponent))
    }
}
