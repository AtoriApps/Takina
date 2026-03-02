package org.atoriapps.takina.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.atoriapps.takina.core.connections.SecurityMode
import org.atoriapps.takina.core.models.toBareJid
import org.atoriapps.takina.core.request.IqRequestDsl
import org.atoriapps.takina.core.request.MessageRequestDsl
import org.atoriapps.takina.core.request.PresenceRequestDsl

class DslProviderStyleTest {
    private val alice = "alice@example.com".toBareJid()
    private val bob = "bob@example.com".toBareJid()

    @Test
    fun `account and config dsl supports value and provider forms`() = runTest {
        val takina = createTakina {
            addAccount {
                jid { alice }
                password { "secret" }
                resource { "takina-device" }
                connectTimeoutMillis { 5000 }
                connection {
                    host { "example.com" }
                    port { 5222 }
                    securityMode { SecurityMode.START_TLS }
                    saslMechanisms("SCRAM-SHA-256", "PLAIN")
                    trustAllCertificates = false
                }
            }
            configs {
                reconnect {
                    enabled { true }
                    delayMillis = 100L
                    factor { 1.5 }
                    maxAttempts { 3 }
                }
                observability {
                    sampling { 1.0 }
                    alertThreshold = 0.9
                }
            }
        }
        assertTrue(takina.runtime.accountStates.value.containsKey(alice))
    }

    @Test
    fun `request dsl supports provider form for all fields`() = runTest {
        val message = MessageRequestDsl().apply {
            from { alice }
            to { bob }
            body { "provider-message" }
        }.build()
        assertEquals(alice, message.from)
        assertEquals(bob, message.to)
        assertEquals("provider-message", message.body)

        val presence = PresenceRequestDsl().apply {
            from { alice }
            to { bob }
            show { "chat" }
            status { "online" }
        }.build()
        assertEquals("chat", presence.show)
        assertEquals("online", presence.status)

        val iq = IqRequestDsl().apply {
            from { alice }
            to { bob }
            type { "get" }
            payload { "<query xmlns='jabber:iq:version'/>" }
        }.build()
        assertEquals("get", iq.type)
        assertTrue(iq.payload.contains("jabber:iq:version"))
    }
}
