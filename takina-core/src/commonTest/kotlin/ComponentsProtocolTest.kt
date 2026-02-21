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
import org.atoriapps.takina.core.components.RosterComponent
import org.atoriapps.takina.core.components.StreamManagementComponent
import org.atoriapps.takina.core.components.capabilities
import org.atoriapps.takina.core.components.carbons
import org.atoriapps.takina.core.components.connectionDiscovery
import org.atoriapps.takina.core.components.csiPush
import org.atoriapps.takina.core.components.discovery
import org.atoriapps.takina.core.components.httpUpload
import org.atoriapps.takina.core.components.mam
import org.atoriapps.takina.core.components.muc
import org.atoriapps.takina.core.components.receipts
import org.atoriapps.takina.core.components.roster
import org.atoriapps.takina.core.components.streamManagement
import org.atoriapps.takina.core.connections.ConnectionConfig
import org.atoriapps.takina.core.connections.TakinaConnection
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

        val capsComponent = takina.capabilities
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
    fun capabilitiesComponent_shouldAutoAppendAndCacheWhenEnabled() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(CapabilitiesComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val caps = takina.capabilities
        caps.autoAppendToOutboundPresence = true
        caps.defaultOutboundCapabilities = caps.build(node = "https://takina.im", ver = "caps-auto")
        val connection = TakinaConnection(config = ConnectionConfig(jid = jid), passwordProvider = { "password" })

        val outbound = "<presence from='alice@example.com/takina'/>"
        val outboundAfterIntercept = caps.interceptOutboundFrame(connection, outbound, takina).xml
        assertTrue(outboundAfterIntercept.contains("<c xmlns='http://jabber.org/protocol/caps'"))
        assertTrue(outboundAfterIntercept.contains("node='https://takina.im'"))
        assertTrue(outboundAfterIntercept.contains("ver='caps-auto'"))

        val inbound = "<presence from='bob@example.com/mobile'><c xmlns='http://jabber.org/protocol/caps' hash='sha-1' node='https://bob.im/caps' ver='v2'/></presence>"
        caps.interceptInboundStanza(connection, "presence", inbound, takina)
        val cached = caps.cachedInboundCapabilities("bob@example.com".toBareJid())
        assertNotNull(cached)
        assertEquals("https://bob.im/caps", cached.node)
        assertEquals("v2", cached.ver)
    }

    @Test
    fun discoveryComponent_shouldCaptureInboundIqResults() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(DiscoveryComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val discovery = takina.discovery
        val connection = TakinaConnection(config = ConnectionConfig(jid = jid), passwordProvider = { "password" })
        val discoInfoResult = "<iq from='example.com' to='alice@example.com/takina' type='result' id='disco-1'><query xmlns='http://jabber.org/protocol/disco#info'><feature var='urn:xmpp:sm:3'/></query></iq>"
        val softwareVersionResult = "<iq from='example.com' to='alice@example.com/takina' type='result' id='version-1'><query xmlns='jabber:iq:version'><name>Takina Server</name><version>1.0.0</version></query></iq>"
        discovery.interceptInboundStanza(connection, "iq", discoInfoResult, takina)
        discovery.interceptInboundStanza(connection, "iq", softwareVersionResult, takina)

        val discoCaptured = discovery.latestDiscoInfoResult("example.com".toBareJid())
        val versionCaptured = discovery.latestSoftwareVersionResult("example.com".toBareJid())
        assertNotNull(discoCaptured)
        assertNotNull(versionCaptured)
        assertEquals("disco-1", discoCaptured.id)
        assertEquals("version-1", versionCaptured.id)
        assertEquals(2, discovery.capturedResultsSnapshot().size)
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

        val sm = takina.streamManagement
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
    fun streamManagementComponent_shouldPersistAndRestoreStateViaStore() {
        val jid = "alice@example.com".toBareJid()
        val store = InMemorySmStore()

        val takina = createTakina(registerAllComponents = false) {
            registerComponent(StreamManagementComponent)
            onConfigureComponent(StreamManagementComponent) {
                stateStore = store
                persistStateToStore = true
                restorePersistedStateOnStartup = true
            }
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val sm = takina.streamManagement
        val state = sm.stateFor(jid)
        val enabled = sm.parseInboundFrame("<enabled xmlns='urn:xmpp:sm:3' id='sm-persist' resume='true' max='120'/>")
        assertIs<StreamManagementComponent.InboundFrame.Enabled>(enabled)
        sm.applyInboundFrame(state, enabled)
        sm.onOutboundStanzaSent(state, "<message id='m1'/>")
        sm.onOutboundStanzaSent(state, "<message id='m2'/>")
        sm.onOutboundStanzaSent(state, "<iq id='i3'/>")
        val ack = sm.parseInboundFrame("<a xmlns='urn:xmpp:sm:3' h='2'/>")
        assertIs<StreamManagementComponent.InboundFrame.Acknowledged>(ack)
        sm.applyInboundFrame(state, ack)

        val persisted = store.load(jid)
        assertNotNull(persisted)
        assertEquals("sm-persist", persisted.sessionId)
        assertEquals(3, persisted.outboundSentCount)
        assertEquals(2, persisted.lastServerAckCount)
        assertEquals(listOf("<iq id='i3'/>"), persisted.pendingOutbound)

        val takinaRestored = createTakina(registerAllComponents = false) {
            registerComponent(StreamManagementComponent)
            onConfigureComponent(StreamManagementComponent) {
                stateStore = store
                persistStateToStore = true
                restorePersistedStateOnStartup = true
            }
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val smRestored = takinaRestored.streamManagement
        val restoredState = smRestored.stateFor(jid)
        assertTrue(restoredState.enabled)
        assertEquals("sm-persist", restoredState.sessionId)
        assertTrue(restoredState.allowResume)
        assertEquals(3, restoredState.outboundSentCount)
        assertEquals(2, restoredState.lastServerAckCount)
        assertEquals(listOf("<iq id='i3'/>"), smRestored.snapshotUnackedForResume(restoredState))

        smRestored.clearState(jid)
        assertEquals(null, store.load(jid))
    }

    @Test
    fun streamManagementComponent_shouldNotRestoreWhenFlagDisabled() {
        val jid = "alice@example.com".toBareJid()
        val store = InMemorySmStore()
        store.save(
            jid,
            StreamManagementComponent.PersistedSessionState(
                sessionId = "sm-manual",
                allowResume = true,
                maxResumeSeconds = 120,
                outboundSentCount = 5,
                lastServerAckCount = 4,
                pendingOutbound = listOf("<message id='m5'/>"),
            ),
        )

        val takina = createTakina(registerAllComponents = false) {
            registerComponent(StreamManagementComponent)
            onConfigureComponent(StreamManagementComponent) {
                stateStore = store
                persistStateToStore = true
                restorePersistedStateOnStartup = false
            }
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val sm = takina.streamManagement
        val state = sm.stateFor(jid)
        assertFalse(state.enabled)
        assertEquals(null, state.sessionId)
        assertEquals(emptyList(), sm.snapshotUnackedForResume(state))
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

        val receipts = takina.receipts
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

    @Test
    fun carbonsComponent_shouldBuildIqAndParseForwardedMessage() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(CarbonsComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val carbons = takina.carbons
        val enableXml = carbons.enable(from = jid).toXml()
        assertTrue(enableXml.contains("<iq"))
        assertTrue(enableXml.contains("type='set'"))
        assertTrue(enableXml.contains("<enable xmlns='urn:xmpp:carbons:2'/>"))

        val disableXml = carbons.disable(from = jid).toXml()
        assertTrue(disableXml.contains("<disable xmlns='urn:xmpp:carbons:2'/>"))

        val incoming = """
            <message from='alice@example.com/mobile' to='alice@example.com/desktop' type='chat'>
              <received xmlns='urn:xmpp:carbons:2'>
                <forwarded xmlns='urn:xmpp:forward:0'>
                  <delay xmlns='urn:xmpp:delay' stamp='2026-02-19T10:00:00Z'/>
                  <message from='bob@example.com/phone' to='alice@example.com/mobile' type='chat' id='msg-1'>
                    <body>hello from phone</body>
                  </message>
                </forwarded>
              </received>
            </message>
        """.trimIndent()

        val parsed = carbons.parseEnvelope(incoming)
        assertNotNull(parsed)
        assertEquals(CarbonsComponent.CarbonFrame.Received, parsed.frame)
        assertTrue(parsed.forwardedMessageXml.contains("id='msg-1'"))
        assertTrue(parsed.forwardedMessageXml.contains("<body>hello from phone</body>"))
    }

    @Test
    fun rosterComponent_shouldBuildRosterAndSubscriptionRequests() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(RosterComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val roster = takina.roster
        val getXml = roster.rosterGet(from = jid, version = "ver-1").toXml()
        assertTrue(getXml.contains("jabber:iq:roster"))
        assertTrue(getXml.contains("ver='ver-1'"))
        val getVersioningXml = roster.rosterGet(from = jid, requestVersioning = true).toXml()
        assertTrue(getVersioningXml.contains("ver=''"))

        val setXml = roster.rosterSetItem(jid = "bob@example.com".toBareJid(), name = "Bob", from = jid).toXml()
        assertTrue(setXml.contains("type='set'"))
        assertTrue(setXml.contains("jid='bob@example.com'"))
        assertTrue(setXml.contains("name='Bob'"))

        val subscribeXml = roster.requestSubscription(to = "bob@example.com".toBareJid(), from = jid).toXml()
        assertTrue(subscribeXml.contains("type='subscribe'"))

        val rosterResult = roster.parseRosterResult("<iq type='result' id='r1'><query xmlns='jabber:iq:roster' ver='v2'><item jid='bob@example.com' name='Bob' subscription='both'/></query></iq>")
        assertNotNull(rosterResult)
        assertEquals("v2", rosterResult.version)
        assertEquals(1, rosterResult.items.size)
        assertEquals("both", rosterResult.items.first().subscription)
        assertEquals(emptyList(), rosterResult.items.first().groups)

        val subEvent = roster.parseSubscriptionEvent("<presence from='bob@example.com/phone' to='alice@example.com/takina' type='subscribe'/>")
        assertNotNull(subEvent)
        assertEquals(RosterComponent.SubscriptionAction.REQUEST, subEvent.action)

        val connection = TakinaConnection(config = ConnectionConfig(jid = jid), passwordProvider = { "password" })
        roster.interceptInboundStanza(
            connection = connection,
            stanzaType = "iq",
            xml = "<iq type='result' id='r2'><query xmlns='jabber:iq:roster' ver='v3'><item jid='bob@example.com' name='Bob' subscription='both'><group>Friends</group></item></query></iq>",
            context = takina,
        )
        roster.interceptInboundStanza(
            connection = connection,
            stanzaType = "iq",
            xml = "<iq from='mallory@evil.com' type='set' id='rp1'><query xmlns='jabber:iq:roster' ver='v4'><item jid='mallory@evil.com' name='Mallory' subscription='both'/></query></iq>",
            context = takina,
        )
        val snapshotAfterUnauthorized = roster.snapshot(jid)
        assertNotNull(snapshotAfterUnauthorized)
        assertEquals(1, snapshotAfterUnauthorized.items.size)
        assertEquals(listOf("Friends"), snapshotAfterUnauthorized.items.values.first().groups)

        roster.interceptInboundStanza(
            connection = connection,
            stanzaType = "iq",
            xml = "<iq from='alice@example.com' type='set' id='rp2'><query xmlns='jabber:iq:roster' ver='v5'><item jid='bob@example.com' subscription='remove'/></query></iq>",
            context = takina,
        )
        val snapshotAfterRemove = roster.snapshot(jid)
        assertNotNull(snapshotAfterRemove)
        assertEquals(0, snapshotAfterRemove.items.size)
    }

    @Test
    fun mamComponent_shouldBuildQueryAndParseResult() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(MamComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val mam = takina.mam
        val queryXml = mam.queryArchiveAwait(
            from = jid,
            with = "bob@example.com".toBareJid(),
            pageAfter = "cursor-1",
            pageMax = 20,
        ).toXml()
        assertTrue(queryXml.contains("urn:xmpp:mam:2"))
        assertTrue(queryXml.contains("http://jabber.org/protocol/rsm"))
        assertTrue(queryXml.contains("<after>cursor-1</after>"))

        val message = """
            <message from='example.com' to='alice@example.com/takina'>
              <result xmlns='urn:xmpp:mam:2' queryid='q1' id='m1'>
                <forwarded xmlns='urn:xmpp:forward:0'>
                  <delay xmlns='urn:xmpp:delay' stamp='2026-02-20T00:00:00Z'/>
                  <message from='bob@example.com' to='alice@example.com' type='chat' id='msg-1'>
                    <body>hello</body>
                    <stanza-id xmlns='urn:xmpp:sid:0' by='example.com' id='sid-1'/>
                  </message>
                </forwarded>
              </result>
            </message>
        """.trimIndent()
        val envelope = mam.parseResultEnvelope(message)
        assertNotNull(envelope)
        assertEquals("q1", envelope.queryId)
        assertEquals("m1", envelope.resultId)
        assertEquals("sid-1", envelope.stanzaIds.first().id)

        val fin = mam.parseFin("<iq type='result' id='mam-fin'><fin xmlns='urn:xmpp:mam:2' complete='true' stable='true' queryid='q1'><set xmlns='http://jabber.org/protocol/rsm'><first>f1</first><last>l1</last><count>25</count></set></fin></iq>")
        assertNotNull(fin)
        assertTrue(fin.complete)
        assertEquals(25, fin.rsm?.count)
        assertEquals("l1", mam.nextPageAfter(fin))
        assertEquals("f1", mam.nextPageBefore(fin))

        val finNoSet = mam.parseFin("<iq type='result' id='mam-fin-2'><fin xmlns='urn:xmpp:mam:2' complete='false' stable='true' queryid='q1'/></iq>")
        assertNotNull(finNoSet)
        assertEquals(null, finNoSet.rsm)

        val aggregator = mam.createAggregator(queryId = "q1")
        assertTrue(aggregator.ingest(message))
        assertTrue(aggregator.ingest("<iq type='result' id='mam-fin'><fin xmlns='urn:xmpp:mam:2' complete='true' stable='true' queryid='q1'><set xmlns='http://jabber.org/protocol/rsm'><first>f1</first><last>l2</last><count>26</count></set></fin></iq>"))
        val aggregated = aggregator.snapshot()
        assertEquals(1, aggregated.results.size)
        assertTrue(aggregated.isComplete)
        assertEquals("l2", aggregated.nextPageAfter)
    }

    @Test
    fun mucComponent_shouldBuildJoinInviteAndBookmarks() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(MucComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val muc = takina.muc
        val joinXml = muc.joinRoom(roomJid = "room@conference.example.com".toBareJid(), nick = "alice", from = jid, historyMaxStanzas = 10).toXml()
        assertTrue(joinXml.contains("http://jabber.org/protocol/muc"))
        assertTrue(joinXml.contains("maxstanzas='10'"))

        val inviteXml = muc.directInvite(
            invitee = "bob@example.com".toBareJid(),
            roomJid = "room@conference.example.com".toBareJid(),
            from = jid,
            reason = "快来",
            continueThread = true,
        ).toXml()
        assertTrue(inviteXml.contains("jabber:x:conference"))
        assertTrue(inviteXml.contains("continue='true'"))

        val parsedInvite = muc.parseDirectInvite(inviteXml)
        assertNotNull(parsedInvite)
        assertEquals("room@conference.example.com", parsedInvite.roomJid)

        val bookmarksXml = muc.publishBookmarks2Await(
            bookmarks = listOf(MucComponent.BookmarkRoom(jid = "room@conference.example.com".toBareJid(), name = "工作群", autoJoin = true, nick = "alice")),
            from = jid,
        ).toXml()
        assertTrue(bookmarksXml.contains("urn:xmpp:bookmarks:1"))
        assertTrue(bookmarksXml.contains("<item id='room@conference.example.com'>"))
        assertFalse(bookmarksXml.contains("conference jid='"))
        assertTrue(bookmarksXml.contains("publish-options"))
        assertTrue(bookmarksXml.contains("pubsub#persist_items"))

        val parsedBookmarks = muc.parseBookmarks2Result("<iq type='result'><pubsub xmlns='http://jabber.org/protocol/pubsub'><items node='urn:xmpp:bookmarks:1'><item id='room@conference.example.com'><conference xmlns='urn:xmpp:bookmarks:1' name='工作群' autojoin='true'><nick>alice</nick></conference></item></items></pubsub></iq>")
        assertEquals(1, parsedBookmarks.size)
        assertEquals("room@conference.example.com", parsedBookmarks.first().jid.toString())
        assertEquals("alice", parsedBookmarks.first().nick)

        val retractXml = muc.retractBookmarkAwait(roomJid = "room@conference.example.com".toBareJid(), from = jid).toXml()
        assertTrue(retractXml.contains("<retract node='urn:xmpp:bookmarks:1' notify='true'>"))
        assertTrue(retractXml.contains("<item id='room@conference.example.com'/>"))
    }

    @Test
    fun csiPushComponent_shouldBuildCsiAndPushPayload() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(CsiPushComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val csiPush = takina.csiPush
        assertEquals("<active xmlns='urn:xmpp:csi:0'/>", csiPush.activeElement().toXmlString())
        assertEquals("<inactive xmlns='urn:xmpp:csi:0'/>", csiPush.inactiveElement().toXmlString())

        val enableXml = csiPush.enablePushAwait(pushServiceJid = "push.example.com".toBareJid(), node = "app-node", secret = "sec", from = jid).toXml()
        assertTrue(enableXml.contains("urn:xmpp:push:0"))
        assertTrue(enableXml.contains("node='app-node'"))
        assertTrue(enableXml.contains("secret"))
        val disableWithoutNodeXml = csiPush.disablePushAwait(pushServiceJid = "push.example.com".toBareJid(), from = jid).toXml()
        assertTrue(disableWithoutNodeXml.contains("<disable"))
        assertFalse(disableWithoutNodeXml.contains(" node='"))

        val disco = csiPush.parseDiscoFeatures("<iq type='result'><query xmlns='http://jabber.org/protocol/disco#info'><feature var='urn:xmpp:csi:0'/><feature var='urn:xmpp:push:0'/></query></iq>")
        assertTrue(disco.supportsCsi)
        assertTrue(disco.supportsPush)
    }

    @Test
    fun httpUploadComponent_shouldBuildRequestAndParseSlot() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(HttpUploadComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val upload = takina.httpUpload
        val requestXml = upload.requestSlotAwait(
            filename = "photo.jpg",
            size = 1024,
            contentType = "image/jpeg",
            from = jid,
            to = "upload.example.com".toBareJid(),
        ).toXml()
        assertTrue(requestXml.contains("urn:xmpp:http:upload:0"))
        assertTrue(requestXml.contains("filename='photo.jpg'"))

        val slot = upload.parseSlotResult("<iq type='result' id='slot-1'><slot xmlns='urn:xmpp:http:upload:0'><put url='https://upload.example.com/put'><header name='Authorization'>Bearer token</header><header name='X-Unsafe'>drop-me</header></put><get url='https://upload.example.com/get'/></slot></iq>")
        assertNotNull(slot)
        assertEquals("https://upload.example.com/put", slot.putUrl)
        assertEquals("https://upload.example.com/get", slot.getUrl)
        assertEquals("Bearer token", slot.putHeaders["Authorization"])
        assertEquals(1, slot.putHeadersOrdered.size)

        val insecureSlot = upload.parseSlotResult("<iq type='result' id='slot-2'><slot xmlns='urn:xmpp:http:upload:0'><put url='http://upload.example.com/put'/><get url='http://upload.example.com/get'/></slot></iq>")
        assertEquals(null, insecureSlot)
        upload.allowInsecureHttpSlotUrls = true
        assertNotNull(upload.parseSlotResult("<iq type='result' id='slot-2'><slot xmlns='urn:xmpp:http:upload:0'><put url='http://upload.example.com/put'/><get url='http://upload.example.com/get'/></slot></iq>"))

        val uploadError = upload.parseUploadError("<iq type='error' id='slot-3'><error type='modify'><not-acceptable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/><text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>too large</text><file-too-large xmlns='urn:xmpp:http:upload:0'><max-file-size>4096</max-file-size></file-too-large></error></iq>")
        assertNotNull(uploadError)
        assertEquals("not-acceptable", uploadError.condition)
        assertEquals(4096L, uploadError.maxFileSize)
        assertFalse(uploadError.retryable)
    }

    @Test
    fun connectionDiscoveryComponent_shouldParseDiscoAndHostMeta() {
        val jid = "alice@example.com".toBareJid()
        val takina = createTakina(registerAllComponents = false) {
            registerComponent(ConnectionDiscoveryComponent)
            addAccount {
                this.jid = jid
                password { "password" }
            }
        }

        val discovery = takina.connectionDiscovery
        val result = discovery.parseDiscoFeatures(
            "<iq type='result'><query xmlns='http://jabber.org/protocol/disco#info'><feature var='urn:xmpp:features:tls'/><feature var='urn:xmpp:alt-connections:websocket'/></query></iq>",
        )
        assertTrue(result.supportsDirectTls)
        assertTrue(result.supportsWebSocket)
        assertFalse(result.supportsBosh)

        val endpoints = discovery.parseHostMetaLinks(
            "<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'><Link rel='urn:xmpp:alt-connections:websocket' href='wss://xmpp.example.com/ws'/><Link rel='urn:xmpp:alt-connections:websocket' href='ws://xmpp.example.com/ws'/><Link rel='urn:xmpp:alt-connections:xbosh' href='https://xmpp.example.com/http-bind'/></XRD>",
        )
        assertEquals(2, endpoints.size)

        val jsonEndpoints = discovery.parseHostMetaJsonLinks("""{"links":[{"rel":"urn:xmpp:alt-connections:websocket","href":"wss://xmpp.example.com/ws"},{"rel":"urn:xmpp:alt-connections:xbosh","href":"http://xmpp.example.com/http-bind"}]}""")
        assertEquals(1, jsonEndpoints.size)
        assertEquals(ConnectionDiscoveryComponent.EndpointType.WEBSOCKET, discovery.choosePreferredEndpoint(endpoints)?.type)
        assertEquals(ConnectionDiscoveryComponent.EndpointType.BOSH, discovery.choosePreferredEndpoint(endpoints = endpoints, preferWebSocket = false)?.type)
    }

    private class InMemorySmStore : StreamManagementComponent.StreamManagementStateStore {
        private val data = linkedMapOf<String, StreamManagementComponent.PersistedSessionState>()

        override fun load(jid: org.atoriapps.takina.core.xmpp.BareJid): StreamManagementComponent.PersistedSessionState? = data[jid.toString()]

        override fun save(
            jid: org.atoriapps.takina.core.xmpp.BareJid,
            state: StreamManagementComponent.PersistedSessionState,
        ) {
            data[jid.toString()] = state
        }

        override fun clear(jid: org.atoriapps.takina.core.xmpp.BareJid) {
            data.remove(jid.toString())
        }
    }
}
