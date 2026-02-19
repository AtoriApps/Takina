package org.atoriapps.takina.core

import org.atoriapps.takina.core.components.CapabilitiesComponent
import org.atoriapps.takina.core.components.MessageReceiptsComponent
import org.atoriapps.takina.core.components.StreamManagementComponent
import org.atoriapps.takina.core.components.capabilities
import org.atoriapps.takina.core.components.receipts
import org.atoriapps.takina.core.components.streamManagement
import org.atoriapps.takina.core.xmpp.toBareJid
import org.atoriapps.takina.core.xmpp.stanzas.PresenceStanza
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(sm.shouldAutoReconnect(state))
        sm.markReconnectAttempt(state)
        sm.resetReconnectAttempts(state)

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

        sm.autoAckRequestInterval = 2
        sm.onOutboundStanzaSent(state, "<message id='m1'/>")
        assertFalse(sm.shouldSendAckRequest(state))
        sm.onOutboundStanzaSent(state, "<message id='m2'/>")
        assertTrue(sm.shouldSendAckRequest(state))
        sm.markAckRequestSent(state)
        assertFalse(sm.shouldSendAckRequest(state))
        sm.onOutboundStanzaSent(state, "<iq id='i3'/>")
        assertFalse(sm.shouldSendAckRequest(state))
        assertEquals(3, state.outboundSentCount)
        assertEquals(3, sm.snapshotUnackedForResume(state).size)

        val ack = sm.parseInboundFrame("<sm:a xmlns:sm='urn:xmpp:sm:3' h='2'/>")
        assertIs<StreamManagementComponent.InboundFrame.Acknowledged>(ack)
        sm.applyInboundFrame(state, ack)
        assertFalse(state.awaitingServerAckReply)
        assertEquals(2, state.lastServerAckCount)
        assertEquals(listOf("<iq id='i3'/>"), sm.snapshotUnackedForResume(state))

        val request = sm.parseInboundFrame("<sm:r xmlns:sm='urn:xmpp:sm:3'/>")
        assertIs<StreamManagementComponent.InboundFrame.AckRequest>(request)

        val resumed = sm.parseInboundFrame("<resumed xmlns='urn:xmpp:sm:3' previd='sm-1' h='3'/>")
        assertIs<StreamManagementComponent.InboundFrame.Resumed>(resumed)
        sm.applyInboundFrame(state, resumed)
        assertTrue(state.resumed)
        assertEquals(3, state.lastServerAckCount)

        sm.onOutboundStanzaSent(state, "<message id='resume-pending-1'/>")
        sm.onOutboundStanzaSent(state, "<message id='resume-pending-2'/>")
        sm.markResumeRequested(state, previousId = "sm-1")
        val enabledAfterResume = sm.parseInboundFrame("<enabled xmlns='urn:xmpp:sm:3' id='sm-direct-enabled' resume='true'/>")
        assertIs<StreamManagementComponent.InboundFrame.Enabled>(enabledAfterResume)
        sm.applyInboundFrame(state, enabledAfterResume)
        assertEquals(
            listOf("<message id='resume-pending-1'/>", "<message id='resume-pending-2'/>"),
            sm.consumePendingReplayAfterEnable(state),
        )

        sm.onOutboundStanzaSent(state, "<message id='m4'/>")
        sm.onOutboundStanzaSent(state, "<message id='m5'/>")
        val failed = sm.parseInboundFrame("<failed xmlns='urn:xmpp:sm:3' h='1'/>")
        assertIs<StreamManagementComponent.InboundFrame.Failed>(failed)
        sm.applyInboundFrame(state, failed)
        val replayAfterEnable = sm.consumePendingReplayAfterEnable(state)
        assertEquals(listOf("<message id='m5'/>"), replayAfterEnable)

        val enabledAgain = sm.parseInboundFrame("<enabled xmlns='urn:xmpp:sm:3' id='sm-2' resume='true'/>")
        assertIs<StreamManagementComponent.InboundFrame.Enabled>(enabledAgain)
        sm.applyInboundFrame(state, enabledAgain)
        replayAfterEnable.forEach { xml -> sm.onOutboundStanzaSent(state, xml) }
        assertEquals(1, state.outboundSentCount)

        val resumeReplay = sm.snapshotUnackedForResume(state)
        sm.scheduleResumeReplay(state, resumeReplay)
        resumeReplay.forEach { xml -> sm.onOutboundStanzaSent(state, xml) }
        assertEquals(1, state.outboundSentCount)
    }

    @Test
    fun messageReceiptsComponent_shouldBuildAndParseFrames() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(MessageReceiptsComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val receipts = takina.receipts()
        val message = takina.request.message {
            from = jid
            to = "bob@example.com".toBareJid()
            body = "hello"
        }.stanza

        val withRequest = receipts.appendRequest(message)
        val requestXml = withRequest.toXml()
        assertTrue(requestXml.contains("urn:xmpp:receipts"))
        assertTrue(requestXml.contains("<request"))
        assertIs<MessageReceiptsComponent.ReceiptFrame.Request>(
            receipts.parseFromMessageXml(requestXml),
        )
        val autoReply = receipts.buildAutoReply(
            selfJid = jid,
            inboundMessageXml = requestXml,
        )
        assertNotNull(autoReply)
        val autoReplyXml = autoReply.toXml()
        assertTrue(autoReplyXml.contains("<received"))
        assertTrue(autoReplyXml.contains("id='${message.id}'"))

        val withReceived = receipts.appendReceived(message, messageId = "msg-1")
        val receivedXml = withReceived.toXml()
        val parsed = receipts.parseFromMessageXml(receivedXml)
        assertIs<MessageReceiptsComponent.ReceiptFrame.Received>(parsed)
        assertEquals("msg-1", parsed.id)
    }
}
