package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.CapabilitiesComponent
import org.atoriapps.takina.core.components.StreamManagementComponent
import org.atoriapps.takina.core.components.capabilities
import org.atoriapps.takina.core.components.streamManagement
import org.atoriapps.takina.core.xmpp.toBareJid
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComponentsProtocolTest {
    @Test
    fun capabilitiesComponent_shouldBuildAndParseCapsPayload() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(CapabilitiesComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val capsComponent = takina.capabilities()
        val caps = capsComponent.build(
            node = "https://takina.im",
            ver = "caps-v1",
            hash = "sha-1",
        )

        val presence = takina.request.presence {
            from = jid
            status = "online"
        }.stanza as PresenceStanza
        val withCaps = capsComponent.appendToPresence(presence, caps)
        val xml = withCaps.toXml()

        assertTrue(xml.contains("http://jabber.org/protocol/caps"))
        assertTrue(xml.contains("node='https://takina.im'"))
        assertTrue(xml.contains("ver='caps-v1'"))

        val parsed = capsComponent.parseFromPresenceXml(xml)
        assertNotNull(parsed)
        assertEquals("https://takina.im", parsed.node)
        assertEquals("caps-v1", parsed.ver)
        assertEquals("sha-1", parsed.hash)
    }

    @Test
    fun streamManagementComponent_shouldTrackSessionAndParseFrames() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(StreamManagementComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val sm = takina.streamManagement()
        val state = sm.stateFor(jid)

        assertEquals(
            "<enable xmlns='urn:xmpp:sm:3' resume='true'/>",
            sm.enable().toXmlString(),
        )
        assertEquals(
            "<resume xmlns='urn:xmpp:sm:3' previd='sm-old' h='12'/>",
            sm.resume(previousId = "sm-old", handledByServer = 12).toXmlString(),
        )
        assertEquals("<r xmlns='urn:xmpp:sm:3'/>", sm.ackRequest().toXmlString())
        assertEquals("<a xmlns='urn:xmpp:sm:3' h='3'/>", sm.ack(3).toXmlString())

        val enabled = sm.parseInboundFrame("<enabled xmlns='urn:xmpp:sm:3' id='sm-1' resume='true' max='120'/>")
        assertIs<StreamManagementComponent.InboundFrame.Enabled>(enabled)
        sm.applyInboundFrame(state, enabled)
        assertTrue(state.enabled)
        assertEquals("sm-1", state.sessionId)
        assertTrue(state.allowResume)
        assertEquals(120, state.maxResumeSeconds)

        sm.onOutboundStanzaSent(state)
        sm.onOutboundStanzaSent(state)
        sm.onOutboundStanzaSent(state)
        assertEquals(3, state.outboundSentCount)

        val ack = sm.parseInboundFrame("<sm:a xmlns:sm='urn:xmpp:sm:3' h='2'/>")
        assertIs<StreamManagementComponent.InboundFrame.Acknowledged>(ack)
        sm.applyInboundFrame(state, ack)
        assertEquals(2, state.lastServerAckCount)

        val request = sm.parseInboundFrame("<sm:r xmlns:sm='urn:xmpp:sm:3'/>")
        assertIs<StreamManagementComponent.InboundFrame.AckRequest>(request)

        val resumed = sm.parseInboundFrame("<resumed xmlns='urn:xmpp:sm:3' previd='sm-1' h='3'/>")
        assertIs<StreamManagementComponent.InboundFrame.Resumed>(resumed)
        sm.applyInboundFrame(state, resumed)
        assertTrue(state.resumed)
        assertEquals(3, state.lastServerAckCount)
    }
}
